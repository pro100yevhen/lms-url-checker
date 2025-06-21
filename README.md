# LMS URL Checker

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A Spring Boot application designed to validate links within FoxmindEd LMS (Moodle 4.3.3+) course assignments. This tool helps identify broken or invalid links across all courses, making it easier to maintain course content quality.

## Table of Contents
- [Features](#features)
- [Quick Start](#quick-start)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Running the Application](#running-the-application)
- [Docker Architecture](#docker-architecture)
- [Technical Details](#technical-details)
- [Troubleshooting](#troubleshooting)
- [Known Limitations](#known-limitations)
- [Contributing](#contributing)
- [License](#license)

## Features

- Fetches all course IDs from the Moodle LMS
- Extracts links from assignment descriptions
- Validates links concurrently with configurable timeout and redirect limits
- Displays results in a clean, responsive web interface
- Shows detailed information including course name, task name, and error messages
- Supports vertical scrolling for easy navigation through results
- Docker support for easy deployment

<!-- Screenshot recommendation -->
*Note: Consider adding a screenshot of the application interface here. The application features a clean, responsive interface that displays link validation results in a table format with color-coded status indicators.*

## Quick Start

For the impatient, here's how to get up and running quickly with Docker:

1. Create a `.env` file with your Moodle credentials:
   ```bash
   MOODLE_BASE_URL=https://your-moodle/webservice/rest/server.php
   MOODLE_TOKEN=your_moodle_token
   ```

2. Run with Docker Compose:
   ```bash
   docker-compose -f docker/docker-compose.yml up --build
   ```

3. Open in your browser:
   ```
   http://localhost:8080
   ```

## Prerequisites

### Local Development
- Java 21 or higher
- Gradle 8.5+
- Access to a Moodle LMS instance
- Moodle web service token with appropriate permissions

## Configuration

### Environment Variables
Configure through either `.env` file or Docker environment variables:

| Variable                   | Description                                                                 | Default     |
|----------------------------|-----------------------------------------------------------------------------|-------------|
| `MOODLE_BASE_URL`          | Moodle web service endpoint (e.g., `https://your-moodle/webservice/rest/server.php`) | *Required*  |
| `MOODLE_TOKEN`             | Moodle web service token with manager permissions                           | *Required*  |
| `LINK_CHECKER_TIMEOUT`     | Timeout in seconds for link validation requests                             | 30          |
| `LINK_CHECKER_PARALLELISM` | Number of concurrent link validation requests                               | 10          |
| `CACHE_DURATION_HOURS`     | Duration in hours for which link validation results are cached              | 24          |

### Port Configuration
The application runs on port `8080` by default. To change the exposed port:

1. **Docker Compose**:
   ```yaml
   services:
     lms-checker:
       ports:
         - "YOUR_HOST_PORT:8080"  # e.g., "8090:8080"
   ```
2. **Local Execution**:
   ```properties
   server.port=YOUR_PORT
   ```

## Running the Application

### Docker Deployment (Recommended)
1. Create `.env` file in your project root:
   ```bash
   MOODLE_BASE_URL=your_moodle_ws_endpoint
   MOODLE_TOKEN=your_moodle_token
   # Optional: LINK_CHECKER_TIMEOUT=30
   ```

2. Build and start the container:
   ```bash
   docker-compose -f docker/docker-compose.yml up --build
   ```

3. Access the interface at:
   ```bash
   http://localhost:8080  # or your configured host port
   ```

### Local Execution
1. Build the application:
   ```bash
   gradle clean build
   ```

2. Run with environment variables:
   ```bash
   export MOODLE_BASE_URL=your_moodle_ws_endpoint
   export MOODLE_TOKEN=your_moodle_token
   java -jar build/libs/lms-url-checker-1.0.jar
   ```

3. Access the interface at:
   ```bash
   http://localhost:8080
   ```

## Docker Architecture
- **Multi-stage build**:
    - Build stage: Gradle 8.5 + JDK 21
    - Runtime stage: Eclipse Temurin 21 JDK Alpine
- Optimized layer caching for faster builds
- Lightweight production image (~350MB)

## Technical Details

### Link Validation Logic
| Parameter              | Default | Configurable Via                |
|------------------------|---------|----------------------------------|
| Timeout                | 30s     | `LINK_CHECKER_TIMEOUT` env var   |
| Max Redirects          | 5       | Code constant in `LinkValidatorService` |
| Concurrent Requests    | 10      | Code constant in `LinkValidatorService` |

### Moodle Integration
- Required permissions:
    - `core_course_get_courses`
    - `mod_assign_get_assignments`
- Web service user must be enrolled in all courses to check

## Troubleshooting

**Common Issues**:
- *403 Forbidden*: Ensure Moodle token has correct permissions
- *Connection Timeout*: Verify `MOODLE_BASE_URL` is reachable from container/network
- *Empty Results*: Check user course enrollments in Moodle

**View Logs**:
```bash
docker-compose -f docker/docker-compose.yml logs -f
```

## Known Limitations
- First-run latency due to Moodle API response times
- Websites with non-standard ports (e.g., :8443) show sanitized errors
- Token rotation required per Moodle security policy

## Contributing
PRs and issues welcome! Please include:
- Detailed reproduction steps for bugs
- Screenshots for UI changes
- Updated tests for new features

## License
This project is licensed under the MIT License – see the LICENSE file for details.
