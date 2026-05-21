# AGENTS.md — Contexto del progetto

## Identità
- **Progetto**: `session-observability`
- **Repo**: `https://github.com/davidozz0/session-observability`
- **Base package**: `com.davidozzo.demo.observability`
- **Scopo**: Progetto didattico per approfondire observability in microservizi Spring Boot
- **Dev**: davidozzo (d.davidozzo@gmail.com)

## Stack
| Strato | Tecnologia | Versione |
|---|---|---|
| Framework | Spring Boot | 3.4.4 |
| Java | JDK | 21 (Temurin) |
| Database | H2 in-memory (dev) | — |
| DB futuro | PostgreSQL (prod) | Template in `application-prod.yml` |
| Logging | Logback + SLF4J + MDC + Logstash encoder | — |
| Build | Maven | 3.9+ |
| Persistenza | Spring Data JPA + Hibernate | @Version per optimistic locking |
| Metriche | Micrometer + Actuator + Prometheus | Counter, Timer, health check |
| Lombok | Usato (getter/setter/costruttori) | — |

**Dipendenze chiave in pom.xml**: spring-boot-starter-web, spring-boot-starter-data-jpa, h2 (runtime), logstash-logback-encoder 8.0, spring-boot-starter-actuator, micrometer-registry-prometheus, lombok (optional)

## Regole operative per l'AI
1. **MAI chiamare commit/push in autonomia** — solo su richiesta esplicita dell'utente
2. **Non generare UUID in Service** — l'UUID è generato dal `SessionLoggingFilter`
3. **MDC lifecycle solo nel Filter** — mai MDC.put/remove/clear in Service/Controller
4. **Service usa solo MDC.get in lettura** — per leggere il contesto corrente
5. **No @RequestHeader per sessionId** — il filtro lo legge dall'header e lo mette in MDC
6. **Lombok è usato** — @Getter, @Setter, @NoArgsConstructor, @RequiredArgsConstructor, @Data, @AllArgsConstructor
7. **Nessun commento nel codice** — il codice deve essere autoesplicativo
8. **Java 21** — usare pattern matching instanceof, records dove appropriato
9. **Aggiornamento AGENTS.md** — durante plan/build, chiedere sempre all'utente se vuole aggiornare AGENTS.md con le nuove decisioni
10. **Centralizzare senza over-engineering** — cercare il punto giusto tra astrazione e semplicità: centralizzare responsabilità affini (es. ciclo di vita MDC in un solo filtro) e separare i compiti (es. filtro = put/clear, service = sola lettura), ma senza creare layer inutili o astrarre prima del bisogno

## Architettura — MDC centralizzato

Il `SessionLoggingFilter` è l'**unico responsabile** del ciclo di vita dell'MDC.

### SessionLoggingFilter (config/SessionLoggingFilter.java)
```
Flusso:
  doFilter()
    ├─ resolveSessionId()
    │   ├─ path == "/api/session/start"    → UUID.randomUUID()
    │   ├─ path in PUBLIC_PATHS            → null (MDC vuoto, OK)
    │   │   (/api/session/list, /h2-console)
    │   └─ altri path                      → header X-Session-Id
    │       se header mancante             → IllegalArgumentException → 400
    │
    ├─ if (sessionId != null) MDC.put("sessionId", sessionId)
    ├─ chain.doFilter()  ← controller + service girano con MDC popolato
    └─ finally { MDC.clear() }
```

### PUBLIC_PATHS
Sono path che **non richiedono** header X-Session-Id:
- `/api/session/start` — genera UUID e lo mette in MDC
- `/api/session/list` — non usa MDC, va in chiaro
- `/h2-console` — console H2

### Thread safety
MDC è ThreadLocal (SLF4J). Ogni request thread Tomcat ha copia isolata. Zero race condition.

## Endpoint API

| Metodo | Path | Header richiesto | Response |
|---|---|---|---|
| GET | /api/session/start | Nessuno | 200 + header `X-Session-Id: <uuid>` (corpo vuoto) |
| GET | /api/session/execute | `X-Session-Id` | 200 `{ "status": "OK" }` |
| GET | /api/session/list | Nessuno | 200 `[{ "sessionId", "createdAt" }]` |

## Struttura progetto

