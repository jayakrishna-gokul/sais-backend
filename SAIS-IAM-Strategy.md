# SAIS - Identity & Access Management Strategy
**Enterprise RAG Platform - Authentication & Authorization Architecture**

---

## Executive Summary

SAIS operates as a **multi-tenant SaaS platform** where each customer organization is isolated. The IAM system must support:
- **Installer-based deployment**: First admin created during installation
- **Organization-level isolation**: Complete data segregation between customers
- **Role-based access**: Admin and User roles with different permissions
- **Scalability**: Support hundreds of users per organization
- **Enterprise readiness**: SSO integration path for large deployments

---

## 1. USER HIERARCHY & ROLES

### **Level 1: Super Admin (System Level)**
- **Who**: Person who runs the installer on customer's infrastructure
- **Created**: During `./install.sh` execution
- **Scope**: Entire SAIS instance
- **Capabilities**:
  - Create/manage organizations (if supporting multiple orgs on one instance)
  - System-wide settings
  - Monitor all organizations
  - Backup/restore
  
**Current Implementation**: Single organization per installation (no super admin needed yet)

---

### **Level 2: Organization Admin**
- **Who**: First user created by installer for the organization
- **Created**: During installation via SQL insert
- **Scope**: Single organization only
- **Capabilities**:
  - Invite/create users within their organization
  - Manage connectors (Confluence, SharePoint, etc.)
  - View admin dashboard (sync stats, errors)
  - Manage organization settings
  - **Cannot** see other organizations' data

**Database Schema:**
```sql
users (
  id VARCHAR(36) PRIMARY KEY,
  email VARCHAR(255) UNIQUE,
  password VARCHAR(255),           -- bcrypt hashed
  full_name VARCHAR(255),
  organization_id VARCHAR(36),     -- Isolates data
  role VARCHAR(50) DEFAULT 'USER', -- 'ADMIN' or 'USER'
  enabled BOOLEAN DEFAULT TRUE,
  must_change_password BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)
```

---

### **Level 3: Regular Users**
- **Who**: Employees/team members within an organization
- **Created**: By Organization Admin via UI
- **Scope**: Single organization only
- **Capabilities**:
  - Search knowledge base
  - View search results and citations
  - Access conversation history
  - **Cannot** manage connectors
  - **Cannot** see admin dashboard
  - **Cannot** create other users

---

## 2. MULTI-TENANCY MODEL

### **Data Isolation Strategy**

Every table has `organization_id` foreign key:

```sql
-- All data tables include organization_id
documents (
  id, 
  organization_id,  -- ← Isolation boundary
  user_id,
  content,
  ...
)

chunks (
  id,
  document_id,      -- ← Inherits organization via document
  content,
  ...
)

conversation_sessions (
  id,
  organization_id,  -- ← Isolation boundary
  user_id,
  ...
)
```

### **Query-Level Enforcement**

```java
// EVERY query must filter by organization_id
@Query("SELECT d FROM Document d WHERE d.organizationId = :orgId")
List<Document> findByOrganization(String orgId);

// Spring Security extracts orgId from JWT token
String organizationId = SecurityContextHolder
    .getContext()
    .getAuthentication()
    .getOrganizationId();
```

### **JWT Token Structure**

```json
{
  "sub": "user-uuid",
  "email": "admin@acme.com",
  "organizationId": "org-uuid-123",
  "role": "ADMIN",
  "exp": 1234567890
}
```

Every API call validates:
1. JWT is valid and not expired
2. Extract `organizationId` from token
3. Filter all queries by that `organizationId`

---

## 3. USER REGISTRATION FLOWS

### **Flow A: Initial Setup (Installer)**

