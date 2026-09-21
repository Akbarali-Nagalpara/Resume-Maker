# ResumeFlow Backend

Java 25 + Spring Boot 4.1 + Maven. Upload → parse → edit → generate → download,
with strict preservation of the user's original resume template.

## Prerequisites

- JDK 25 (used for builds; see `JAVA_HOME` below)
- Maven 3.9+
- PostgreSQL 18 with a `resumeflow` database
- Redis 7+

## Environment

Copy `.env.example` to `.env` and fill in real values. Credentials are never
stored in source code; `application.yml` only holds `DB_*`/`REDIS_*` placeholders
with local defaults.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `resumeflow` | PostgreSQL |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | PostgreSQL auth |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis |
| `RESUMEFLOW_STORAGE_PATH` | `./storage` | Original/generated artifacts (outside web root) |
| `MAX_RESUME_SIZE_MB` | `10` | Upload limit |

## Run

```sh
export JAVA_HOME=<jdk-25-home>
export DB_PASSWORD=...
cd backend
mvn spring-boot:run
```

Health: `GET http://localhost:8080/actuator/health`

If port 8080 is taken (e.g. by the frontend dev server), start the backend on
another port — no code change needed, Spring Boot maps `SERVER_PORT`:

```sh
SERVER_PORT=8081 mvn spring-boot:run
```

Production build: `mvn verify` (unit + Testcontainers integration tests, then jar).
Golden PDF tests (`GoldenPdfIT`) additionally require the document
processor running on the host at `:8001`, since the test JVM calls it over
plain localhost (only PostgreSQL/Redis run in containers).

```sh
export DB_PASSWORD=...
java -jar target/resumeflow-backend-0.1.0-SNAPSHOT.jar
```

## REST API (`/api/v1/resumes`)

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/resumes` (multipart `file`) | Upload + parse |
| `GET` | `/api/v1/resumes/{id}` | Editor state (content + template + sections) |
| `PUT` | `/api/v1/resumes/{id}/content` | Update content (`{expectedVersion, content}`) |
| `POST` | `/api/v1/resumes/{id}/generate` | Generate with preserved template |
| `GET` | `/api/v1/resumes/{id}/preview` | Current preview reference |
| `GET` | `/api/v1/resumes/{id}/download` | Download latest generated file |
| `GET` | `/api/v1/resumes/{id}/versions` | List versions |
| `GET` | `/api/v1/resumes/{id}/versions/{version}` | Get one version |
| `DELETE` | `/api/v1/resumes/{id}` | Delete resume + artifacts |

Errors use RFC 7807 Problem Details; every response carries `X-Request-Id`.

## Architecture

`controller` (HTTP only) → `service` (business logic) → `repository` (JPA) /
`parser` / `generator` / `storage` (interfaces + replaceable implementations).
Entities never leave the API layer (`dto` + `mapper` only).

Services: `ResumeService` (orchestrator), `ResumeUploadService`,
`ResumeParserService`, `ResumeTemplateService`, `ResumeContentService`,
`ResumeGenerationService`, `ResumePreviewService`, `ResumeVersionService`,
`FileStorageService`, `ResumeCacheService`.

Schema is Flyway-managed (`src/main/resources/db/migration`); Hibernate runs
with `ddl-auto: validate`. Note: Spring Boot 4 ships no Flyway
auto-configuration, so `config/FlywayConfig` wires it manually.

## Redis (cache-aside, PostgreSQL authoritative)

| Key | TTL | Eviction |
| --- | --- | --- |
| `resume:{id}:editor` | 10 min | content update, generate, delete |
| `resume:{id}:template` | 45 min | content update, generate, delete |
| `resume:{id}:preview` | 10 min | content update, generate, delete |

Binaries are never cached. Cache failures degrade to direct DB reads.

## Frontend integration (Phase 10 seam)

The frontend's `mockResumeService` (`frontend/src/features/resume-flow/services/`)
implements the `ResumeService` interface. To connect this backend, add an HTTP
implementation behind the same interface using `VITE_API_BASE_URL`:

- `parseResume(file, onProgress)` → `POST /api/v1/resumes` (multipart), then
  `GET /api/v1/resumes/{id}` for the editor model. Progress callbacks stay
  client-side estimates; the editor `ResumeData` maps 1:1 from `content`.
- `saveResume(resume)` → `PUT /api/v1/resumes/{id}/content` with
  `{expectedVersion, content}`. `expectedVersion` is `resume.version` from the
  editor state; `409` means reload + retry.
- `downloadResume(resume, fileName)` → `POST /api/v1/resumes/{id}/generate`
  then `GET /api/v1/resumes/{id}/download`.

No screen changes are required; the wire shape already matches the mock.

## Known limitations

- DOCX round-trip preserves styles/layout strongly, but only mapped content
  nodes are editable (personal name, summary, skills, experience, projects,
  education). Tables, text boxes, footnotes and embedded objects are carried
  over byte-identical but not content-editable.
- PDF generation patches changed fields in the original file (redact +
  insert at the original baseline with the original size/color). A patch
  applies only when the replacement fits (compared against both the element
  width and the original advance) and the font maps to Base-14 or a
  metric-compatible twin (Nimbus/Liberation families; symbol bullets restyled
  to •); otherwise an explicit warning is returned and the original is kept.
  Added lines/items have no original element and are reported, not placed.
  No-edit generations return the original bytes untouched.
- Patched text keeps its visual position, but PDF text-extraction order may
  place inserted text later in the stream (cosmetic for selection/search
  order, not for rendering).
- Personal header fields beyond name (title, contact lines) are extracted but
  not yet mapped back into DOCX generation (title/contact paragraphs have no
  stable node mapping); they round-trip unchanged. In PDFs, contact edits
  outside the name produce an explicit `contact-preserved` warning.
- `location` subfields are stored and previewed but not rendered into
  generated files.
- Unstyled section-heading detection is heuristic (short title-shaped lines,
  bullets never headings, date lines never headings); unusual body lines
  starting with section keywords may need explicit Heading styles in DOCX
  for perfect classification. Hyphen-wrapped fragments stay split across
  lines (truthful and patchable, cosmetic in the editor).
  unusual body lines starting with section keywords may need explicit Heading
  styles in DOCX for perfect classification.
- Parsing is synchronous in the request thread; large files should move to an
  async job model (endpoint shape already supports polling via versions).
