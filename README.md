# Chat Application - Backend

A learning project exploring **polyglot persistence** and **distributed systems** through a chat application built with Spring Boot (Java 21), Apache Cassandra, PostgreSQL, and Redis.

**Why Cassandra for chat?** Discord migrated from MongoDB to Cassandra at 100M messages/day when their working set (hot data + indexes) exceeded RAM. Cassandra delivered consistent performance at 120M+ messages/day. This project demonstrates the same architecture patterns at learning scale.

**What's Inside:**
- Real Cassandra schema (partition keys, clustering columns, denormalization)
- QUORUM consistency for read-your-own-write guarantees
- Hexagonal architecture with polyglot persistence (PostgreSQL/Cassandra/Redis)
- Production lessons from Discord's migration story

**[Jump to: When to Use Cassandra](#-when-to-use-cassandra-vs-postgresql)** | **[Jump to: What I Learned](#-what-i-learned-building-this)**

## 🏗️ Architecture

**Polyglot Persistence** - Right database for the job:

| Database | Use Case | Why? |
|----------|----------|------|
| **PostgreSQL** | Users | ACID for future features (transactions, payments). Currently just basic user storage. |
| **Cassandra** | Messages | Write-optimized, horizontal scalability, time-series data |
| **Redis** | Cache | Fast user lookups, reduce PostgreSQL load |

**Hexagonal Architecture** - Domain ← Application ← Infrastructure ← Web  
(Clean separation allows swapping Cassandra for another DB without changing business logic)

## 📊 Cassandra Schema Design

### Query-Driven Tables

**Table 1: `messages_by_conversation`** - "Get last N messages for conversation X"
```sql
PRIMARY KEY ((conversation_id), created_at DESC)
```
- Partition key: `conversation_id` (all messages for a conversation on same node)
- Clustering: `created_at DESC` (newest messages first)

**Table 2: `conversations_by_user`** - "Get all conversations for user X"
```sql
PRIMARY KEY ((user_id), conversation_id)
```
- Partition key: `user_id` (user's inbox on same node)
- Clustering: `conversation_id` (allows UPDATE instead of DELETE+INSERT)
- Trade-off: Sort by `last_message_time` in Java (negligible cost) to avoid tombstones from DELETE operations

### Denormalization

One message → 3 writes:
1. Insert into `messages_by_conversation`
2. Update Alice's `conversations_by_user`
3. Update Bob's `conversations_by_user`

**Why?** Fast reads (no JOINs), each user has their own inbox view. **Cost:** 3x write amplification.

## 🚀 Redis Caching

Cache-aside pattern for user data: Check Redis → Miss? Query PostgreSQL → Populate cache. TTL: 24 hours.

## 🛠️ Tech Stack

Java 21 | Spring Boot 4.0.6 | Cassandra 4.1 | PostgreSQL 15 | Redis 7 | Docker Compose

## 🚀 Quick Start

```bash
# 1. Start infrastructure (Cassandra, PostgreSQL, Redis)
docker-compose up -d

# 2. Wait 30-60s, then create Cassandra schema
docker exec -i $(docker ps -qf "ancestor=cassandra:4.1") cqlsh < schema.cql

# 3. Start application (PostgreSQL tables auto-created by JPA)
./gradlew bootRun
```

**Test it:** `curl -X POST http://localhost:8080/api/users -H "Content-Type: application/json" -d '{"name":"Alice","email":"alice@example.com"}'`

**Stop:** `docker-compose down`

## 📚 Key Technical Decisions

### Consistency Level: QUORUM

Configured `LOCAL_QUORUM` for reads and writes:

```
Write to 2/3 replicas + Read from 2/3 replicas = Overlap guaranteed
→ You always see your own writes (read-your-own-write consistency)
```

**Trade-off:** Slightly higher latency vs ONE, but messages appear immediately and in order.

**Dev vs Prod:** Currently single node (RF=1), so QUORUM = strong consistency. Production would use 3+ nodes with RF=3.

### Primary Key Choice: Avoid Tombstones

**Original:** `PRIMARY KEY ((user_id), last_message_time DESC, conversation_id)`  
**Problem:** Can't UPDATE clustering columns → required DELETE+INSERT on every message → tombstones

**Final:** `PRIMARY KEY ((user_id), conversation_id)`  
**Solution:** Sort in Java instead. Negligible cost (<100 conversations), avoids tombstones.

### Time-Bucketing: Not Needed at My Scale

Discord uses `PRIMARY KEY ((channel_id, bucket), message_id)` with 10-day buckets to keep partitions <100MB.

My case: 1-on-1 chats with <1000 messages = <1MB partitions. Simpler schema without bucketing works fine.

## 🎓 When to Use Cassandra vs PostgreSQL

**✅ Cassandra:** Write-heavy workloads (>50K writes/sec), time-series data, horizontal scaling needed
- Chat at scale (Discord: 120M+ msgs/day), IoT sensors, logs/metrics, activity feeds

**❌ Cassandra:** ACID transactions, ad-hoc queries, small scale (<10K writes/sec)
- E-commerce inventory, analytics, most CRUD apps

**Rule of thumb:** If PostgreSQL + good indexes + vertical scaling works, use that. Cassandra's complexity is only justified when you've exhausted single-node options.

### Discord's Migration Story

**Problem:** MongoDB failed at 100M msgs/day when working set (hot data + indexes) exceeded RAM → unpredictable latency

**Solution:** Cassandra - now handles 120M+ msgs/day with consistent low latency

**Why Cassandra Scales:**
1. **LSM trees** - Sequential append-only writes (fast) vs B-tree random updates (slower under load)
2. **Horizontal write scaling** - All nodes accept writes (masterless) vs PostgreSQL single-primary bottleneck
3. **Predictable latency** - Performance doesn't degrade as data grows

**Production Lessons:**
- **Tombstones** - Deletes create metadata that slows reads. Discord: avoid frequent deletes, reduced `gc_grace_seconds` to 2 days
- **Null writes = tombstones** - Don't `UPDATE field = null`, skip null fields entirely
- **Partition size** - Keep <100MB. Discord uses 10-day buckets for channels with millions of messages

**Migration threshold:** When working set > available RAM → Cassandra's LSM architecture wins

## 🎓 What I Learned

**Query-Driven Design:** Design tables for your queries, not relationships. Created separate tables for "messages in conversation" vs "user's conversations" instead of JOIN.

**Partition Keys Matter:** `conversation_id` keeps all messages together on one node (good for range queries). Bad partition key = hot spots.

**Denormalization Required:** No JOINs in Cassandra. Trade-off: Fast reads vs duplicate data + complex writes (3x write amplification for 1-on-1 chat).

**Primary Key Consequences:** Originally used `last_message_time` as clustering key for auto-sort. Problem: Can't UPDATE clustering columns → DELETE+INSERT every message → tombstones. Solution: Sort in Java, simpler and faster.

**CAP Theorem:** Cassandra is AP (Availability + Partition Tolerance). QUORUM gives read-your-own-write but it's still eventually consistent.

**Horizontal vs Vertical Scaling:** PostgreSQL replicas = more read capacity, same write bottleneck. Cassandra = all nodes accept writes (masterless).

## 🚧 What I'd Change for Production

**Cassandra:**
- 3+ nodes with `NetworkTopologyStrategy` replication (currently single node)
- TWCS compaction for time-series data (currently default STCS)
- Monitoring: partition sizes, tombstone counts, latency percentiles

**Application:**
- Idempotency keys (prevent duplicate messages on retry)
- Circuit breakers (fail fast when nodes are down)
- Authentication and rate limiting (currently wide open!)

**Ops:**
- Backup/recovery strategy
- Capacity planning for write amplification (group chats: 1 message → N+1 writes)

## 💭 Mistakes & Lessons

**1. Clustering key can't be updated**  
Original: `PRIMARY KEY ((user_id), last_message_time, conversation_id)` for auto-sort.  
Problem: Every message = DELETE+INSERT → tombstones.  
Fix: `PRIMARY KEY ((user_id), conversation_id)`, sort in Java. Lesson: Fight the database less.

**2. Tombstones are sneaky**  
Planned message deletion without understanding tombstones persist for 10 days and slow reads.  
Fix: Removed deletion. Lesson: Simple features can have complex distributed implications.

**3. "NoSQL" ≠ "No Planning"**  
Assumed Cassandra = easier than SQL. Reality: MORE upfront design needed. Must know queries before tables.  
Lesson: Query-driven design is harder than normalization because you can't easily change your mind.

## 🚧 Project Scope & Limitations

**This is a learning project to understand Cassandra fundamentals, not production-ready code.**

**What it demonstrates:**
- ✅ Cassandra data modeling (partition keys, clustering columns, denormalization)
- ✅ Consistency level configuration (QUORUM for read-your-own-write guarantees)
- ✅ Polyglot persistence (right database for the job)
- ✅ Hexagonal architecture (clean separation of domain/infrastructure)
- ✅ Production patterns from Discord's migration story

**Intentionally simplified:**
- **User management:** Basic PostgreSQL storage with unique email constraints. Chosen for future ACID requirements (transactions, payments) but currently only handles simple user creation.
- **1-on-1 chat only:** Group chat would use the same Cassandra patterns but with higher write amplification (1 message → N+1 writes for N participants)
- **No authentication:** Focus is on data modeling, not security
- **No real-time updates:** WebSocket/SSE would add transport complexity without teaching database concepts
- **No message editing/deletion:** Avoided to keep tombstone management simple

**Missing for production:**
- Idempotency keys (prevent duplicate messages on retry)
- Monitoring (partition sizes, tombstone counts, latency percentiles)
- Rate limiting and circuit breakers
- Multi-datacenter replication
- Backup and disaster recovery
- Security (input validation, XSS prevention, authentication)

## 📖 References

**Documentation:**
- [Apache Cassandra Documentation](https://cassandra.apache.org/doc/latest/)
- [Spring Data Cassandra](https://spring.io/projects/spring-data-cassandra)
- [Hexagonal Architecture (Ports & Adapters)](https://alistair.cockburn.us/hexagonal-architecture/)
- [Cache-Aside Pattern](https://docs.microsoft.com/en-us/azure/architecture/patterns/cache-aside)

**Articles & Case Studies:**
- [How Discord Stores Billions of Messages](https://discord.com/blog/how-discord-stores-billions-of-messages)
- [Cassandra Deep Dive - Hello Interview](https://www.hellointerview.com/learn/system-design/deep-dives/cassandra)
- [Why Chat Apps Prefer Cassandra Over Postgres](https://medium.com/@faizanhaidar48/why-chat-messaging-real-time-apps-prefer-cassandra-over-postgres-the-internal-story-explained-a4777b36f2fd)

---

**Author:** Ozan Özden  
**Tech Stack:** Java 21 | Spring Boot | Cassandra | PostgreSQL | Redis  
**Architecture:** Hexagonal/Clean Architecture | Polyglot Persistence
