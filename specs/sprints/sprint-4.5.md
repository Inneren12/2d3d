# Sprint 4.5: Backend Integration - FastAPI Sync with 3-Way Merge
**Status:** Ready
**Dates:** 2026-07-09 — 2026-07-30
**Duration:** 2-3 weeks
**Team:** 1 Backend developer + 1 Android developer
**Goal:** Implement FastAPI backend with PostgreSQL for project sync, including entity-level 3-way merge conflict resolution, version-based locking, token refresh with Double-Checked Locking, and idempotency keys for safe retries.

**Key Deliverables:**
- FastAPI backend with PostgreSQL database
- RESTful API endpoints for project sync
- Entity-level 3-way merge (nodes, members, annotations)
- Version-based optimistic locking (not timestamp-based)
- Token refresh with Mutex (prevent stampede)
- Idempotency-Key support for POST/PUT
- Background sync with `isActivelyEditing` guard
- Testcontainers for E2E testing
- Android sync client with conflict resolution UI

**Critical Architecture Applied:**
- ARCH-SYNC-006: Version-based conflict resolution (not time-based), Double-Checked Locking for tokens
- ARCH-STATE-004: Room as SSOT, sync writes to Room (not in-memory state)
- Content-based Idempotency Keys (survive Process Death)
- 3-way merge at entity level (most complex PR, 2-3 weeks)

**Alternative Approach**: If 3-way merge proves too complex for MVP, can use simpler operation-based sync with CRDTs (see PR-2.5 alternative).

---

## Pack A: FastAPI Backend Foundation

### PR-01: FastAPI Project Setup

**Context (Human):** Set up FastAPI backend with PostgreSQL. This is a separate Python project from the Android app.

<ac-block id="S4.5-PR01-AC1">
**Acceptance Criteria for PR01 (Backend Setup)**:
- [ ] Create directory `/backend/` in project root
- [ ] Initialize Python virtual environment: `python -m venv venv`
- [ ] Dependencies in `requirements.txt`:
  - `fastapi>=0.104.0`
  - `uvicorn[standard]>=0.24.0`
  - `sqlalchemy>=2.0.0`
  - `psycopg2-binary>=2.9.0`
  - `alembic>=1.12.0` (for migrations)
  - `pydantic>=2.4.0`
  - `python-jose[cryptography]>=3.3.0` (for JWT)
  - `passlib[bcrypt]>=1.7.4` (for password hashing)
- [ ] Create `main.py` with basic FastAPI app:
```python
  from fastapi import FastAPI

  app = FastAPI(title="Drawing Sync API", version="1.0.0")

  @app.get("/health")
  def health_check():
      return {"status": "ok"}
```
- [ ] Run server: `uvicorn main:app --reload` on port 8000
- [ ] Test: `curl http://localhost:8000/health` returns `{"status": "ok"}`
- [ ] Create `Dockerfile` for containerized deployment
- [ ] Create `docker-compose.yml` with FastAPI + PostgreSQL services
- [ ] Test: `docker-compose up` starts both services
- [ ] File location: `/backend/main.py`, `/backend/requirements.txt`, `/backend/Dockerfile`
</ac-block>

---

### PR-02: Database Models (SQLAlchemy)

**Context (Human):** Define database schema for projects, nodes, members, annotations. Use SQLAlchemy ORM for type safety.

<ac-block id="S4.5-PR02-AC1">
**Acceptance Criteria for PR02 (DB Models)**:
- [ ] Create SQLAlchemy models in `/backend/models/`:
  - `User` (id, email, hashed_password, created_at)
  - `Project` (id, user_id, name, created_at, updated_at, version)
  - `Node` (id, project_id, node_id, x, y, z, version)
  - `Member` (id, project_id, member_id, start_node_id, end_node_id, profile_ref, length, version)
  - `Annotation` (id, project_id, annotation_id, type, data_json, target_id, version)
- [ ] CRITICAL: Each entity has `version: int` field for optimistic locking (ARCH-SYNC-006)
- [ ] `Project.version` tracks overall project version (increments on any change)
- [ ] Entity `version` fields track individual entity versions
- [ ] Foreign keys: `Node.project_id`, `Member.project_id` reference `Project.id`
- [ ] Indexes: `project_id`, `node_id`, `member_id`, `annotation_id` for fast lookups
- [ ] PostgreSQL types: Use `DOUBLE PRECISION` for coordinates (not FLOAT)
- [ ] Test: Models can be imported without errors
- [ ] Test: Create tables via `Base.metadata.create_all(engine)`
- [ ] File location: `/backend/models/project.py`, `/backend/models/user.py`
</ac-block>

---

### PR-03: Alembic Migrations Setup

**Context (Human):** Database schema versioning with Alembic. Critical for production deployments.

<ac-block id="S4.5-PR03-AC1">
**Acceptance Criteria for PR03 (Migrations)**:
- [ ] Initialize Alembic: `alembic init alembic`
- [ ] Configure `alembic.ini` with PostgreSQL connection string
- [ ] Configure `alembic/env.py` to import SQLAlchemy models
- [ ] Create initial migration: `alembic revision --autogenerate -m "initial schema"`
- [ ] Review generated migration SQL (verify correctness)
- [ ] Apply migration: `alembic upgrade head`
- [ ] Test: Tables exist in PostgreSQL (verify via `psql` or pgAdmin)
- [ ] Test: Migration is reversible: `alembic downgrade -1` then `alembic upgrade head`
- [ ] Document migration workflow in `/backend/README.md`
- [ ] File location: `/backend/alembic/versions/001_initial_schema.py`
</ac-block>

