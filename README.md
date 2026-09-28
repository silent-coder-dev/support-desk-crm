# 🛡️ SupportDesk CRM

### Enterprise Customer Ingestion, Concurrency Shield & Automated Notification Engine

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot 3](https://img.shields.io/badge/Spring_Boot-3.3+-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL 8.0](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Multi--Stage-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![Gmail SMTP](https://img.shields.io/badge/Gmail-SMTP%20TLS-EA4335?style=for-the-badge&logo=gmail&logoColor=white)](https://mail.google.com/)
[![Bootstrap 5.3](https://img.shields.io/badge/Bootstrap-5.3-7952B3?style=for-the-badge&logo=bootstrap&logoColor=white)](https://getbootstrap.com/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-3.4-38B2AC?style=for-the-badge&logo=tailwind-css&logoColor=white)](https://tailwindcss.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)

<div align="center">
  <p>
    <b>A production-grade, multi-threaded customer service CRM built to eliminate agent write collisions, prevent database spam, and dispatch automated lifecycle email notifications. Built by silent_coder.</b>
  </p>

  <p>
    <a href="#-1-evolution-tracker-what-why--how">Evolution Tracker</a> • 
    <a href="#-2-database-migration-journey-sqlite--mysql-80">Database Migration</a> • 
    <a href="#-3-concurrency-shield-optimistic-locking">Concurrency Shield</a> • 
    <a href="#-4-automated-email-notification-engine">Email Engine</a> • 
    <a href="#-5-rest-api-reference">REST APIs</a> • 
    <a href="#-6-docker--render-cloud-deployment">Deployment</a> • 
    <a href="#-7-local-installation--setup">Setup</a> • 
    <a href="#-live-application--endpoints">Live Endpoints</a>
  </p>
</div>

---

## 📊 1. Evolution Tracker: What, Why & How

<table>
  <thead>
    <tr>
      <th align="left">Dimension / Component</th>
      <th align="left">Initial Prototype</th>
      <th align="left">Production Upgrade</th>
      <th align="left">Architectural Rationale</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><b>Java Platform</b></td>
      <td>Java 17</td>
      <td><b>OpenJDK 21 (LTS)</b></td>
      <td>Performance improvements, modern runtime efficiency, and container resource management.</td>
    </tr>
    <tr>
      <td><b>Framework</b></td>
      <td>Spring Boot 2.x / 3.0</td>
      <td><b>Spring Boot 3.3+</b></td>
      <td>Native Jakarta EE 10 baseline, robust security filters, and optimized bean lifecycle management.</td>
    </tr>
    <tr>
      <td><b>Persistence Store</b></td>
      <td>SQLite 3 (<code>crm.db</code>)</td>
      <td><b>MySQL 8.0 (InnoDB)</b></td>
      <td>SQLite single-file write locking caused <code>Hikari Connection Timeout</code> under concurrency. MySQL delivers row-level locking and enterprise concurrency.</td>
    </tr>
    <tr>
      <td><b>Dialect Engine</b></td>
      <td>Community SQLite Dialect</td>
      <td><b>Hibernate MySQLDialect</b></td>
      <td>Eliminated <code>Cannot add UNIQUE column</code> limitations and auto-increment identifier mismatches.</td>
    </tr>
    <tr>
      <td><b>ID Generation</b></td>
      <td>Implicit SQLite RowID</td>
      <td><b><code>GenerationType.IDENTITY</code></b></td>
      <td>Guarantees multi-table relational integrity and auto-increment identity without sequence lock contention.</td>
    </tr>
    <tr>
      <td><b>Live Queue Sync</b></td>
      <td>Manual Page Refresh</td>
      <td><b>Server-Sent Events (SSE)</b></td>
      <td>Instant queue status synchronization, agent auto-claim indicators, and SLA updates without client polling.</td>
    </tr>
    <tr>
      <td><b>Lifecycle Notifications</b></td>
      <td>Silent Database Writes</td>
      <td><b>Automated Gmail SMTP Engine</b></td>
      <td>Delivers instant reference tracking URLs and resolution notices directly to the customer's inbox.</td>
    </tr>
    <tr>
      <td><b>Environment Configs</b></td>
      <td>Hardcoded properties</td>
      <td><b>12-Factor Variables (<code>DB_*</code>, <code>MAIL_*</code>)</b></td>
      <td>Protects production credentials from git history; enables dynamic container runtime injection on Render/AWS.</td>
    </tr>
    <tr>
      <td><b>Deployment Target</b></td>
      <td>Local host execution</td>
      <td><b>Multi-Stage Alpine Docker</b></td>
      <td>Mitigates ephemeral cloud filesystem loss and enforces consistent <code>Asia/Kolkata</code> timezone operations.</td>
    </tr>
  </tbody>
</table>

---

## 🗄️ 2. Database Migration Journey (SQLite ➔ MySQL 8.0)

### Problem Architecture (Why SQLite Broke Under Load)

```text
               [ Client HTTP Traffic ]
                          │
                          ▼
            [ Tomcat Worker Thread Pool ]
                          │
         ┌────────────────┴────────────────┐
         ▼                                 ▼
[Thread A: Save Ticket]           [Thread B: Load User]
         │                                 │
         ▼                                 ▼
    [Takes Write Lock]            [Waits for Database]
         │                                 │
         ▼                                 ▼
🔒  [ SQLite Database File ] ◄─────────────┘ (Blocked!)
         │
         ▼ (Held > 3000ms)
    💥 [ HikariPool Connection Timeout: HTTP 500 ]

Issues Diagnosed & Solved
HikariCP Connection Starvation: SQLite locks the entire database file during writes. When filter advices executed concurrent reads, HikariCP exhausted its 30-second lease timeout, crashing active sessions.

DDL Column Alteration Lockout: Alterations adding email and mobile_number columns failed because SQLite does not natively support ALTER TABLE ADD COLUMN ... UNIQUE constraints on existing data sets.

Ephemeral Cloud Storage Wipe: Container platforms such as Render refresh the local filesystem on sleep or redeployment, causing persistent data loss for embedded file databases.

Comparative Resolution Matrix
🛡️ 3. Concurrency Shield: Optimistic Locking
The Concurrency Threat (Silent Write Overwrite)
[Time: 10:00 AM] ──► Agent A loads TKT-001 (Version: 1)  ──────────────┐
                                                                         │
[Time: 10:01 AM] ──► Agent B loads TKT-001 (Version: 1)  ──────┐        │
                                                               │        │
[Time: 10:05 AM] ──► Agent A completes investigation:         │        │
                     Status: CLOSED                            │        │
                     Note: "Refund processed via gateway"      │        │
                     Database Commits ─────────────────────────┼────────┘
                     Status = CLOSED | Version = 2             │
                                                               │
[Time: 10:08 AM] ──► Agent B finishes typing (late):          │
                     Status: IN_PROGRESS                       │
                     Note: "Contacted bank partner"            │
                     Attempting Save ──────────────────────────┘
                               │
            ┌──────────────────┴──────────────────┐
            ▼                                     ▼
   WITHOUT Concurrency Shield            WITH Concurrency Shield
   ──────────────────────────            ───────────────────────
   ❌ Agent A's work is erased!          🛡️ Version mismatch caught!
   Status rewinds to IN_PROGRESS.        Write is blocked instantly.
   Refund audit notes are lost.          Alert: "Conflict Detected".

Technical Solution: JPA @Version
@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", unique = true, nullable = false)
    private String ticketId;

    // Optimistic locking token to intercept write collisions
    @Version
    @Column(name = "version")
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.OPEN;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Note> notes = new ArrayList<>();
}

Concurrency Guard Lifecycle
📧 4. Automated Email Notification Engine

[Customer Ingestion Form] ──► [Ticket Generated: TKT-001] ──► [Receipt Email Dispatched]
                                                                        │
                                                                  (Agent Action)
                                                                        ▼
[Customer Inbox] ◄─── [Resolution Email Dispatched] ◄─── [Agent Closes Ticket]
Dual-Stage Notification Pipeline
Dynamic 12-Factor Configuration (application.properties)
Properties
# Base URL for dynamic notification hyperlinks (Externalized for Cloud Deployments)
app.base-url=${APP_BASE_URL:http://localhost:8080}

# Gmail SMTP Gateway
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=5000
📡 5. REST API Reference
🐳 6. Docker & Render Cloud Deployment
Dockerfile
# Stage 1: Build JAR using Java 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache tzdata
ENV TZ="Asia/Kolkata"

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Kolkata"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
Deploying to Render Cloud

💻 7. Local Installation & Setup

# 1. Database Initialization
# Run in MySQL: CREATE DATABASE crm_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 2. Configure Local Environment Variables
$env:DB_URL="jdbc:mysql://localhost:3306/crm_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_mysql_password"
$env:MAIL_USERNAME="your-email@gmail.com"
$env:MAIL_PASSWORD="your-16-character-app-password"
$env:APP_BASE_URL="http://localhost:8080"

# 3. Compile and Run Application
.\mvnw clean package -DskipTests
.\mvnw spring-boot:run

📂 8. Project Structure

support-desk-crm/
├── Dockerfile                            # Production multi-stage Alpine container build
├── pom.xml                               # Java 21, MySQL, Spring Mail, JPA dependencies
├── src/main/java/com/support/crm/
│   ├── config/                           # SecurityConfig, CustomAuthenticationSuccessHandler
│   ├── controller/
│   │   ├── AdminController.java          # Executive operations metrics & inspection
│   │   ├── AuthController.java           # Agent signup & authenticated session routing
│   │   ├── CustomerPortalController.java # Public intake & reference tracking
│   │   ├── ProfileController.java        # Agent profile management & avatar uploads
│   │   ├── TicketRestController.java     # REST endpoints with HTTP 409 conflict mapping
│   │   └── TicketWebController.java      # Thymeleaf UI controller & SSE stream dispatcher
│   ├── dto/                              # Request & response data transfer objects
│   ├── model/
│   │   ├── Note.java                     # Relational chronological timeline notes
│   │   ├── Ticket.java                   # Core ticket entity with JPA @Version shield
│   │   ├── TicketAuditLog.java           # Operational audit event logging
│   │   ├── TicketStatus.java             # OPEN, IN_PROGRESS, RESOLVED, CLOSED
│   │   └── User.java                     # Operator profile entity
│   ├── repository/
│   │   ├── NoteRepository.java           # Internal note persistence
│   │   ├── TicketAuditLogRepository.java # Audit event queries
│   │   ├── TicketRepository.java         # Dynamic JPQL queries & keyword filters
│   │   └── UserRepository.java           # User entity data access
│   └── service/
│       ├── CustomUserDetailsService.java # Spring Security user details provider
│       ├── NotificationService.java      # Real-time Gmail SMTP notification engine
│       ├── SseNotificationService.java    # Multi-client live SSE event sync
│       └── TicketService.java            # Deduplication, auto-claim & SLA lifecycle logic
└── src/main/resources/
    ├── application.properties            # 12-Factor externalized environment bindings
    └── templates/                     
        ├── auth/                         # Login & Agent registration templates
        ├── landing.html                  # Motion-based landing page (by silent_coder)
        ├── portal/                       # Public ticket intake & live tracking
        ├── profile/                      # Operator profile & security controls
        └── tickets/                      # Queue dashboard, ticket detail & admin console
🌐 Live Application & Endpoints
Root Landing Portal: https://support-desk-crm-qs00.onrender.com/

Customer Intake Portal: https://support-desk-crm-qs00.onrender.com/portal/inquiry

Live Ticket Tracking: https://support-desk-crm-qs00.onrender.com/portal/track

Operator Queue Console: https://support-desk-crm-qs00.onrender.com/tickets

Create Ticket (Operator Console): https://support-desk-crm-qs00.onrender.com/tickets/new

Operator Profile & Settings: https://support-desk-crm-qs00.onrender.com/profile

🔐 Authentication & Administration Endpoints
Operator Sign In: https://support-desk-crm-qs00.onrender.com/login

Agent Enrolment / Sign Up: https://support-desk-crm-qs00.onrender.com/signup

Executive Admin Dashboard: https://support-desk-crm-qs00.onrender.com/admin/dashboard

Admin Full Ticket Registry: https://support-desk-crm-qs00.onrender.com/admin/tickets (Fallback alias: /tickets/admin/all-tickets)

🔑 System Roles & Access Matrix
📄 License
This software is distributed under the MIT License. Built with precision by silent_coder.