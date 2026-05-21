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

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository sessionRepository;

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public SessionStartResponse startSession() {
        String sessionId = MDC.get("sessionId");

        SessionEntity entity = new SessionEntity(sessionId);
        sessionRepository.save(entity);

        log.info("Sessione creata con successo");

        return new SessionStartResponse(sessionId);
    }

    public ExecuteResponse execute() {
        String sessionId = MDC.get("sessionId");
        if (sessionId == null) {
            throw new IllegalArgumentException("X-Session-Id header mancante");
        }

        SessionEntity entity = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sessione non trovata: " + sessionId));

        entity.setLastAccessedAt(LocalDateTime.now());
        sessionRepository.save(entity);

        log.info("Esecuzione operazione per la sessione");
        log.debug("Dettaglio operazione: sessione creata il {}", entity.getCreatedAt());
        log.trace("Trace operativo: versione entita' {}", entity.getVersion());

        return new ExecuteResponse("OK");
    }
}
