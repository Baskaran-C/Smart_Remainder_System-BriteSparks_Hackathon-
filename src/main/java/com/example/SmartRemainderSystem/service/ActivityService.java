package com.example.SmartRemainderSystem.service;

import com.example.SmartRemainderSystem.entity.User;
import com.example.SmartRemainderSystem.entity.UserActivity;
import com.example.SmartRemainderSystem.entity.enums.ActivityType;
import com.example.SmartRemainderSystem.repository.UserActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

    private final UserActivityRepository userActivityRepository;

    // How many days of history to analyse for active-window detection
    private static final int ANALYSIS_DAYS = 7;
    // Minimum activity count in an hour to consider it "active"
    private static final int MIN_ACTIVITY_THRESHOLD = 1;

    /**
     * Determine whether the current hour is within the user's historically active window.
     * Rule-based: look at the last 7 days of activity and check if this hour-of-day
     * has been active at least once.
     */
    @Transactional(readOnly = true)
    public boolean isActiveWindow(User user) {
        LocalDateTime since = LocalDateTime.now().minusDays(ANALYSIS_DAYS);
        List<UserActivity> activities = userActivityRepository.findRecentActivity(user, since);

        if (activities.isEmpty()) {
            // No history — assume active (conservative: don't hold back the first reminder)
            log.debug("No activity history for user {} — assuming active window", user.getId());
            return true;
        }

        int currentHour = LocalTime.now().getHour();
        Map<Integer, Integer> hourCounts = new HashMap<>();

        for (UserActivity activity : activities) {
            int hour = activity.getActivityTime().getHour();
            hourCounts.merge(hour, 1, Integer::sum);
        }

        int countThisHour = hourCounts.getOrDefault(currentHour, 0);
        boolean isActive = countThisHour >= MIN_ACTIVITY_THRESHOLD;

        log.debug("User {} active window check: hour={}, historyCount={}, isActive={}",
                user.getId(), currentHour, countThisHour, isActive);
        return isActive;
    }

    /**
     * Returns a human-readable description of the user's typical active hours
     * (used in decision log reasons).
     */
    @Transactional(readOnly = true)
    public String getActiveWindowSummary(User user) {
        LocalDateTime since = LocalDateTime.now().minusDays(ANALYSIS_DAYS);
        List<UserActivity> activities = userActivityRepository.findRecentActivity(user, since);

        if (activities.isEmpty()) {
            return "No activity history available";
        }

        Map<Integer, Integer> hourCounts = new HashMap<>();
        for (UserActivity activity : activities) {
            int hour = activity.getActivityTime().getHour();
            hourCounts.merge(hour, 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder("Historically active at: ");
        hourCounts.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_ACTIVITY_THRESHOLD)
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(String.format("%02d:00, ", e.getKey())));
        return sb.toString().replaceAll(", $", "");
    }

    /**
     * Record a user activity event.
     */
    @Transactional
    public void record(User user, ActivityType type) {
        UserActivity activity = new UserActivity(user, type);
        userActivityRepository.save(activity);
    }

    @Transactional
    public void record(User user, ActivityType type, Long referenceId) {
        UserActivity activity = new UserActivity(user, type, referenceId);
        userActivityRepository.save(activity);
    }
}
