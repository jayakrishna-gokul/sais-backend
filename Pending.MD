✅ PHASE 1: CORE PRODUCT (MUST HAVE)
Block 1: Phase B Validation (4-6 hours)
-  1.1 Rent E2E L4 GPU VM (30 mins)
-  1.2 Install Ollama on VM (20 mins)
-  1.3 Pull mistral:7b-instruct (15 mins)
-  1.4 Configure external access (10 mins)
-  1.5 Update local config to point to VM (10 mins)
- 1.6 Test 1 query (verify connection) (10 mins)
-  1.7 Upload 5 test documents (30 mins)
-  1.8 Run 20 test queries (1 hour)
-  1.9 Measure latency for each (record in spreadsheet) (30 mins)
-  1.10 Test 3 concurrent queries (30 mins)
-  1.11 Document results in PHASE_B_RESULTS.md (30 mins)
-  1.12 TERMINATE VM (5 mins)
-  1.13 Verify ₹0/hour billing (5 mins)

DONE WHEN: You have numbers proving local LLM works

Block 2: Docker Deployment (6-8 hours)
-  2.1 Create Dockerfile (30 mins)
-  2.2 Create docker-compose.yml (1 hour)
-  2.3 Create .env.example (15 mins)
-  2.4 Test: mvn clean package (30 mins)
-  2.5 Test: docker-compose up -d (1 hour, will hit issues)
-  2.6 Fix Docker networking issues (1 hour)
-  2.7 Fix GPU access for Ollama container (1 hour)
-  2.8 Verify all 4 services running (30 mins)
-  2.9 Test: Register user via API (10 mins)
-  2.10 Test: Upload document (10 mins)
-  2.11 Test: Search query works (10 mins)
-  2.12 Document any issues found (30 mins)
-  2.13 Fix critical bugs (1 hour)

DONE WHEN: docker-compose up works, all services talk to each other

Block 3: Installation Guide (3-4 hours)

☐ 3.1 Create INSTALLATION.md (2 hours)
- Prerequisites section
- Step-by-step installation
- Configuration instructions
- Verification steps
- Troubleshooting section

☐ 3.2 Test guide on FRESH VM (1 hour)
- Follow your own guide exactly
- Note where you get stuck
- Fix guide based on actual experience

☐ 3.3 Time the installation (record: should be <30 mins) (30 mins)

DONE WHEN: Someone can follow guide and deploy without your help

✅ PHASE 2: CONFLUENCE CONNECTOR (CRITICAL)

Block 4: Confluence API Integration (8-10 hours)

☐ 4.1 Add dependency (5 mins)
- Add Jsoup for HTML parsing to pom.xml
- Add Apache HttpClient
- mvn clean install

☐ 4.2 Create ConfluencePage.java model (30 mins)
☐ 4.3 Create ConfluenceResponse.java model (20 mins)
☐ 4.4 Create ConfluenceConnector.java (2 hours)
- fetchPages() method
- extractText() method
- Handle pagination
- Basic auth with API token

☐ 4.5 Test with free Atlassian Cloud trial (1 hour)
- Create test Confluence space
- Add 10 test pages
- Test fetching pages
- Test text extraction

☐ 4.6 Create ConfluenceIngestionService.java (2 hours)
- ingestSpace() method
- Batch processing (10 pages at a time)
- Error handling
- Progress logging

☐ 4.7 Create ConfluenceController.java (1 hour)
- POST /api/connectors/confluence/ingest
- GET /api/connectors/confluence/status
- Proper error responses

☐ 4.8 Test end-to-end (2 hours)
- Ingest 50 Confluence pages
- Verify all pages indexed
- Search for content from Confluence
- Verify sources cite Confluence

☐ 4.9 Fix bugs found during testing (1 hour)

DONE WHEN: Can ingest Confluence space, search works, sources correct

✅ PHASE 3: DOCUMENTATION & POLISH (NICE TO HAVE)
Block 5: API Documentation (2-3 hours)
☐ 5.1 Add Swagger dependency (10 mins)
☐ 5.2 Add Swagger annotations to controllers (1 hour)
☐ 5.3 Test Swagger UI at /swagger-ui.html (10 mins)
☐ 5.4 Create API.md with examples (1 hour)
- Auth endpoints with curl examples
- Document endpoints with examples
- Search endpoint with example response
- Confluence connector endpoints

