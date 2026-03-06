# SAPPHIRE AI - PROJECT CONTEXT

## CORE MISSION
- **100% local deployment** - NOTHING leaves the organization
- **No external API dependencies** in production (OpenAI/Groq/Anthropic)
- **On-premise or private VPC only**
- Our moat: Data sovereignty, complete privacy, local inference

## CURRENT STATUS
- Week 1, Day 1, Task 1.3: Performance testing
- Groq used ONLY for debugging non-LLM issues
- Final deployment: Local LLM only (Ollama/GPU)

## TECH STACK
- Embeddings: HuggingFace (sentence-transformers/all-MiniLM-L6-v2) - 384d
- Vector DB: Qdrant (local, port 6334)
- LLM: Ollama (local) - gemma:2b currently failing
- Database: PostgreSQL (local)
- Auth: JWT (local)

## CRITICAL CONSTRAINTS
- NO cloud LLM APIs in production
- Must work on CPU (no GPU requirement initially)
- Target: <3s latency per query on CPU
- Must handle 10+ concurrent users

## CURRENT BLOCKERS
1. gemma:2b: 90s latency (UNACCEPTABLE)
2. Need CPU-viable local LLM model
3. 99-100% error rate on local inference

## NEXT STEPS
- Find faster local CPU model
- OR accept GPU requirement
- Complete Week 1 polish tasks
- Deploy 100% local

## STRATEGY:
- Document GPU requirement clearly
- On-premise deployment = client provides GPU
- Private VPC = rent GPU instance (AWS g4dn, Azure NC series)
- Still 100% local to org, just needs GPU hardware
Precedent: Many enterprise AI tools require GPU (Whisper, Stable Diffusion, local LLMs)

## TO DO:
- Answer Quality (Most Important)
    - Test 20-30 diverse questions
    - Verify answers are factually correct
    - Check hallucination rate
    - Test "I don't know" responses for out-of-scope queries
    - Validate source citations

- Retrieval Accuracy
  - Verify top-K returns relevant chunks
  - Test edge cases (ambiguous queries)
  - Validate multi-document reasoning
  - Check chunk quality scores

- Error Handling
  - Test with corrupted PDFs
  - Test with empty documents
  - Test with oversized files
  - Verify graceful degradation

- Multi-User Isolation
  - Test org-level data segregation
  - Verify users can't see other org's data

## CHECK LIST:
- WHAT'S MISSING (For Full MVP)
    - P0 - Critical (Must Fix):
        - First query warmup (add startup warmup) - 30 mins
        - Qdrant port config (change 6333 → 6334) - 2 mins
        - Production config validation (env vars) - Done ✅

    - P1 - High Priority (Should Add):
        - Health check endpoint - Already added ✅
        - Document management (list/delete docs) - 2 hours
        - Error messages (user-friendly) - Already added ✅
        - Benchmark suite (automated testing) - 3 hours

    - P2 - Nice to Have (Can Defer):
  
        - Word/Excel support - 4 hours
        - Multi-document upload - 2 hours
        - Search filters (by date/type) - 3 hours
        - Confluence connector - 8 hours
        - Admin dashboard - 20 hours


## CONCRETE ACTION PLAN
- Week 1: Polish MVP (8 hours)
  - Day 1 (3 hours):
    - Add startup warmup (30 min)
    - Fix Qdrant port to 6334 (5 min)
    - Test 50 queries end-to-end (1 hour)
    - Document API (Postman collection) (1 hour)
    - Create deployment guide (30 min)

  - Day 2 (3 hours):
    - Add document list endpoint (1 hour)
    - Add document delete endpoint (1 hour)
    - Test upload 10 documents (30 min)
    - Test concurrent searches (30 min)

  - Day 3 (2 hours):
    - Create startup script (30 min)
    - Test full deployment from scratch (1 hour)
    - Fix any bugs found (30 min)
    
- Week 2: Beta Testing (Variable)
   - Deploy to 3-5 beta users:
   - Monitor for issues
   - Collect feedback
   - Fix critical bugs only
   - Measure actual usage patterns

- Week 3+: Based on Feedback
  - Add features based on what users actually need.

## CURRENT STAGE: 
=== JANUARY 05,2026 ===

✅ TASK 2.4 RESULT
 - What we learned:
- ✅ 7 out of 15 requests succeeded (47% success rate)
- ❌ 8 failed due to Groq rate limit (12k TPM)
- ✅ System handled errors gracefully (no crashes)
- ✅ Retrieval still worked (82ms, confidence 0.29)

Concurrent testing verdict:

- ✅ PASS - System didn't crash
- ❌ FAIL - Can't handle 10+ concurrent users with Groq

This is WHY we need Phase B (local GPU).

📋 DAY 2: COMPLETE ✅
What we built today:

- ✅ List documents endpoint (paginated, filtered)
- ✅ Delete document endpoint (cascade delete)
- ✅ Bulk upload tested (10 documents)
- ✅ Concurrent search tested (revealed Groq limitation)

What we learned:

- ✅ Document management works perfectly
- ✅ Groq can't handle production load (12k TPM too low)
- ✅ Need Phase B deployment urgently


- 🎯 NEXT STEPS - YOUR CHOICE
- Option A: Stop for Today

Day 2 complete
Continue tomorrow with Day 3 OR Phase B
