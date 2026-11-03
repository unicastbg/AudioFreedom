package com.svetlio.audiofreedom.monitor;

import android.media.AudioAttributes;
import android.os.IBinder;
import java.lang.reflect.Method;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

/** Root is used only to obtain non-anonymized media session IDs, never PCM. */
public final class AudioFreedomSessionMonitor {
    public static void main(String[] args) throws Exception {
        int excludedUid = Integer.parseInt(args[0]);
        boolean once = args.length > 1 && args[1].equals("--once");
        Object binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "audio");
        Object service = Class.forName("android.media.IAudioService$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, binder);
        Method getConfigs = Class.forName("android.media.IAudioService")
                .getMethod("getActivePlaybackConfigurations");
        AtomicBoolean running = new AtomicBoolean(true);
        if (!once) {
            Thread input = new Thread(() -> {
                try { while (System.in.read() != -1) { } } catch (Exception ignored) { }
                running.set(false);
            }, "AudioFreedom-parent-pipe");
            input.setDaemon(true);
            input.start();
        }
        String previous = null;
        do {
            TreeSet<Integer> sessions = new TreeSet<>();
            for (Object config : (List<?>) getConfigs.invoke(service)) {
                Class<?> type = config.getClass();
                boolean active = (Boolean) type.getMethod("isActive").invoke(config);
                int uid = (Integer) type.getMethod("getClientUid").invoke(config);
                int session = (Integer) type.getMethod("getSessionId").invoke(config);
                AudioAttributes attributes = (AudioAttributes) type.getMethod("getAudioAttributes").invoke(config);
                int usage = attributes.getUsage();
                if (active && uid != excludedUid && session > 0 &&
                        (usage == AudioAttributes.USAGE_MEDIA || usage == AudioAttributes.USAGE_GAME))
                    sessions.add(session);
            }
            String line = "SESSIONS:" + sessions.toString().replaceAll("[\\[\\] ]", "");
            if (!line.equals(previous)) {
                System.out.println(line);
                System.out.flush();
                previous = line;
            }
            if (once) break;
            Thread.sleep(1000);
        } while (running.get());
    }
}
