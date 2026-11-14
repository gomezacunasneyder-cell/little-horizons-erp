package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.Result;

/** Prints the message of a controller Result as a success or an error. */
final class ResultPrinter {

    private final ConsoleOutput output;

    ResultPrinter(ConsoleOutput output) {
        this.output = output;
    }

    void report(Result<?> result) {
        if (result.isSuccess()) {
            output.success(result.getMessage());
        } else {
            output.error(result.getMessage());
        }
    }
}
