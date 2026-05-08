package com.grid07.api.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

// Runs every 5 minutes to batch pending notifications and send them as summaries
@Component
public class NotificationScheduler {

    private final ViralityService viralityService;

    public NotificationScheduler(ViralityService viralityService) {
        this.viralityService = viralityService;
    }

    // Checks Redis for queued notifications, groups them per user, and logs a summary
    @Scheduled(fixedDelay = 300000)
    public void sweepPendingNotifications() {
        System.out.println("[CRON] Starting notification sweep...");

        Set<String> keys = viralityService.getAllPendingNotifKeys();

        if (keys == null || keys.isEmpty()) {
            System.out.println("[CRON] No pending notifications found.");
            return;
        }

        for (String key : keys) {
            String[] parts = key.split(":");
            if (parts.length < 2) continue;

            long userId;
            try {
                userId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                System.out.println("[CRON] Could not parse userId from key: " + key);
                continue;
            }

            List<String> messages = viralityService.popAllPendingNotifications(userId);

            if (messages.isEmpty()) continue;

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
