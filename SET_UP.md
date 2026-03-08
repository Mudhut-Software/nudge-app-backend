# Nudge App Backend - Setup Guide

## Prerequisites

- **Java 17** (required)
- **PostgreSQL** (running locally or accessible remotely)
- **Maven 3.9.7** (included via Maven Wrapper)

## Project Overview

A Spring Boot 3.4.2 application written in Kotlin, using PostgreSQL, Spring Security with JWT authentication, and email services (SMTP / MailerSend).

## Environment Variables

The project uses [spring-dotenv](https://github.com/paulschwarz/spring-dotenv-archived) to load variables from a `.env` file in the project root.

Create a `.env` file with the following variables:

```properties
# Development
DEV_DB_URL=jdbc:postgresql://localhost:5432/nudge_dev
DEV_DB_USERNAME=postgres
DEV_DB_PASSWORD=postgres
DEV_EMAIL_USERNAME=your-email@gmail.com
DEV_EMAIL_PASSWORD=your-app-password
MAILERSEND_API_TOKEN=your-mailersend-token

# Testing
TEST_DB_URL=jdbc:postgresql://localhost:5432/nudge_test
TEST_DB_USERNAME=postgres
TEST_DB_PASSWORD=postgres

# Staging
STAGING_DB_URL=jdbc:postgresql://staging-db-host:5432/nudge_staging
STAGING_DB_USERNAME=staging_user
STAGING_DB_PASSWORD=staging_password

# Production
PROD_DB_URL=jdbc:postgresql://prod-db-host:5432/nudge_prod
PROD_DB_USERNAME=prod_user
PROD_DB_PASSWORD=prod_secure_password
PROD_EMAIL_USERNAME=your-email@gmail.com
PROD_EMAIL_PASSWORD=your-app-password

# SSL (Production only)
SSL_KEYSTORE_PATH=/path/to/keystore.p12
SSL_KEYSTORE_PASSWORD=keystore_password

# JWT (All Environments)
JWT_SECRET=your-jwt-secret-key
ACCESS_TOKEN_EXPIRY_IN_MILLIS=604800
REFRESH_TOKEN_EXPIRY_IN_MILLIS=15552000
```

## Database Setup

Create the required PostgreSQL databases:

```bash
psql -U postgres -c "CREATE DATABASE nudge_dev;"
psql -U postgres -c "CREATE DATABASE nudge_test;"
```

## Profiles

The application has four profiles defined in `src/main/resources/`:

| Profile     | File                           | DDL Auto       | SQL Logging | Notes                                                  |
|-------------|--------------------------------|----------------|-------------|--------------------------------------------------------|
| **dev**     | `application-dev.properties`   | `update`       | Yes         | Default profile. HikariCP pool (max 20). SMTP email.  |
| **test**    | `application-test.properties`  | `create-drop`  | Yes         | Recreates schema on each run. For automated tests.     |
| **staging** | `application-staging.properties` | `validate`   | No          | Validates schema without modifications.                |
| **prod**    | `application-prod.properties`  | `none`         | No          | SSL enabled. Hibernate caching and batch tuning.       |

The default active profile is set to `dev` in `application.properties`.

## Running the Application

### Using Maven Wrapper (recommended)

```bash
# Run with default profile (dev)
./mvnw spring-boot:run

# Run with a specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw spring-boot:run -Dspring-boot.run.profiles=test
./mvnw spring-boot:run -Dspring-boot.run.profiles=staging
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

### Using a packaged JAR

```bash
# Build the JAR
./mvnw clean package

# Run with default profile
java -jar target/nudge-0.0.1-SNAPSHOT.jar

# Run with a specific profile
java -jar target/nudge-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
java -jar target/nudge-0.0.1-SNAPSHOT.jar --spring.profiles.active=test
java -jar target/nudge-0.0.1-SNAPSHOT.jar --spring.profiles.active=staging
java -jar target/nudge-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### Running Tests

```bash
./mvnw test
```

## Key Dependencies

| Dependency              | Purpose                            |
|-------------------------|------------------------------------|
| Spring Boot Starter Web | REST API                           |
| Spring Boot Starter JPA | Database access (Hibernate)        |
| Spring Security + OAuth2 | Authentication and authorization  |
| JJWT                    | JWT token generation/validation    |
| PostgreSQL Driver       | Database connectivity              |
| Spring Mail + MailerSend | Email services                    |
| Thymeleaf               | Server-side templates              |
| Hibernate Validator     | Bean validation                    |
| Jackson Kotlin Module   | Kotlin-friendly JSON serialization |
