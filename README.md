# Chat Application - Backend

A learning project exploring **polyglot persistence** and **distributed systems** through a chat application built with Spring Boot (Java 21), Apache Cassandra, PostgreSQL, and Redis.

**Why Cassandra for chat?** Discord migrated from MongoDB to Cassandra at 100M messages/day when their index+data exceeded RAM. Cassandra delivered <1ms writes and <5ms reads at 120M+ messages/day. This project demonstrates the same architecture patterns at learning scale.

**TL;DR - What's Inside:**
- ✅ Real Cassandra schema with partition keys, clustering columns, time-series design
- ✅ QUORUM consistency configuration for read-your-own-write guarantees  
- ✅ Denormalization strategy (messages duplicated across tables, no JOINs)
- ✅ Hexagonal architecture (domain independent of infrastructure)
- ✅ Production lessons from Discord (tombstones, time-bucketing, partition sizing)
- ✅ Technical deep-dive: LSM vs B-trees, horizontal write scaling, CAP theorem trade-offs

**[Jump to: When to Use Cassandra vs PostgreSQL](#would-i-use-cassandra-for-chat-it-depends-on-scale)**

---

## 🎯 Project Goals

This project demonstrates:
- **Apache Cassandra** fundamentals: partition keys, clustering columns, query-driven design, denormalization
- **Polyglot Persistence**: Using the right database for the right job
- **Hexagonal Architecture**: Clean separation of concerns with ports & adapters pattern
- **Cache-aside Pattern**: Performance optimization with Redis
- **Domain-Driven Design**: Pure domain entities with business logic validation

## 🏗️ Architecture

### Polyglot Persistence Pattern

Different databases serve different purposes:

| Database | Use Case | Why? |
|----------|----------|------|
| **PostgreSQL** | User management | ACID guarantees, relational integrity, email uniqueness constraints |
| **Cassandra** | Message storage | Distributed, write-optimized, time-series data, horizontal scalability |
| **Redis** | User data cache | Sub-millisecond reads, reduces PostgreSQL load |

### Hexagonal Architecture

```
├── domain/                     # Pure business logic (no framework dependencies)
│   ├── model/
│   │   ├── User.java          # Domain entity with validation
│   │   └── Message.java       # Domain entity with validation
│   └── exception/             # Domain-specific exceptions
│       ├── InvalidUserException.java
│       └── InvalidMessageException.java
│
├── application/               # Use cases & ports
│   ├── port/
│   │   ├── UserRepository.java
│   │   └── MessageRepository.java
│   ├── UserService.java       # User management + Redis caching
│   ├── MessageService.java
│   └── exception/             # Application-layer exceptions
│       ├── UserNotFoundException.java (404)
│       └── UserAlreadyExistsException.java (409)
│
├── infrastructure/            # Technical implementation
│   ├── postgres/
│   │   ├── entity/UserEntity.java
│   │   └── dao/UserDAO.java
│   ├── cassandra/
│   │   ├── entity/
│   │   │   ├── MessageEntity.java
│   │   │   └── ConversationByUserEntity.java
│   │   └── dao/
│   ├── adapter/
│   │   ├── UserRepositoryImpl.java
│   │   └── MessageRepositoryImpl.java
│   └── config/
│       ├── JacksonConfig.java
│       └── CorsConfig.java
│
└── web/                       # REST API
    ├── UserController.java
    ├── MessageController.java
    └── exception/GlobalExceptionHandler.java
```

**Dependency Flow:** Domain ← Application ← Infrastructure ← Web

## 📊 Cassandra Data Model

### Query-Driven Design

Cassandra requires designing tables based on access patterns, not relationships.

#### Table 1: `messages_by_conversation`

**Query:** "Get last N messages for conversation X"

```sql
CREATE TABLE messages_by_conversation (
    conversation_id uuid,
    created_at timestamp,
    message_text text,
    sender_id uuid,
    PRIMARY KEY ((conversation_id), created_at)
) WITH CLUSTERING ORDER BY (created_at DESC);
```

**Key Design Decisions:**
- **Partition Key:** `conversation_id` - all messages for a conversation stored together on same node
- **Clustering Column:** `created_at DESC` - messages sorted newest-first within partition
- **Why DESC?** Fetching recent messages is most common query pattern

#### Table 2: `conversations_by_user`

**Query:** "Get all conversations for user X, ordered by most recent activity"

```sql
CREATE TABLE conversations_by_user (
    user_id uuid,
    conversation_id uuid,
    last_message_time timestamp,
    last_message_text text,
    other_user_id uuid,
    PRIMARY KEY ((user_id), conversation_id)
);
```

**Key Design Decisions:**
- **Partition Key:** `user_id` - all conversations for a user on same node
- **Clustering Column:** `conversation_id` - allows UPDATEs instead of DELETE+INSERT
- **Trade-off:** Sort by `last_message_time` in application layer (Java) instead of Cassandra
  - **Why?** Allows updating `last_message_time` without deleting/re-inserting rows
  - **Performance:** Sorting 50-100 conversations in Java is negligible (~microseconds)
  - **Benefit:** Avoids DELETE+INSERT overhead on every message (2 operations → 1 UPDATE)

### Denormalization Strategy

When a message is sent, it's written to **both tables:**

1. **`messages_by_conversation`** - INSERT new message
2. **`conversations_by_user`** - UPDATE conversation summary for ALL participants

**Example:** Alice sends message to Bob
```java
// 1. Insert message (Cassandra - messages_by_conversation)
messageDAO.save(messageEntity);

// 2. Update conversation for Alice (Cassandra - conversations_by_user)
conversationDAO.save(new ConversationByUserEntity(
    userId: Alice,
    conversationId: "conv-123",
    lastMessageTime: now,
    lastMessageText: "Hey Bob!",
    otherUserId: Bob
));

// 3. Update conversation for Bob (Cassandra - conversations_by_user)
conversationDAO.save(new ConversationByUserEntity(
    userId: Bob,
    conversationId: "conv-123",
    lastMessageTime: now,
    lastMessageText: "Hey Bob!",
    otherUserId: Alice
));
```

**Why Denormalize?**
- ✅ Fast reads - no joins needed
- ✅ Each user sees their inbox instantly
- ❌ Duplicate data (trade-off for performance)
- ❌ Eventual consistency (acceptable for chat app)

## 🚀 Redis Caching Strategy

### Cache-Aside Pattern

**User data caching** to avoid PostgreSQL queries on every conversation/message fetch:

```java
public String getUserName(UUID userId) {
    // 1. Try Redis first (cache hit: ~1ms)
    String cached = redisTemplate.opsForValue().get("user:" + userId);
    if (cached != null) return cached;
    
    // 2. Cache miss - query PostgreSQL (~10ms)
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(...));
    
    // 3. Warm cache for next time
    cacheUser(user);
    
    return user.getName();
}
```

**Cache Structure:**

```
Redis Keys:
- users:all         → List<User> (JSON)
- user:{id}         → User (JSON)
```

**Cache Invalidation:**
- `users:all` deleted when new user created
- Individual user cache updated when user data changes
- TTL: 24 hours

**Why this works:**
- User data changes infrequently
- Conversations/messages query user names frequently
- Single Redis GET vs PostgreSQL query saves ~10ms per request

## 🛠️ Technologies

- **Java 21** - Latest LTS with modern language features
- **Spring Boot 4.0.6** - Framework
- **Apache Cassandra 4.1** - Distributed NoSQL for messages
- **PostgreSQL 15** - Relational database for users
- **Redis 7** - In-memory cache
- **Docker Compose** - Local development environment
- **Lombok** - Reduce boilerplate
- **Jackson** - JSON serialization with Java 8 time support

## 🚀 Getting Started

### Prerequisites

- **Docker & Docker Compose** - For running Cassandra, PostgreSQL, and Redis
- **Java 21** - Language runtime
- **Gradle** - Build tool (or use included `./gradlew`)

### 1. Start Infrastructure

```bash
docker-compose up -d
```

This starts:
- **Cassandra** on `localhost:9042`
- **PostgreSQL** on `localhost:5432` (db: `chatapp`, user: `chatuser`, password: `chatpass`)
- **Redis** on `localhost:6379`

**Wait 30-60 seconds** for Cassandra to fully initialize before proceeding.

### 2. Create Cassandra Keyspace & Tables

Run the schema file to create the keyspace and tables:

```bash
docker exec -i $(docker ps -qf "ancestor=cassandra:4.1") cqlsh < schema.cql
```

**If that doesn't work** (container name different), find your container name first:

```bash
docker ps | grep cassandra
# Then use the container name:
docker exec -i <container-name> cqlsh < schema.cql
```

**Verify the schema was created:**

```bash
docker exec -it $(docker ps -qf "ancestor=cassandra:4.1") cqlsh
```

Then in the CQL shell:

```cql
USE chat_app;
DESCRIBE TABLES;
-- Should show: conversations_by_user, messages_by_conversation, users
```

### 3. Start the Application

```bash
./gradlew bootRun
```

**PostgreSQL tables are auto-created** by JPA (`spring.jpa.hibernate.ddl-auto: update` in `application.yml`)

Application starts on `http://localhost:8080`

### 4. Test the API

**Create a user:**

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice",
    "email": "alice@example.com"
  }'
