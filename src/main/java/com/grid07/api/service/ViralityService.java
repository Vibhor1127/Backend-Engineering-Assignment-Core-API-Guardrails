package com.grid07.api.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

// Manages virality scores, bot reply limits, cooldowns, and notifications using Redis
@Service
public class ViralityService {

    private static final String VIRALITY_KEY   = "post:%d:virality_score";
    private static final String BOT_COUNT_KEY  = "post:%d:bot_count";
    private static final String COOLDOWN_KEY   = "cooldown:bot_%d:human_%d";
    private static final String NOTIF_SENT_KEY = "notif_sent:user_%d";
    private static final String PENDING_NOTIFS = "user:%d:pending_notifs";

    private static final int MAX_BOT_REPLIES = 100;
    private static final int MAX_DEPTH       = 20;

    private static final long COOLDOWN_MINUTES  = 10;
    private static final long NOTIF_COOLDOWN_MINUTES = 15;

    private static final int BOT_REPLY_POINTS    = 1;
    private static final int HUMAN_LIKE_POINTS   = 20;
    private static final int HUMAN_COMMENT_POINTS = 50;

    private final RedisTemplate<String, String> redisTemplate;

    public ViralityService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Adds 1 point to the virality score when a bot replies
    public void addViralityForBotReply(Long postId) {
        String key = String.format(VIRALITY_KEY, postId);
        redisTemplate.opsForValue().increment(key, BOT_REPLY_POINTS);
    }

    // Adds 20 points to the virality score when a human likes
    public void addViralityForHumanLike(Long postId) {
        String key = String.format(VIRALITY_KEY, postId);
        redisTemplate.opsForValue().increment(key, HUMAN_LIKE_POINTS);
    }

    // Adds 50 points to the virality score when a human comments
    public void addViralityForHumanComment(Long postId) {
        String key = String.format(VIRALITY_KEY, postId);
        redisTemplate.opsForValue().increment(key, HUMAN_COMMENT_POINTS);
    }

    // Returns the current virality score for a post
    public long getViralityScore(Long postId) {
        String key = String.format(VIRALITY_KEY, postId);
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0L : Long.parseLong(value);
    }

    // Atomically increments the bot reply counter and returns the new count
    public long incrementBotCount(Long postId) {
        String key = String.format(BOT_COUNT_KEY, postId);
        Long count = redisTemplate.opsForValue().increment(key);
        return count == null ? 0L : count;
    }

    // Rolls back the bot reply counter by one after a rejected request
    public void decrementBotCount(Long postId) {
        String key = String.format(BOT_COUNT_KEY, postId);
        redisTemplate.opsForValue().decrement(key);
    }

    // Returns the current bot reply count for a post
    public long getBotCount(Long postId) {
        String key = String.format(BOT_COUNT_KEY, postId);
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0L : Long.parseLong(value);
    }

    // Checks if a bot is still in cooldown for a specific human user
    public boolean isBotInCooldown(Long botId, Long humanId) {
        String key = String.format(COOLDOWN_KEY, botId, humanId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // Sets a 10-minute cooldown between a bot and a human so the bot can't spam them
    public void startBotCooldown(Long botId, Long humanId) {
        String key = String.format(COOLDOWN_KEY, botId, humanId);
        redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(COOLDOWN_MINUTES));
    }

    // Checks if a user already received a notification in the last 15 minutes
    public boolean userReceivedRecentNotification(Long userId) {
        String key = String.format(NOTIF_SENT_KEY, userId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // Marks that a notification was just sent to this user for the next 15 minutes
    public void setNotificationCooldown(Long userId) {
        String key = String.format(NOTIF_SENT_KEY, userId);
        redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(NOTIF_COOLDOWN_MINUTES));
    }

    // Adds a notification message to the user's pending queue in Redis
    public void addPendingNotification(Long userId, String message) {
        String key = String.format(PENDING_NOTIFS, userId);
        redisTemplate.opsForList().rightPush(key, message);
    }

    // Grabs all pending notifications for a user and clears the queue
    public java.util.List<String> popAllPendingNotifications(Long userId) {
        String key = String.format(PENDING_NOTIFS, userId);
        long size = redisTemplate.opsForList().size(key) != null
                ? redisTemplate.opsForList().size(key) : 0L;

        if (size == 0) {
            return java.util.Collections.emptyList();
        }

        java.util.List<String> messages = redisTemplate.opsForList().range(key, 0, -1);
        redisTemplate.delete(key);
        return messages == null ? java.util.Collections.emptyList() : messages;
    }

    // Finds all Redis keys that have pending notifications waiting to be sent
    public java.util.Set<String> getAllPendingNotifKeys() {
        return redisTemplate.keys("user:*:pending_notifs");
    }
}
