package com.littlehorizons.erp.util;

import com.littlehorizons.erp.exception.ConfigurationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads config.txt once and gives the rest of the application its values (database url, user
 * and password). Design pattern: SINGLETON. There is exactly one configuration for the whole
 * program, so a private constructor plus a static getInstance() guarantee that the file is
 * read a single time and every class sees the same values.
 *
 * Secrets live in config.txt, which is listed in .gitignore, so they never reach the
 * repository. File format: one "key=value" per line; blank lines and lines starting with #
 * are ignored.
 */
public final class AppConfig {

    private static final String CONFIG_FILE = "config.txt";
    private static final String UTF8_BOM = "﻿"; // Windows Notepad may add it at the start

    private static AppConfig instance;

    private final Map<String, String> values;

    private AppConfig(Map<String, String> values) {
        this.values = values;
    }

    /** Returns the single instance, reading config.txt the first time it is called. */
    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig(load(Path.of(CONFIG_FILE)));
        }
        return instance;
    }

    /** Value of a key that must exist (an empty value is allowed, e.g. "db.password="). */
    public String required(String key) {
        String value = values.get(key);
        if (value == null) {
            throw new ConfigurationException("Falta la clave '" + key + "' en config.txt.");
        }
        return value;
    }

    /** Value of a key, or the given default when the key is not in the file. */
    public String optional(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    private static Map<String, String> load(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new ConfigurationException("No se encontró config.txt en "
                    + file.toAbsolutePath() + ". Copie config.txt como config.txt "
                    + "y complete sus datos.");
        }
        Map<String, String> map = new HashMap<>();
        // try-with-resources closes the reader even if an exception is thrown.
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.startsWith(UTF8_BOM)) {
                    line = line.substring(1);
                }
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    throw new ConfigurationException("config.txt, línea " + lineNumber
                            + ": formato inválido, se esperaba clave=valor.");
                }
                map.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
        } catch (IOException e) {
            throw new ConfigurationException("No se pudo leer config.txt: " + e.getMessage(), e);
        }
        return map;
    }
}
