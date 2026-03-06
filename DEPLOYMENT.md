# Sapphire AI - Deployment Guide

## Overview

Sapphire AI is a Retrieval-Augmented Generation (RAG) system designed for 100% local, on-premise deployment with complete data sovereignty.

**Version:** 1.0.0 (Phase A - Development Validation Complete)  
**Last Updated:** January 4, 2026

---

## Architecture

### Core Components
- **Database:** PostgreSQL 16+ (local)
- **Vector Store:** Qdrant standalone (local, port 6334)
- **Embeddings:** HuggingFace sentence-transformers/all-MiniLM-L6-v2 (384 dimensions)
- **LLM (Phase A):** Groq API (development only)
- **LLM (Phase B):** Local Ollama on GPU (production)
- **Authentication:** JWT-based
- **API:** Spring Boot REST

### Data Flow
```
User Query → Authentication → Embedding Generation (HuggingFace) → 
Vector Search (Qdrant) → Retrieval → LLM Generation → Response
```

---

## Prerequisites

### Required Software
- **Java:** 21 or higher
- **Maven:** 3.9+
- **PostgreSQL:** 16+
- **Qdrant:** Latest standalone version
- **Git:** For version control

### Optional (Phase A)
- **Groq API Key:** For development testing (free tier: 100k tokens/day)

### Required (Phase B - Production)
- **GPU:** NVIDIA GTX 1060 (6GB VRAM) minimum
- **Ollama:** With local LLM models

---

## Installation

### 1. Database Setup
```bash
# Start PostgreSQL service
# Windows: Services → PostgreSQL → Start
# Linux: sudo systemctl start postgresql

# Create database and user
psql -U postgres
```
```sql
CREATE DATABASE sapphireai;
CREATE USER sapphire WITH PASSWORD 'your-secure-password';
GRANT ALL PRIVILEGES ON DATABASE sapphireai TO sapphire;
\q
```

**Security Note:** Change `your-secure-password` to a strong password.

---

### 2. Qdrant Setup

#### Option A: Windows Standalone
```powershell
# Download from https://github.com/qdrant/qdrant/releases
# Extract to C:\qdrant
cd C:\qdrant
.\qdrant.exe
```

**Expected Output:**
```
Qdrant HTTP listening on 6333
Qdrant gRPC listening on 6334
```

#### Option B: Docker
```bash
docker run -d \
  -p 6333:6333 \
  -p 6334:6334 \
  -v $(pwd)/qdrant_storage:/qdrant/storage \
  --name qdrant \
  qdrant/qdrant
```

**Verify Qdrant:**
```bash
curl http://localhost:6333/collections
# Expected: {"result":{"collections":[]}}
```

---

### 3. Application Configuration

#### Local Development (Phase A)

**File: `application-local.yml`**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sapphireai
    username: sapphire
    password: ${DB_PASSWORD:your-secure-password}

sapphire:
  inference:
    strategy: remote  # Use Groq for Phase A
    
    remote:
      enabled: true
      api-url: https://api.groq.com/openai
      api-key: ${GROQ_API_KEY:}
      model: llama-3.3-70b-versatile
      
  vector:
    base-url: http://localhost:6334  # gRPC port
    collection-name: sapphire_chunks
    
  document:
    storage-path: C:/sapphire/documents  # Windows
    # storage-path: /var/sapphire/documents  # Linux
    
  security:
    jwt:
      secret: "MySecretKeyForDevelopmentOnlyChangeInProduction1234567890"
      expiration-ms: 86400000  # 24 hours
```

**Set Environment Variables:**

Windows:
```powershell
$env:DB_PASSWORD = "your-secure-password"
$env:GROQ_API_KEY = "gsk_your_groq_api_key"
```

Linux:
```bash
export DB_PASSWORD="your-secure-password"
export GROQ_API_KEY="gsk_your_groq_api_key"
```

---

### 4. Build Application
```bash
# Clone repository
git clone <repository-url>
cd sapphire-ai-service

# Build (skip tests for faster build)
mvn clean package -DskipTests