```
┌─────────────────────────────────────────┐
│  1. Customer runs ./install.sh          │
│                                         │
│  Prompts:                               │
│  - Organization name: "Acme Corp"       │
│  - Admin email: admin@acme.com          │
│  - Admin full name: "John Smith"        │
│                                         │
│  Script does:                           │
│  - Creates organization record          │
│  - Creates admin user with default      │
│    password: admin123                   │
│  - Sets must_change_password = TRUE     │
│                                         │
│  Output:                                │
│  "Login: admin@acme.com / admin123"     │
│  "⚠️  CHANGE PASSWORD ON FIRST LOGIN"   │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│  2. Admin's First Login                 │
│                                         │
│  - Enters admin@acme.com / admin123     │
│  - Backend checks must_change_password  │
│  - Redirects to "Change Password" page  │
│  - Cannot access system until changed   │
└─────────────────────────────────────────┘
```

---

### **Flow B: Admin Creates Additional Users**

```
┌─────────────────────────────────────────┐
│  Admin Dashboard → Users → Add User     │
│                                         │
│  Form:                                  │
│  - Email: jane@acme.com                 │
│  - Full Name: "Jane Doe"                │
│  - Role: [User / Admin]                 │
│                                         │
│  Backend:                               │
│  - Generates random temp password       │
│  - Sets must_change_password = TRUE     │
│  - Sends invite email with temp pwd     │
│    (or displays on screen to copy)      │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│  New User's First Login                 │
│                                         │
│  - Enters temp password                 │
│  - Forced to change password            │
│  - Can now access search                │
└─────────────────────────────────────────┘
```

---

### **Flow C: Self-Service (Future - Optional)**

For organizations that want employees to self-register:

```
┌─────────────────────────────────────────┐
│  Public Registration Page               │
│  (Only if enabled in org settings)      │
│                                         │
│  - User enters email                    │
│  - System validates email domain        │
│    matches organization domain          │
│    (e.g., @acme.com)                    │
│  - Sends verification email             │
│  - User verifies and sets password      │
│  - Auto-assigned "User" role            │
│  - Admin can upgrade to Admin later     │
└─────────────────────────────────────────┘
```

**Implementation Priority**: Phase 2 (post-pilot)

---

## 4. PASSWORD MANAGEMENT

### **Storage**

```java
// NEVER store plaintext passwords
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
String hashed = encoder.encode(plainPassword);

// Store in database
//user.setPassword(hashed);
```

**Current Issue**: Installer uses hardcoded hash → **FIXED BELOW**

---

### **Password Policies**

```yaml
Minimum length: 8 characters
Must include: 
  - At least 1 uppercase
  - At least 1 lowercase  
  - At least 1 number
Expiry: 90 days (enterprise setting)
History: Cannot reuse last 3 passwords
```

---

### **Change Password Feature**

**API Endpoint:**
```
POST /api/auth/change-password
{
  "currentPassword": "...",
  "newPassword": "...",
  "confirmPassword": "..."
}
```

**UI Location:**
- User Profile → Security → Change Password
- Forced modal on first login if `must_change_password = true`

---

### **Password Reset Flow**

```
1. User clicks "Forgot Password"
2. Enters email
3. System sends reset link (valid 1 hour)
4. User clicks link → enters new password
5. must_change_password = FALSE
```

**Implementation Status**: **NOT BUILT YET** ← Critical for production

---

## 5. ROLE-BASED ACCESS CONTROL (RBAC)

### **Permission Matrix**

| Feature | Regular User | Organization Admin |
|---------|-------------|-------------------|
| **Search knowledge base** | ✅ | ✅ |
| **View search results** | ✅ | ✅ |
| **Upload documents** | ✅ | ✅ |
| **View own uploads** | ✅ | ✅ |
| **Manage connectors** | ❌ | ✅ |
| **Sync Confluence/etc** | ❌ | ✅ |
| **View sync errors** | ❌ | ✅ |
| **Create users** | ❌ | ✅ |
| **Manage users** | ❌ | ✅ |
| **View admin dashboard** | ❌ | ✅ |
| **System settings** | ❌ | ✅ |

---

### **Enforcement in Code**

```java
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/connectors")
    public List<Connector> getConnectors() {
        // Only admins can access
    }
}

@RestController  
@RequestMapping("/api/search")
public class SearchController {
    
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping
    public SearchResponse search() {
        // Both users and admins can search
    }
}
```