```

**Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Alice",
  "email": "alice@example.com"
}
```

**Send a message:**

```bash
curl -X POST http://localhost:8080/api/messages \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "123e4567-e89b-12d3-a456-426614174000",
    "senderId": "550e8400-e29b-41d4-a716-446655440000",
    "messageText": "Hello Cassandra!",
    "participantUserIds": ["550e8400-e29b-41d4-a716-446655440000"]
  }'
```

**Get user's conversations:**

```bash
curl http://localhost:8080/api/users/550e8400-e29b-41d4-a716-446655440000/conversations
```

### 5. Stop Everything

```bash
docker-compose down
# To remove volumes as well (deletes all data):
docker-compose down -v
```

## 📚 Key Learnings & Trade-offs

### 1. Why Cassandra for Chat? The Technical Deep Dive

**Cassandra was designed for Facebook's inbox** - not as a generic database, but specifically for message storage at scale.

**The Write Performance Advantage:**

```
PostgreSQL (B-Tree):
Message arrives → Find index page → Read page → Update page → Write page back
→ Random disk I/O, requires locks, ~10-20ms

Cassandra (LSM Tree):
Message arrives → Append to commit log → Append to memtable → ACK to user ✅
→ Sequential I/O, no locks, ~1-5ms
(SSTables flushed and compacted in background)
```

