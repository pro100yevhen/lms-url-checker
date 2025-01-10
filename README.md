# LMS URL Checker

A Spring Boot application designed to validate links within FoxmindEd LMS (Moodle 4.3.3+) course assignments. This tool helps identify broken or invalid links across all courses, making it easier to maintain course content quality.

## Features

- Fetches all course IDs from the Moodle LMS
- Extracts links from assignment descriptions
- Validates links concurrently with configurable timeout and redirect limits
- Displays results in a clean, responsive web interface
- Shows detailed information including course name, task name, and error messages
- Supports vertical scrolling for easy navigation through results

## Prerequisites

- Java 17 or higher
- Gradle
- Access to a Moodle LMS instance
- Moodle web service token with appropriate permissions

## Setup

1. **Moodle Configuration**
    - Create a new user in Moodle with manager permissions
    - Generate a web service token at: `https://your-moodle-instance/admin/webservice/tokens.php`
    - Select "Moodle mobile web service" for permissions
    - Note: Tokens expire after one month by default. Adjust "Valid until" or uncheck it as needed
    - You need to assign the user to all courses witch you want to check
   

2. **Application Configuration**
   Create `application.properties` or use environment variables:
   ```properties
   moodle.token=${MOODLE_TOKEN}
   moodle.base-url=${MOODLE_BASE_URL}
   ```
    - `MOODLE_TOKEN`: Your Moodle web service token
    - `MOODLE_BASE_URL`: Your Moodle web service endpoint (e.g., `https://lms.foxminded.ua/webservice/rest/server.php`)

## Building and Running

1. **Build the application:**
   ```bash
   gradle clean build
   ```

2. **Run the application:**
   ```bash
   java -jar target/lms-url-checker.jar
   ```

3. **Access the interface:**
   Open `http://localhost:8080/moodle/links` in your browser

## How It Works

1. **Course Discovery**
    - Uses `core_course_get_courses` Moodle API method to fetch all course IDs
    - Requires manager permissions to access all courses

2. **Link Extraction**
    - For each course, calls `mod_assign_get_assignments` to get assignment details
    - Parses assignment descriptions (intro field) to extract URLs
    - Filters for HTTP/HTTPS links only

3. **Link Validation**
    - Validates links concurrently (10 concurrent requests by default)
    - 30-second timeout per request
    - Maximum of 5 redirects allowed
    - Checks HTTP response status codes
    - Handles various error conditions (timeouts, DNS issues, etc.)

4. **Results Display**
    - Shows invalid links only
    - Displays course name, task name, full URL, and error details
    - Links are clickable and open in new tabs
    - Responsive design works on both desktop and mobile

## Configuration Options

Key configuration parameters in `LinkValidatorService`:
```java
private static final int MAX_REDIRECTS = 5;
private static final int TIMEOUT_SECONDS = 30;
private static final int MAX_CONCURRENT_REQUESTS = 10;
```

## Known Limitations

- Some websites may require specific headers for successful validation
- Current implementation achieves approximately 80% accuracy (650 out of 800 links validated successfully in testing)
- Token-based authentication requires periodic token renewal unless configured otherwise

## Contributing

Feel free to submit issues and enhancement requests!
