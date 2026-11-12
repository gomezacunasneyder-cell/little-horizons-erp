package com.littlehorizons.erp.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes application events and errors to error_log.txt (project root). Design pattern:
 * SINGLETON, so every layer appends to the same file through the same object.
 *
 * Rules that make it safe to use anywhere:
 *  - It does NOT depend on config.txt (it must be able to log that config.txt is missing).
 *  - log() is synchronized, so two threads never interleave their lines.
 *  - If the log file cannot be written it reports on System.err and carries on: logging must
 *    never be the reason the application crashes.
 *
 * The user sees a friendly message on the console; the full technical detail (including the
 * stack trace) goes here.
 */
public final class ErrorLogger {

    private static final String LOG_FILE_NAME = "error_log.txt";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ErrorLogger INSTANCE = new ErrorLogger(Path.of(LOG_FILE_NAME));

    private final Path logFile;

    private ErrorLogger(Path logFile) {
        this.logFile = logFile;
    }

    public static ErrorLogger getInstance() {
        return INSTANCE;
    }

    public void info(String message) {
        log(LogLevel.INFO, message, null);
    }

    public void warn(String message) {
        log(LogLevel.WARN, message, null);
    }

    public void error(String message, Throwable cause) {
        log(LogLevel.ERROR, message, cause);
    }

    public synchronized void log(LogLevel level, String message, Throwable cause) {
        try (BufferedWriter writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(LocalDateTime.now().format(TIMESTAMP) + " [" + level + "] " + message);
            writer.newLine();
            if (cause != null) {
                writer.write(stackTraceOf(cause));
            }
        } catch (IOException e) {
            System.err.println("[logger] Could not write to " + LOG_FILE_NAME + ": " + e.getMessage());
        }
    }

    public Path getLogFile() {
        return logFile;
    }

    private static String stackTraceOf(Throwable cause) {
        StringWriter buffer = new StringWriter();
        cause.printStackTrace(new PrintWriter(buffer));
        return buffer.toString();
    }
}