# OR build with tests
mvn clean package
```

**Build Output:**
```
target/sapphire-ai-service-1.0.0.jar
```

---

### 5. Run Application

#### Development Mode
```bash
mvn spring-boot:run -Dspring.profiles.active=local
```

#### Production Mode
```bash
java -jar target/sapphire-ai-service-1.0.0.jar --spring.profiles.active=local
```

**Expected Startup Logs:**
```
Warming up system components...
✅ Embeddings ready: 5000ms
✅ LLM providers ready: 100ms
🎉 System warmup complete: 5100ms
Started SapphireAiApplication in 21.403 seconds
Tomcat started on port 8080
```

---

## Verification

### 1. Health Check
```bash
curl http://localhost:8080/api/health
```

**Expected Response:**
```json
{
  "status": "UP",
  "embedding": {
    "provider": "huggingface:sentence-transformers/all-MiniLM-L6-v2",
    "dimension": 384
  },
  "inference": {
    "available": true
  }
}
```

### 2. API Documentation
Open browser: `http://localhost:8080/swagger-ui.html`

### 3. Test Workflow

Using Postman (import `Sapphire-AI-MVP.postman_collection.json`):

1. **Register User**
```
   POST /api/auth/register
   {
     "email": "test@example.com",
     "password": "SecurePassword123",
     "fullName": "Test User",
     "organizationName": "Test Org"
   }
```

2. **Login** (saves JWT token automatically)
```
   POST /api/auth/login
   {
     "email": "test@example.com",
     "password": "SecurePassword123"
   }
```

3. **Upload Document**
```
   POST /api/documents/upload
   Headers: Authorization: Bearer {token}
   Body: form-data, file: your-document.pdf
```

4. **Search**
```
   POST /api/search
   Headers: Authorization: Bearer {token}
   {
     "query": "What is the vacation policy?",
     "topK": 5,
     "generateAnswer": true
   }
```

---

## Performance Metrics

### Phase A (Groq API)
| Metric | Target | Actual |
|--------|--------|--------|
| **First query** | <6s | ~5s (model warmup) |
| **Subsequent queries** | <1s | **160-450ms** ✅ |
| **Answer quality** | 90%+ | **100%** ✅ |
| **Retrieval accuracy** | >0.5 | **0.56-0.65** ✅ |
| **Chunking quality** | >0.7 | **0.8-1.0** ✅ |

### Phase B (Local GPU - Expected)
| Metric | Expected |
|--------|----------|
| **Latency** | 3-6s per query |
| **Concurrent users** | 10+ |
| **Model** | Mistral 7B (Q4) |

---

## Troubleshooting

### Slow First Query
**Symptom:** First query takes 5+ seconds  
**Cause:** Embedding model loading (one-time)  
**Solution:** Normal behavior. Warmup runs on startup.

### Qdrant Connection Failed
**Symptom:** `Connection refused: localhost:6333`  
**Cause:** Wrong port or Qdrant not running  
**Solution:**
```bash
# Check Qdrant status
curl http://localhost:6333/collections

# Verify config uses port 6334 (gRPC)
# application-local.yml: base-url: http://localhost:6334
```

### JWT Token Errors
**Symptom:** `Illegal base64 character`  
**Cause:** JWT secret contains invalid characters  
**Solution:** Use alphanumeric only, 64+ characters, no dashes
```yaml
jwt:
  secret: "MySecretKeyForDevelopmentOnlyChangeInProduction1234567890"
```

### Groq Rate Limit
**Symptom:** `429 Too Many Requests`  
**Cause:** Exceeded 100k tokens/day (free tier)  
**Solution:**
- Wait 24 hours for reset
- Reduce query frequency
- Switch to local LLM (Phase B)

### Chunking Issues
**Symptom:** Chunks exceed 2000 characters  
**Cause:** Bug in chunking logic  
**Solution:** Already fixed ✅ All chunks validated <2000 chars

---

## Production Deployment Checklist

