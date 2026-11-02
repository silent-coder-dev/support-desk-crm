# Tech Stack and Why We Use It

Versions below are the ones in the current build.

## At a glance

| Layer | Technology | Version |
|---|---|---|
| Language | Java (LTS) | 21 |
| Framework | Spring Boot | 4.1.1 |
| Web | Spring MVC on embedded Tomcat | 7.0.9 / 11.0.24 |
| Security | Spring Security | 7.1.1 |
| Database access | Spring Data JPA with Hibernate | Hibernate 7.4.5 |
| Connection pool | HikariCP | 7.0.2 |
| Database | MySQL (InnoDB) | 8.0 |
| Server-side pages | Thymeleaf | 3.1.5 |
| UI styling | Bootstrap and Bootstrap Icons, with one shared `app.css` | 5.3.3 / 1.11.3 |
| Live updates | Server-Sent Events (Spring `SseEmitter`) | built in |
| Email | Brevo HTTP API, called with Spring's `RestClient` | - |
| Image storage | Cloudinary | 2.0.0 |
| Tests | JUnit 5, Mockito, MockMvc, H2 | managed by Spring Boot |
| Packaging | Docker multi-stage build | Temurin 21 |
| CI | GitHub Actions | - |
| Hosting | Render | - |

## Backend

### Java 21

An LTS release, so it gets long-term updates. It is well supported in containers and gives us records (`TicketEvent`) and `switch` expressions (`TicketEventListener`), which keep the code short.

### Spring Boot

Spring Boot gives us a web server, security, database access, email and health checks that work together with very little setup. For a CRUD app with login and forms, it covers everything in one framework.

- **Trade-off:** It uses more memory and starts slower than a small framework. For this app, developer speed matters more.

### Spring MVC

Requests are handled by one thread each, so the code reads top to bottom. That makes it easier to write and debug than a reactive stack like WebFlux, and this app's traffic doesn't need WebFlux.

### Spring Security

Login, sessions, password hashing, role checks and CSRF protection are hard to get right by hand. Spring Security provides them tested and ready.

### Spring Data JPA and Hibernate

We write `findByTicketId(...)` and get working SQL, which removes a lot of repetitive code. Two features matter most here:

- **`@Version`** gives us optimistic locking for one annotation, and this is the core of the collision protection.
- **`@OneToMany`** maps a ticket to its notes.

**Trade-off:** Lazy loading can cause surprises if you aren't careful. We reduce that risk by setting `open-in-view=false` and converting entities to DTOs inside the transaction.

### HikariCP

Opening a database connection is slow. Hikari keeps a pool of ready connections (up to 10 here). It is Spring Boot's default and needs almost no setup.

## Database

### MySQL 8 (InnoDB)

The project started on SQLite and moved away because of real failures:

| Need | SQLite | MySQL (InnoDB) |
|---|---|---|
| Many agents writing at once | Locks the whole file | Locks only the affected row |
| Survive a container restart | The file is lost | Runs as a separate service |
| Add a `UNIQUE` column to an existing table | Not supported | Supported |

**Why not PostgreSQL?** It would work equally well. The app has nothing MySQL-specific in its code. MySQL was simply the choice for this project.

**Why not MongoDB?** Tickets, notes and users have clear relationships, and we rely on transactions. A relational database is the natural fit.

## Frontend

### Thymeleaf instead of React or Angular

The server builds the full HTML page, so there is no separate frontend project, no API layer between the UI and the logic, and no login tokens to manage in the browser. `th:action` adds CSRF tokens automatically, and `th:text` escapes user input, which blocks most XSS by default.

- **Trade-off:** Pages reload more often than in a single-page app. The queue page reloads when a ticket changes. For an internal support tool, that is acceptable.

### Bootstrap with one shared stylesheet

Bootstrap gives ready-made grids, forms, tables and toasts, which is what a tool like this needs. Our own look sits on top in a single file, `static/css/app.css`, and repeated page pieces live in `templates/fragments/layout.html`. There is no CSS build step, and Node.js is not needed at all.

### No chart library

The admin dashboard's charts are plain HTML and CSS: paired daily columns, a split bar and ranked bars. They are easy to read, load instantly and use no JavaScript. A chart library would make sense once the app needs trends over time.

## Real-time updates

### Server-Sent Events instead of WebSockets or polling

| Option | How it works | Fit here |
|---|---|---|
| **Polling** | The browser asks "anything new?" every few seconds | Wasteful, and updates arrive late |
| **WebSockets** | A two-way channel | More than we need, since browsers have nothing to send back |
| **SSE** | The server pushes over a normal HTTP connection | Exactly our case, and browsers reconnect by themselves |

- **Trade-off:** The list of connected browsers is kept in the server's memory, so this works on a single server. Running several servers would need something like Redis Pub/Sub.

## Integrations

### Brevo HTTP API

Render's free plan blocks SMTP ports, so the app sends email over HTTPS with Brevo's API instead. The free plan is enough for this project.

- **Trade-off:** a failed email is logged but not retried, and the sender address must be verified in Brevo first.

### Cloudinary

Containers lose their local files when they restart, so avatars saved to disk would disappear. Cloudinary stores them permanently and serves them from a CDN, and the free tier is enough for this project.

## Build and deployment

### Docker multi-stage build

The first stage has Maven and the full JDK to build the app. The final image contains only the JRE and the jar, so it is much smaller. It runs as a non-root user and reports its health through `/actuator/health`.

### Environment variables for configuration

Passwords and addresses live outside the code, following the 12-factor app approach. The same image runs anywhere with different settings, and no secret ever goes into git. See [.env.example](../.env.example) for the full list.

### Actuator

Exposes only `/actuator/health`, so Render and Docker can tell if the app is alive. Email is left out of the check on purpose, so a Brevo problem doesn't get a healthy app restarted.

### H2 for tests

Tests run on an in-memory database, so they need no MySQL server and work anywhere, including CI. The real MySQL behaviour was checked separately by hand.

### Lombok

Generates getters, setters, constructors and builders, which keeps entities and DTOs short.

### GitHub Actions

Runs all tests on every push, so a broken change is caught before it is deployed.

## What we removed, and why

| Removed | Why |
|---|---|
| `sqlite-jdbc`, `hibernate-community-dialects` | Left over from the SQLite days. Removing them cut the jar from 78 MB to 66 MB. |
| Duplicate `spring-boot-starter-security` | Declared twice in `pom.xml` |
| `@CrossOrigin("*")` on the API | Nothing outside this site calls the API, so any-origin access was an unnecessary risk |
| `WebMvcConfig` and the `/uploads` folder handler | Avatars moved to Cloudinary. The old handler's path was also wrong on Linux. |
| Tailwind, Lucide and Chart.js | The UI redo moved everything to one design on Bootstrap, so three extra libraries and a build step went away |
