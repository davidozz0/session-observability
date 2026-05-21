package com.davidozzo.demo.observability.service;

import com.davidozzo.demo.observability.model.dto.ExecuteResponse;
import com.davidozzo.demo.observability.model.dto.SessionStartResponse;
import com.davidozzo.demo.observability.model.entity.SessionEntity;
import com.davidozzo.demo.observability.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository sessionRepository;

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public SessionStartResponse startSession() {
        String sessionId = UUID.randomUUID().toString();

        SessionEntity entity = new SessionEntity(sessionId);
        sessionRepository.save(entity);

        MDC.put("sessionId", sessionId);
        log.info("Sessione creata con successo");
        MDC.remove("sessionId");

        return new SessionStartResponse(sessionId);
    }

    public ExecuteResponse execute(String sessionId) {
        SessionEntity entity = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sessione non trovata: " + sessionId));

        entity.setLastAccessedAt(LocalDateTime.now());
        sessionRepository.save(entity);

        log.info("Esecuzione operazione per la sessione [{}]", sessionId);
        log.debug("Dettaglio operazione: sessione creata il {}", entity.getCreatedAt());
        log.trace("Trace operativo: versione entita' {}", entity.getVersion());

        return new ExecuteResponse("OK");
    }
}
