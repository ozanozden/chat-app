# Chat Application - Backend

A learning project exploring **polyglot persistence** and **distributed systems** through a chat application built with Spring Boot (Java 21), Apache Cassandra, PostgreSQL, and Redis.

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

## 🏃 Running the Project

### Prerequisites

- Java 21
- Docker & Docker Compose
- Gradle

### Setup

1. **Start infrastructure:**
```bash
docker-compose up -d
```

This starts:
- Cassandra (port 9042)
- PostgreSQL (port 5432)
- Redis (port 6379)

2. **Create Cassandra keyspace:**
```bash
docker exec -it $(docker ps -qf "ancestor=cassandra:4.1") cqlsh

CREATE KEYSPACE IF NOT EXISTS chat_app 
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};
```

3. **Run the application:**
```bash
./gradlew bootRun
```

Application starts on `http://localhost:8080`

### API Endpoints

**Users:**
```
POST   /api/v1/users                     # Create user
GET    /api/v1/users                     # Get all users
```

**Messages:**
```
POST   /api/v1/messages                  # Send message
GET    /api/v1/users/{userId}/conversations          # Get user's conversations
GET    /api/v1/conversations/{id}/messages           # Get conversation messages
```

### Example: Send Message

```bash
curl -X POST http://localhost:8080/api/v1/messages \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "00000000-0000-0000-0000-000000000001",
    "senderId": "user-alice-uuid",
    "text": "Hey Bob!",
    "participantUserIds": ["user-alice-uuid", "user-bob-uuid"]
  }'
```

## 📚 Key Learnings & Trade-offs

### 1. Cassandra vs RDBMS

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

### 3. Clustering Key Choice

**Original design:** `PRIMARY KEY ((user_id), last_message_time DESC, conversation_id)`
- ✅ Pro: Cassandra handles sorting
- ❌ Con: Can't UPDATE `last_message_time` (it's part of key)
- ❌ Con: Must DELETE old row + INSERT new row on every message

**Final design:** `PRIMARY KEY ((user_id), conversation_id)`
- ✅ Pro: Can UPDATE `last_message_time` (single operation)
- ✅ Pro: No DELETE needed
- ❌ Con: Must sort in application layer (negligible cost for <100 conversations)

**Decision:** Application-layer sorting is worth it to avoid DELETE overhead.

### 4. Cache Strategy

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

## 🎓 Learning Outcomes

This project taught me:

1. **Cassandra query-driven design** - Start with queries, then design tables
2. **Denormalization patterns** - Trading storage for performance
3. **Polyglot persistence** - Using multiple databases strategically
4. **Hexagonal architecture** - Keeping business logic independent of frameworks
5. **Cache-aside pattern** - Practical Redis usage for performance
6. **Trade-off analysis** - Every design decision has pros/cons

## 🚧 Limitations & Future Improvements

**Current Limitations:**
- No authentication/authorization
- No read receipts
- No message editing/deletion
- No group chat (>2 participants)
- No pagination cursors (just limit parameter)
- No WebSocket real-time updates

**Why these are out of scope:**
This is a **learning project** focused on:
- Understanding Cassandra fundamentals
- Practicing clean architecture
- Exploring polyglot persistence patterns

Production chat apps would need additional features, but the core concepts demonstrated here remain the same.

## 📝 Notes

**Why chat app for Cassandra?**
While production chat apps often need strong consistency (making Cassandra suboptimal for real-world messaging), chat is an excellent learning vehicle because:
- Messages naturally partition by conversation
- Time-series characteristics are clear
- Denormalization trade-offs become concrete
- Write-heavy workload matches Cassandra strengths

**Is this production-ready?**
No - this is a learning project. Production requirements would include:
- Message delivery guarantees
- Conflict resolution for concurrent writes
- Proper authentication & authorization
- Rate limiting
- Monitoring & observability
- Backup & disaster recovery

## 📖 References

- [Apache Cassandra Documentation](https://cassandra.apache.org/doc/latest/)
- [Spring Data Cassandra](https://spring.io/projects/spring-data-cassandra)
- [Hexagonal Architecture (Ports & Adapters)](https://alistair.cockburn.us/hexagonal-architecture/)
- [Cache-Aside Pattern](https://docs.microsoft.com/en-us/azure/architecture/patterns/cache-aside)

---

**Author:** Ozan Özden  
**Tech Stack:** Java 21 | Spring Boot | Cassandra | PostgreSQL | Redis  
**Architecture:** Hexagonal/Clean Architecture | Polyglot Persistence
