package com.example.SmartRemainderSystem.repository;

import com.example.SmartRemainderSystem.entity.Appointment;
import com.example.SmartRemainderSystem.entity.User;
import com.example.SmartRemainderSystem.entity.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByUser(User user);

    List<Appointment> findByStatus(AppointmentStatus status);

    List<Appointment> findByUserAndStatus(User user, AppointmentStatus status);

    // Find appointments in the future that are still active (for conflict detection)
    @Query("SELECT a FROM Appointment a WHERE a.user = :user " +
           "AND a.appointmentTime > :now " +
           "AND a.status NOT IN ('CANCELLED', 'COMPLETED', 'NO_SHOW') " +
           "ORDER BY a.appointmentTime ASC")
    List<Appointment> findUpcomingByUser(@Param("user") User user,
                                         @Param("now") LocalDateTime now);

    // Stats for admin dashboard
    long countByStatus(AppointmentStatus status);
}
