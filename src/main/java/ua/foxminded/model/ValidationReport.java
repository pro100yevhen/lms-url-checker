package ua.foxminded.model;

import java.util.List;

public class ValidationReport {
    private long validCount;
    private long invalidCount;
    private final StringBuilder invalidLinks;

    public ValidationReport() {
        this.validCount = 0;
        this.invalidCount = 0;
        this.invalidLinks = new StringBuilder();
    }

    public static ValidationReport fromResults(List<LinkValidationResult> results) {
        ValidationReport report = new ValidationReport();
        results.forEach(result -> {
            if (result.isValid()) {
                report.validCount++;
            } else {
                report.invalidCount++;
                report.invalidLinks.append(result.getLink()).append("\n");
            }
        });
        return report;
    }

    public ValidationReport merge(ValidationReport other) {
        this.validCount += other.validCount;
        this.invalidCount += other.invalidCount;
        this.invalidLinks.append(other.invalidLinks);
        return this;
    }

    public String generateSummary() {
        return String.format(
                "Checked links: %d\nValid links: %d\nInvalid links: %d\nInvalid links list:\n%s",
                validCount + invalidCount, validCount, invalidCount,
                invalidLinks.length() > 0 ? invalidLinks.toString() : "No invalid links"
        );
    }

    public long getValidCount() {
        return validCount;
    }

    public long getInvalidCount() {
        return invalidCount;
    }

    public StringBuilder getInvalidLinks() {
        return invalidLinks;
    }
}
