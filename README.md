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
┌──────────────────────────────────────────────────────────────────┐
│                         HTTP Client                              │
└──────┬───────────────────────────────┬──────────────┬────────────┘
       │ GET /api/session/start        │ GET /api/    │ GET /api/  │
       │                               │ session/     │ session/  │
       │                               │ execute      │ list      │
       │                               │ Header:      │           │
       │                               │ X-Session-Id │           │
       ▼                               ▼              ▼           │
┌──────────────────────────────────────────────────────────────────┐
│                SessionLoggingFilter (implements Filter)          │
│                                                                  │
│  /api/session/start  → UUID.randomUUID() → MDC.put("sessionId") │
│  /api/session/list   → skip (endpoint pubblico, MDC vuoto)      │
│  /h2-console         → skip (endpoint pubblico, MDC vuoto)      │
│  altri               → header X-Session-Id → MDC.put             │
│                       se header mancante → 400 BAD_REQUEST       │
│                                                                  │
│  finally { MDC.clear() }  ← pulizia garantita                   │
└──────────────────────────────────────────────────────────────────┘
           │                               │
           ▼                               ▼
┌──────────────────────────┐  ┌─────────────────────────────┐
│  SessionController       │  │  SessionController           │
│  GET /start              │  │  GET /execute  GET /list     │
│  GET /execute            │  │                              │
│  GET /list               │  └──────────────────────────────┘
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────────────────────────────────────────────┐
│                     SessionService                               │
│  - startSession(): MDC.get("sessionId") → salva su H2           │
│  - execute():      MDC.get("sessionId") → cerca sessione        │
│  - listSessions(): findAll() → restituisce elenco               │
│  ❌ Non genera UUID, non gestisce MDC (sola lettura)            │
└──────────────────┬───────────────────────────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────────────────────────┐
│              SessionRepository (JPA / H2)                        │
│  - Oggi: H2 in-memory (profilo dev)                              │
│  - Futuro: PostgreSQL (profilo prod, properties pronte)          │
└──────────────────────────────────────────────────────────────────┘
```

## Tecnologie

| Strato | Tecnologia | Note |
|---|---|---|
| Framework | Spring Boot 3.4.4 | Parent ufficiale |
| Java | 21 | Pattern matching, instanceof pattern |
| Database | H2 in-memory (dev) | PostgreSQL pronto (prod) |
| Logging | Logback + SLF4J + MDC + Logstash encoder | Testo (console) + JSON (file) |
| Build | Maven 3.9+ | Profili dev/prod |
| Persistenza | Spring Data JPA + Hibernate | @Version per optimistic locking |

## Endpoint API

### GET /api/session/start

Avvia una nuova sessione. Il `sessionId` (UUID v4) viene generato dal `SessionLoggingFilter` e restituito nell'header della risposta.

**Request:**
```
GET /api/session/start
```

**Response (200):**
```
X-Session-Id: 550e8400-e29b-41d4-a716-446655440000
```
(corpo vuoto)

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

**Response (400) — header mancante:**
```
400 BAD_REQUEST
```
(gestito dal `SessionLoggingFilter` prima ancora di chiamare il controller)

**Response (404):**
```json
{
  "error": "Sessione non trovata: <uuid>"
}
```

### GET /api/session/list

Restituisce l'elenco di tutte le sessioni create con timestamp di creazione. Endpoint pubblico (non richiede `X-Session-Id`).

**Request:**
```
GET /api/session/list
```

**Response (200):**
```json
[
  {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "createdAt": "2026-05-21T08:30:00"
  }
]
```

## Observability: come funziona

### MDC (Mapped Diagnostic Context)

L'MDC è una mappa `ThreadLocal` fornita da SLF4J/Logback. Ogni thread Tomcat che gestisce una richiesta HTTP ha la propria copia isolata della mappa — questo garantisce **thread safety nativa**.

### Gestione centralizzata MDC nel `SessionLoggingFilter`

Il `SessionLoggingFilter` è l'**unico responsabile** del ciclo di vita dell'MDC:

```
Richiesta HTTP
      │
      ▼
SessionLoggingFilter.doFilter()
      │
      ├─ /api/session/start     → UUID.randomUUID() → MDC.put("sessionId", uuid)
      ├─ /api/session/list      → salta (endpoint pubblico)
      ├─ /h2-console            → salta (endpoint pubblico)
      ├─ altri con header       → header X-Session-Id → MDC.put("sessionId", valore)
      └─ altri senza header     → 400 BAD_REQUEST (fermato qui)
      │
      ▼
chain.doFilter(request, response)   ← controller + service girano con MDC già popolato
      │                                ogni log.info() include automaticamente
      │                                il sessionId nel pattern
      ▼
finally { MDC.clear() }             ← pulizia garantita
```

Il **service** non fa mai `MDC.put`, `MDC.remove` o `MDC.clear` — usa solo `MDC.get("sessionId")` in sola lettura.

### Pattern di log

La configurazione produce **due output**:

**Console** (testo, leggibile in sviluppo):
```
2026-05-21 10:15:30.123 [http-nio-8080-exec-3] INFO  [sessionId=550e8400] c.d.d.o.service.SessionService - Sessione creata con successo
```

**File `logs/app.json`** (JSON strutturato, pronto per ELK/Loki):
```json
{
  "@timestamp": "2026-05-21T10:15:30.123+02:00",
  "level": "INFO",
  "logger_name": "c.d.d.o.service.SessionService",
  "message": "Sessione creata con successo",
  "mdc": {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

Il `sessionId` è un campo strutturato del record di log (sotto `mdc.sessionId`), indicizzabile senza parsing di testo.

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
SESSID=$(curl -s -D - http://localhost:8080/api/session/start | grep -i X-Session-Id | awk '{print $2}' | tr -d '\r')
curl -H "X-Session-Id: $SESSID" http://localhost:8080/api/session/execute
curl http://localhost:8080/api/session/list

# Console H2 (solo in dev)
http://localhost:8080/h2-console
```

## Struttura del progetto

```
session-observability/
├── pom.xml
├── src/main/java/com/davidozzo/demo/observability/
│   ├── SessionObservabilityApplication.java
│   ├── config/SessionLoggingFilter.java        ← cuore dell'observability (MDC lifecycle)
│   ├── controller/SessionController.java       ← endpoint REST (/start, /execute, /list)
│   ├── controller/GlobalExceptionHandler.java  ← error handling
│   ├── service/SessionService.java             ← logica di sessione (solo MDC.get)
│   ├── model/
│   │   ├── dto/ExecuteResponse.java
│   │   ├── dto/SessionSummary.java
│   │   └── entity/SessionEntity.java           ← entità JPA con @Version
│   └── repository/SessionRepository.java       ← JpaRepository
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml                     ← H2 in-memory
│   ├── application-prod.yml                    ← PostgreSQL (template)
│   └── logback-spring.xml                      ← console (testo) + file (JSON con MDC)
└── src/test/java/
```

## Prossimi sviluppi

- [x] Progetto base con sessioni e MDC logging
- [x] Log strutturato JSON (Logstash encoder)
- [ ] Micrometer + Actuator (metriche request/error)
- [ ] Correlation ID (traceId + spanId nel MDC)
- [ ] OpenTelemetry / distributed tracing
- [ ] Test di integrazione con verifica MDC
- [ ] Containerizzazione (Dockerfile)
- [ ] Helm chart per Kubernetes
