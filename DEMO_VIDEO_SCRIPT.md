# Sapphire AI Service - Demo Video Script

**Duration:** 5 minutes  
**Format:** Screen recording with voiceover  
**Tools:** OBS Studio / Loom / QuickTime (Mac)

---

## Pre-Recording Setup

**Before you start recording:**

1. **Clean your desktop** - close unnecessary windows
2. **Prepare test data:**
   - Confluence connection already configured
   - At least 50+ pages synced
   - Test query ready: "REST API authentication methods"
3. **Browser setup:**
   - Clear browser history/cache
   - Zoom to 125% (readable on video)
   - Use Incognito/Private mode (clean UI)
4. **Audio:**
   - Test microphone (clear, no background noise)
   - Speak slowly and clearly
   - Smile while talking (changes voice tone positively)

---

## Video Structure

| Section | Duration | Content |
|---------|----------|---------|
| Intro | 0:00-0:30 | Problem + Solution |
| Installation | 0:30-1:30 | Show deployment simplicity |
| Configuration | 1:30-2:30 | Add Confluence connector |
| Search Demo | 2:30-4:00 | Show accurate retrieval |
| Value Prop | 4:00-5:00 | Why customers need this |

---

## SCRIPT

### Section 1: Introduction (0:00 - 0:30)

**[Screen: Slide with "Sapphire AI Service" title]**

**SAY:**
> "Hi, I'm [Your Name], and I'm going to show you Sapphire AI Service - an enterprise RAG knowledge search engine that runs 100% on your infrastructure.
>
> The problem we solve: Your company has thousands of documents in Confluence, SharePoint, and other knowledge bases. Finding the right information takes hours. Traditional search returns dozens of irrelevant results.
>
> Sapphire AI changes this. Watch how fast we can deploy it and get accurate answers."

**[Transition to Ubuntu terminal]**

---

### Section 2: Installation (0:30 - 1:30)

**[Screen: Ubuntu terminal]**

**SAY:**
> "Installation takes about 10 minutes. I've already set up the prerequisites - PostgreSQL, Qdrant vector database, and Java 21. Now let's deploy the application."

**[Type these commands on screen - sped up 2x]:**

```bash
# Show the JAR file
ls -lh sapphire-ai-service-1.0.0.jar

# Start the service
sudo systemctl start sapphire-ai

# Check it's running
sudo systemctl status sapphire-ai
```

**SAY:**
> "The service is now running. Let's verify it's healthy."

**[Run health check]:**

```bash
curl http://localhost:8080/actuator/health
```

**[Show output: {"status":"UP"}]**

**SAY:**
> "Perfect. The API is up and all dependencies are connected. Now let's access the UI."

---

### Section 3: Configuration (1:30 - 2:30)