---

## 6. SSO INTEGRATION (ENTERPRISE)

For large customers (100+ users), manual user creation doesn't scale.

### **Supported Protocols**

**Phase 1 (Current)**: Username/password only
**Phase 2 (Enterprise)**: 
- SAML 2.0 (Okta, Azure AD, OneLogin)
- OAuth 2.0 / OIDC (Google Workspace, Microsoft 365)

---

### **SSO User Provisioning**

```
┌─────────────────────────────────────────┐
│  1. Admin enables SSO in settings       │
│                                         │
│  - SSO Provider: Okta                   │
│  - Entity ID: https://acme.okta.com     │
│  - SSO URL: https://...                 │
│  - Certificate: [upload .pem]           │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│  2. User visits http://sais.acme.com    │
│                                         │
│  - Clicks "Login with SSO"              │
│  - Redirected to Okta                   │
│  - Authenticates with corp credentials  │
│  - Okta sends SAML assertion            │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│  3. SAIS receives SAML assertion        │
│                                         │
│  - Extracts email from assertion        │
│  - Looks up user in database            │
│                                         │
│  IF user exists:                        │
│    → Generate JWT, login               │
│                                         │
│  IF user does NOT exist:                │
│    → Auto-create user (JIT provisioning)│
│    → Assign default "User" role        │
│    → Generate JWT, login               │
└─────────────────────────────────────────┘
```

**Just-In-Time (JIT) Provisioning**: User created on first SSO login

**Implementation Timeline**: Post-pilot (requires enterprise customer demand)

---

## 7. CURRENT IMPLEMENTATION GAPS

### **✅ What Works Today**

- Multi-tenant database schema with `organization_id` isolation
- JWT-based authentication
- Role differentiation (ADMIN vs USER)
- Organization creation during install
- Search works with proper data isolation

---

### **❌ What's Missing (CRITICAL)**

| Feature | Status | Priority | Effort |
|---------|--------|----------|--------|
| **Change Password UI** | Missing | 🔴 Critical | 4 hours |
| **Force password change on first login** | Missing | 🔴 Critical | 2 hours |
| **Admin → Create User UI** | Missing | 🔴 Critical | 6 hours |
| **User Management Page** | Missing | 🟡 High | 8 hours |
| **Password Reset Flow** | Missing | 🟡 High | 6 hours |
| **Email service integration** | Missing | 🟢 Medium | 4 hours |
| **SSO/SAML support** | Missing | 🟢 Low | 40 hours |

---

## 8. INSTALLER FIX (IMMEDIATE)

### **Current Problem**

```bash
# ❌ WRONG: Prompts for password but uses hardcoded hash
read -s -p "Admin password: " ADMIN_PASSWORD
HASHED_PASSWORD='$2a$10$WRONG_HASH'  # Doesn't match user input!
```

---

### **Solution A: Default Password (RECOMMENDED)**

```bash
# ✅ CORRECT: Use working hash, fixed password
echo "Setting default admin password: admin123"
HASHED_PASSWORD='$2a$10$9GVTMV/3gqLc3ppDC25xge95u7G2JL509bS0YBqk20x7Jj42eD37a'

docker exec sais-postgres psql -U sapphire -d sapphireai -c "
INSERT INTO users (id, email, password, full_name, organization_id, 
                   role, must_change_password, created_at, updated_at)
VALUES ('$USER_ID', '$ADMIN_EMAIL', '$HASHED_PASSWORD', '$ADMIN_NAME', 
        '$ORG_ID', 'ADMIN', TRUE, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;
"

echo ""
echo "✅ Admin user created"
echo "   Email: $ADMIN_EMAIL"
echo "   Password: admin123"
echo ""
echo "⚠️  SECURITY: Change password immediately after first login"
```

**Trade-off**: Everyone gets same initial password, but MUST change it.

---

### **Solution B: Custom Password (COMPLEX)**

Requires backend API call during installation:

