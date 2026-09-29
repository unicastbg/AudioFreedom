package com.svetlio.audiofreedom

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.StatFs
import com.svetlio.audiofreedom.assistant.runtime.LocalLlmRuntime
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class AssistantModelPackType {
    Language,
    Voice,
}

internal data class AssistantModelPack(
    val type: AssistantModelPackType,
    val id: String,
    val version: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
)

internal object AssistantModelCatalog {
    val language = AssistantModelPack(
        type = AssistantModelPackType.Language,
        id = "qwen3-0.6b-q4_0",
        version = "q4_0-r1",
        displayName = "Qwen3 0.6B Q4",
        fileName = "language-model.gguf",
        downloadUrl =
            "https://huggingface.co/ggml-org/Qwen3-0.6B-GGUF/resolve/main/" +
                "Qwen3-0.6B-Q4_0.gguf?download=true",
        sizeBytes = 428_970_080L,
        sha256 = "da2572f16c06133561ce56accaa822216f2391ef4d37fba427801cd6736417d4",
    )

    val voice = AssistantModelPack(
        type = AssistantModelPackType.Voice,
        id = "whisper-tiny-multilingual",
        version = "tiny-r1",
        displayName = "Whisper Tiny multilingual",
        fileName = "voice-model.bin",
        downloadUrl =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/" +
                "ggml-tiny.bin?download=true",
        sizeBytes = 77_691_713L,
        sha256 = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
    )
}

internal data class AssistantModelPackInfo(
    val installed: Boolean,
    val sizeBytes: Long = 0,
    val installedPackId: String? = null,
    val installedVersion: String? = null,
) {
    fun hasUpdate(pack: AssistantModelPack): Boolean =
        installed && (installedPackId != pack.id || installedVersion != pack.version)

    fun isCurrent(pack: AssistantModelPack): Boolean =
        installed && installedPackId == pack.id && installedVersion == pack.version
}

internal data class AssistantModelDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
) {
    val percent: Int
        get() = ((downloadedBytes * 100L) / totalBytes.coerceAtLeast(1L))
            .toInt()
            .coerceIn(0, 100)
}

internal object AssistantModelPackStore {
    private const val DirectoryName = "assistant-models"
    private const val MetadataPreferences = "assistant_model_pack_metadata"
    private const val MinimumLanguageModelBytes = 1024L * 1024L
    private const val MaximumLanguageModelBytes = 2L * 1024L * 1024L * 1024L
    private const val StorageHeadroomBytes = 16L * 1024L * 1024L
    private const val MaximumRedirects = 5
    private val GgufMagic = byteArrayOf(
        'G'.code.toByte(),
        'G'.code.toByte(),
        'U'.code.toByte(),
        'F'.code.toByte(),
    )

    fun info(context: Context, pack: AssistantModelPack): AssistantModelPackInfo {
        val model = packFile(context, pack)
        val installed = when (pack.type) {
            AssistantModelPackType.Language ->
                model.isFile &&
                    model.length() in MinimumLanguageModelBytes..MaximumLanguageModelBytes &&
                    hasGgufHeader(model)
            AssistantModelPackType.Voice -> model.isFile && model.length() > 0L
        }
        if (!installed) return AssistantModelPackInfo(installed = false)

        val metadata = metadata(context)
        return AssistantModelPackInfo(
            installed = true,
            sizeBytes = model.length(),
            installedPackId = metadata.getString(metadataKey(pack.type, "id"), null),
            installedVersion = metadata.getString(metadataKey(pack.type, "version"), null),
        )
    }

    fun languageModelPath(context: Context): String? =
        packFile(context, AssistantModelCatalog.language)
            .takeIf { info(context, AssistantModelCatalog.language).installed }
            ?.absolutePath

    fun voiceModelPath(context: Context): String? =
        packFile(context, AssistantModelCatalog.voice)
            .takeIf { info(context, AssistantModelCatalog.voice).installed }
            ?.absolutePath

