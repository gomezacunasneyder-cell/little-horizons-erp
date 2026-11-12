package com.littlehorizons.erp.view;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Everything the application prints goes through this class, so the look of the console
 * (banner, messages, tables) is defined in one place. It only prints: no logic, no database.
 */
public final class ConsoleOutput {

    private static final int BANNER_WIDTH = 46;

    private final PrintStream out;

    public ConsoleOutput(PrintStream out) {
        this.out = out;
    }

    /** A UTF-8 stdout, so accents and box characters are not garbled on Windows consoles. */
    public static PrintStream utf8Stdout() {
        return new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
    }

    public void banner(String title, String subtitle) {
        out.println();
        out.println("╔" + "═".repeat(BANNER_WIDTH) + "╗");
        out.println("║" + pad("   " + title) + "║");
        out.println("║" + pad("   " + subtitle) + "║");
        out.println("╚" + "═".repeat(BANNER_WIDTH) + "╝");
        out.println();
    }

    /** Section title underlined with dashes. */
    public void title(String text) {
        out.println();
        out.println(text);
        out.println("-".repeat(Math.max(text.length(), 20)));
    }

    public void line(String text) {
        out.println(text);
    }

    public void blank() {
        out.println();
    }

    public void success(String message) {
        out.println("[OK] " + message);
    }

    public void error(String message) {
        out.println("[ERROR] " + message);
    }

    public void info(String message) {
        out.println("[INFO] " + message);
    }

    /** Prints a prompt without a line break and flushes it so it appears before the user types. */
    public void prompt(String text) {
        out.print(text);
        out.flush();
    }

    /** Prints a numbered menu: "1. First option", "2. Second option"... */
    public void menu(List<String> options) {
        for (int i = 0; i < options.size(); i++) {
            out.println((i + 1) + ". " + options.get(i));
        }
        out.println();
    }

    /** Prints a table with columns as wide as their longest cell. */
    public void table(List<String> headers, List<List<String>> rows) {
        if (rows.isEmpty()) {
            info("Sin resultados.");
            return;
        }
        int[] widths = new int[headers.size()];
        for (int c = 0; c < headers.size(); c++) {
            widths[c] = headers.get(c).length();
        }
        for (List<String> row : rows) {
            for (int c = 0; c < widths.length; c++) {
                widths[c] = Math.max(widths[c], cell(row.get(c)).length());
            }
        }
        String separator = separator(widths);
        out.println(separator);
        out.println(formatRow(headers, widths));
        out.println(separator);
        for (List<String> row : rows) {
            out.println(formatRow(row, widths));
        }
        out.println(separator);
    }

    private String formatRow(List<String> cells, int[] widths) {
        StringBuilder row = new StringBuilder("|");
        for (int c = 0; c < widths.length; c++) {
            row.append(' ').append(String.format("%-" + widths[c] + "s", cell(cells.get(c)))).append(" |");
        }
        return row.toString();
    }

    private String separator(int[] widths) {
        StringBuilder line = new StringBuilder("+");
        for (int width : widths) {
            line.append("-".repeat(width + 2)).append('+');
        }
        return line.toString();
    }

    private String cell(String value) {
        return value == null ? "-" : value;
    }

    private String pad(String text) {
        return text + " ".repeat(Math.max(0, BANNER_WIDTH - text.length()));
    }
}
