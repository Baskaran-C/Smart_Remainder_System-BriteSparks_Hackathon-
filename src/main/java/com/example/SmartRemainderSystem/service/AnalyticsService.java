package com.example.SmartRemainderSystem.service;

import com.example.SmartRemainderSystem.dto.AdminDashboardResponse;
import com.example.SmartRemainderSystem.entity.enums.AppointmentStatus;
import com.example.SmartRemainderSystem.entity.enums.Decision;
import com.example.SmartRemainderSystem.entity.enums.EventType;
import com.example.SmartRemainderSystem.entity.enums.ReminderStatus;
import com.example.SmartRemainderSystem.repository.AppointmentRepository;
import com.example.SmartRemainderSystem.repository.DecisionLogRepository;
import com.example.SmartRemainderSystem.repository.ReminderEventRepository;
import com.example.SmartRemainderSystem.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final AppointmentRepository appointmentRepository;
    private final ReminderRepository reminderRepository;
    private final ReminderEventRepository reminderEventRepository;
    private final DecisionLogRepository decisionLogRepository;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        AdminDashboardResponse r = new AdminDashboardResponse();

        // Appointment stats
        r.setTotalAppointments(appointmentRepository.count());
        r.setPendingAppointments(appointmentRepository.countByStatus(AppointmentStatus.PENDING));
        r.setConfirmedAppointments(appointmentRepository.countByStatus(AppointmentStatus.CONFIRMED));
        r.setCancelledAppointments(appointmentRepository.countByStatus(AppointmentStatus.CANCELLED));
        r.setCompletedAppointments(appointmentRepository.countByStatus(AppointmentStatus.COMPLETED));
        r.setNoShowAppointments(appointmentRepository.countByStatus(AppointmentStatus.NO_SHOW));

        // Reminder stats
        r.setTotalReminders(reminderRepository.count());
        r.setScheduledReminders(reminderRepository.countByStatus(ReminderStatus.SCHEDULED) +
                                reminderRepository.countByStatus(ReminderStatus.WAITING));
        r.setSentReminders(reminderRepository.countByStatus(ReminderStatus.SENT));
        r.setDeliveredReminders(reminderRepository.countByStatus(ReminderStatus.DELIVERED));
        r.setSeenReminders(reminderRepository.countByStatus(ReminderStatus.SEEN));
        r.setConfirmedReminders(reminderRepository.countByStatus(ReminderStatus.CONFIRMED));
        r.setFailedReminders(reminderRepository.countByStatus(ReminderStatus.FAILED));
        r.setStoppedReminders(reminderRepository.countByStatus(ReminderStatus.STOPPED));

        // Decision engine stats
        r.setTotalDecisions(decisionLogRepository.count());
        r.setSendDecisions(decisionLogRepository.countByDecision(Decision.SEND));
        r.setWaitDecisions(decisionLogRepository.countByDecision(Decision.WAIT));
        r.setMoveDecisions(decisionLogRepository.countByDecision(Decision.MOVE));
        r.setRecoverDecisions(decisionLogRepository.countByDecision(Decision.RECOVER));
        r.setStopDecisions(decisionLogRepository.countByDecision(Decision.STOP));

        // Effectiveness rates
        long sent = r.getSentReminders() + r.getDeliveredReminders() +
                    r.getSeenReminders() + r.getConfirmedReminders();
        long delivered = r.getDeliveredReminders() + r.getSeenReminders() + r.getConfirmedReminders();
        long seen = r.getSeenReminders() + r.getConfirmedReminders();
        long confirmed = r.getConfirmedReminders();

        r.setDeliveryRate(sent > 0 ? (double) delivered / sent * 100 : 0);
        r.setSeenRate(delivered > 0 ? (double) seen / delivered * 100 : 0);
        r.setConfirmRate(seen > 0 ? (double) confirmed / seen * 100 : 0);
        r.setNoShowRate(r.getTotalAppointments() > 0
                ? (double) r.getNoShowAppointments() / r.getTotalAppointments() * 100 : 0);

        return r;
    }
}