**[Screen: Browser - navigate to http://localhost:3000]**

**SAY:**
> "Here's the web interface. I'll log in with the admin account."

**[Login with admin/admin123]**

**[Navigate to Admin → Connections]**

**SAY:**
> "To index your company's knowledge base, we add a Confluence connection. This takes about 30 seconds."

**[Click "Add Connection" button]**

**[Fill in form - but don't actually submit, use pre-recorded or existing connection]:**
- Connection Name: "Engineering Docs"
- Confluence URL: https://yourcompany.atlassian.net/wiki
- Email: your-email@company.com
- API Token: [paste token]

**SAY:**
> "I've already added our engineering documentation space. Let's trigger a sync to index all the pages."

**[Click "Sync Now" button]**

**[Show sync progress - should already be completed]**

**SAY:**
> "In a production environment, this would run automatically every hour. We've now indexed 200+ pages from Confluence. Let's see how the search works."

---

### Section 4: Search Demo (2:30 - 4:00)

**[Screen: Search page]**

**SAY:**
> "This is where the magic happens. I'm going to ask a question that would normally require reading through multiple documents."

**[Type in search box: "How do I authenticate with our REST API?"]**

**[Click Search]**

**[Show loading - should take 1-2 seconds]**

**SAY:**
> "Notice how fast that was. Under 2 seconds. Here's what Sapphire AI found."

**[Show results:]**
- Answer box with clear answer
- 80% confidence score
- 5 source documents listed

**SAY - READ THE ANSWER:**
> "The answer says: 'All requests require Bearer token in the Authorization header, formatted as: Authorization: Bearer YOUR_TOKEN_HERE.'
>
> Notice the 80% confidence score. This tells me the system is very confident in this answer. And here are the exact source documents it used - including the REST API Reference page.
>
> Let me click on one of these sources to verify."

**[Click on a source - show the full document]**

**SAY:**
> "Perfect. This is exactly where that information came from. The system didn't hallucinate - it pulled from our actual documentation."

**[Go back to search]**

**SAY:**
> "Let me try another question."

**[Type: "What are the rate limits for our API?"]**

**[Show answer quickly]**

**SAY - READ THE ANSWER:**
> "It says: '1000 requests per hour per token, returns 429 status when limit exceeded.'
>
> Again, highly confident answer with clear sources. This would have taken me 10-15 minutes to find manually by searching through multiple Confluence pages."

---

### Section 5: Value Proposition (4:00 - 5:00)

**[Screen: Back to search page with both queries visible]**

**SAY:**
> "So why does this matter for your organization?
>
> **First: Accuracy.** We're seeing 80-90% confidence on real queries. The system cites its sources, so you can verify every answer.
>
> **Second: Speed.** What used to take 10-15 minutes now takes 2 seconds. Your engineers get instant answers and stay in flow.
>
> **Third: Data Sovereignty.** Unlike Glean or Perplexity where you upload everything to their cloud, your documents never leave your infrastructure. All indexing, embedding, and search happens locally in your PostgreSQL and Qdrant. We use Groq's SOC2-compliant API only for answer generation - they see the question and retrieved snippets, not your raw documents. And when you're ready for absolute sovereignty, we offer Phase B deployment with local GPU - zero external dependencies.
>
> **Fourth: Easy deployment.** You saw the installation - one JAR file, standard Linux tools. No complex Kubernetes setup, no vendor lock-in.

**[Screen: Show admin dashboard with stats]**

**SAY:**
> "The admin dashboard shows you what's indexed, search volume, and system health. Everything you need to manage this in production."

**[Screen: Return to search page]**

**SAY:**
> "Sapphire AI Service - accurate knowledge search that runs entirely on your infrastructure. If you want to see this running in your environment, reach out at [your email].
>
> Thanks for watching."

**[Fade to black with contact info]**

---

## Post-Recording Checklist

**Edit the video:**
- [ ] Trim any dead air / pauses
- [ ] Add title card at start (0:00-0:05)
- [ ] Add contact info at end (4:55-5:00)
- [ ] Speed up boring parts (terminal commands) to 1.5-2x
- [ ] Add captions/subtitles (YouTube auto-generate works)

**Publish:**
- [ ] Upload to YouTube (Unlisted)
- [ ] Title: "Sapphire AI Service - Enterprise RAG Demo"
- [ ] Description: Include deployment guide link
- [ ] Add to website/pitch deck

**Share:**
- [ ] LinkedIn post with video
- [ ] Send to prospects
- [ ] Include in sales emails

---

## Alternative: Short Version (2 minutes)

**If 5 minutes is too long, do this:**

**0:00-0:20** - Problem statement + solution
**0:20-1:00** - Show search demo (skip installation)
**1:00-1:40** - Show second query + sources
**1:40-2:00** - Value prop + CTA

**Cut:**
- Installation steps (assume already running)
- Configuration (show already configured)
- Deep dives into sources

---

## Recording Tips

**Technical:**
- Record at 1920x1080 minimum
- Use Loom (easiest) or OBS Studio (pro)
- Test audio levels before full recording
- Use keyboard shortcuts (not mouse clicking)
- Zoom browser to 125% (more readable)

**Presentation:**
- Speak slower than normal (people watch at 1.5x)
- Pause between sections (easier to edit)
- Don't say "um" or "uh" (pause instead)
- Emphasize numbers: "TWO seconds" not "2 seconds"
- Smile (it changes your voice tone)

**Common mistakes:**
- ❌ Reading the screen (viewers can read)
- ❌ Going too fast (slow down 20%)
- ❌ Too much technical jargon
- ❌ No clear call-to-action at end

**Good habits:**
- ✅ Show, don't tell
- ✅ Use real data (not "example.com")
- ✅ Highlight the "wow" moments (speed, accuracy)
- ✅ Keep it moving (cut anything boring)

---

## Sample YouTube Description

```
Sapphire AI Service - Enterprise RAG Knowledge Search

Watch how we deploy an accurate, on-premise RAG system in under 5 minutes.

✅ 80-90% accuracy on real queries
✅ Sub-2-second response times
✅ 100% on-premise (your data never leaves)
✅ Easy deployment (one JAR file)

Perfect for enterprises that need:
- GDPR compliance
- Data sovereignty
- Integration with Confluence/SharePoint
- Accurate answers (not hallucinations)

🔗 Deployment Guide: [link]
📧 Contact: [your email]
🌐 Website: [your site]

Timestamps:
0:00 - Problem & Solution
0:30 - Installation
1:30 - Configuration
2:30 - Search Demo
4:00 - Why This Matters

#RAG #EnterpriseAI #KnowledgeManagement #OnPremise
```

---

## Ready to Record?

**Checklist before hitting record:**
- [ ] Clean desktop
- [ ] Test queries work (run them once)
- [ ] Audio test (record 10 seconds)
- [ ] Browser zoom at 125%
- [ ] Script printed (glance, don't read)
- [ ] Water nearby (stay hydrated)
- [ ] Phone on silent

**Record 2-3 takes:**
- Take 1: Practice run (will have mistakes)
- Take 2: Your "good" version
- Take 3: Backup (if Take 2 has issues)

Pick the best parts from each take when editing.

**Good luck! 🎬**