**At 1M messages/sec:**
- PostgreSQL: Random I/O becomes bottleneck, single write node saturated
- Cassandra: Sequential append across multiple nodes, scales linearly

**The Horizontal Scaling Difference:**

```
PostgreSQL with Read Replicas:
┌─────────────┐
│  PRIMARY    │ ← ALL 1M writes/sec go here (BOTTLENECK)
│  (Writes)   │
└──────┬──────┘
       │ Async replication
       ├──────────┬──────────┐
       ▼          ▼          ▼
    Replica1   Replica2   Replica3
   (Read-only) (Read-only) (Read-only)

Adding replicas: ✅ More read capacity ❌ Same write capacity


Cassandra (Masterless):
┌─────────┐  ┌─────────┐  ┌─────────┐
│ Node 1  │  │ Node 2  │  │ Node 3  │
│ R + W   │  │ R + W   │  │ R + W   │
└─────────┘  └─────────┘  └─────────┘
  333K w/s     333K w/s     333K w/s
  
Adding nodes: ✅ More read capacity ✅ More write capacity
```

**This is why Discord (12M concurrent users) uses Cassandra/ScyllaDB.**

### 2. Cassandra vs RDBMS - When to Choose Each

**When to use Cassandra:**
- ✅ Write-heavy workloads (chat messages, logs, time-series)
- ✅ Need horizontal scalability
- ✅ Eventual consistency is acceptable
- ✅ Query patterns are known upfront

**When to use PostgreSQL:**
- ✅ Need ACID transactions
- ✅ Complex queries with joins
- ✅ Strong consistency required
- ✅ Data has relational structure

### 2. Denormalization Trade-offs

