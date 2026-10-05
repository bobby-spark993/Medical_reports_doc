# Prescription Scanner — Backend

Spring Boot backend for digitising patient medical documents. A photo/scan of a
lab report or prescription is sent to the Google Gemini vision API, the data is
extracted as strict JSON, staff review/edit it, and it is stored in PostgreSQL.
Patients can then be searched by name and/or PID to retrieve their complete
record and every document.

The Gemini API key is used **only on the backend** and is never sent to the
browser.

## Requirements

- Java 21+ (built and tested with the Maven wrapper)
- PostgreSQL (database `Medicine_scanner`, user `postgres`)
- A Gemini API key from <https://aistudio.google.com/apikey>

## Configuration (environment variables)

No secrets are committed. Real values live in the gitignored
`src/main/resources/application-local.properties` or in environment variables.

| Variable | Default | Purpose |
| --- | --- | --- |
| `GEMINI_API_KEY` | *(required)* | Gemini API key. Backend only. |
| `GEMINI_MODEL` | `gemini-3.8-flash` | Vision model used for extraction. |
| `DB_URL` | `jdbc:postgresql://localhost:5432/Medicine_scanner` | JDBC URL. |
| `DB_USER` | `postgres` | Database user. |
| `DB_PASSWORD` | *(required)* | Database password. |
| `UPLOAD_DIR` | `storage/uploads` | Private directory for original scans. |
| `MAX_UPLOAD_MB` | `20` | Max size per uploaded file. |
| `JWT_SECRET` | *(required in prod)* | Signing secret for auth cookies. |
| `UPLOAD max types` | jpg/jpeg/png/webp/pdf | `app.upload.allowed-types` |

### Email (password-reset OTP)

OTP codes are emailed to the account when SMTP is configured. Create the
gitignored repo-root `.env` (see `.env.example`) once:

```dotenv
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=you@gmail.com
SMTP_PASSWORD=your-16-char-app-password
MAIL_FROM=Prescription Scanner <you@gmail.com>
```

`scripts/run-backend.mjs` loads `.env` automatically for `pnpm run dev`. One
Gmail app password can be reused in every project — Google does not require a
unique one per project. If SMTP is not configured, the OTP is printed to the
server console instead (so staff can always reset a password).

Setup:

```powershell
cd backend
Copy-Item src/main/resources/application-local.properties.example `
          src/main/resources/application-local.properties
# then fill in DB password, GEMINI_API_KEY and JWT_SECRET
```

## Run

```powershell
# from the repository root - starts backend (:8080) and frontend (:5173)
pnpm run dev

# backend only (Maven wrapper)
cd backend
.\mvnw.cmd spring-boot:run
```

`spring.jpa.hibernate.ddl-auto=update` creates/updates the tables on startup; a
functional index on `(lower(name), gender, age)` is ensured separately by
`SchemaInitializer`.

## Test

```powershell
cd backend
.\mvnw.cmd test
```

- `PatientMatchingServiceTest` — unit tests for name normalization, `pidShort`
  extraction and candidate matching.
- `PatientSearchIntegrationTest` — search against PostgreSQL (rolled back).
- `MedicalDocumentFlowIntegrationTest` — full scan/confirm flow with Gemini and
  file storage mocked, using the sample lab report and prescription.

## API

All `/api/**` routes require the auth cookie from `POST /api/auth/login`.

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/documents/scan` | multipart `files`; extract only, nothing saved |
| POST | `/api/documents/confirm` | save reviewed data + patient in one transaction |
| GET | `/api/patients/search?name=&pid=` | partial name + exact/prefix PID search |
| GET | `/api/patients/{id}` | full patient detail incl. all documents |
| GET | `/api/patients/by-pid/{pid}` | same, keyed by PID |
| GET | `/api/documents/{id}/file` | stream the original scan |

### Sample curl

```bash
# 1. Log in, keeping the httpOnly auth cookie
curl -s -c cookies.txt -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"********"}' \
  http://localhost:8080/api/auth/login

# 2. Scan (extract draft + possible patient matches; nothing saved)
curl -s -b cookies.txt \
  -F "files=@sample-lab-report.jpg" \
  http://localhost:8080/api/documents/scan

# 3. Confirm the reviewed data (patientAction: AUTO | LINK_EXISTING | CREATE_NEW)
curl -s -b cookies.txt -H "Content-Type: application/json" -d @confirm.json \
  http://localhost:8080/api/documents/confirm

# 4. Search and retrieve
curl -s -b cookies.txt "http://localhost:8080/api/patients/search?name=sushila"
curl -s -b cookies.txt "http://localhost:8080/api/patients/by-pid/SNP260404071824"

# 5. Download the original file
curl -s -b cookies.txt -o report.jpg "http://localhost:8080/api/documents/1/file"
```

## Patient matching rules

1. Exact `pid` match → link the document to that patient.
2. Lab reports print only a trailing 6-digit number next to the name
   (`patientRefNo`, e.g. `071824`); it is matched against `Patient.pidShort`
   and, when a new patient is created, stored in `pidShort` for next time.
3. Name + age + gender collisions are **never auto-merged**. They are returned
   as `possibleMatches` so the user chooses "link to this patient" or "create
   new patient".
4. Existing non-empty patient fields are never overwritten; only blanks are
   filled.

## Notes

- Files are stored under a UUID name and are only reachable through the
  authenticated `GET /api/documents/{id}/file` route.
- Logs record which document was processed but never the API key or full
  patient data. Every save/view of patient data writes an audit-log row.
