package com.example.SmartRemainderSystem.service;

import com.example.SmartRemainderSystem.entity.Appointment;
import com.example.SmartRemainderSystem.entity.User;
import com.example.SmartRemainderSystem.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConflictService {

    private final AppointmentRepository appointmentRepository;

    // Minimum buffer needed between appointments to avoid travel conflicts (minutes)
    private static final int MIN_BUFFER_MINUTES = 30;

    /**
     * Detect travel conflicts for a user's upcoming appointments.
     * A conflict exists when the gap between two consecutive appointments
     * is less than the minimum buffer.
     */
    @Transactional(readOnly = true)
    public List<ConflictResult> detectConflicts(User user) {
        List<Appointment> upcoming = appointmentRepository.findUpcomingByUser(user, LocalDateTime.now());
        List<ConflictResult> conflicts = new ArrayList<>();

        for (int i = 0; i < upcoming.size() - 1; i++) {
            Appointment a1 = upcoming.get(i);
            Appointment a2 = upcoming.get(i + 1);

            long gapMinutes = ChronoUnit.MINUTES.between(a1.getAppointmentTime(), a2.getAppointmentTime());

            if (gapMinutes < MIN_BUFFER_MINUTES) {
                ConflictResult conflict = new ConflictResult();
                conflict.setAppointment1(a1);
                conflict.setAppointment2(a2);
                conflict.setGapMinutes(gapMinutes);
                conflict.setDescription(String.format(
                        "Appointment at %s (%s) and appointment at %s (%s) are only %d minutes apart — possible travel conflict.",
                        a1.getAppointmentTime().toLocalTime(), a1.getOfficeName(),
                        a2.getAppointmentTime().toLocalTime(), a2.getOfficeName(),
                        gapMinutes));
                conflicts.add(conflict);
                log.warn("Travel conflict detected for user {}: {} and {}",
                        user.getId(), a1.getId(), a2.getId());
            }
        }

        return conflicts;
    }

    /**
     * Check if a specific appointment is involved in any conflict.
     */
    @Transactional(readOnly = true)
    public boolean hasConflict(Appointment appointment) {
        List<ConflictResult> conflicts = detectConflicts(appointment.getUser());
        return conflicts.stream().anyMatch(c ->
                c.getAppointment1().getId().equals(appointment.getId()) ||
                c.getAppointment2().getId().equals(appointment.getId()));
    }

    /**
     * Check if multiple close appointments warrant a compressed combined reminder.
     */
    @Transactional(readOnly = true)
    public boolean shouldSendCompressedReminder(User user) {
        List<Appointment> upcoming = appointmentRepository.findUpcomingByUser(user, LocalDateTime.now());
        if (upcoming.size() < 2) return false;

        // If 2 or more appointments are within 4 hours of each other
        for (int i = 0; i < upcoming.size() - 1; i++) {
            long gapMinutes = ChronoUnit.MINUTES.between(
                    upcoming.get(i).getAppointmentTime(),
                    upcoming.get(i + 1).getAppointmentTime());
            if (gapMinutes <= 240) return true;
        }
        return false;
    }

    public static class ConflictResult {
        private Appointment appointment1;
        private Appointment appointment2;
        private long gapMinutes;
        private String description;

        public Appointment getAppointment1() { return appointment1; }
        public void setAppointment1(Appointment v) { appointment1 = v; }
        public Appointment getAppointment2() { return appointment2; }
        public void setAppointment2(Appointment v) { appointment2 = v; }
        public long getGapMinutes() { return gapMinutes; }
        public void setGapMinutes(long v) { gapMinutes = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { description = v; }
    }
}