---

### PR-04: Authentication (JWT Tokens)

**Context (Human):** User authentication with JWT access/refresh tokens. Needed for multi-user sync.

<ac-block id="S4.5-PR04-AC1">
**Acceptance Criteria for PR04 (Auth)**:
- [ ] Implement JWT token generation:
```python
  def create_access_token(user_id: str, expires_delta: timedelta = timedelta(hours=1)) -> str
  def create_refresh_token(user_id: str, expires_delta: timedelta = timedelta(days=30)) -> str
```
- [ ] Implement token verification:
```python
  def verify_token(token: str) -> dict  # Returns {"user_id": "..."} or raises HTTPException
```
- [ ] Create endpoints:
  - `POST /auth/register` - Register new user (email, password)
  - `POST /auth/login` - Login (returns access_token + refresh_token)
  - `POST /auth/refresh` - Refresh access token (requires refresh_token)
- [ ] Password hashing: Use `passlib` with bcrypt
- [ ] Token expiry: Access token 1 hour, refresh token 30 days
- [ ] Test: Register user, login, receive tokens
- [ ] Test: Access protected endpoint with valid token succeeds
- [ ] Test: Access protected endpoint with expired token returns 401
- [ ] Test: Refresh token generates new access token
- [ ] File location: `/backend/auth.py`, `/backend/routers/auth.py`
</ac-block>

---

## Pack B: Sync Endpoints (Basic CRUD)

### PR-05: GET /projects - List User Projects

**Context (Human):** Fetch all projects belonging to authenticated user. Returns minimal metadata (not full drawing data).

<ac-block id="S4.5-PR05-AC1">
**Acceptance Criteria for PR05 (List Projects)**:
- [ ] Create endpoint: `GET /projects`
- [ ] Requires authentication (JWT token in `Authorization: Bearer <token>`)
- [ ] Query: `SELECT id, name, updated_at, version FROM projects WHERE user_id = ?`
- [ ] Response:
```json
  {
    "projects": [
      {
        "id": "project-uuid",
        "name": "Blueprint 2024-01",
        "updated_at": "2024-07-15T10:30:00Z",
        "version": 42
      }
    ]
  }
```
- [ ] Pagination: Support `?limit=20&offset=0` query params
- [ ] Test: Authenticated user sees only their projects
- [ ] Test: Unauthenticated request returns 401
- [ ] Test: Pagination works correctly (fetch page 1, page 2)
- [ ] File location: `/backend/routers/projects.py`
</ac-block>

---

### PR-06: GET /projects/{id} - Fetch Full Project

**Context (Human):** Download complete project data (nodes, members, annotations) for local editing.

<ac-block id="S4.5-PR06-AC1">
**Acceptance Criteria for PR06 (Fetch Project)**:
- [ ] Create endpoint: `GET /projects/{project_id}`
- [ ] Requires authentication + user owns project (or returns 403)
- [ ] Query:
  1. Fetch `Project` by id
  2. Fetch all `Node` where `project_id = ?`
  3. Fetch all `Member` where `project_id = ?`
  4. Fetch all `Annotation` where `project_id = ?`
- [ ] Response:
```json
  {
    "id": "project-uuid",
    "name": "Blueprint",
    "version": 42,
    "updated_at": "2024-07-15T10:30:00Z",
    "nodes": [
      {"id": "node-1", "x": 100.0, "y": 200.0, "z": 0.0, "version": 5}
    ],
    "members": [
      {"id": "member-1", "start_node_id": "node-1", "end_node_id": "node-2", "profile_ref": "W8x10", "length": 2500.0, "version": 3}
    ],
    "annotations": [
      {"id": "anno-1", "type": "text", "data": {}, "target_id": "node-1", "version": 2}
    ]
  }
```
- [ ] CRITICAL: Include `version` field for each entity (needed for conflict detection)
- [ ] Test: User can fetch their own project
- [ ] Test: User cannot fetch another user's project (403 Forbidden)
- [ ] Test: Non-existent project returns 404
- [ ] Performance: Response time <500ms for project with 1000 entities
- [ ] File location: `/backend/routers/projects.py`
</ac-block>

---

### PR-07: POST /projects - Create New Project

**Context (Human):** Create project on server (upload initial drawing from Android after capture).

<ac-block id="S4.5-PR07-AC1">
**Acceptance Criteria for PR07 (Create Project)**:
- [ ] Create endpoint: `POST /projects`
- [ ] Request body:
```json
  {
    "name": "New Project",
    "nodes": [],
    "members": [],
    "annotations": []
  }
```
- [ ] Algorithm:
  1. Generate `project_id` (UUID)
  2. Insert `Project` with `version = 1`
  3. Insert all nodes with `version = 1`
  4. Insert all members with `version = 1`
  5. Insert all annotations with `version = 1`
  6. All in single database transaction (atomicity)
- [ ] Response: Return created project with `id` and `version`
- [ ] Test: Create project succeeds, returns 201 Created
- [ ] Test: Invalid data (missing fields) returns 400 Bad Request
- [ ] Test: Duplicate node IDs in same project rejected (unique constraint)
- [ ] Test: Transaction rollback on error (no partial inserts)
- [ ] File location: `/backend/routers/projects.py`
</ac-block>

