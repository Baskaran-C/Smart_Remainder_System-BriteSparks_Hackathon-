package com.example.SmartRemainderSystem.repository;

import com.example.SmartRemainderSystem.entity.LocationSnapshot;
import com.example.SmartRemainderSystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocationSnapshotRepository extends JpaRepository<LocationSnapshot, Long> {

    // Get the most recent location for a user
    Optional<LocationSnapshot> findTopByUserOrderByCapturedAtDesc(User user);
}
