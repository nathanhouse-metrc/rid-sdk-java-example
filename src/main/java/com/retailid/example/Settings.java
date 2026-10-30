package com.retailid.example;

import com.retailid.api.Json;
import com.retailid.api.RetailIdConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists the Retail ID API settings between runs.
 *
 * <p>Settings are stored as JSON in {@code settings.json} in the working directory (normally the
 * repository root), or the path in {@code $RID_EXAMPLE_SETTINGS} (or the
 * {@code rid.example.settings} system property). The file uses the same keys as the SDK's
 * {@code config.json}, so it can be handed straight to {@code RetailIdClient.fromConfig(path)}.
 * It holds API keys, so it is git-ignored; never commit it.
 */
public final class Settings {

    public static final String ENV_VAR = "RID_EXAMPLE_SETTINGS";
    public static final String SYSTEM_PROPERTY = "rid.example.settings";
    public static final String DEFAULT_FILE = "settings.json";

    /** Fields shown on the Settings tab, in order. */
    public static final List<String> FIELDS = List.of("vendor_api_key", "user_api_key", "state_code", "base_url");

    /** Remembered between runs for convenience; not a secret. */
    public static final String LICENSE_FIELD = "license_number";

    private Settings() {
    }

    public static Path path() {
        String override = System.getProperty(SYSTEM_PROPERTY);
        if (override == null || override.isBlank()) {
            override = System.getenv(ENV_VAR);
        }
        return Paths.get(override == null || override.isBlank() ? DEFAULT_FILE : override).toAbsolutePath();
    }

    /** Shown instead of the real path when set (the screenshot tool uses a temporary file). */
    static String displayPathOverride;

    /** The settings path for showing in the UI: relative to the working directory, or with ~ for home. */
    public static String displayPath() {
        return displayPathOverride != null ? displayPathOverride : displayPath(path());
    }

    static String displayPath(Path path) {
        Path normal = path.toAbsolutePath().normalize();
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        if (normal.startsWith(cwd)) {
            return cwd.relativize(normal).toString();
        }
        Path home = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
        if (normal.startsWith(home)) {
            return Paths.get("~").resolve(home.relativize(normal)).toString();
        }
        return normal.toString();
    }

    /** Returns the saved settings, or empty values if nothing has been saved yet. */
    public static Map<String, String> load() {
        return load(path());
    }

    public static Map<String, String> load(Path path) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : FIELDS) {
            values.put(key, "");
        }
        values.put(LICENSE_FIELD, "");
        Map<String, Object> data = readObject(path);
        for (String key : values.keySet()) {
            Object value = data.get(key);
            if (value != null) {
                values.put(key, value.toString());
            }
        }
        return values;
    }

    /** Writes the settings, keeping any extra keys (timeout, max_retries, ...) already in the file. */
    public static Path save(Map<String, String> values) {
        return save(values, path());
    }

    public static Path save(Map<String, String> values, Path path) {
        Map<String, Object> data = readObject(path);
        for (String key : FIELDS) {
            data.put(key, blankToNull(values.get(key)));
        }
        data.put(LICENSE_FIELD, blankToNull(values.get(LICENSE_FIELD)));
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, JsonText.pretty(data, 4) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + path + ": " + e.getMessage(), e);
        }
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // Not a POSIX file system (Windows); the file inherits the user's folder permissions.
        }
        return path;
    }

    /** Builds an SDK config; throws {@code RetailIdConfigException} if keys or location are missing. */
    public static RetailIdConfig toConfig(Map<String, String> values) {
        Map<String, Object> data = new LinkedHashMap<>();
        values.forEach((key, value) -> data.put(key, blankToNull(value)));
        return RetailIdConfig.fromMap(data);
    }

    private static Map<String, Object> readObject(Path path) {
        try {
            Object parsed = Json.parse(Files.readString(path, StandardCharsets.UTF_8));
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) parsed);
                return map;
            }
        } catch (NoSuchFileException e) {
            // Nothing saved yet.
        } catch (IOException | IllegalArgumentException e) {
            // Unreadable or not JSON: start again rather than refusing to open.
        }
        return new LinkedHashMap<>();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
