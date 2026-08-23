package com.example.SmartRemainderSystem.repository;

import com.example.SmartRemainderSystem.entity.User;
import com.example.SmartRemainderSystem.entity.UserActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {

    List<UserActivity> findByUserOrderByActivityTimeDesc(User user);

    // Fetch last N days of activity for active-window analysis
    @Query("SELECT ua FROM UserActivity ua WHERE ua.user = :user " +
           "AND ua.activityTime >= :since ORDER BY ua.activityTime DESC")
    List<UserActivity> findRecentActivity(@Param("user") User user,
                                          @Param("since") LocalDateTime since);
}