    fun installImportedLanguageModel(context: Context, source: Uri): AssistantModelPackInfo {
        val pack = AssistantModelCatalog.language
        val directory = modelDirectory(context)
        check(directory.exists() || directory.mkdirs()) { "Unable to create model storage" }
        val temporary = temporaryFile(context, pack)
        val target = packFile(context, pack)
        temporary.delete()
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: error("Unable to open the selected model")
            var copied = 0L
            input.use { sourceStream ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = sourceStream.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= MaximumLanguageModelBytes) {
                            "The selected model is too large"
                        }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            require(copied >= MinimumLanguageModelBytes) {
                "The selected file is too small to be a model"
            }
            require(hasGgufHeader(temporary)) { "The selected file is not a GGUF model" }
            moveIntoPlace(temporary, target)
            clearMetadata(context, pack.type)
            return info(context, pack)
        } finally {
            temporary.delete()
        }
    }

    suspend fun installManagedPack(
        context: Context,
        pack: AssistantModelPack,
        wifiOnly: Boolean,
        onProgress: suspend (AssistantModelDownloadProgress) -> Unit,
    ): AssistantModelPackInfo = withContext(Dispatchers.IO) {
        requireSuitableNetwork(context, wifiOnly)
        val directory = modelDirectory(context)
        check(directory.exists() || directory.mkdirs()) { "Unable to create model storage" }
        val availableBytes = StatFs(directory.absolutePath).availableBytes
        require(availableBytes >= pack.sizeBytes + StorageHeadroomBytes) {
            "Not enough free storage for ${pack.displayName}"
        }

        val temporary = temporaryFile(context, pack)
        val target = packFile(context, pack)
        temporary.delete()
        var connection: HttpURLConnection? = null
        try {
            connection = openDownloadConnection(pack.downloadUrl)
            val contentLength = connection.contentLengthLong
            if (contentLength > 0L && contentLength != pack.sizeBytes) {
                throw IOException("The server returned an unexpected model size")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            var lastReportedPercent = -1
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > pack.sizeBytes) {
                            throw IOException("The downloaded model is larger than expected")
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        val progress = AssistantModelDownloadProgress(copied, pack.sizeBytes)
                        if (progress.percent != lastReportedPercent) {
                            lastReportedPercent = progress.percent
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                    }
                    output.fd.sync()
                }
            }

            require(copied == pack.sizeBytes) { "The model download was incomplete" }
            val actualSha256 = digest.digest().joinToString("") {
                "%02x".format(it.toInt() and 0xff)
            }
            require(actualSha256.equals(pack.sha256, ignoreCase = true)) {
                "Model verification failed"
            }
            if (pack.type == AssistantModelPackType.Language) {
                require(hasGgufHeader(temporary)) { "The downloaded file is not a GGUF model" }
            }
            moveIntoPlace(temporary, target)
            saveMetadata(context, pack)
            withContext(Dispatchers.Main) {
                onProgress(AssistantModelDownloadProgress(pack.sizeBytes, pack.sizeBytes))
            }
            info(context, pack)
        } finally {
            connection?.disconnect()
            temporary.delete()
        }
    }

    fun remove(context: Context, pack: AssistantModelPack) {
        packFile(context, pack).delete()
        temporaryFile(context, pack).delete()
        clearMetadata(context, pack.type)
    }

    private fun requireSuitableNetwork(context: Context, wifiOnly: Boolean) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: error("No internet connection")
        val capabilities = connectivity.getNetworkCapabilities(network)
            ?: error("No internet connection")
        require(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            "No internet connection"
        }
        require(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            "The internet connection is not ready"
        }
        if (wifiOnly) {
            require(!connectivity.isActiveNetworkMetered) {
                "Connect to Wi-Fi or turn off Wi-Fi downloads only"
            }
        }
    }

    private fun openDownloadConnection(downloadUrl: String): HttpURLConnection {
        var currentUrl = URL(downloadUrl)
        repeat(MaximumRedirects + 1) { redirectCount ->
            val connection = currentUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.setRequestProperty("User-Agent", "AudioFreedom/${BuildConfig.VERSION_NAME}")
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) return connection
            if (responseCode in setOf(
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307,
                    308,
                )
            ) {
                val location = connection.getHeaderField("Location")
                    ?: throw IOException("The model download redirect was invalid")
                connection.disconnect()
                if (redirectCount == MaximumRedirects) {
                    throw IOException("Too many model download redirects")
                }
                currentUrl = URL(currentUrl, location)
            } else {
                connection.disconnect()
                throw IOException("Model download failed (HTTP $responseCode)")
            }
        }
        throw IOException("Too many model download redirects")
    }

    private fun saveMetadata(context: Context, pack: AssistantModelPack) {
        check(
            metadata(context).edit()
                .putString(metadataKey(pack.type, "id"), pack.id)
                .putString(metadataKey(pack.type, "version"), pack.version)
                .commit(),
        ) { "Unable to save model information" }
    }

    private fun clearMetadata(context: Context, type: AssistantModelPackType) {
        metadata(context).edit()
            .remove(metadataKey(type, "id"))
            .remove(metadataKey(type, "version"))
            .apply()
    }

    private fun metadata(context: Context) =
        context.getSharedPreferences(MetadataPreferences, Context.MODE_PRIVATE)

    private fun metadataKey(type: AssistantModelPackType, suffix: String) =
        "${type.name.lowercase()}.$suffix"

    private fun moveIntoPlace(temporary: File, target: File) {
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun modelDirectory(context: Context) = File(context.filesDir, DirectoryName)

    private fun packFile(context: Context, pack: AssistantModelPack) =
        File(modelDirectory(context), pack.fileName)

    private fun temporaryFile(context: Context, pack: AssistantModelPack) =
        File(modelDirectory(context), "${pack.fileName}.installing")

    private fun hasGgufHeader(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(GgufMagic.size)
            input.read(header) == header.size && header.contentEquals(GgufMagic)
        }
    }.getOrDefault(false)
}