### Security
- [ ] Change JWT secret to random 64+ character string
- [ ] Set strong database password (16+ chars)
- [ ] Remove Groq API key (use local LLM)
- [ ] Enable HTTPS (not included in MVP)
- [ ] Configure firewall rules
- [ ] Disable Swagger UI in production

### Infrastructure
- [ ] PostgreSQL backups configured
- [ ] Qdrant data directory backed up
- [ ] Document storage backed up
- [ ] Log rotation configured
- [ ] Monitoring/alerting setup

### Phase B Requirements
- [ ] GPU installed (NVIDIA GTX 1060+ with 6GB VRAM)
- [ ] Ollama installed
- [ ] Local LLM model downloaded (`ollama pull mistral:7b`)
- [ ] Switch inference strategy to `quality`
- [ ] Test latency (expect 3-6s per query)

---

## Configuration Reference

### Supported Document Types
- PDF (`.pdf`)
- Text (`.txt`)
- Word (`.doc`, `.docx`) - Planned
- Excel (`.xls`, `.xlsx`) - Planned
- HTML (`.html`) - Supported

### Chunking Strategy
- **Default:** Semantic (paragraph-based)
- **Chunk size:** 800 characters (target)
- **Max size:** 2000 characters (hard limit)
- **Overlap:** 200 characters
- **Quality threshold:** >0.7

### Embedding Model
- **Provider:** HuggingFace (local)
- **Model:** sentence-transformers/all-MiniLM-L6-v2
- **Dimensions:** 384
- **Speed:** ~100ms per query

### Vector Database
- **Provider:** Qdrant (local)
- **Collection:** sapphire_chunks
- **Distance:** Cosine similarity
- **Top-K:** 20 (initial retrieval)

---

## Maintenance

### Database Backups
```bash
# Backup
pg_dump -U sapphire sapphireai > backup_$(date +%Y%m%d).sql

# Restore
psql -U sapphire sapphireai < backup_20260104.sql
```

### Qdrant Backups
```bash
# Stop Qdrant
# Copy storage directory
cp -r C:\qdrant\storage C:\qdrant\backup_$(date +%Y%m%d)
```

### Log Management
```bash
# Logs location
logs/sapphire-ai.log

# Rotate logs (configured in application.yml)
# Max size: 10MB
# Max history: 30 days
```

---

## Support & Troubleshooting

### Common Issues

**Q: Why is Groq only for Phase A?**  
A: Groq is cloud-based. Phase B uses 100% local LLM for data sovereignty.

**Q: Can I use CPU without GPU?**  
A: Yes for Phase A (Groq). Phase B requires GPU for acceptable latency (3-6s).

**Q: How many documents can I upload?**  
A: Limited by storage and PostgreSQL capacity. Tested with 200-page PDFs ✅

**Q: What's the difference between confidence scores?**  
A: Two scores exist:
- **Qdrant score (0.56-0.65):** Vector similarity
- **Backend confidence (0.2-0.5):** Custom RAG metric

Both are informational. Answer quality is what matters.

---

## Appendix: Validated Performance

### Test Results (Phase A Complete)

**Chunking:**
- Small docs (2 pages): ✅ 282-1985 chars
- Large docs (200 pages): ✅ 652-1999 chars
- Quality scores: ✅ 0.8-1.0

**Retrieval:**
- Vacation policy: ✅ 0.622 score
- Equipment laptop: ✅ 0.560 score
- Git commands: ✅ 0.650 score
- Irrelevant query: ✅ 0.029 score

**Answer Quality:**
- Tested queries: ✅ 100% accuracy
- Hallucination rate: ✅ 0%
- "I don't know" handling: ✅ Works

**System Ready for Phase B Deployment** ✅

---

## Version History

**v1.0.0 (Phase A)** - January 4, 2026
- Initial release
- Development validation complete
- Groq API integration
- Local embeddings (HuggingFace)
- Qdrant vector store
- Chunking validated (2-200 pages)
- Performance: 160-450ms average

**Roadmap (Phase B)**
- Local GPU LLM deployment
- Production hardening
- Multi-user testing
- Error handling enhancement