☐ 5.5 Test all examples work (30 mins)

DONE WHEN: API is documented, examples work

Block 6: Demo Materials (4-5 hours)
☐ 6.1 Record screen demo (2 hours)
- Install OBS Studio
- Write demo script
- Record 5-minute walkthrough:
* Show docker-compose up
* Upload document
* Connect Confluence
* Run search queries
* Show results with sources
- Do 2-3 takes until good

☐ 6.2 Edit video (1 hour)
- Basic cuts
- Add title slide
- Export

☐ 6.3 Upload to YouTube (unlisted) (10 mins)

☐ 6.4 Create one-pager PDF (1 hour)
- Problem statement
- Solution overview
- Key features
- Pricing
- Contact info
- Use Canva or Google Slides

☐ 6.5 Create simple website (1 hour)
- Use Carrd.co (free tier)
- Single page with:
* Headline
* 3 key benefits
* Demo video embed
* Pricing
* Contact form
- Buy domain: sapphireai.com (₹1000)
- Link Carrd to domain

DONE WHEN: You have demo video + website + one-pager

✅ PHASE 4: FINAL TESTING (CRITICAL)
Block 7: End-to-End Validation (4-6 hours)
☐ 7.1 Rent fresh VM for testing (10 mins)
☐ 7.2 Follow installation guide (should be <30 mins)
☐ 7.3 Load test data (1 hour)
- Upload 50 PDFs
- Ingest Confluence space (100 pages)
- Wait for processing

☐ 7.4 Run 50 test queries (1 hour)
- Measure average latency
- Check answer quality
- Verify no crashes

☐ 7.5 Stress test (1 hour)
- 10 concurrent queries
- 100 total queries in 10 minutes
- Monitor memory/CPU
- Check for any errors

☐ 7.6 Document issues found (30 mins)
☐ 7.7 Fix CRITICAL bugs only (2 hours max)
☐ 7.8 Terminate test VM (5 mins)

DONE WHEN: System handles load, no critical bugs

Block 8: Pre-Launch Checklist (1-2 hours)
☐ 8.1 Code review (30 mins)
- Remove debug statements
- Remove test/temp code
- Check no hardcoded secrets

☐ 8.2 Security check (30 mins)
- Passwords not in git
- .env in .gitignore
- JWT secret changeable
- No SQL injection vulnerabilities

☐ 8.3 Clean up repo (20 mins)
- Update README.md
- Remove unused files
- Organize documentation

☐ 8.4 Final smoke test (20 mins)
- Fresh clone of repo
- Follow installation guide
- Verify works

☐ 8.5 Tag release (10 mins)
- git tag v1.0.0
- git push --tags
- Create GitHub release (if public)

DONE WHEN: Repo is clean, documented, secure

Jan 17: ☑ 1.8 Run 20 test queries (1 hour)
Cold start: 8.6s ✓

======VERSION 2=============
⏳ Block 2: Docker deployment (1-2 hours)
├─ Test in Oracle VM
├─ Fix any issues found
└─ Document what works

📝 Block 3: Installation Guide (3-4 hours)
├─ Write: How to deploy on Linux
├─ Write: Prerequisites (Docker, GPU)
├─ Write: Configuration options
└─ Write: Troubleshooting section

🔌 Block 4: Confluence Connector (8-12 hours)
├─ Implement: Connect to Confluence API
├─ Implement: Sync pages to SAIS
├─ Implement: Schedule updates
└─ Test: Actually works with Confluence

📚 Block 5: API Documentation (2-3 hours)
├─ Document: All REST endpoints
├─ Document: Request/response formats
└─ Export: Postman collection

🎬 Block 6: Demo Setup (2-3 hours)
├─ Create: Sample documents
├─ Create: Demo queries
└─ Create: Quick start guide

✅ Block 7: Final Testing (3-5 hours)
├─ Test: End-to-end workflow
├─ Test: Error handling
└─ Test: Performance with 1000+ docs

📦 Block 8: Package & Ship (2-3 hours)
├─ Git: Clean up, final commit
├─ GitHub: Create release
├─ Documentation: Final review
└─ README: Complete with all info