---

### PR-08: PUT /projects/{id} - Full Replace (Simple Sync)

**Context (Human):** Simple sync approach: replace entire project (last-write-wins). Used if 3-way merge too complex for MVP.

<ac-block id="S4.5-PR08-AC1">
**Acceptance Criteria for PR08 (Full Replace)**:
- [ ] Create endpoint: `PUT /projects/{project_id}`
- [ ] Request body: Same as POST (full project data)
- [ ] Request header: `If-Match: {client_version}` for optimistic locking
- [ ] Algorithm:
  1. Fetch current `Project.version` from database
  2. Compare with `If-Match` header
  3. If mismatch: Return 409 Conflict with server state
  4. If match: Delete all entities, insert new ones, increment version
  5. Commit transaction
- [ ] Response: Return updated project with new `version`
- [ ] Test: Update succeeds when version matches
- [ ] Test: Update fails with 409 when version mismatch (conflict detected)
- [ ] Test: 409 response includes current server state for client to resolve
- [ ] Test: Transaction ensures atomicity (all-or-nothing)
- [ ] File location: `/backend/routers/projects.py`
</ac-block>

---

## Pack C: Version-Based Conflict Detection

### PR-09: Version Field Strategy

**Context (Human):** CRITICAL: Use `version` field (integer counter), NOT `updated_at` timestamp. Timestamps are unreliable (client clock drift, timezone issues).

<ac-block id="S4.5-PR09-AC1">
**Acceptance Criteria for PR09 (Version Strategy)**:
- [ ] Every entity has `version: int` field (starts at 1)
- [ ] `Project.version` is global project version (increments on ANY change)
- [ ] `Node.version`, `Member.version`, `Annotation.version` are entity-specific versions
- [ ] Conflict detection: Compare `local.version` vs `remote.version` (NOT timestamps)
- [ ] Rationale: Client clocks can be wrong (user set wrong date), timezones differ, NTP sync unreliable
- [ ] Version comparison is deterministic: `local.version > remote.version` means local is newer
- [ ] Test: Create entity with version=1
- [ ] Test: Update entity increments version to 2
- [ ] Test: Two updates increment version to 3
- [ ] Test: Timestamp ignored for conflict detection (version used instead)
- [ ] Documentation: Explain why version-based (not time-based) in `/backend/docs/VERSIONING.md`
- [ ] File location: Update all models in `/backend/models/`
</ac-block>

---

### PR-10: Optimistic Locking Middleware

**Context (Human):** Implement optimistic locking check as reusable middleware. Prevents lost updates.

<ac-block id="S4.5-PR10-AC1">
**Acceptance Criteria for PR10 (Optimistic Locking)**:
- [ ] Create function: `check_version_conflict(entity_id: str, client_version: int, db_version: int) -> Optional[ConflictError]`
- [ ] If `client_version != db_version`: Return `ConflictError` with details
- [ ] If `client_version == db_version`: Return None (no conflict)
- [ ] ConflictError includes:
  - `entity_id`: ID of conflicting entity
  - `client_version`: Version client has
  - `server_version`: Version server has
  - `server_state`: Current entity data from server
- [ ] Apply check in all UPDATE/DELETE endpoints
- [ ] Test: Update with matching version succeeds
- [ ] Test: Update with mismatched version returns 409 with ConflictError
- [ ] Test: ConflictError includes complete server state for resolution
- [ ] File location: `/backend/utils/versioning.py`
</ac-block>

---

## Pack D: Token Refresh & Idempotency

### PR-11: Token Refresh Mutex (Prevent Stampede)

**Context (Human):** CRITICAL: When multiple requests get 401, they all try to refresh token simultaneously. This causes "token stampede" where tokens invalidate each other. Use Double-Checked Locking (ARCH-SYNC-006).

<ac-block id="S4.5-PR11-AC1">
**Acceptance Criteria for PR11 (Token Mutex)**:
- [ ] Create Android class: `class TokenRefreshManager`
- [ ] Field: `private val refreshMutex = Mutex()`
- [ ] Field: `private val tokenStore: TokenStore` (shared preferences or encrypted storage)
- [ ] Method:
```kotlin
  suspend fun ensureFreshToken(failedToken: String? = null): String {
      val current = tokenStore.getAccessToken()

      // Fast path: Token already fresh (someone else refreshed it)
      if (failedToken != null && current != failedToken) {
          return current
      }

      // Slow path: Need to refresh
      refreshMutex.withLock {
          val locked = tokenStore.getAccessToken()

          // Double-check: Someone else refreshed while we waited
          if (failedToken != null && locked != failedToken) {
              return locked
          }

          // Actually refresh
          val response = authApi.refreshToken(tokenStore.getRefreshToken())
          tokenStore.save(response.accessToken, response.refreshToken)
          return response.accessToken
      }
  }
```
- [ ] Usage in API interceptor:
```kotlin
  if (response.code == 401) {
      val failedToken = request.header("Authorization")?.removePrefix("Bearer ")
      val freshToken = tokenRefreshManager.ensureFreshToken(failedToken)

      // Retry with fresh token
      val newRequest = request.newBuilder()
          .header("Authorization", "Bearer $freshToken")
          .build()
      return chain.proceed(newRequest)
  }
```
- [ ] Test: 10 parallel 401 responses → only 1 refresh call made
- [ ] Test: Fast path used when token already fresh (no API call)
- [ ] Test: Double-check prevents redundant refreshes
- [ ] Test: No infinite 401 loop (if refresh fails, stop retrying)
- [ ] Documentation: Explain Double-Checked Locking pattern
- [ ] File location: `feature/editor/src/main/kotlin/.../sync/TokenRefreshManager.kt`
</ac-block>

