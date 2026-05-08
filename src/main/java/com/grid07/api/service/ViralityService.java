package com.grid07.api.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * ViralityService handles all Redis operations:
 * - Virality score updates
 * - Bot comment cap (horizontal cap)
 * - Thread depth cap (vertical cap)
 * - Bot-to-human cooldown (cooldown cap)
 * - Notification throttling
 */
@Service
public class ViralityService {

    // Redis key patterns - keeping them consistent everywhere
    private static final String VIRALITY_KEY   = "post:%d:virality_score";
    private static final String BOT_COUNT_KEY  = "post:%d:bot_count";
    private static final String COOLDOWN_KEY   = "cooldown:bot_%d:human_%d";
    private static final String NOTIF_SENT_KEY = "notif_sent:user_%d";
    private static final String PENDING_NOTIFS = "user:%d:pending_notifs";

    // Guardrail limits
    private static final int MAX_BOT_REPLIES = 100;
    private static final int MAX_DEPTH       = 20;

    // TTLs
    private static final long COOLDOWN_MINUTES  = 10;
    private static final long NOTIF_COOLDOWN_MINUTES = 15;

    // Points per action
    private static final int BOT_REPLY_POINTS    = 1;
    private static final int HUMAN_LIKE_POINTS   = 20;
    private static final int HUMAN_COMMENT_POINTS = 50;

    private final RedisTemplate<String, String> redisTemplate;

    public ViralityService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ==================== VIRALITY SCORE ====================

    public void addViralityForBotReply(Long postId) {
        String key = String.format(VIRALITY_KEY, postId);
        redisTemplate.opsForValue().increment(key, BOT_REPLY_POINTS);
    }

    public void addViralityForHumanLike(Long postId) {
        String key = String.format(VIRALITY_KEY, postId);
        redisTemplate.opsForValue().increment(key, HUMAN_LIKE_POINTS);
    }

    public void addViralityForHumanComment(Long postId) {
        String key = String.format(VIRALITY_KEY, postId);
        redisTemplate.opsForValue().increment(key, HUMAN_COMMENT_POINTS);
    }

    public long getViralityScore(Long postId) {
        String key = String.format(VIRALITY_KEY, postId);
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0L : Long.parseLong(value);
    }

    // ==================== ATOMIC BOT COUNTER (Horizontal Cap) ====================

    /**
     * Atomically increments the bot reply counter for a post.
     * Returns the new count AFTER increment.
     * If the count exceeds 100, the caller must reject the request.
     *
     * Redis INCR is atomic - even 200 concurrent threads will each get
     * a unique count. This is the key to passing the Race Condition test.
     */
    public long incrementBotCount(Long postId) {
        String key = String.format(BOT_COUNT_KEY, postId);
        Long count = redisTemplate.opsForValue().increment(key);
        return count == null ? 0L : count;
    }

    /**
     * If the bot count went over 100, decrement it back.
     * We do this so the counter stays accurate after rejections.
     */
    public void decrementBotCount(Long postId) {
        String key = String.format(BOT_COUNT_KEY, postId);
        redisTemplate.opsForValue().decrement(key);
    }

    public long getBotCount(Long postId) {
        String key = String.format(BOT_COUNT_KEY, postId);
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0L : Long.parseLong(value);
    }

    // ==================== COOLDOWN CAP ====================

    /**
     * Check if a specific bot is in cooldown for a specific human user.
     * If the key exists in Redis, the bot is still cooling down.
     */
    public boolean isBotInCooldown(Long botId, Long humanId) {
        String key = String.format(COOLDOWN_KEY, botId, humanId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Start a 10 minute cooldown for this bot-human pair.
     * We set the key with a TTL so Redis removes it automatically.
     */
    public void startBotCooldown(Long botId, Long humanId) {
        String key = String.format(COOLDOWN_KEY, botId, humanId);
        redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(COOLDOWN_MINUTES));
    }

    // ==================== NOTIFICATION ENGINE ====================

    /**
     * Check if the user already got a notification recently (within 15 minutes).
     */
    public boolean userReceivedRecentNotification(Long userId) {
        String key = String.format(NOTIF_SENT_KEY, userId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Set the 15-minute notification cooldown for the user.
     */
    public void setNotificationCooldown(Long userId) {
        String key = String.format(NOTIF_SENT_KEY, userId);
        redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(NOTIF_COOLDOWN_MINUTES));
    }

    /**
     * Push a notification message into the user's pending list in Redis.
     */
    public void addPendingNotification(Long userId, String message) {
        String key = String.format(PENDING_NOTIFS, userId);
        redisTemplate.opsForList().rightPush(key, message);
    }

    /**
     * Pop all pending notifications for a user and clear the list.
     * Used by the CRON sweeper.
     */
    public java.util.List<String> popAllPendingNotifications(Long userId) {
        String key = String.format(PENDING_NOTIFS, userId);
        long size = redisTemplate.opsForList().size(key) != null
                ? redisTemplate.opsForList().size(key) : 0L;

        if (size == 0) {
            return java.util.Collections.emptyList();
        }

        // Get all messages then delete the key
        java.util.List<String> messages = redisTemplate.opsForList().range(key, 0, -1);
        redisTemplate.delete(key);
        return messages == null ? java.util.Collections.emptyList() : messages;
    }

    /**
     * Get all user IDs that have pending notifications sitting in Redis.
     * We use the key pattern "user:*:pending_notifs" to find them.
     */
    public java.util.Set<String> getAllPendingNotifKeys() {
        return redisTemplate.keys("user:*:pending_notifs");
    }
}
