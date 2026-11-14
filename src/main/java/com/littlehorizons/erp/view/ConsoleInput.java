package com.littlehorizons.erp.view;

import com.littlehorizons.erp.exception.InputClosedException;
import com.littlehorizons.erp.exception.OperationCancelledException;
import com.littlehorizons.erp.exception.ValidationException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Reads and validates everything the user types. Every read method repeats the question until
 * the answer is valid, showing a clear message each time, so the rest of the application
 * never receives a bad value and never crashes on a typo (letters instead of a number, an
 * impossible date, an empty name...).
 *
 * Typing "cancelar" at any prompt throws OperationCancelledException; each menu catches it and
 * goes back one level.
 *
 * The core is read(prompt, parser): the parser is a lambda (Function) that converts the text
 * into the wanted type or throws ValidationException. All the other methods are just
 * different lambdas passed to it.
 */
public final class ConsoleInput {

    public static final String CANCEL_KEYWORD = "cancelar";

    private final Scanner scanner;
    private final ConsoleOutput out;

    public ConsoleInput(Scanner scanner, ConsoleOutput out) {
        this.scanner = scanner;
        this.out = out;
    }

    /** An input reading from the keyboard (standard input) as UTF-8. */
    public static ConsoleInput fromStandardInput(ConsoleOutput out) {
        return new ConsoleInput(new Scanner(System.in, StandardCharsets.UTF_8), out);
    }

    /** Shows the prompt and returns the trimmed line. Handles "cancelar" and closed input. */
    public String readLine(String prompt) {
        out.prompt(prompt);
        if (!scanner.hasNextLine()) {
            throw new InputClosedException();
        }
        String line = scanner.nextLine().trim();
        if (line.equalsIgnoreCase(CANCEL_KEYWORD)) {
            throw new OperationCancelledException();
        }
        return line;
    }

    /** Asks until the parser accepts the text. The parser throws ValidationException to reject. */
    public <T> T read(String prompt, Function<String, T> parser) {
        while (true) {
            String line = readLine(prompt);
            try {
                return parser.apply(line);
            } catch (ValidationException e) {
                out.error(e.getMessage());
            } catch (NumberFormatException e) {
                out.error("Ingrese un número válido.");
            } catch (DateTimeParseException e) {
                out.error("Fecha inválida. Use el formato aaaa-mm-dd (ejemplo: 2015-03-27).");
            }
        }
    }

    public int readInt(String prompt, int min, int max) {
        return read(prompt, text -> {
            int value = Integer.parseInt(text);
            if (value < min || value > max) {
                throw new ValidationException("Ingrese un número entre " + min + " y " + max + ".");
            }
            return value;
        });
    }

    public String readRequiredText(String prompt, int maxLength) {
        return read(prompt, text -> {
            if (text.isEmpty()) {
                throw new ValidationException("Este dato es obligatorio.");
            }
            return checkLength(text, maxLength);
        });
    }

    /** Optional text: an empty answer returns null. */
    public String readOptionalText(String prompt, int maxLength) {
        return read(prompt, text -> text.isEmpty() ? null : checkLength(text, maxLength));
    }

    /** Required text that must match a pattern (document number, phone, email...). */
    public String readMatching(String prompt, Pattern pattern, String errorMessage) {
        return read(prompt, text -> {
            if (!pattern.matcher(text).matches()) {
                throw new ValidationException(errorMessage);
            }
            return text;
        });
    }

    /** Optional text that, when typed, must match the pattern. Empty returns null. */
    public String readOptionalMatching(String prompt, Pattern pattern, String errorMessage) {
        return read(prompt, text -> {
            if (text.isEmpty()) {
                return null;
            }
            if (!pattern.matcher(text).matches()) {
                throw new ValidationException(errorMessage);
            }
            return text;
        });
    }

    /** Reads a date as yyyy-MM-dd and checks an extra rule (for example "must be in the past"). */
    public LocalDate readDate(String prompt, Predicate<LocalDate> rule, String ruleMessage) {
        return read(prompt, text -> {
            LocalDate date = LocalDate.parse(text);
            if (!rule.test(date)) {
                throw new ValidationException(ruleMessage);
            }
            return date;
        });
    }

    /** Reads a time as HH:mm (24 hours), for example 07:30. */
    public LocalTime readTime(String prompt) {
        return read(prompt, text -> {
            try {
                return LocalTime.parse(text);
            } catch (DateTimeParseException e) {
                throw new ValidationException("Hora inválida. Use el formato HH:mm (ejemplo: 07:30).");
            }
        });
    }

    public boolean readYesNo(String prompt) {
        return read(prompt + " (s/n): ", text -> {
            String answer = text.toLowerCase();
            if (answer.equals("s") || answer.equals("si") || answer.equals("sí")) {
                return true;
            }
            if (answer.equals("n") || answer.equals("no")) {
                return false;
            }
            throw new ValidationException("Responda s (sí) o n (no).");
        });
    }

    /** Shows the options as a numbered list and returns the one the user chooses. */
    public <T> T readOption(String prompt, List<T> options, Function<T, String> label) {
        for (int i = 0; i < options.size(); i++) {
            out.line("  " + (i + 1) + ". " + label.apply(options.get(i)));
        }
        int choice = readInt(prompt, 1, options.size());
        return options.get(choice - 1);
    }

    private String checkLength(String text, int maxLength) {
        if (text.length() > maxLength) {
            throw new ValidationException("Máximo " + maxLength + " caracteres.");
        }
        return text;
    }
}
