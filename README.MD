# Sapphire AI Service

Enterprise RAG Knowledge Search Engine with 91% accuracy target.
This is the line you never deviate from:

“Sapphire keeps all documents, embeddings, vectors, and retrieval fully on your infrastructure.
For answer generation, we currently support a SOC2-compliant inference API for fast deployment.
For customers with strict compliance needs, the same system runs fully on-prem with a local LLM — no architectural changes required.”
You are not saying:

“Zero external calls” ❌

“100% on-prem today” ❌

You are saying:

“You own your data, not us” ✅

That’s the moat.

## Features

- ✅ Multi-tenant organization support
- ✅ SSO-ready (OAuth2/JWT)
- ✅ Intelligent document chunking (Policy/Technical/General)
- ✅ Hybrid search (Vector + BM25)
- ✅ Conversation with 15+ turn support (auto-summarization)
- ✅ Multiple document types (PDF, Word, Excel, HTML)
- ✅ On-premise deployment

## Prerequisites

- Java 21
- PostgreSQL 16+
- Maven 3.9+
- Qdrant (vector database)
- Ollama (local LLM)

## Quick Start

### 1. Install Dependencies
```bash
# PostgreSQL
sudo apt install postgresql-16

# Qdrant (Docker)
docker run -d -p 6333:6333 qdrant/qdrant

# Ollama
curl https://ollama.ai/install.sh | sh
```

### 2. Setup Ollama Models
```bash
chmod +x scripts/setup-ollama.sh
./scripts/setup-ollama.sh
```

### 3. Configure Database
```bash
sudo -u postgres psql
CREATE DATABASE sapphireai;
CREATE USER sapphire WITH PASSWORD 'yourpassword';
GRANT ALL PRIVILEGES ON DATABASE sapphireai TO sapphire;
\q
```

### 4. Configure Application

Edit `src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    password: yourpassword

sapphire:
  security:
    jwt:
      secret: your-secret-key-min-256-bits
```

### 5. Build
```bash
mvn clean package
```

### 6. Run
```bash
chmod +x scripts/start.sh
./scripts/start.sh
```

## API Endpoints

### Authentication
- POST `/api/auth/register` - Register new user
- POST `/api/auth/login` - Login

### Documents
- POST `/api/documents/upload` - Upload document
- GET `/api/documents` - List documents
- DELETE `/api/documents/{id}` - Delete document

### Search
- POST `/api/search` - Search and get answer
- GET `/api/search/sessions/{id}/history` - Get conversation history

## Testing
```bash
# Register user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@company.com",
    "password": "password123",
    "fullName": "John Doe"
  }'

# Login (get token)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@company.com",
    "password": "password123"
  }'

# Upload document
curl -X POST http://localhost:8080/api/documents/upload \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@handbook.pdf"

# Search
curl -X POST http://localhost:8080/api/search \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What is the vacation policy?",
    "generateAnswer": true
  }'
```

## Architecture
```
User Query
    ↓
[Embedding Service] → Convert to vector
    ↓
[Qdrant Vector DB] → Find similar chunks (hybrid search)
    ↓
[Re-ranking] → Score and sort
    ↓
[Context Expansion] → Retrieve parent chunks
    ↓
[LLM Service (Mistral 7B)] → Generate answer
    ↓
Response with sources
```

## Configuration

See `application.yml` for all configuration options:
- LLM settings (model, temperature)
- Chunking parameters
- RAG scoring weights
- Security settings

## Troubleshooting

**Port already in use:**
```bash
lsof -i :8080
kill -9 <PID>
```

**Ollama not responding:**
```bash
ollama serve &
ollama list
```

**Database connection failed:**
```bash
sudo systemctl status postgresql
sudo systemctl start postgresql
```

## License

Proprietary
```

---

## ✅ PROJECT COMPLETE

**Total files created:** 35+

**Structure:**
```
sapphire-ai-service/
├── pom.xml ✅
├── README.md ✅
├── src/main/
│   ├── java/com/sapphireai/
│   │   ├── SapphireAiApplication.java ✅
│   │   ├── config/ (5 files) ✅
│   │   ├── controller/ (3 files) ✅
│   │   ├── model/ (14 files) ✅
│   │   ├── repository/ (6 files) ✅
│   │   ├── security/ (4 files) ✅
│   │   └── service/ (9 files) ✅
│   └── resources/
│       ├── application.yml ✅
│       ├── application-prod.yml ✅
│       └── db/migration/ (2 SQL files) ✅
└── scripts/ (2 files) ✅