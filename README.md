# Session Observability — Demo Microservizio Spring Boot

Progetto didattico per approfondire le pratiche di **observability** in un microservizio Spring Boot.

## Scopo didattico

Questo progetto nasce con l'obiettivo di esplorare e mettere in pratica concetti fondamentali di observability:

- **Correlazione dei log** tramite MDC (Mapped Diagnostic Context)
- **Gestione delle sessioni** con identificativo generato e propagato via header HTTP
- **Concorrenza e thread safety** nei log in ambienti multi-request
- **Architettura pronta per l'evoluzione** verso metriche, tracing distribuito e log strutturato

Non è un progetto production-ready, ma un **laboratorio** pensato per essere esteso incrementalmente: partiamo da due endpoint REST e un database H2 embedded, con la possibilità di sostituire DB, aggiungere metriche, tracing e logging JSON.

## Architettura

```
┌──────────────────────────────────────────────────────────────┐
│                       HTTP Client                            │
└──────────┬────────────────────────────────────┬──────────────┘
           │ POST /api/session/start            │ GET /api/session/execute
           │                                    │ Header: X-Session-Id
           ▼                                    ▼
┌──────────────────────────────────────────────────────────────┐
│              SessionLoggingFilter (implements Filter)        │
│  - Legge X-Session-Id dall'header                            │
│  - Inietta il valore in MDC (ThreadLocal)                    │
│  - Pulisce MDC nel finally block                             │
└──────────────────────────────────────────────────────────────┘
           │                                    │
           ▼                                    ▼
┌─────────────────────────────────────┐  ┌─────────────────────────────┐
│       SessionController             │  │     SessionController       │
│  POST /api/session/start            │  │  GET /api/session/execute   │
└───────────────┬─────────────────────┘  └──────────────┬──────────────┘
                │                                       │
                ▼                                       ▼
┌──────────────────────────────────────────────────────────────┐
│                     SessionService                           │
│  - startSession(): genera UUID, salva su H2                  │
│  - execute(): cerca sessione, aggiorna accesso, logga        │
└──────────────────┬───────────────────────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────────────────────┐
│              SessionRepository (JPA / H2)                    │
│  - Oggi: H2 in-memory (profilo dev)                          │
│  - Futuro: PostgreSQL (profilo prod, properties pronte)      │
└──────────────────────────────────────────────────────────────┘
```

## Tecnologie

| Strato | Tecnologia | Note |
|---|---|---|
| Framework | Spring Boot 3.4.4 | Parent ufficiale |
| Java | 21 | Pattern matching, records |
| Database | H2 in-memory (dev) | PostgreSQL pronto (prod) |
| Logging | Logback + SLF4J + MDC | Rolling file, pattern custom |
| Build | Maven 3.9+ | Profili dev/prod |
| Persistenza | Spring Data JPA + Hibernate | @Version per optimistic locking |

## Endpoint API

### POST /api/session/start

Avvia una nuova sessione. Genera un UUID v4, lo salva su H2 e lo restituisce.

**Request:**
```
POST /api/session/start
```

**Response (200):**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### GET /api/session/execute

Esegue un'operazione fittizia associata a una sessione esistente.

**Request:**
```
GET /api/session/execute
X-Session-Id: 550e8400-e29b-41d4-a716-446655440000
```

**Response (200):**
```json
{
  "status": "OK"
}
```

**Response (404):**
```json
{
  "error": "Sessione non trovata: <uuid>"
}
```

## Observability: come funziona

### MDC (Mapped Diagnostic Context)

L'MDC è una mappa `ThreadLocal` fornita da SLF4J/Logback. Ogni thread Tomcat che gestisce una richiesta HTTP ha la propria copia isolata della mappa — questo garantisce **thread safety nativa**.

### Flusso del logging

```
Richiesta HTTP
      │
      ▼
SessionLoggingFilter.doFilter()
      │
      ├─ Legge header X-Session-Id
      ├─ MDC.put("sessionId", valore)
      │
      ▼
chain.doFilter(request, response)   ← qui girano controller e service
      │                                ogni log.info() include automaticamente
      │                                il sessionId nel pattern
      ▼
finally { MDC.clear() }             ← pulizia garantita
```

### Pattern di log

Il file `logback-spring.xml` definisce:

```xml
<pattern>%d{ISO8601} [%thread] %-5level [sessionId=%X{sessionId}] %logger{36} - %msg%n</pattern>
```

Output esempio:
```
2026-05-21 10:15:30.123 [http-nio-8080-exec-3] INFO  [sessionId=550e8400] c.d.d.o.service.SessionService - Sessione creata con successo
```

### Concorrenza

Grazie al `ThreadLocal` di MDC, richieste concorrenti non interferiscono:

```
Thread-1: MDC["sessionId"] = "aaa" → log(INFO) → stampa "aaa"
Thread-2: MDC["sessionId"] = "bbb" → log(INFO) → stampa "bbb"
Thread-3: MDC["sessionId"] = "ccc" → log(INFO) → stampa "ccc"
```

Nessun `synchronized`, nessun singleton log manager da gestire.

## Profili Spring

| Profilo | Attivo di default | Database |
|---|---|---|
| `dev` | ✅ | H2 in-memory (jdbc:h2:mem:observability) |
| `prod` | — | PostgreSQL (template pronto in `application-prod.yml`) |

Per attivare il profilo prod:
```bash
java -jar target/session-observability.jar --spring.profiles.active=prod
```

## Come eseguire

```bash
# Compilazione
mvn clean package

# Esecuzione (profilo dev, H2)
java -jar target/session-observability-0.0.1-SNAPSHOT.jar

# Test
curl -X POST http://localhost:8080/api/session/start
curl -H "X-Session-Id: <sessionId>" http://localhost:8080/api/session/execute

# Console H2 (solo in dev)
http://localhost:8080/h2-console
```

## Struttura del progetto

```
session-observability/
├── pom.xml
├── src/main/java/com/davidozzo/demo/observability/
│   ├── SessionObservabilityApplication.java
│   ├── config/SessionLoggingFilter.java        ← cuore dell'observability
│   ├── controller/SessionController.java       ← endpoint REST
│   ├── controller/GlobalExceptionHandler.java  ← error handling
│   ├── service/SessionService.java             ← logica di sessione
│   ├── model/dto/*.java                        ← DTO request/response
│   ├── model/entity/SessionEntity.java         ← entità JPA
│   └── repository/SessionRepository.java       ← data access
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   └── logback-spring.xml                      ← pattern MDC, rolling file
└── src/test/java/
```

## Prossimi sviluppi

- [x] Progetto base con sessioni e MDC logging
- [ ] Micrometer + Actuator (metriche request/error)
- [ ] Correlation ID (traceId + spanId nel MDC)
- [ ] Log strutturato JSON (Logstash encoder)
- [ ] OpenTelemetry / distributed tracing
- [ ] Test di integrazione con verifica MDC
- [ ] Containerizzazione (Dockerfile)
- [ ] Helm chart per Kubernetes
