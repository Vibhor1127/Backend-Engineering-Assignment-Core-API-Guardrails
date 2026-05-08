# Spring Boot Backend Assignment - Grid07

## About This Project

This project is a backend microservice built using Spring Boot. The idea is simple - it manages posts and comments like a social platform, but with strict rules to control how bots can interact with human posts. Redis is used to enforce these rules in real time, and PostgreSQL stores all the actual data.

I built this phase by phase, starting with the basic REST APIs, then adding Redis guardrails, and finally the notification system with a scheduled task.

---

## Tech Used

- Java 17
- Spring Boot 3.2.5
- PostgreSQL 18 (local)
- Redis 7 (via Docker)
- Maven
- Postman for testing

---

## How to Run This Locally

### What You Need First

- JDK 17 or higher installed
- Maven installed
- PostgreSQL installed and running
- Docker Desktop installed (for Redis)

---

### 1. Start Redis

I used Docker to run Redis locally. Open terminal and run:

```bash
docker run -d --name myredis -p 6379:6379 redis:7
```

Check if it started:

```bash
docker ps
```

Quick test:

```bash
docker exec -it myredis redis-cli ping
```

If you see `PONG` then Redis is running fine.

Next time you restart your PC, just do:

```bash
docker start myredis
```

---

### 2. Setup the Database

Open terminal and go to PostgreSQL bin folder:

```bash
cd "C:\Program Files\PostgreSQL\18\bin"
.\psql -U postgres
```

Type your postgres password. Then run:

```sql
CREATE DATABASE assignmentdb;
CREATE USER assignuser WITH PASSWORD 'assign123';
GRANT ALL PRIVILEGES ON DATABASE assignmentdb TO assignuser;
GRANT ALL ON SCHEMA public TO assignuser;
\q
```

---

### 3. Update application.properties

Open `src/main/resources/application.properties` and set it like this:

```properties
server.port=8080

spring.datasource.url=jdbc:postgresql://localhost:5432/assignmentdb
spring.datasource.username=assignuser
spring.datasource.password=assign123
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

spring.data.redis.host=localhost
spring.data.redis.port=6379

spring.task.scheduling.pool.size=5
```

---

### 4. Run the Project

```bash
mvn spring-boot:run
```

Once you see this in the terminal, everything started correctly:

```
Tomcat started on port 8080
Started Grid07Application
[CRON] Starting notification sweep...
[CRON] No pending notifications found.
```

---

## Testing the APIs

Import the Postman collection from the `postman` folder. Test in this order:

**1. Create a user**
```
POST /api/users
{
  "username": "rahul_dev",
  "isPremium": false
}
```

**2. Create a bot**
```
POST /api/bots
{
  "name": "commentbot",
  "personaDescription": "replies to trending posts"
}
```

**3. Create a post**
```
POST /api/posts
{
  "authorId": 1,
  "authorType": "USER",
  "content": "just finished my backend project, feels good!"
}
```

**4. Add a bot comment**
```
POST /api/posts/1/comments
{
  "authorId": 1,
  "authorType": "BOT",
  "content": "nice one!",
  "depthLevel": 1,
  "postOwnerId": 1
}
```

**5. Like the post**
```
POST /api/posts/1/like
{
  "userId": 1
}
```

**6. Check virality score**
```
GET /api/posts/1/virality
```

Response:
```json
{
  "postId": 1,
  "viralityScore": 21,
  "botReplyCount": 1
}
```

**7. Test the depth limit (should fail with 429)**
```
POST /api/posts/1/comments
{
  "authorId": 1,
  "authorType": "BOT",
  "content": "going too deep",
  "depthLevel": 21,
  "postOwnerId": 1
}
```

---

## Endpoints

| Method | URL | What it does |
|--------|-----|-------------|
| POST | /api/users | add a user |
| GET | /api/users | get all users |
| POST | /api/bots | add a bot |
| GET | /api/bots | get all bots |
| POST | /api/posts | create a post |
| GET | /api/posts | get all posts |
| POST | /api/posts/{id}/comments | add comment with guardrails |
| POST | /api/posts/{id}/like | like a post |
| GET | /api/posts/{id}/virality | get virality score |

---

## Folder Structure

```
src/main/java/com/grid07/api/
├── Grid07Application.java
├── config/
│   └── RedisConfig.java
├── controller/
│   ├── PostController.java
│   └── UserController.java
├── dto/
│   ├── CreatePostRequest.java
│   ├── CreateCommentRequest.java
│   └── LikePostRequest.java
├── entity/
│   ├── User.java
│   ├── Bot.java
│   ├── Post.java
│   └── Comment.java
├── exception/
│   ├── TooManyRequestsException.java
│   └── GlobalExceptionHandler.java
├── repository/
│   ├── UserRepository.java
│   ├── BotRepository.java
│   ├── PostRepository.java
│   └── CommentRepository.java
└── service/
    ├── PostService.java
    ├── ViralityService.java
    └── NotificationScheduler.java
```

---

## How I Handled Thread Safety (Phase 2)

This was the trickiest part. The requirement was that if 200 bots send comments at the same time, the system must stop at exactly 100.

The problem with a normal if-check is that two threads can read the same counter value simultaneously and both pass. So I used Redis INCR which is atomic.

The approach:

```java
long count = redisTemplate.opsForValue().increment(key);

if (count > 100) {
    redisTemplate.opsForValue().decrement(key);
    throw new TooManyRequestsException("bot limit reached for this post");
}
```

Increment first, then check. Redis handles INCR atomically so each of the 200 threads gets a unique number. Once it crosses 100, we decrement and reject. Database only gets written if this passes.

For cooldown I used Redis TTL:

```java
redisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofMinutes(10));
```

Redis removes the key automatically after 10 minutes.

The app is completely stateless - no HashMaps, no static variables. Everything lives in Redis.

---

## Virality Points

| Action | Points |
|--------|--------|
| Bot reply | +1 |
| Human like | +20 |
| Human comment | +50 |

---

## Notification Batching (Phase 3)

When a bot replies to a user's post:
- If user was notified in last 15 minutes, message goes into a Redis queue
- If not, it logs immediately and starts 15 minute cooldown

Every 5 minutes a scheduled task runs, picks up all queued messages per user, and logs one combined message. This avoids spam.

Example log:
```
[CRON] Summarized Push Notification for User 1: Bot 2 replied to your post and 3 others interacted with your posts.
```
