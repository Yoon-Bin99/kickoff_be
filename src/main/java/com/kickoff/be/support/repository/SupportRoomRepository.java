package com.kickoff.be.support.repository;

import com.kickoff.be.support.entity.SupportRoom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportRoomRepository extends JpaRepository<SupportRoom, Long> {

    Optional<SupportRoom> findByUser_Id(Long userId);
}
