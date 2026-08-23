package com.example.SmartRemainderSystem.controller;

import com.example.SmartRemainderSystem.entity.User;
import com.example.SmartRemainderSystem.entity.enums.ActivityType;
import com.example.SmartRemainderSystem.repository.UserRepository;
import com.example.SmartRemainderSystem.service.ActivityService;
import com.example.SmartRemainderSystem.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class NotificationController {

    private final LocationService locationService;
    private final ActivityService activityService;
    private final UserRepository userRepository;

    /**
     * POST /api/user/location
     * Receive a user's GPS coordinates.
     * Used by the location-aware reminder feature.
     */
    @PostMapping("/location")
    public ResponseEntity<String> receiveLocation(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        double latitude = Double.parseDouble(body.get("latitude").toString());
        double longitude = Double.parseDouble(body.get("longitude").toString());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        locationService.saveLocation(user, latitude, longitude);
        activityService.record(user, ActivityType.LOCATION_SHARED, userId);

        return ResponseEntity.ok("Location recorded");
    }

    /**
     * POST /api/user/activity
     * Record a user activity event (app opened, etc.).
     */
    @PostMapping("/activity")
    public ResponseEntity<String> recordActivity(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String activityType = body.get("activityType").toString();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        activityService.record(user, ActivityType.valueOf(activityType));
        return ResponseEntity.ok("Activity recorded");
    }
}