**Storing conversation summary separately:**
- ✅ Pro: Fast inbox queries (no scanning all messages)
- ✅ Pro: Each user has their own view
- ❌ Con: Duplicate storage (each message saved multiple times)
- ❌ Con: Update complexity (must update all participant rows)

### 3. Clustering Key Choice & Time-Bucketing Strategy

**My Implementation:** `PRIMARY KEY ((user_id), conversation_id)`

**Why I didn't use time-bucketing** (like Discord):
- Discord buckets messages by ~10-day windows: `PRIMARY KEY ((channel_id, bucket), message_id)`
- **Their reason:** Prevent large partitions (>100MB causes GC pressure during compaction)
- **My case:** 2-person chats with <1000 messages = tiny partitions (<1MB)
- **Decision:** Simpler schema without bucketing is fine at small scale

**Discord's Time-Bucketing Lesson:**
```sql
-- Discord's approach (billions of messages per channel):
PRIMARY KEY ((channel_id, bucket), message_id)
-- where bucket = floor(message_id / messages_per_bucket)

-- Prevents:
- Single partition from growing unbounded
- GC pauses during compaction of huge partitions
- Tombstone scanning across millions of deleted messages
```

**When you need time-bucketing:**
- Partition size approaching 100MB (Cassandra soft limit)
- Channels/conversations with millions of messages
- High delete rate (avoid tombstone accumulation in single partition)

**Primary Key Evolution in This Project:**

**Original design:** `PRIMARY KEY ((user_id), last_message_time DESC, conversation_id)`
- ✅ Pro: Cassandra handles sorting
- ❌ Con: Can't UPDATE `last_message_time` (it's part of key)
- ❌ Con: Must DELETE old row + INSERT new row on every message

**Final design:** `PRIMARY KEY ((user_id), conversation_id)`
- ✅ Pro: Can UPDATE `last_message_time` (single operation)
- ✅ Pro: No DELETE needed (avoids tombstones)
- ❌ Con: Must sort in application layer (negligible cost for <100 conversations)

**Decision:** Application-layer sorting worth it to avoid DELETE overhead and tombstone accumulation.

### 4. Cassandra Consistency Levels

**Understanding the Terminology:**

| Term | Definition | Example (RF=3) |
|------|------------|----------------|
| **Replication Factor (RF)** | How many copies of data exist across nodes | RF=3 means each partition copied to 3 nodes |
| **ONE** | Read/write succeeds if ANY 1 replica responds | Fastest but can read stale data |
| **QUORUM** | Requires majority of replicas to agree | `floor(RF/2) + 1` = `floor(3/2) + 1` = 2 nodes |
| **ALL** | Requires ALL replicas to agree | Strongest consistency, highest latency |
| **LOCAL_QUORUM** | Quorum within local datacenter only | Production standard for multi-DC clusters |

**Why QUORUM Prevents Stale Reads:**

```
Write to 2/3 replicas (QUORUM) + Read from 2/3 replicas (QUORUM) = Overlap guaranteed

Example:
- Write goes to Nodes A, B ✅ (returns success)
- Node C still replicating ⏳
- Read queries Nodes B, C → Node B has latest data ✅
- Result: You always see your own write (read-your-own-write consistency)
```

**Comparison of Consistency Levels:**

| Write | Read | Read-Your-Own-Write? | Latency | Availability | Use Case |
|-------|------|---------------------|---------|--------------|----------|
| ONE | ONE | ❌ No (stale reads possible) | ~5ms | High (1 node) | Logs, metrics, analytics |
| QUORUM | QUORUM | ✅ Yes (majority overlap) | ~15ms | Medium (2/3 nodes) | **Chat, social feeds, e-commerce** ✅ |
| ALL | ALL | ✅ Yes (all agree) | ~50ms | Low (all nodes) | Banking, inventory |

**Current Configuration:** `LOCAL_QUORUM` for both reads and writes

```yaml
# application.yml
spring.cassandra.request.consistency: local_quorum
```

**Trade-offs:**
- ✅ Read-your-own-write guarantee (messages appear immediately)
- ✅ Messages appear in chronological order (no out-of-order anomalies)
- ✅ Single node failure doesn't block operations (2/3 still works)
- ❌ Higher latency (~15ms vs ~5ms for ONE)
- ❌ Requires 2/3 nodes available (vs 1/3 for ONE)

