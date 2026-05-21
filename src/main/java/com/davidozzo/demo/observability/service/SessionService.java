package com.davidozzo.demo.observability.service;

import com.davidozzo.demo.observability.model.dto.ExecuteResponse;
import com.davidozzo.demo.observability.model.dto.SessionSummary;
import com.davidozzo.demo.observability.model.entity.SessionEntity;
import com.davidozzo.demo.observability.repository.SessionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository sessionRepository;
    private final MeterRegistry meterRegistry;

    public String startSession() {
        String sessionId = MDC.get("sessionId");

        SessionEntity entity = new SessionEntity(sessionId);
        sessionRepository.save(entity);

        meterRegistry.counter("session.created").increment();

        log.info("Sessione creata con successo");

        return sessionId;
    }

    public ExecuteResponse execute() {
        String sessionId = MDC.get("sessionId");

        SessionEntity entity = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> {
                    meterRegistry.counter("session.executed", "status", "error").increment();
                    return new IllegalArgumentException("Sessione non trovata: " + sessionId);
                });

        Timer.Sample sample = Timer.start(meterRegistry);

        entity.setLastAccessedAt(LocalDateTime.now());
        sessionRepository.save(entity);

        log.info("Esecuzione operazione per la sessione");
        log.debug("Dettaglio operazione: sessione creata il {}", entity.getCreatedAt());
        log.trace("Trace operativo: versione entita' {}", entity.getVersion());

        sample.stop(meterRegistry.timer("session.execute.duration"));
        meterRegistry.counter("session.executed", "status", "success").increment();

        return new ExecuteResponse("OK");
    }

    public List<SessionSummary> listSessions() {
        List<SessionEntity> entities = sessionRepository.findAll();
        log.info("Elenco sessioni richiesto: {} sessioni trovate", entities.size());
        return entities.stream()
                .map(e -> new SessionSummary(e.getSessionId(), e.getCreatedAt()))
                .toList();
    }
}
