package com.littlehorizons.erp.controller.grading;

import com.littlehorizons.erp.exception.ConfigurationException;
import com.littlehorizons.erp.util.AppConfig;

import java.util.Arrays;
import java.util.List;

/**
 * Chooses the FinalGradeStrategy from config.txt. Both keys are optional:
 * grading.final.strategy = simple (default) | weighted, and
 * grading.final.weights = four weights for periods I to IV, for example 20,20,30,30.
 */
public final class GradingStrategies {

    private static final String DEFAULT_WEIGHTS = "20,20,30,30";

    private GradingStrategies() {
    }

    public static FinalGradeStrategy fromConfig(AppConfig config) {
        String name = config.optional("grading.final.strategy", "simple").trim().toLowerCase();
        return switch (name) {
            case "simple" -> new SimpleAverageStrategy();
            case "weighted" -> new WeightedAverageStrategy(parseWeights(config.optional("grading.final.weights", DEFAULT_WEIGHTS)));
            default -> throw new ConfigurationException("grading.final.strategy debe ser «simple» o «weighted», no «" + name + "».");
        };
    }

    private static List<Integer> parseWeights(String text) {
        try {
            return Arrays.stream(text.split(",")).map(String::trim).map(Integer::valueOf).toList();
        } catch (NumberFormatException e) {
            throw new ConfigurationException("grading.final.weights solo admite números separados por coma.");
        }
    }
}
