# Spring Boot Backend Foundation

## Purpose
This component serves as the REST API backend for the Enterprise Knowledge Intelligence Platform. It connects to the PostgreSQL database to manage users, roles, permissions, and structured document metadata.

## Architecture
- **Language**: Java 21+
- **Framework**: Spring Boot 3.4+
- **Database Layer**: Spring Data JPA / Hibernate
- **Database**: PostgreSQL
- **Build Tool**: Maven

## Environment Variables
The application connects to PostgreSQL using the following environment variables (with sensible local defaults):
- `DB_HOST` (default: localhost)
- `DB_PORT` (default: 5432)
- `DB_NAME` (default: eip_db)
- `DB_USERNAME` (default: eip_dev)
- `DB_PASSWORD` (default: dev_pass_123)

## How to Run
Ensure PostgreSQL is running locally via Docker.
```bash
cd subjects/DBE-DSD/backend
./mvnw spring-boot:run
```

## How to Test
```bash
./mvnw clean test
```