```bash
# Call backend API to create user with custom password
curl -X POST http://localhost:8080/api/admin/setup \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$ADMIN_EMAIL\",
    \"password\": \"$ADMIN_PASSWORD\",
    \"fullName\": \"$ADMIN_NAME\",
    \"organizationName\": \"$ORG_NAME\"
  }"
```

**Trade-off**: More complex, requires backend to be fully started before user creation.

**Recommendation**: Use Solution A for MVP

---

## 9. IMPLEMENTATION ROADMAP

### **Phase 1: MVP (This Week)**

**Goal**: Make installer work + basic user management

```
Day 1:
- ✅ Fix installer with working bcrypt hash
- ✅ Set must_change_password = TRUE for first user
- [ ] Build "Change Password" page in UI
- [ ] Force password change modal on first login

Day 2:
- [ ] Build "Admin → Users" page
- [ ] "Add User" form (generates temp password)
- [ ] Display temp password to admin (copy to clipboard)
- [ ] List existing users
```

---

### **Phase 2: Production Ready (Week 2)**

```
- [ ] Password reset via email
- [ ] Email service integration (SendGrid/AWS SES)
- [ ] User edit/disable functionality
- [ ] Audit log (who created which user, when)
- [ ] Password policy enforcement
```

---

### **Phase 3: Enterprise Features (Post-Pilot)**

```
- [ ] SSO/SAML integration
- [ ] JIT user provisioning
- [ ] Group-based permissions
- [ ] API keys for service accounts
```

---

## 10. SECURITY BEST PRACTICES

### **Already Implemented**

✅ Passwords hashed with BCrypt (strength 10)
✅ JWT tokens with expiration
✅ Organization-level data isolation in database
✅ Role-based access control in backend

---

### **Must Implement Before Production**

❌ **HTTPS/TLS**: Currently HTTP only
❌ **Rate limiting**: No brute-force protection on login
❌ **Session management**: No logout, no token revocation
❌ **Audit logging**: No record of who did what
❌ **Password complexity validation**: No enforcement in UI

---

### **Security Checklist**

```
Network Security:
[ ] Deploy behind HTTPS (Let's Encrypt or customer cert)
[ ] Configure firewall (only 443 exposed)
[ ] Enable HSTS headers

Authentication:
[✅] Bcrypt password hashing
[ ] Force password change on first login
[ ] Rate limit login attempts (5 per minute per IP)
[ ] Lock account after 5 failed attempts
[ ] Session timeout (30 min inactivity)

Authorization:
[✅] JWT tokens
[✅] Role-based access control
[ ] Token refresh mechanism
[ ] Logout/token revocation

Data Protection:
[✅] Organization-level isolation
[ ] Encrypt sensitive fields at rest
[ ] Audit all data access
[ ] GDPR compliance (data export, deletion)
```

---

## 11. FINAL RECOMMENDATION

### **For Pilot Deployment**

**Minimum Viable IAM:**

1. ✅ Installer creates first admin with password `admin123`
2. ✅ Set `must_change_password = TRUE`
3. **BUILD NOW**: Change password UI (4 hours)
4. **BUILD NOW**: Admin → Create User page (6 hours)
5. Display temp passwords on screen for admin to share

**Total Effort**: 1 day of development

---

### **Post-Pilot (Production)**

1. Email integration for user invites
2. Password reset flow
3. HTTPS deployment
4. Rate limiting
5. Audit logging

**Total Effort**: 1 week of development

---

### **Enterprise Sales**

When selling to large customers:
1. SSO/SAML is **non-negotiable** for 100+ users
2. Budget 2-3 weeks for SAML integration
3. Charge premium for SSO ($5K-$10K setup fee)

---

## CONCLUSION

**Current State**: 
- Authentication works
- Multi-tenancy works
- Installer creates first admin
- ❌ **Cannot create additional users**
- ❌ **Cannot change passwords**

**Action Items**:
1. Fix installer with working hash (30 min)
2. Build Change Password page (4 hours)
3. Build Admin → Users page (6 hours)

**Timeline**: 1 day to make system usable for pilot

---

**This is the minimum required to demonstrate SAIS to a customer.**