**Development vs Production:**

| Environment | Nodes | RF | Consistency | Why? |
|-------------|-------|----|-----------|----|
| **Development** (current) | 1 | 1 | LOCAL_QUORUM (effectively strong) | Single node = no replicas = no stale reads |
| **Production** (recommended) | 3+ | 3 | LOCAL_QUORUM | Majority overlap ensures consistency |

With RF=1 and 1 node, QUORUM = "read from the only copy" = strong consistency by default.  
With RF=3 and 3 nodes, QUORUM = "read from 2/3 nodes" = eventual consistency with read-your-own-write guarantee.

### 5. Cache Strategy

**Redis Hash vs Separate Keys:**
- Considered: `user:names` hash with all names
- Chosen: `user:{id}` individual keys + `users:all` list
- **Why?** Easier cache invalidation and TTL management per user

## 🧪 Testing

The project demonstrates:
- ✅ Domain validation (empty messages, invalid emails)
- ✅ Exception handling with proper HTTP status codes
- ✅ Jackson serialization of immutable domain objects
- ✅ CORS configuration for frontend integration

## 🎓 Key Takeaways

### What I Learned

**Distributed Systems Concepts:**
- **CAP Theorem in practice** - Cassandra chooses Availability + Partition Tolerance, sacrifices Consistency
- **Eventual consistency trade-offs** - Faster writes but potential stale reads (mitigated with QUORUM)
- **Read-your-own-write** - How majority overlap (QUORUM) guarantees you see your own writes
- **Horizontal scaling** - Adding nodes increases both write capacity AND storage (unlike PostgreSQL read replicas)

