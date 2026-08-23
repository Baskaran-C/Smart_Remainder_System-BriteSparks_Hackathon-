package com.example.SmartRemainderSystem.service;

import com.example.SmartRemainderSystem.entity.Appointment;
import com.example.SmartRemainderSystem.entity.LocationSnapshot;
import com.example.SmartRemainderSystem.entity.User;
import com.example.SmartRemainderSystem.repository.LocationSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final LocationSnapshotRepository locationSnapshotRepository;

    // Assumed average urban travel speed in km/h
    private static final double AVERAGE_SPEED_KMH = 30.0;
    // Buffer time at destination before appointment (minutes)
    private static final int ARRIVAL_BUFFER_MINUTES = 15;
    // If no location available, assume this default travel time (minutes)
    private static final int DEFAULT_TRAVEL_MINUTES = 45;

    /**
     * Evaluate whether the user should leave now based on current location
     * and appointment office location.
     */
    public LocationContext evaluate(Appointment appointment) {
        LocationContext context = new LocationContext();
        context.setLocationAvailable(false);
        context.setTravelMinutes(DEFAULT_TRAVEL_MINUTES);

        if (appointment.getOfficeLatitude() == null || appointment.getOfficeLongitude() == null) {
            context.setReason("Office location not configured — using default travel estimate");
            return computeDepartureDecision(context, appointment);
        }

        Optional<LocationSnapshot> latestLocation =
                locationSnapshotRepository.findTopByUserOrderByCapturedAtDesc(appointment.getUser());

        if (latestLocation.isEmpty()) {
            context.setReason("User location not available — using default travel estimate");
            return computeDepartureDecision(context, appointment);
        }

        LocationSnapshot snap = latestLocation.get();

        // Only use location snapshot if it's recent (within last 30 minutes)
        if (snap.getCapturedAt().isBefore(LocalDateTime.now().minusMinutes(30))) {
            context.setReason("Last known location is stale (>30 min old) — using default estimate");
            return computeDepartureDecision(context, appointment);
        }

        double distanceKm = haversineDistanceKm(
                snap.getLatitude(), snap.getLongitude(),
                appointment.getOfficeLatitude(), appointment.getOfficeLongitude()
        );

        int travelMinutes = (int) Math.ceil((distanceKm / AVERAGE_SPEED_KMH) * 60);
        context.setLocationAvailable(true);
        context.setDistanceKm(distanceKm);
        context.setTravelMinutes(travelMinutes);
        context.setReason(String.format(
                "Distance to office: %.1f km. Estimated travel: %d min at %d km/h average.",
                distanceKm, travelMinutes, (int) AVERAGE_SPEED_KMH));

        return computeDepartureDecision(context, appointment);
    }

    private LocationContext computeDepartureDecision(LocationContext context, Appointment appointment) {
        // Recommended departure = appointmentTime - travelTime - arrivalBuffer
        LocalDateTime recommendedDeparture = appointment.getAppointmentTime()
                .minusMinutes(context.getTravelMinutes())
                .minusMinutes(ARRIVAL_BUFFER_MINUTES);

        context.setRecommendedDepartureTime(recommendedDeparture);

        // Send a reminder 15 minutes before recommended departure
        LocalDateTime reminderTime = recommendedDeparture.minusMinutes(15);
        context.setReminderSendTime(reminderTime);

        LocalDateTime now = LocalDateTime.now();
        // "Departure time soon" = within 20 minutes of recommended departure
        boolean departureSoon = now.isAfter(reminderTime) && now.isBefore(appointment.getAppointmentTime());
        context.setDepartureTimeSoon(departureSoon);

        return context;
    }

    /**
     * Haversine formula — great-circle distance between two GPS coordinates.
     * Returns distance in kilometres.
     */
    private double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // Earth's radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public void saveLocation(User user, double latitude, double longitude) {
        LocationSnapshot snapshot = new LocationSnapshot(user, latitude, longitude);
        locationSnapshotRepository.save(snapshot);
        log.info("Location saved for user {}: ({}, {})", user.getId(), latitude, longitude);
    }

    /**
     * Immutable value object returned by evaluate().
     */
    public static class LocationContext {
        private boolean locationAvailable;
        private double distanceKm;
        private int travelMinutes;
        private LocalDateTime recommendedDepartureTime;
        private LocalDateTime reminderSendTime;
        private boolean departureTimeSoon;
        private String reason;

        public boolean isLocationAvailable() { return locationAvailable; }
        public void setLocationAvailable(boolean v) { locationAvailable = v; }
        public double getDistanceKm() { return distanceKm; }
        public void setDistanceKm(double v) { distanceKm = v; }
        public int getTravelMinutes() { return travelMinutes; }
        public void setTravelMinutes(int v) { travelMinutes = v; }
        public LocalDateTime getRecommendedDepartureTime() { return recommendedDepartureTime; }
        public void setRecommendedDepartureTime(LocalDateTime v) { recommendedDepartureTime = v; }
        public LocalDateTime getReminderSendTime() { return reminderSendTime; }
        public void setReminderSendTime(LocalDateTime v) { reminderSendTime = v; }
        public boolean isDepartureTimeSoon() { return departureTimeSoon; }
        public void setDepartureTimeSoon(boolean v) { departureTimeSoon = v; }
        public String getReason() { return reason; }
        public void setReason(String v) { reason = v; }
    }
}
