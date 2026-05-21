package com.davidozzo.demo.observability.controller;

import com.davidozzo.demo.observability.model.dto.ExecuteResponse;
import com.davidozzo.demo.observability.model.dto.SessionSummary;
import com.davidozzo.demo.observability.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/start")
    public ResponseEntity<Void> startSession() {
        log.info("Richiesta avvio nuova sessione");
        String sessionId = sessionService.startSession();
        return ResponseEntity.ok()
                .header("X-Session-Id", sessionId)
                .build();
    }

    @GetMapping("/execute")
    public ResponseEntity<ExecuteResponse> execute() {
        log.info("Richiesta esecuzione");
        ExecuteResponse response = sessionService.execute();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/list")
    public ResponseEntity<List<SessionSummary>> listSessions() {
        log.info("Richiesta elenco sessioni");
        List<SessionSummary> sessions = sessionService.listSessions();
        return ResponseEntity.ok(sessions);
    }
}
