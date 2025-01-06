package ua.foxminded.model;

public class LinkValidationResult {
    private final String link;
    private final boolean valid;

    public LinkValidationResult(String link, boolean valid) {
        this.link = link;
        this.valid = valid;
    }

    public String getLink() {
        return link;
    }

    public boolean isValid() {
        return valid;
    }
}