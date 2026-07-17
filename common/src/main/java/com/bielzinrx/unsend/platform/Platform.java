package com.bielzinrx.unsend.platform;

import java.util.ServiceLoader;

/** Platform bridge. */
public final class Platform {
    private static volatile IPlatformHelper HELPER;

    private Platform() {}

    /** Call from Fabric/Forge module constructors before any chat handling. */
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
        // Prefer the mod classloader — TCCL on FJP workers is often wrong on Forge.
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
