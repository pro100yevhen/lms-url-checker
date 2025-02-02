package ua.foxminded.model;

public record LinkValidationResult(
        String link,
        boolean valid,
        String courseName,
        String taskName,
        String statusMessage
) {}