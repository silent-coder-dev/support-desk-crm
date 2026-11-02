# 🛡️ SupportDesk CRM

### Customer Ticketing with Collision Protection, Live Queues and Automatic Email Updates

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot 4](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL 8.0](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Multi--Stage-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![Bootstrap 5.3](https://img.shields.io/badge/Bootstrap-5.3-7952B3?style=for-the-badge&logo=bootstrap&logoColor=white)](https://getbootstrap.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)

Customers raise and track support requests without an account. Agents work from a live queue that protects them from overwriting each other. Admins see workload and activity at a glance. Built by silent_coder.

## Documentation

| Document | What's in it |
|---|---|
| [doc/problem.md](doc/problem.md) | The problem, how it was tackled, the solution, and known limits |
| [doc/techstack.md](doc/techstack.md) | Every technology and why it was chosen |
| [doc/design.md](doc/design.md) | Fonts, colors, components and pages |
| [doc/interview.md](doc/interview.md) | Every concept and method explained in Hinglish |

## Features

- **No-login customer portal:** submit a request and get a reference like `TKT-001`.
- **Private tracking:** progress is visible only with the reference and the email on the ticket.
- **Duplicate detection:** a repeat submission returns the same ticket, and new details become a follow-up note.
- **One person per ticket:** once a staff member is handling a ticket, others can only read it. If two people save at the same moment, the later save is rejected.
- **Abuse protection:** the public form limits repeat requests by IP, email and text, and stops bots with a hidden field.
- **Live queue:** agents' screens update by themselves through Server-Sent Events.
- **Automatic emails:** customers are emailed when a ticket is created, updated and resolved. Emails never say who changed it or what they wrote.
- **Staff management:** admins add agents and other admins, disable accounts, and reset passwords.
- **Admin dashboard:** key numbers, new vs resolved over 14 days, average time to resolve, the tickets waiting longest, agent workload, and an audit trail.

## Quick start

**You need:** Java 21 and MySQL 8.

**1. Create the database:**

```sql
CREATE DATABASE crm_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

**2. Set your settings** in PowerShell:

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/crm_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_mysql_password"
$env:ADMIN_PASSWORD="choose_a_password"
```

**3. Run with the `dev` profile:**

```powershell
cd support-crm
.\mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```

**4. Open** http://localhost:8080/admin-login and sign in as `admin` with the password from step 2. Customers use http://localhost:8080, and staff use http://localhost:8080/staff-login.

> Without the `dev` profile, login over plain `http://` will fail, because production settings only send the session cookie over HTTPS.

**Run the tests** (no MySQL needed):

```powershell
.\mvnw test
```

## Configuration

The full list is in [.env.example](.env.example).

| Variable | Required | Purpose |
|---|---|---|
| `DB_URL`, `DB_PASSWORD` | Yes | MySQL connection. The app won't start without them. |
| `DB_USERNAME` | No | MySQL user. Defaults to `root`. |
| `ADMIN_PASSWORD` | First run | Creates the `admin` login. Also replaces the old `admin123` password on an existing database. |
| `APP_BASE_URL` | Production | Public address used for links in emails |
| `BREVO_API_KEY`, `MAIL_FROM_EMAIL`, `MAIL_FROM_NAME` | For email | Brevo API key and a sender verified in Brevo. Uses Brevo's HTTP API, which works on Render's free plan. Without them, emails are skipped. |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | For avatars | Photo uploads show an error without them |
| `APP_SIGNUP_ENABLED` | No | Defaults to `false`. When `true`, **anyone** can register as an agent and read all customer tickets. |
| `JPA_DDL_AUTO` | No | Hibernate schema mode. Defaults to `update`. |

## Deploying

The [Dockerfile](Dockerfile) builds from the repository root.

```bash
docker build -t support-desk-crm .
docker run -p 8080:8080 --env-file .env support-desk-crm
```

**On Render:**

1. Create a Web Service from this repository, using Docker.
2. Add the variables above under **Environment**, including `ADMIN_PASSWORD` and `APP_BASE_URL`.
3. Set the health check path to `/actuator/health`.

The database must run outside the container, since a container's files don't survive a restart.

## Who can do what

| Action | Public | Agent | Admin |
|---|---|---|---|
| Submit a ticket, track own ticket | ✅ | ✅ | ✅ |
| View the queue, update and claim tickets | | ✅ | ✅ |
| Create tickets for a customer | | ✅ | ✅ |
| Change own profile and password | | ✅ | ✅ |
| Admin dashboard and full registry | | | ✅ |
| Add, disable and reset staff | | | ✅ |

**Adding staff:** an admin opens **Staff** in the sidebar and adds the person. The app creates a temporary password, shown once, for the admin to share privately. Admins can also disable an account, which signs the person out straight away, or reset a password.

## REST API

The API uses the same login session as the website. Write requests must also send the CSRF token from `GET /api/tickets/csrf`, in the `X-CSRF-TOKEN` header.

| Method | Path | Purpose | Success | Errors |
|---|---|---|---|---|
| `POST` | `/api/tickets` | Create a ticket | 201 | 400 |
| `GET` | `/api/tickets?status=OPEN&search=text` | List newest tickets | 200 | 400 |
| `GET` | `/api/tickets/{id}` | Ticket with notes | 200 | 404 |
| `PUT` | `/api/tickets/{id}` | Update status and notes. `version` is required. | 200 | 400, 404, 409 |
| `POST` | `/api/tickets/{id}/claim` | Claim a ticket | 200 | 404, 409 |
| `POST` | `/api/tickets/{id}/resolve` | Close a ticket | 200 | 404 |

Without a login, the API returns 401. A `409` on `PUT` means someone else changed the ticket first, so reload it and try again.

```json
PUT /api/tickets/TKT-001
{ "status": "IN_PROGRESS", "notes": "Looking into it", "version": 3 }
```

## Project structure

```
├── Dockerfile
├── .env.example               All settings
├── doc/                       Project documentation
└── support-crm/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/support/crm/
        │   │   ├── config/        Admin setup, Cloudinary
        │   │   ├── controller/    Web pages, REST API, error handling
        │   │   ├── dto/           Request and response objects
        │   │   ├── exception/     Custom exceptions
        │   │   ├── model/         Ticket, Note, User, TicketAuditLog
        │   │   ├── repository/    Database queries
        │   │   ├── security/      Login and access rules
        │   │   └── service/       Business logic, email, live updates
        │   ├── resources/
        │   │   ├── templates/     Thymeleaf pages
        │   │   ├── static/        Shared stylesheet and scripts
        │   │   └── application*.properties
        └── test/                  Automated tests
```

## Live application

| Page | Link |
|---|---|
| Landing | https://support-desk-crm-qs00.onrender.com/ |
| Submit a ticket | https://support-desk-crm-qs00.onrender.com/portal/inquiry |
| Track a ticket | https://support-desk-crm-qs00.onrender.com/portal/track |
| Staff sign in | https://support-desk-crm-qs00.onrender.com/staff-login |
| Admin sign in | https://support-desk-crm-qs00.onrender.com/admin-login |

## License

Distributed under the MIT License. Built by silent_coder.

## All endpoints

| Who | Method | Path | Purpose |
|---|---|---|---|
| Customer | GET | `/` | Landing page |
| Customer | GET, POST | `/portal/inquiry` | Submit a request |
| Customer | GET, POST | `/portal/track` | Track with reference and email |
| Admin | GET | `/admin-login` | Admin sign in |
| Staff | GET | `/staff-login` | Staff sign in |
| Both | POST | `/login` | Handles both sign-in forms |
| Both | POST | `/logout` | Sign out |
| Both | GET | `/profile` | Own profile |
| Both | POST | `/profile/details`, `/profile/avatar`, `/profile/change-password` | Update own profile |
| Staff, Admin | GET | `/tickets`, `/tickets/{id}`, `/tickets/live-stream` | Queue, ticket, live updates |
| Staff, Admin | GET, POST | `/tickets/new` | Log a ticket for a customer |
| Staff, Admin | POST | `/tickets/{id}/update`, `/tickets/{id}/claim`, `/tickets/{id}/resolve` | Work on a ticket |
| Staff, Admin | GET, POST, PUT | `/api/tickets`, `/api/tickets/{id}`, `/api/tickets/{id}/claim`, `/api/tickets/{id}/resolve`, `/api/tickets/csrf` | REST API |
| Admin | GET | `/admin/dashboard`, `/admin/tickets`, `/admin/tickets/{id}` | Dashboard and all tickets |
| Admin | GET, POST | `/admin/staff`, `/admin/staff/new` | Staff list, add staff |
| Admin | POST | `/admin/staff/{id}/disable`, `/enable`, `/reset-password` | Manage a staff account |
| Anyone | GET | `/actuator/health` | Health check |