**Cassandra-Specific Patterns:**
- **Query-driven design** - Design tables based on access patterns, not entity relationships
- **Partition key selection** - Determines data distribution across nodes (conversation_id = even distribution)
- **Clustering columns** - Determines sort order within partition (created_at DESC = newest first)
- **Denormalization** - Duplicate data across tables to avoid JOINs (messages stored in 2+ tables)
- **UPDATE vs DELETE+INSERT** - Why primary key design matters (can't update clustering keys)
- **Time-bucketing** - Partition by (entity_id, time_bucket) to prevent unbounded partition growth
- **Tombstone management** - Deletes create tombstones that must be scanned on reads (minimize deletes!)

**Production Lessons from Discord (Handling Billions of Messages):**

1. **Tombstone Accumulation Problem:**
   - Discord had channels with 1M deleted messages → 1M tombstones
   - Reading channel forced scanning ALL tombstones → continuous GC
   - **Solution:** Reduced `gc_grace_seconds` from 10 days to 2 days
   - **Takeaway:** Avoid designs that require frequent deletes

2. **Only Write Non-Null Values:**
   - Cassandra treats null writes as deletes → creates tombstones
   - Discord reduced tombstones from ~12/message to 0 by skipping null fields
   - **Takeaway:** `UPDATE SET field = null` creates tombstones - avoid if possible

3. **Edit/Delete Race Conditions:**
   - Concurrent edits + deletes created corrupt rows with missing required fields
   - **Solution:** Detect null required fields, delete corrupted messages
   - **Takeaway:** Cassandra's eventual consistency means concurrent updates can collide

4. **Partition Size Limits:**
   - Keep partitions <100MB (soft limit before GC pressure)
   - Discord uses ~10-day buckets to cap partition size
   - **Takeaway:** Monitor partition sizes with `nodetool cfstats`

**Architectural Decisions:**
- **Polyglot persistence** - PostgreSQL (ACID for users), Cassandra (writes for messages), Redis (speed for cache)
- **Hexagonal architecture** - Domain layer independent of infrastructure (Cassandra could be swapped for MongoDB without changing business logic)
- **Cache-aside pattern** - Check cache first, fallback to database, warm cache on miss

### When I Would Actually Use Cassandra

**✅ Good fit:**
- **Chat applications at scale** - Discord (120M+ messages/day), Facebook Messenger - horizontal write scaling + predictable <5ms latency
- **IoT sensor data** - 1M devices × 1 reading/sec = 1M writes/sec (PostgreSQL can't handle this)
- **Logging/metrics** - Microservices writing 100K logs/sec across services
- **Time-series data** - Stock prices, weather data, click streams (ordered by time, append-only)
- **Activity feeds** - Social media posts, notifications (partition by user_id, sort by time)

**❌ Bad fit:**
- **E-commerce transactions** - Need ACID guarantees for inventory/payments
- **Ad-hoc analytics** - Can't do `WHERE text LIKE '%search%'` without scanning everything
- **Small scale** - PostgreSQL handles 10K writes/sec on single node (Cassandra overhead not worth it)

### Would I Use Cassandra for Chat? It Depends on Scale.

**At small-to-medium scale (<100K concurrent users):** PostgreSQL is simpler and sufficient.

**At massive scale (1M+ concurrent users, global distribution):** Cassandra's architecture wins.

**Why Cassandra Excels at Chat (at Scale):**

1. **Write Performance** - LSM trees (append-only, sequential I/O) vs B-trees (random disk writes)
   - Cassandra: Writes acknowledged before compaction → ~1-5ms latency
   - PostgreSQL: Must update B-tree indexes on every write → ~10-20ms latency
   - At 1M writes/sec, this difference is critical

2. **Horizontal Write Scaling** - PostgreSQL's fundamental limitation
   - PostgreSQL: Single primary node handles ALL writes (replicas are read-only)
   - Cassandra: ALL nodes can write (masterless architecture)
   - Adding nodes in PostgreSQL → more read capacity
   - Adding nodes in Cassandra → more read AND write capacity

3. **Lock-Free Concurrency** - Immutable SSTables + timestamp-based conflict resolution
   - Cassandra: "Last write wins" based on timestamp, no locks needed
   - PostgreSQL: Row-level locking can create contention under heavy concurrent writes

4. **Partition-Based Distribution** - Natural chat workload fit
   - Each conversation = separate partition
   - Popular conversations on different nodes (no hot spots)
   - PostgreSQL sharding requires manual partition management

**Real-World Examples:**

**Discord (2017 - present):**
- **Scale:** 120M+ messages/day, billions stored
- **Why Cassandra:** MongoDB index couldn't fit in RAM → unpredictable latencies
- **Performance:** <1ms writes, <5ms reads (regardless of data volume)
- **Schema:** `PRIMARY KEY ((channel_id, bucket), message_id)` with 10-day time buckets
- **Challenges faced:**
  - Tombstone accumulation (reduced gc_grace_seconds from 10d to 2d)
  - Large partition problem (one channel had 1M tombstones → continuous GC)
  - Edit/delete race conditions (fixed by null field detection)
- **Takeaway:** "Writes were sub-millisecond and reads were under 5 milliseconds"

**Facebook:**
- Cassandra was literally designed for Facebook Inbox (the original use case)

**Other notable users:**
- **Netflix** - Thousands of Cassandra nodes for viewing history, recommendations
- **Apple** - User data, iCloud backend
- **Ticketmaster** - Seat inventory with denormalized section aggregates

**When PostgreSQL is Better:**

| Factor | PostgreSQL | Cassandra |
|--------|-----------|-----------|
| **Scale** | <100K messages/day | >100M messages/day (Discord: 120M/day) |
| **Write volume** | <10K writes/sec | >100K writes/sec (Discord: ~1.4K writes/sec sustained) |
| **Read latency** | ~5-20ms (with indexes) | <5ms (Discord actual: <5ms at any scale) |
| **Write latency** | ~10-20ms (B-tree updates) | <1ms (Discord actual: sub-millisecond) |
| **Query flexibility** | Ad-hoc queries, JOINs, full-text search | Predefined access patterns only |
| **Consistency** | Strong by default | Eventual (tunable to strong with QUORUM) |
| **Ops complexity** | Simple (single instance) | Complex (tombstones, compaction, gc_grace_seconds) |
| **Setup time** | Minutes | Hours (cluster + schema + bucket strategy) |

**Discord's Migration Decision Point:**
- MongoDB worked fine at 40M messages/day
- Started failing at 100M messages/day (index + data > RAM)
- Migrated to Cassandra for predictable sub-5ms latency
- **Threshold:** When your data + indexes > RAM → Cassandra's LSM architecture wins

**My Decision for This Project:**

Cassandra is **the right choice for chat at Discord's scale** (120M+ messages/day) because:

1. **Write Performance:** LSM trees (sequential append) vs B-trees (random updates)
   - Cassandra: <1ms writes proven at scale
   - PostgreSQL: ~10-20ms writes under heavy load

2. **Horizontal Write Scaling:** PostgreSQL's fundamental bottleneck
   - PostgreSQL: Single primary handles ALL writes (read replicas don't help)
   - Cassandra: Distribute 120M writes/day across N nodes

3. **Predictable Latency:** Discord's main requirement
   - MongoDB: Latency spiked when index + data > RAM
   - Cassandra: <5ms reads regardless of data volume

**At small scale** (<10M messages/day), PostgreSQL is simpler and sufficient.

**The Migration Threshold (from Discord's experience):**
```
MongoDB worked fine: 40M messages/day ✅
Started failing: 100M messages/day ❌ (data + index > RAM)
Switched to Cassandra: 120M+ messages/day ✅ (sub-5ms latency)

Rule: When data + indexes > available RAM → Cassandra
```

**What This Project Demonstrates:**

✅ **I understand the technical depth**, not just surface-level "Cassandra is scalable"
- LSM vs B-tree architecture (why writes are faster)
- Masterless replication vs primary-replica (why writes scale horizontally)  
- Tombstone accumulation and gc_grace_seconds tuning
- Time-bucketing strategies to prevent large partitions
- Consistency level trade-offs (ONE vs QUORUM vs ALL)

✅ **I can evaluate trade-offs with real data**
- Not "big data" vagueness, but "120M messages/day = threshold"
- Discord's actual latencies: <1ms writes, <5ms reads
- Partition size limit: 100MB soft limit

✅ **I know when NOT to use it**
- Small scale: PostgreSQL simpler, equivalent performance
- ACID requirements: Banking, inventory, bookings
- Ad-hoc queries: Analytics, reporting, search

✅ **I've implemented production patterns**
- QUORUM consistency for read-your-own-write
- Denormalization for inbox queries
- Avoiding deletes to prevent tombstone accumulation
- Understanding when time-bucketing is needed (not at my scale)

## 🚧 Project Scope & Limitations

**This is a learning project, not production code.** It demonstrates:
- ✅ Cassandra fundamentals (partition keys, clustering, denormalization, consistency levels)
- ✅ Polyglot persistence patterns (right database for right job)
- ✅ Hexagonal architecture (clean separation of concerns)
- ✅ Distributed systems trade-offs (CAP theorem in practice)

**Intentionally omitted** (not needed for learning goals):
- Authentication/authorization (would add complexity without teaching distributed systems concepts)
- WebSocket real-time updates (focus is on data modeling, not real-time transport)
- Group chat >2 participants (same Cassandra patterns, just more denormalization)
- Message editing/deletion (adds tombstone management complexity)
- Read receipts, typing indicators (nice-to-have features)

**Production requirements not implemented:**
- Message delivery guarantees (idempotency, exactly-once semantics)
- Conflict resolution for concurrent writes (LWW, CRDTs)
- Rate limiting, monitoring, observability
- Backup & disaster recovery procedures
- Circuit breakers, retry logic with exponential backoff

## 📖 References

- [Apache Cassandra Documentation](https://cassandra.apache.org/doc/latest/)
- [Spring Data Cassandra](https://spring.io/projects/spring-data-cassandra)
- [Hexagonal Architecture (Ports & Adapters)](https://alistair.cockburn.us/hexagonal-architecture/)
- [Cache-Aside Pattern](https://docs.microsoft.com/en-us/azure/architecture/patterns/cache-aside)

---

**Author:** Ozan Özden  
**Tech Stack:** Java 21 | Spring Boot | Cassandra | PostgreSQL | Redis  
**Architecture:** Hexagonal/Clean Architecture | Polyglot Persistence
