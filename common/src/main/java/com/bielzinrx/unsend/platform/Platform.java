package com.bielzinrx.unsend.platform;

import java.util.ServiceLoader;

public final class Platform {
    private static volatile IPlatformHelper HELPER;

    private Platform() {}

    public static void bootstrap(IPlatformHelper helper) {
        if (helper == null) return;
        HELPER = helper;
    }

    public static IPlatformHelper get() {
        IPlatformHelper h = HELPER;
        if (h != null) return h;
        synchronized (Platform.class) {
            if (HELPER != null) return HELPER;
            HELPER = load();
            return HELPER;
        }
    }

    private static IPlatformHelper load() {
        ClassLoader modCl = Platform.class.getClassLoader();
        IPlatformHelper found = find(modCl);
        if (found != null) return found;

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null && tccl != modCl) {
            found = find(tccl);
            if (found != null) return found;
        }

        throw new IllegalStateException(
            "[Unsay] No IPlatformHelper found! Call Platform.bootstrap(...) from the loader module, "
                + "or ensure META-INF/services is present.");
    }

    private static IPlatformHelper find(ClassLoader cl) {
        try {
            for (IPlatformHelper helper : ServiceLoader.load(IPlatformHelper.class, cl)) {
                if (helper != null) return helper;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
