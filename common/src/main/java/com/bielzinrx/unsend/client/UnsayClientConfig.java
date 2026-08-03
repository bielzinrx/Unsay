package com.bielzinrx.unsend.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Client-only controls and bulk-delete preferences. */
public final class UnsayClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long RELOAD_INTERVAL_MS = 2000L;
    private static UnsayClientConfig instance;
    private static long lastCheckMs;
    private static long lastModified = Long.MIN_VALUE;

    public String quickDeleteNewest = "SHIFT+DELETE|SHIFT+BACKSPACE";
    public String quickDeleteOldest = "CTRL+SHIFT+DELETE|CTRL+SHIFT+BACKSPACE";
    public String deleteSelection = "DELETE";
    public String selectAllVisible = "CTRL+SHIFT+A";
    public String selectionModifier = "CTRL";
    public boolean confirmBulkDelete = true;
    public boolean animations = true;
    public int maxBulkMessages = 50;

    private transient List<KeyChord> newestChords = List.of();
    private transient List<KeyChord> oldestChords = List.of();
    private transient List<KeyChord> deleteSelectionChords = List.of();
    private transient List<KeyChord> selectAllChords = List.of();

    private UnsayClientConfig() {}

    public static synchronized UnsayClientConfig get() {
        long now = System.currentTimeMillis();
        if (instance == null || now - lastCheckMs >= RELOAD_INTERVAL_MS) {
            lastCheckMs = now;
            Path path = path();
            long modified = modified(path);
            if (instance == null || modified != lastModified) {
                instance = load(path);
                lastModified = modified(path);
            }
        }
        return instance;
    }

    public boolean matchesNewest(int keyCode, boolean shift, boolean ctrl, boolean alt) {
        return matches(newestChords, keyCode, shift, ctrl, alt);
    }

    public boolean matchesOldest(int keyCode, boolean shift, boolean ctrl, boolean alt) {
        return matches(oldestChords, keyCode, shift, ctrl, alt);
    }

    public boolean matchesDeleteSelection(int keyCode, boolean shift, boolean ctrl, boolean alt) {
        return matches(deleteSelectionChords, keyCode, shift, ctrl, alt);
    }

    public boolean matchesSelectAll(int keyCode, boolean shift, boolean ctrl, boolean alt) {
        return matches(selectAllChords, keyCode, shift, ctrl, alt);
    }

    public boolean selectionModifierDown(Minecraft mc) {
        if (mc == null || mc.getWindow() == null) return false;
        String mod = selectionModifier == null ? "CTRL" : selectionModifier.trim().toUpperCase(Locale.ROOT);
        long window = mc.getWindow().getWindow();
        return switch (mod) {
            case "SHIFT" -> keyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT);
            case "ALT" -> keyDown(window, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT);
            case "NONE" -> true;
            default -> keyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL);
        };
    }

    public int bulkLimit() {
        return Math.max(2, Math.min(50, maxBulkMessages));
    }

    public static Path path() {
        Minecraft mc = Minecraft.getInstance();
        Path root = mc != null && mc.gameDirectory != null
            ? mc.gameDirectory.toPath() : Path.of(".");
        return root.resolve("config").resolve("unsay-client.json");
    }

    private static UnsayClientConfig load(Path path) {
        UnsayClientConfig cfg = null;
        try {
            if (Files.isRegularFile(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    cfg = GSON.fromJson(reader, UnsayClientConfig.class);
                }
            }
        } catch (Throwable ignored) {
        }
        if (cfg == null) cfg = new UnsayClientConfig();
        cfg.normalize();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(cfg, writer);
            }
        } catch (Throwable ignored) {
        }
        return cfg;
    }

    private void normalize() {
        if (quickDeleteNewest == null || quickDeleteNewest.isBlank()) {
            quickDeleteNewest = "SHIFT+DELETE|SHIFT+BACKSPACE";
        }
        if (quickDeleteOldest == null || quickDeleteOldest.isBlank()) {
            quickDeleteOldest = "CTRL+SHIFT+DELETE|CTRL+SHIFT+BACKSPACE";
        }
        if (deleteSelection == null || deleteSelection.isBlank()) deleteSelection = "DELETE";
        if (selectAllVisible == null || selectAllVisible.isBlank()) selectAllVisible = "CTRL+SHIFT+A";
        if (selectionModifier == null || selectionModifier.isBlank()) selectionModifier = "CTRL";
        maxBulkMessages = bulkLimit();
        newestChords = parseAlternatives(quickDeleteNewest);
        oldestChords = parseAlternatives(quickDeleteOldest);
        deleteSelectionChords = parseAlternatives(deleteSelection);
        selectAllChords = parseAlternatives(selectAllVisible);
    }

    private static boolean matches(List<KeyChord> chords, int keyCode, boolean shift, boolean ctrl, boolean alt) {
        for (KeyChord chord : chords) {
            if (chord.matches(keyCode, shift, ctrl, alt)) return true;
        }
        return false;
    }

    private static List<KeyChord> parseAlternatives(String spec) {
        List<KeyChord> out = new ArrayList<>();
        if (spec == null) return out;
        for (String raw : spec.split("\\|")) {
            KeyChord chord = KeyChord.parse(raw);
            if (chord != null) out.add(chord);
        }
        return out;
    }

    private static boolean keyDown(long window, int left, int right) {
        return InputConstants.isKeyDown(window, left) || InputConstants.isKeyDown(window, right);
    }

    private static long modified(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.getLastModifiedTime(path).toMillis() : -1L;
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private record KeyChord(int keyCode, boolean shift, boolean ctrl, boolean alt) {
        boolean matches(int key, boolean shiftDown, boolean ctrlDown, boolean altDown) {
            return key == keyCode && shift == shiftDown && ctrl == ctrlDown && alt == altDown;
        }

        static KeyChord parse(String spec) {
            if (spec == null || spec.isBlank()) return null;
            boolean shift = false, ctrl = false, alt = false;
            String keyName = null;
            for (String part : spec.trim().toUpperCase(Locale.ROOT).split("\\+")) {
                String token = part.trim().replace(' ', '_');
                switch (token) {
                    case "SHIFT" -> shift = true;
                    case "CTRL", "CONTROL" -> ctrl = true;
                    case "ALT" -> alt = true;
                    default -> keyName = token;
                }
            }
            int code = resolveKey(keyName);
            return code == GLFW.GLFW_KEY_UNKNOWN ? null : new KeyChord(code, shift, ctrl, alt);
        }

        private static int resolveKey(String token) {
            if (token == null || token.isBlank()) return GLFW.GLFW_KEY_UNKNOWN;
            String name = switch (token) {
                case "DEL" -> "DELETE";
                case "BKSP" -> "BACKSPACE";
                case "RETURN" -> "ENTER";
                case "PGUP" -> "PAGE_UP";
                case "PGDN" -> "PAGE_DOWN";
                default -> token;
            };
            try {
                if (name.startsWith("KEY_")) return Integer.parseInt(name.substring(4));
                Field field = GLFW.class.getField("GLFW_KEY_" + name);
                return field.getInt(null);
            } catch (Throwable ignored) {
                return GLFW.GLFW_KEY_UNKNOWN;
            }
        }
    }
}
