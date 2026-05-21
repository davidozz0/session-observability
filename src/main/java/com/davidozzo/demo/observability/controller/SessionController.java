package com.davidozzo.demo.observability.controller;

import com.davidozzo.demo.observability.model.dto.ExecuteResponse;
import com.davidozzo.demo.observability.model.dto.SessionStartResponse;
import com.davidozzo.demo.observability.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/start")
    public ResponseEntity<SessionStartResponse> startSession() {
        log.info("Richiesta avvio nuova sessione");
        SessionStartResponse response = sessionService.startSession();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/execute")
    public ResponseEntity<ExecuteResponse> execute() {
        log.info("Richiesta esecuzione");
        ExecuteResponse response = sessionService.execute();
        return ResponseEntity.ok(response);
    }
}