---

### PR-12: Idempotency-Key Header (Content-Based)

**Context (Human):** CRITICAL: If request fails (network timeout) but server received it, retry could create duplicate. Use Idempotency-Key to make retries safe. Must survive Process Death (can't be in-memory).

<ac-block id="S4.5-PR12-AC1">
**Acceptance Criteria for PR12 (Idempotency Key)**:
- [ ] Generate idempotency key from CONTENT (not random UUID):
```kotlin
  fun generateIdempotencyKey(projectId: String, version: Int): String {
      val content = "$projectId:$version"
      return UUID.nameUUIDFromBytes(content.toByteArray()).toString()
  }
```
- [ ] Rationale: Content-based key survives Process Death (regenerated from same data)
- [ ] Add header to all POST/PUT requests: `Idempotency-Key: {key}`
- [ ] Backend: Store `(idempotency_key, response)` in cache (Redis or DB table)
- [ ] Backend: If key already exists, return cached response (don't execute again)
- [ ] Backend: Cache TTL = 24 hours (expired keys can be retried)
- [ ] Test: First request with key executes normally
- [ ] Test: Retry with same key returns cached response (no duplicate)
- [ ] Test: Request with different key executes normally
- [ ] Test: Process Death → app restart → retry uses same key (content-based)
- [ ] Documentation: Explain why content-based (not random UUID)
- [ ] File locations:
  - Android: `feature/editor/src/main/kotlin/.../sync/IdempotencyKeyGenerator.kt`
  - Backend: `/backend/middleware/idempotency.py`
</ac-block>

---

## Pack E: 3-Way Merge (Most Complex PR)

### PR-2.5: Entity-Level 3-Way Merge Algorithm

**Context (Human):** THE MOST COMPLEX PR IN SPRINT 4.5 (est. 2-3 weeks). Merges local and remote changes against common base. This is Git-style 3-way merge applied to drawing entities.

**Alternative**: If too complex for MVP, use operation-based sync with CRDTs (see end of AC block).

<ac-block id="S4.5-PR2.5-AC1">
**Acceptance Criteria for PR2.5 (3-Way Merge)**:
- [ ] Implement Android class: `class ThreeWayMerger`
- [ ] Method signature:
```kotlin
  fun merge(base: Drawing2D, local: Drawing2D, remote: Drawing2D): MergeResult

  data class MergeResult(
      val merged: Drawing2D,
      val conflicts: List<Conflict>,
      val hasConflicts: Boolean
  )

  sealed class Conflict {
      data class NodeConflict(val nodeId: String, val localVersion: Int, val remoteVersion: Int)
      data class MemberConflict(val memberId: String, ...)
      // etc.
  }
```
- [ ] Algorithm (entity-level merge):
  1. Convert lists to maps for O(1) lookup (ARCH-PERF-001):
```kotlin
     val baseMap = base.nodes.associateBy { it.id }
     val localMap = local.nodes.associateBy { it.id }
     val remoteMap = remote.nodes.associateBy { it.id }
```
  2. For each unique node ID across all 3 versions:
```kotlin
     val allNodeIds = (baseMap.keys + localMap.keys + remoteMap.keys).distinct()

     for (nodeId in allNodeIds) {
         val baseNode = baseMap[nodeId]
         val localNode = localMap[nodeId]
         val remoteNode = remoteMap[nodeId]

         when {
             // Case 1: Added locally, not remote → Keep local
             baseNode == null && localNode != null && remoteNode == null ->
                 mergedNodes.add(localNode)

             // Case 2: Added remotely, not local → Keep remote
             baseNode == null && localNode == null && remoteNode != null ->
                 mergedNodes.add(remoteNode)

             // Case 3: Added both sides (different) → Conflict
             baseNode == null && localNode != null && remoteNode != null ->
                 conflicts.add(NodeConflict.BothAdded(nodeId, localNode, remoteNode))

             // Case 4: Deleted locally, unchanged remote → Delete
             baseNode != null && localNode == null && remoteNode == baseNode ->
                 { /* Don't add to merged (deleted) */ }

             // Case 5: Deleted remotely, unchanged local → Delete
             baseNode != null && localNode == baseNode && remoteNode == null ->
                 { /* Don't add to merged (deleted) */ }

             // Case 6: Modified locally, unchanged remote → Keep local
             baseNode != null && localNode != null && remoteNode == baseNode ->
                 mergedNodes.add(localNode)

             // Case 7: Modified remotely, unchanged local → Keep remote
             baseNode != null && localNode == baseNode && remoteNode != null ->
                 mergedNodes.add(remoteNode)

             // Case 8: Modified both sides (same) → Keep either
             baseNode != null && localNode == remoteNode ->
                 mergedNodes.add(localNode)

             // Case 9: Modified both sides (different) → Conflict
             baseNode != null && localNode != null && remoteNode != null && localNode != remoteNode ->
                 conflicts.add(NodeConflict.BothModified(nodeId, localNode, remoteNode))

             // Case 10: Deleted both sides → Don't add
             baseNode != null && localNode == null && remoteNode == null ->
                 { /* Don't add to merged (deleted on both) */ }

             // Case 11: Deleted locally, modified remotely → Conflict
             baseNode != null && localNode == null && remoteNode != null && remoteNode != baseNode ->
                 conflicts.add(NodeConflict.DeletedLocalModifiedRemote(nodeId, remoteNode))

             // Case 12: Modified locally, deleted remotely → Conflict
             baseNode != null && localNode != null && localNode != baseNode && remoteNode == null ->
                 conflicts.add(NodeConflict.ModifiedLocalDeletedRemote(nodeId, localNode))
         }
     }
```
  3. Repeat for members and annotations
  4. Return `MergeResult` with merged drawing and list of conflicts
- [ ] CRITICAL: Use version numbers for conflict detection (not equality comparison)
- [ ] Test: User A adds node offline, User B does nothing → A's node appears in merge
- [ ] Test: User A modifies node, User B does nothing → A's changes appear
- [ ] Test: User A deletes node, User B does nothing → Node deleted in merge
- [ ] Test: User A adds node, User B adds different node → Both nodes in merge (no conflict)
- [ ] Test: User A modifies node, User B modifies SAME node differently → Conflict reported
- [ ] Test: User A deletes node, User B modifies it → Conflict reported
- [ ] Test: Large merge (100 nodes, 3 conflicts) completes in <1 second
- [ ] Memory: Use maps (not lists) to avoid O(N²) lookups (ARCH-PERF-001)
- [ ] Code coverage >90% for merge logic (critical algorithm)
- [ ] Documentation: `/docs/sync/THREE_WAY_MERGE.md` with all 12 cases explained
- [ ] File location: `feature/editor/src/main/kotlin/.../sync/ThreeWayMerger.kt`

**ALTERNATIVE APPROACH (if 3-way merge too complex for MVP)**:
- [ ] Use operation-based sync with CRDTs (Conflict-free Replicated Data Types)
- [ ] Each edit generates operation: `AddNode(id, position)`, `MoveNode(id, newPos)`, etc.
- [ ] Operations have timestamps and are applied in causal order
- [ ] Operations are commutative: `AddNode(A); AddNode(B)` = `AddNode(B); AddNode(A)`
- [ ] Simpler but less powerful (can't detect semantic conflicts like "moved + deleted")
- [ ] Decide based on complexity/timeline: 3-way merge OR operation-based sync
</ac-block>

---

### PR-13: Conflict Resolution UI

**Context (Human):** Show conflicts to user, let them choose local or remote version (or manual merge).

<ac-block id="S4.5-PR13-AC1">
**Acceptance Criteria for PR13 (Conflict UI)**:
- [ ] When `MergeResult.hasConflicts == true`, show conflict resolution screen
- [ ] For each conflict, display:
  - Local version (what user changed)
  - Remote version (what server has)
  - Base version (common ancestor, if available)
  - Conflict type (both modified, deleted+modified, etc.)
- [ ] User can choose:
  - "Keep Mine" (use local version)
  - "Use Theirs" (use remote version)
  - "Manual Edit" (edit in place, combine changes)
- [ ] After resolving all conflicts, retry sync with resolved drawing
- [ ] Test: Conflict screen displays correctly
- [ ] Test: Choosing "Keep Mine" uses local version in final merge
- [ ] Test: Choosing "Use Theirs" uses remote version
- [ ] Test: Manual edit allows user to type custom value
- [ ] Test: Resolving all conflicts enables "Complete Merge" button
- [ ] UI test: Espresso test for conflict resolution flow
- [ ] File location: `feature/editor/src/main/kotlin/.../ui/ConflictResolutionActivity.kt`
</ac-block>

---

## Pack F: Background Sync

### PR-14: BackgroundSyncWorker (WorkManager)

**Context (Human):** Periodic sync in background using WorkManager. Runs every 15 minutes when device online.

<ac-block id="S4.5-PR14-AC1">
**Acceptance Criteria for PR14 (Background Sync)**:
- [ ] Create class: `class BackgroundSyncWorker : CoroutineWorker`
- [ ] Schedule periodic work:
```kotlin
  val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()

  val syncRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(15, TimeUnit.MINUTES)
      .setConstraints(constraints)
      .build()

  WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      "project-sync",
      ExistingPeriodicWorkPolicy.KEEP,
      syncRequest
  )
```
- [ ] Worker implementation:
```kotlin
  override suspend fun doWork(): Result {
      val projects = projectRepository.getAllProjects()

      for (project in projects) {
          if (!isActivelyEditing(project.id)) {
              syncProject(project)
          }
      }

      return Result.success()
  }
```
- [ ] CRITICAL: Check `isActivelyEditing` before syncing (ARCH-STATE-004 protection)
- [ ] Test: Worker scheduled successfully
- [ ] Test: Worker executes when constraints met (network available)
- [ ] Test: Worker skipped when offline (constraint not met)
- [ ] Test: Worker does NOT sync actively edited project (no UI jump)
- [ ] File location: `feature/editor/src/main/kotlin/.../sync/BackgroundSyncWorker.kt`
</ac-block>

---

### PR-15: isActivelyEditing Guard (Prevent UI Jump)

**Context (Human):** CRITICAL: Background sync must NOT update drawing while user is editing. This causes "UI jump" where nodes suddenly move under user's finger. Use guard to prevent this (ARCH-STATE-004).

<ac-block id="S4.5-PR15-AC1">
**Acceptance Criteria for PR15 (Active Editing Guard)**:
- [ ] Create singleton: `object ActiveEditingTracker`
- [ ] Fields:
```kotlin
  private val activeProjects = mutableSetOf<String>()
  private val lastInteractionTime = mutableMapOf<String, Long>()
```
- [ ] Methods:
```kotlin
  fun markAsEditing(projectId: String) {
      activeProjects.add(projectId)
      lastInteractionTime[projectId] = System.currentTimeMillis()
  }

  fun isActivelyEditing(projectId: String, timeoutMs: Long = 5000): Boolean {
      if (projectId !in activeProjects) return false

      val lastTime = lastInteractionTime[projectId] ?: return false
      val elapsed = System.currentTimeMillis() - lastTime

      return elapsed < timeoutMs
  }

  fun markAsIdle(projectId: String) {
      activeProjects.remove(projectId)
      lastInteractionTime.remove(projectId)
  }
```
- [ ] Call `markAsEditing()` on every user interaction (touch, gesture)
- [ ] Call `markAsIdle()` when editor closed
- [ ] Background sync checks `isActivelyEditing()` before syncing
- [ ] If actively editing: Skip sync, retry later
- [ ] Test: User touches screen → `isActivelyEditing()` returns true
- [ ] Test: 5 seconds after last touch → `isActivelyEditing()` returns false
- [ ] Test: Background sync skips actively edited project
- [ ] Test: Background sync processes idle projects
- [ ] Documentation: Explain rationale (prevent UI jump during drag)
- [ ] File location: `feature/editor/src/main/kotlin/.../sync/ActiveEditingTracker.kt`
</ac-block>

---

## Pack G: Testing Infrastructure

### PR-16: Testcontainers Setup (E2E Testing)

**Context (Human):** Use Testcontainers to spin up real PostgreSQL in Docker for E2E tests. Much faster than mocking entire database.

<ac-block id="S4.5-PR16-AC1">
**Acceptance Criteria for PR16 (Testcontainers)**:
- [ ] Add dependency: `testcontainers[postgresql]>=3.7.0` (Python)
- [ ] Create test fixture:
```python
  import pytest
  from testcontainers.postgres import PostgresContainer

  @pytest.fixture(scope="session")
  def postgres_container():
      with PostgresContainer("postgres:15") as postgres:
          yield postgres

  @pytest.fixture(scope="session")
  def db_engine(postgres_container):
      engine = create_engine(postgres_container.get_connection_url())
      Base.metadata.create_all(engine)
      return engine
```
- [ ] Use fixture in tests:
```python
  def test_create_project(db_engine):
      # Test uses real PostgreSQL (in Docker container)
      session = Session(db_engine)
      project = Project(name="Test")
      session.add(project)
      session.commit()

      assert project.id is not None
```
- [ ] Container lifecycle: Start once per test session, reused across tests
- [ ] Test: Container starts successfully
- [ ] Test: Database schema created in container
- [ ] Test: Tests can insert/query data
- [ ] Performance: Test suite with 50 tests completes in <30 seconds
- [ ] Documentation: Explain Testcontainers setup in `/backend/tests/README.md`
- [ ] File location: `/backend/tests/conftest.py`
</ac-block>

---

### PR-17: E2E Tests (Sync Scenarios)

**Context (Human):** Comprehensive E2E tests covering all sync scenarios. Use Testcontainers for real DB.

<ac-block id="S4.5-PR17-AC1">
**Acceptance Criteria for PR17 (E2E Tests)**:
- [ ] Test suite covering:
  - Create project → upload succeeds
  - Fetch project → download matches uploaded data
  - Update project (no conflict) → succeeds
  - Update project (version conflict) → returns 409
  - Concurrent updates → one succeeds, one conflicts
  - 3-way merge (no conflicts) → merged correctly
  - 3-way merge (with conflicts) → conflicts reported
  - Token refresh during sync → succeeds
  - Idempotency key prevents duplicates
  - Background sync skips active projects
- [ ] Each test:
  1. Start with clean database (Testcontainers)
  2. Execute sync scenario
  3. Assert expected outcome
  4. Verify database state
- [ ] Test: Simple sync (no conflicts) completes successfully
- [ ] Test: Conflict detected and 409 returned
- [ ] Test: 3-way merge with User A adds 100 nodes, User B changes 1 profile → both preserved
- [ ] Test: Token expires mid-sync → refresh succeeds, sync completes
- [ ] Test: Duplicate request with same Idempotency-Key returns cached response
- [ ] Performance: E2E test suite completes in <5 minutes
- [ ] File location: `/backend/tests/test_sync_e2e.py`
</ac-block>

---

### PR-18: Android Instrumentation Tests (Sync)

**Context (Human):** Android instrumentation tests for sync client. Uses mock backend (no real server needed).

<ac-block id="S4.5-PR18-AC1">
**Acceptance Criteria for PR18 (Android Sync Tests)**:
- [ ] Use MockWebServer for fake backend:
```kotlin
  @Before
  fun setup() {
      mockServer = MockWebServer()
      mockServer.start()

      // Configure API client to use mock server
      apiClient = ApiClient(baseUrl = mockServer.url("/").toString())
  }
```
- [ ] Test scenarios:
  - Upload project → mock returns 201 Created
  - Download project → mock returns project data
  - Conflict detected → mock returns 409, UI shows conflict screen
  - Token refresh → mock returns new token, retry succeeds
  - Background sync → worker executes, calls API
- [ ] Test: Upload succeeds, project saved to Room
- [ ] Test: Download succeeds, project loaded into Room
- [ ] Test: Conflict UI appears when 409 received
- [ ] Test: Token refresh interceptor works correctly
- [ ] Test: Background sync worker respects `isActivelyEditing` guard
- [ ] All tests run on emulator/device
- [ ] Test suite completes in <10 minutes
- [ ] File location: `feature/editor/src/androidTest/kotlin/.../sync/SyncInstrumentationTest.kt`
</ac-block>

---

## Pack H: Documentation & Closeout

### PR-19: API Documentation (OpenAPI/Swagger)

**Context (Human):** Generate interactive API docs using FastAPI's built-in OpenAPI support.

<ac-block id="S4.5-PR19-AC1">
**Acceptance Criteria for PR19 (API Docs)**:
- [ ] FastAPI automatically generates OpenAPI schema at `/openapi.json`
- [ ] Swagger UI available at `/docs` (interactive API explorer)
- [ ] ReDoc available at `/redoc` (alternative docs format)
- [ ] Document all endpoints with:
  - Request body schema (Pydantic models)
  - Response schema
  - Status codes (200, 201, 400, 401, 403, 409, 500)
  - Example requests/responses
- [ ] Add docstrings to all endpoint functions:
```python
  @app.post("/projects", status_code=201)
  async def create_project(project: ProjectCreate, user: User = Depends(get_current_user)):
      """
      Create a new project.

      - **name**: Project name
      - **nodes**: List of nodes
      - **members**: List of members

      Returns the created project with generated ID and version.
      """
```
- [ ] Test: Visit `/docs`, verify all endpoints listed
- [ ] Test: Try out API call from Swagger UI (interactive test)
- [ ] Documentation complete and accurate
- [ ] File location: Docstrings in `/backend/routers/*.py`
</ac-block>

---

### PR-20: Sync Architecture Documentation

**Context (Human):** Comprehensive docs explaining sync architecture, conflict resolution, version strategy.

<ac-block id="S4.5-PR20-AC1">
**Acceptance Criteria for PR20 (Sync Docs)**:
- [ ] Create `/docs/sync/` directory with files:
  - `ARCHITECTURE.md` - Overall sync design
  - `THREE_WAY_MERGE.md` - Merge algorithm explained (12 cases)
  - `VERSIONING.md` - Why version-based (not timestamp)
  - `CONFLICT_RESOLUTION.md` - How conflicts handled
  - `IDEMPOTENCY.md` - Idempotency key strategy
  - `TOKEN_REFRESH.md` - Double-Checked Locking pattern
- [ ] `ARCHITECTURE.md` includes:
  - Sync flow diagram (client → server → merge → conflict resolution)
  - Entity-level vs operation-based sync comparison
  - Why 3-way merge (not last-write-wins)
- [ ] `THREE_WAY_MERGE.md` includes:
  - All 12 merge cases with examples
  - Code snippets
  - Decision tree diagram
- [ ] `VERSIONING.md` explains:
  - Why `version: int` not `updated_at: timestamp`
  - Clock drift problems
  - Deterministic conflict detection
- [ ] All docs have diagrams (ASCII art or Mermaid)
- [ ] All docs have code examples
- [ ] Markdown valid, no broken links
- [ ] File locations: `/docs/sync/*.md`
</ac-block>

---

### PR-21: Sprint 4.5 Closeout

**Context (Human):** Final verification before Sprint 5 (AR rendering).

<ac-block id="S4.5-PR21-AC1">
**Acceptance Criteria for PR21 (Closeout)**:
- [ ] All 21 PRs merged to main branch
- [ ] Backend tests pass: `pytest backend/tests/` (all E2E tests green)
- [ ] Android instrumentation tests pass: `./gradlew :feature:editor:connectedAndroidTest`
- [ ] Backend code coverage >80%
- [ ] Android sync code coverage >75%
- [ ] API documentation complete (Swagger UI accessible)
- [ ] Sync architecture documentation complete (6 docs in `/docs/sync/`)
- [ ] Backend deployed to staging environment (Docker Compose or cloud)
- [ ] Android app can sync with staging backend
- [ ] Performance: 3-way merge on 1000 entities completes in <1 second
- [ ] Performance: Background sync completes in <5 seconds for typical project
- [ ] Security: JWT tokens validated, unauthorized access blocked (401/403)
- [ ] No TODO comments in production code
- [ ] Update root README.md with Sprint 4.5 deliverables
- [ ] Git tag created: `git tag sprint-4.5-complete`
- [ ] Ready for Sprint 5: Synced projects can be viewed in AR
</ac-block>

---

## Sprint 4.5 Success Metrics

**Definition of Done (Sprint Level):**
- ✅ All 21 PRs completed and merged
- ✅ FastAPI backend operational with PostgreSQL
- ✅ JWT authentication working
- ✅ Version-based conflict detection working
- ✅ Entity-level 3-way merge implemented (or operation-based alternative)
- ✅ Token refresh with Double-Checked Locking (no stampede)
- ✅ Idempotency keys preventing duplicates
- ✅ Background sync with `isActivelyEditing` guard
- ✅ Conflict resolution UI functional
- ✅ Testcontainers E2E tests passing
- ✅ Android sync client working
- ✅ API docs complete (Swagger)
- ✅ Architecture docs complete
- ✅ Ready for Sprint 5 (AR)

**Key Deliverables:**
1. FastAPI backend with PostgreSQL
2. RESTful API (GET, POST, PUT for projects)
3. JWT authentication (access + refresh tokens)
4. Version-based optimistic locking
5. Entity-level 3-way merge algorithm (12 cases)
6. Conflict resolution UI
7. Token refresh with Mutex (Double-Checked Locking)
8. Content-based Idempotency Keys
9. Background sync with WorkManager
10. `isActivelyEditing` guard (prevent UI jump)
11. Testcontainers E2E test suite
12. Android instrumentation tests
13. OpenAPI documentation
14. Comprehensive sync architecture docs

**Technical Debt:**
- 3-way merge handles entity conflicts but not semantic conflicts (e.g., moved + deleted → which wins?)
- No support for multi-device simultaneous editing (operational transforms needed)
- Idempotency cache in memory (should be Redis for distributed systems)
- No database replication (single PostgreSQL instance)
- No rate limiting on API endpoints

**Architectural Decisions Applied:**
- ARCH-SYNC-006: Version-based conflict detection (not timestamp), Double-Checked Locking for tokens
- ARCH-STATE-004: Room as SSOT, background sync writes to Room (not in-memory state)
- ARCH-PERF-001: Use maps (not lists) for O(1) lookup in 3-way merge
- Content-based Idempotency Keys (survive Process Death)
- Entity-level merge (not full-document replace)
- `isActivelyEditing` guard prevents UI jump during sync
- Testcontainers for fast, isolated E2E testing
- JWT refresh token pattern (long-lived + short-lived)

---

## Notes for Developers

**Critical Implementation Details:**
1. **Version-Based Locking**: Use `version: int`, NOT `updated_at: timestamp` (clocks unreliable)
2. **Token Refresh**: Double-Checked Locking with Mutex (prevents stampede)
3. **Idempotency Keys**: Content-based (not random UUID) to survive Process Death
4. **3-Way Merge**: Most complex PR (2-3 weeks), use maps for O(1) lookup
5. **isActivelyEditing Guard**: 5-second timeout prevents UI jump during drag
6. **Testcontainers**: Real PostgreSQL in Docker, much faster than mocks
7. **Background Sync**: WorkManager with network constraint, 15-minute interval
8. **Conflict UI**: Show local/remote/base, let user choose or edit

**Dependencies Between PRs:**
- PR-01 to PR-04 (Backend foundation) sequential
- PR-05 to PR-08 (CRUD endpoints) sequential
- PR-09, PR-10 (Versioning) foundational for later PRs
- PR-11, PR-12 (Token, Idempotency) can develop in parallel
- PR-2.5 (3-way merge) is THE MOST COMPLEX, allow 2-3 weeks
- PR-13 (Conflict UI) depends on PR-2.5
- PR-14, PR-15 (Background sync) can develop in parallel with merge
- PR-16, PR-17 (Testcontainers) after core sync working
- PR-18 (Android tests) after sync client complete
- PR-19, PR-20 (Docs) can start anytime

**Parallel Work Opportunities:**
- Pack A (Backend setup) and Pack D (Token/Idempotency) can overlap
- Pack C (Versioning) and Pack D can overlap
- Pack E (3-way merge) is critical path (longest PR)
- Pack F (Background sync) independent until integration
- Pack G (Testing) can start once core features working
- Pack H (Docs) can develop throughout sprint

**Testing Strategy:**
- Unit tests: Merge algorithm, versioning logic, token refresh
- Integration tests: API endpoints with Testcontainers
- E2E tests: Full sync flows (upload, download, conflict, merge)
- Instrumentation tests: Android sync client with mock server
- Load tests: 1000-entity merge performance
- Security tests: Authentication, authorization, token validation

**MVP vs Full Feature Set:**
If 3-way merge (PR-2.5) too complex for MVP timeline:
- **MVP Alternative**: Use operation-based sync with CRDTs
  - Simpler: Each edit is an operation (AddNode, MoveNode, etc.)
  - Operations commutative: Order doesn't matter
  - No 12-case merge logic needed
  - Tradeoff: Can't detect semantic conflicts (moved + deleted)
- **Decide early**: Week 1 of sprint, prototype 3-way merge
  - If prototype takes >1 week, switch to operation-based
  - Document decision in ADR

**3-Way Merge Complexity:**
- 12 cases to handle (added/deleted/modified on local/remote/base)
- Requires careful testing (property-based tests recommended)
- Entity-level (not field-level) keeps complexity manageable
- Alternative: Operation-based sync (simpler but less powerful)

---

*Last updated: 2026-02-26*
*Sprint status: Ready for implementation*
*Previous sprint: Sprint 4 - Converter*
*Next sprint: Sprint 5 - AR Rendering (Filament)*
