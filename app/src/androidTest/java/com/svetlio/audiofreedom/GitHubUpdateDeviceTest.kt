package com.svetlio.audiofreedom

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class GitHubUpdateDeviceTest {
    @Test fun appAndDriverChecksReportNetworkStatusFromTheAppProcess() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("verifyGitHub") == "true")
        val app = async { AppUpdateChecker.check(BuildConfig.VERSION_NAME) }
        val driver = async { DriverModuleManager.checkForUpdate("0.9.8-beta") }
        val appResult = app.await()
        val driverResult = driver.await()
        println("GitHub app check: ${appResult.javaClass.simpleName}")
        println("GitHub driver check: ${driverResult.javaClass.simpleName}")
        if (args.getString("expectBlocked") == "true") {
            assertEquals(UpdateCheckResult.NetworkUnavailable, appResult)
            assertEquals(DriverModuleUpdateResult.Failed, driverResult)
        } else {
            assertTrue("App update result: $appResult",
                appResult is UpdateCheckResult.UpToDate || appResult is UpdateCheckResult.UpdateAvailable)
            assertTrue("Driver update result: $driverResult", driverResult != DriverModuleUpdateResult.Failed)
        }
    }
}
