package com.littlehorizons.erp.controller;

/**
 * What every controller method returns to the view: whether it worked, a message for the user
 * and, on success, the data. The view only reads it and prints; it never sees an exception or
 * a database object. T is a generic type parameter, so one class serves for Result<Student>,
 * Result<List<Payment>>, Result<Void>, etc.
 */
public final class Result<T> {

    private final boolean success;
    private final T data;
    private final String message;

    private Result(boolean success, T data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    public static <T> Result<T> ok(T data, String message) {
        return new Result<>(true, data, message);
    }

    public static <T> Result<T> ok(String message) {
        return new Result<>(true, null, message);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(false, null, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }
}
