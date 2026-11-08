package com.svetlio.audiofreedom.spatial;

import android.os.IBinder;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Private parent-pipe lease; no socket, PCM access or arbitrary commands. */
public final class AudioFreedomSpatialLease {
    private static Object service;
    private static Method read, write;
    private static void emit(String state) { System.out.println(state); System.out.flush(); }
    private static boolean enabled() throws Exception { return (Boolean) read.invoke(service); }
    private static void set(boolean value) throws Exception { write.invoke(service, value); }
    private static void syncDirectory(File directory) throws Exception {
        FileDescriptor fd = Os.open(directory.getPath(), OsConstants.O_RDONLY, 0);
        try { Os.fsync(fd); } finally { Os.close(fd); }
    }

    private static final class Ownership {
        final File journal;
        final boolean original;
        private boolean finished;
        Ownership(File file, boolean value) { journal = file; original = value; }
        synchronized void override() throws IOException {
            if (journal.exists() && !journal.delete()) throw new IOException("journal");
            finished = true;
        }
        synchronized void restore() {
            if (finished) return;
            try {
                if (original && !enabled()) set(true);
                if (journal.exists() && !journal.delete()) throw new IOException("journal");
                finished = true;
                emit("RESTORED");
            } catch (Exception error) { emit("RESTORE_FAILED"); }
        }
    }

    private static void save(File journal) throws Exception {
        File temporary = new File(journal.getParentFile(), "ownership.tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) {
            out.write("version=1\noriginal=true\n".getBytes("UTF-8"));
            out.getFD().sync();
        }
        Os.chmod(temporary.getPath(), 0600);
        if (!temporary.renameTo(journal)) throw new IOException("journal");
        syncDirectory(journal.getParentFile());
    }

    private static void recover(File journal) throws Exception {
        if (!journal.exists()) return;
        if (journal.length() > 1024) throw new IOException("journal");
        Properties state = new Properties();
        try (InputStream input = new FileInputStream(journal)) { state.load(input); }
        if (!"1".equals(state.getProperty("version")) || !"true".equals(state.getProperty("original")))
            throw new IOException("journal");
        if (!enabled()) set(true);
        if (!journal.delete()) throw new IOException("journal");
        emit("RESTORED");
    }

    public static void main(String[] args) {
        File directory = null;
        try {
            if (args.length != 2 || !(args[0].equals("lease") || args[0].equals("recover")))
                throw new IllegalArgumentException();
            directory = new File(args[1]).getCanonicalFile();
            if (!directory.getPath().matches("/data/(user(_de)?/[0-9]+|data)/com\\.svetlio\\.audiofreedom/no_backup/spatial-audio"))
                throw new IllegalArgumentException();
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("directory");
            Os.chmod(directory.getPath(), 0700);
            Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "audio");
            service = Class.forName("android.media.IAudioService$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, binder);
            Class<?> type = Class.forName("android.media.IAudioService");
            read = type.getMethod("isSpatializerEnabled");
            write = type.getMethod("setSpatializerEnabled", boolean.class);
            try (RandomAccessFile file = new RandomAccessFile(new File(directory, "lock"), "rw")) {
                FileLock lock = null;
                for (int i = 0; i < 48 && lock == null; ++i) {
                    try { lock = file.getChannel().tryLock(); } catch (OverlappingFileLockException ignored) { }
                    if (lock == null) Thread.sleep(250);
                }
                if (lock == null) { emit("BUSY"); return; }
                try {
                    File journal = new File(directory, "ownership.properties");
                    recover(journal);
                    if (args[0].equals("recover")) { emit("RECOVERED"); return; }
                    boolean original = enabled();
                    if (!original) { emit("UNCHANGED"); return; }
                    save(journal);
                    Ownership ownership = new Ownership(journal, original);
                    Thread hook = new Thread(ownership::restore);
                    Runtime.getRuntime().addShutdownHook(hook);
                    AtomicBoolean running = new AtomicBoolean(true);
                    AtomicLong heartbeat = new AtomicLong(SystemClock.elapsedRealtime());
                    Thread pipe = new Thread(() -> {
                        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in))) {
                            String line;
                            while ((line = input.readLine()) != null) {
                                if ("PING".equals(line)) heartbeat.set(SystemClock.elapsedRealtime());
                                else break;
                            }
                        } catch (IOException ignored) { }
                        running.set(false);
                    }, "AudioFreedom-spatial-parent");
                    pipe.setDaemon(true);
                    pipe.start();
                    try {
                        set(false);
                        if (enabled()) throw new IOException("disable");
                        emit("ACTIVE");
                        while (running.get() && SystemClock.elapsedRealtime() - heartbeat.get() < 15000) {
                            if (enabled()) {
                                ownership.override();
                                emit("OVERRIDDEN");
                                break;
                            }
                            Thread.sleep(1000);
                        }
                    } finally {
                        ownership.restore();
                        Runtime.getRuntime().removeShutdownHook(hook);
                    }
                } finally { lock.release(); }
            }
        } catch (Exception error) {
            emit(directory != null && new File(directory, "ownership.properties").isFile()
                    ? "RESTORE_FAILED" : "ERROR");
        }
    }
}
