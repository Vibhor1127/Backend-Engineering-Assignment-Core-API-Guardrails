# Grid07 Backend Assignment - Core API & Guardrails

## Overview

This project is a Spring Boot microservice that acts as the central API gateway and guardrail system. It uses PostgreSQL as the main database and Redis as the real-time gatekeeper for concurrency control and notification management.

---

## Tech Stack

- Java 17
- Spring Boot 3.x
- PostgreSQL (via Spring Data JPA)
- Redis (via Spring Data Redis)
- Docker (for local setup)

---

## How to Run

### Step 1: Start PostgreSQL and Redis with Docker

```bash
docker-compose up -d
```

### Step 2: Build and Run the Spring Boot App

```bash
./mvnw spring-boot:run
```

The server starts on `http://localhost:8080`

### Step 3: Import Postman Collection

Import `postman/Grid07_API_Collection.json` into Postman and start testing.

---

## Project Structure

```
src/main/java/com/grid07/api/
├── config/         # Redis config
├── controller/     # REST controllers
├── dto/            # Request/response data classes
├── entity/         # JPA entities (User, Bot, Post, Comment)
├── exception/      # Custom exceptions and error handler
├── repository/     # Spring Data JPA repositories
└── service/
    ├── PostService.java            # Core business logic
    ├── ViralityService.java        # All Redis operations
    └── NotificationScheduler.java  # CRON sweeper
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/users | Create a user |
| POST | /api/bots | Create a bot |
| POST | /api/posts | Create a post |
| GET | /api/posts | Get all posts |
| POST | /api/posts/{id}/comments | Add a comment |
| POST | /api/posts/{id}/like | Like a post |
| GET | /api/posts/{id}/virality | Get virality score from Redis |

---

## How Thread Safety is Guaranteed (Phase 2 - Atomic Locks)

This was the most important part of the assignment. Here is how I handled it:

### The Problem

When 200 concurrent bot requests come in at the same time, a normal if-check would fail. Two threads could both read the counter as 99, both pass the check, and both write — resulting in 101 comments instead of 100.

### The Solution: Redis INCR is Atomic

Redis's `INCR` command is **single-threaded and atomic by design**. No matter how many concurrent requests hit the server, each `INCR` call returns a unique, sequential number.

The pattern I used in `ViralityService.incrementBotCount()`:

```java
// Increment FIRST, then check the result
long newCount = redisTemplate.opsForValue().increment(key);

if (newCount > 100) {
    // Roll back the counter since we are rejecting this request
    redisTemplate.opsForValue().decrement(key);
    throw new TooManyRequestsException("Bot reply limit reached.");
}
```

This means:
- Thread 1 gets count = 100 → passes, saves comment
- Thread 2 gets count = 101 → fails, counter rolled back, 429 returned
- Thread 3 gets count = 101 → same, 429 returned

The database only gets written to if Redis allows it (guardrails checked before `commentRepository.save()`). This keeps PostgreSQL as source of truth for actual content, and Redis as the gatekeeper.

### Cooldown Cap

The bot cooldown uses Redis key TTL:

```java
redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(10));
```

Redis automatically deletes this key after 10 minutes, so no manual cleanup needed.

### Notification Throttle

Pending notifications are stored in a Redis List per user. The CRON sweeper runs every 5 minutes, pops all messages, and logs a single summarized message — preventing notification spam.

---

## Virality Score Breakdown

| Action | Points |
|--------|--------|
| Bot Reply | +1 |
| Human Like | +20 |
| Human Comment | +50 |

All stored in Redis under `post:{id}:virality_score`.

---

## Key Design Decisions

1. **Stateless application** - No HashMaps or static variables. Every counter, cooldown, and pending notification lives in Redis only.
2. **Increment-then-check pattern** - Safer than check-then-increment for atomic concurrency control.
3. **Transaction boundary** - `@Transactional` on service methods means DB writes only happen if all Redis checks pass.