```
E:\dev\src\session-observability\
├── pom.xml
├── AGENTS.md
├── README.md
├── .gitignore
├── src/main/java/com/davidozzo/demo/observability/
│   ├── SessionObservabilityApplication.java      ← @SpringBootApplication
│   ├── config/
│   │   └── SessionLoggingFilter.java              ← MDC lifecycle (unico punto)
│   ├── controller/
│   │   ├── SessionController.java                 ← GET /start, /execute, /list
│   │   └── GlobalExceptionHandler.java             ← @ControllerAdvice
│   ├── service/
│   │   └── SessionService.java                     ← logica con MDC.get + metriche Micrometer
│   ├── model/
│   │   ├── dto/
│   │   │   ├── ExecuteResponse.java               ← { status }
│   │   │   └── SessionSummary.java                ← { sessionId, createdAt }
│   │   └── entity/
│   │       └── SessionEntity.java                 ← JPA @Entity + @Version
│   └── repository/
│       └── SessionRepository.java                 ← JpaRepository + findBySessionId
├── src/main/resources/
│   ├── application.yml                            ← profilo attivo: dev
│   ├── application-dev.yml                        ← H2 in-memory
│   ├── application-prod.yml                       ← PostgreSQL (template)
│   └── logback-spring.xml                         ← console (testo) + file (JSON)
└── src/test/java/com/davidozzo/demo/observability/
    └── SessionObservabilityApplicationTests.java
```

## Logging

### Console (testo)
```
%replace(CONSOLE): %d{ISO8601} [%thread] %-5level [sessionId=%X{sessionId}] %logger{36} - %msg%n
```

### File JSON (logs/app.json)
```json
{
  "@timestamp": "...",
  "level": "INFO",
  "logger_name": "c.d.d.o.service.SessionService",
  "message": "Sessione creata con successo",
  "mdc": { "sessionId": "uuid" }
}
```
Rotazione giornaliera, 30gg retention.

## Decisioni architetturali (ADR)

### ADR-1: MDC centralizzato nel Filter
- **Decisione**: `SessionLoggingFilter` è l'unico che fa MDC.put/clear
- **Motivazione**: evitare sovrapposizioni in concorrenza, lifecycle chiaro
- **Conseguenza**: Service usa solo `MDC.get("sessionId")` in lettura

### ADR-2: UUID generato dal Filter, non dal Service
- **Decisione**: per `/start` il Filter genera UUID.randomUUID() e lo mette in MDC
- **Motivazione**: il sessionId deve essere nell'MDC prima che Service/Controller logghino
- **Conseguenza**: Service legge da MDC.get, non genera mai UUID

### ADR-3: SessionId nell'header response, non nel body
- **Decisione**: `/start` restituisce sessionId nell'header X-Session-Id
- **Motivazione**: coerente con lo standard REST per resource creation

### ADR-4: Solo sincrono, niente async
- **Decisione**: niente @Async, CompletableFuture, WebFlux
- **Motivazione**: MDC ThreadLocal si propaga solo nel thread corrente

### ADR-5: Validazione header nel Filter, non nel Service
- **Decisione**: se header X-Session-Id mancante, Filter risponde 400
- **Motivazione**: fail fast prima di chiamare il controller

### ADR-6: H2 oggi, PostgreSQL domani
- **Decisione**: oggi H2 in-memory (profilo dev), template prod pronto
- **Motivazione**: stessa entity/repository, swap con properties

### ADR-7: Lombok per ridurre boilerplate
- **Decisione**: usare Lombok per getter, setter, costruttori e DTO
- **Motivazione**: ridurre codice ripetitivo, focus su logica di business
- **Conseguenza**: Entity usa @Getter/@NoArgsConstructor/@Setter, DTO usa @Data/@NoArgsConstructor/@AllArgsConstructor, Service/Controller usano @RequiredArgsConstructor

### ADR-8: Metriche con Micrometer + Actuator
- **Decisione**: usare `MeterRegistry` per counter e timer custom nel Service
- **Motivazione**: visibilità su quante sessioni/create/execute vengono fatte, durata delle execute, health check
- **Conseguenza**: Service inietta `MeterRegistry`; metriche custom esposte via `/actuator/metrics` e `/actuator/prometheus`
- **Metriche definite**:
  - `session.created` (Counter) — ogni startSession()
  - `session.executed` (Counter con tag status=success|error) — ogni execute()
  - `session.execute.duration` (Timer) — durata della execute()

## Prossimi step pianificati
1. [x] Micrometer + Actuator (metriche request/error)
2. [ ] Correlation ID (traceId + spanId nel MDC)
3. OpenTelemetry / distributed tracing (Jaeger/Zipkin)
4. Test di integrazione con verifica MDC
5. Containerizzazione (Dockerfile)
6. Helm chart per Kubernetes