internal object AssistantRuntimeManager : AssistantEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val operationMutex = Mutex()
    private var applicationContext: Context? = null
    private var runtime: LocalLlmRuntime? = null
    private var unloadJob: Job? = null
    private var keepWarm = AssistantKeepWarm.KeepReady

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun updatePolicy(enabled: Boolean, policy: AssistantKeepWarm) {
        keepWarm = policy
        unloadJob?.cancel()
        unloadJob = null
        if (!enabled) {
            scope.launch { unloadNow() }
        } else {
            scheduleUnloadIfNeeded()
        }
    }

    fun languageModelInfo(): AssistantModelPackInfo =
        applicationContext?.let {
            AssistantModelPackStore.info(it, AssistantModelCatalog.language)
        } ?: AssistantModelPackInfo(installed = false)

    fun voiceModelInfo(): AssistantModelPackInfo =
        applicationContext?.let {
            AssistantModelPackStore.info(it, AssistantModelCatalog.voice)
        } ?: AssistantModelPackInfo(installed = false)

    fun voiceModelPath(): String? =
        applicationContext?.let(AssistantModelPackStore::voiceModelPath)

    suspend fun installImportedLanguageModel(source: Uri): AssistantModelPackInfo =
        operationMutex.withLock {
            val context = checkNotNull(applicationContext) {
                "Assistant runtime is not initialized"
            }
            unloadLocked()
            withContext(Dispatchers.IO) {
                AssistantModelPackStore.installImportedLanguageModel(context, source)
            }
        }

    suspend fun installManagedPack(
        type: AssistantModelPackType,
        wifiOnly: Boolean,
        onProgress: suspend (AssistantModelDownloadProgress) -> Unit,
    ): AssistantModelPackInfo = operationMutex.withLock {
        val context = checkNotNull(applicationContext) { "Assistant runtime is not initialized" }
        val pack = when (type) {
            AssistantModelPackType.Language -> AssistantModelCatalog.language
            AssistantModelPackType.Voice -> AssistantModelCatalog.voice
        }
        if (type == AssistantModelPackType.Language) unloadLocked()
        AssistantModelPackStore.installManagedPack(context, pack, wifiOnly, onProgress)
    }

    suspend fun removeModel(type: AssistantModelPackType): AssistantModelPackInfo =
        operationMutex.withLock {
            val context = checkNotNull(applicationContext) {
                "Assistant runtime is not initialized"
            }
            if (type == AssistantModelPackType.Language) unloadLocked()
            val pack = when (type) {
                AssistantModelPackType.Language -> AssistantModelCatalog.language
                AssistantModelPackType.Voice -> AssistantModelCatalog.voice
            }
            withContext(Dispatchers.IO) { AssistantModelPackStore.remove(context, pack) }
            AssistantModelPackStore.info(context, pack)
        }

    override suspend fun plan(request: AssistantRequest): AssistantPlanResult =
        operationMutex.withLock {
            val context = checkNotNull(applicationContext) {
                "Assistant runtime is not initialized"
            }
            val deterministicResult = BuiltInAssistantEngine.plan(request)
            if (deterministicResult !is AssistantPlanResult.NotUnderstood) {
                return@withLock deterministicResult
            }
            val modelPath = AssistantModelPackStore.languageModelPath(context)
                ?: return@withLock deterministicResult
            unloadJob?.cancel()
            unloadJob = null
            val activeRuntime = runtime ?: LocalLlmRuntime().also { runtime = it }
            if (!activeRuntime.isModelLoaded) {
                activeRuntime.loadModel(modelPath)
            }
            try {
                val output = activeRuntime.generate(
                    systemPrompt = AssistantLlmProtocol.systemPrompt,
                    userPrompt = AssistantLlmProtocol.userPrompt(
                        request,
                        if (request.usePersonalization) {
                            AssistantFeedbackStore.recentExamples(context)
                        } else {
                            emptyList()
                        },
                    ),
                    grammar = AssistantLlmProtocol.outputGrammar,
                )
                AssistantLlmProtocol.parse(output, request)
            } finally {
                scheduleUnloadIfNeeded()
            }
        }

    private fun scheduleUnloadIfNeeded() {
        unloadJob?.cancel()
        val idleMilliseconds = when (keepWarm) {
            AssistantKeepWarm.KeepReady -> return
            AssistantKeepWarm.FiveMinutes -> 5 * 60 * 1000L
            AssistantKeepWarm.OneMinute -> 60 * 1000L
        }
        if (runtime?.isModelLoaded != true) return
        unloadJob = scope.launch {
            delay(idleMilliseconds)
            unloadNow()
        }
    }

    private suspend fun unloadNow() {
        operationMutex.withLock { unloadLocked() }
    }

    private suspend fun unloadLocked() {
        unloadJob?.cancel()
        unloadJob = null
        runtime?.unload()
        runtime = null
    }
}
