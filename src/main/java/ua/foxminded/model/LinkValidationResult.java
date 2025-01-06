package ua.foxminded.model;

public class LinkValidationResult {
    private final String link;
    private final boolean valid;
    private final String courseName;
    private final String taskName;

    public LinkValidationResult(final String link, final boolean valid, final String courseName,
                                final String taskName) {
        this.link = link;
        this.valid = valid;
        this.courseName = courseName;
        this.taskName = taskName;
    }

    public String getLink() {
        return link;
    }

    public boolean isValid() {
        return valid;
    }

    public String getCourseName() {
        return courseName;
    }

    public String getTaskName() {
        return taskName;
    }
}