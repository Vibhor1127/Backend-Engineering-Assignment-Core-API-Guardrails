package com.grid07.api.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * NotificationScheduler - The CRON Sweeper
 *
 * Runs every 5 minutes and checks all users who have pending notifications
 * sitting in Redis. It batches them into a single summarized message
 * so users don't get spammed.
 */
@Component
public class NotificationScheduler {

    private final ViralityService viralityService;

    public NotificationScheduler(ViralityService viralityService) {
        this.viralityService = viralityService;
    }

    // Runs every 5 minutes (300000 ms)
    // In production this would be 15 minutes - 5 is for testing
    @Scheduled(fixedDelay = 300000)
    public void sweepPendingNotifications() {
        System.out.println("[CRON] Starting notification sweep...");

        // Get all pending notification keys from Redis
        Set<String> keys = viralityService.getAllPendingNotifKeys();

        if (keys == null || keys.isEmpty()) {
            System.out.println("[CRON] No pending notifications found.");
            return;
        }

        for (String key : keys) {
            // Extract userId from the key pattern "user:{id}:pending_notifs"
            String[] parts = key.split(":");
            if (parts.length < 2) continue;

            long userId;
            try {
                userId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                System.out.println("[CRON] Could not parse userId from key: " + key);
                continue;
            }

            // Pop all pending messages for this user
            List<String> messages = viralityService.popAllPendingNotifications(userId);

            if (messages.isEmpty()) continue;

            // Build a summarized message
            // E.g. "Bot 3 and 2 others interacted with your posts."
            String firstMessage = messages.get(0);
            int otherCount = messages.size() - 1;

            String summary;
            if (otherCount > 0) {
                summary = firstMessage + " and " + otherCount + " others interacted with your posts.";
            } else {
                summary = firstMessage;
            }

            System.out.println("[CRON] Summarized Push Notification for User " + userId + ": " + summary);
        }

        System.out.println("[CRON] Notification sweep done.");
    }
}
