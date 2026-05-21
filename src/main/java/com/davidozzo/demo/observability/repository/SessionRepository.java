package com.davidozzo.demo.observability.repository;

import com.davidozzo.demo.observability.model.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, Long> {

    Optional<SessionEntity> findBySessionId(String sessionId);
}
