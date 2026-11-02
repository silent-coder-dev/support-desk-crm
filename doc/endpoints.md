# Endpoints

## Customer

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Landing page |
| GET, POST | `/portal/inquiry` | Submit a request |
| GET, POST | `/portal/track` | Track a request with reference and email |

## Login

| Method | Path | Purpose |
|---|---|---|
| GET | `/admin-login` | Admin sign-in page |
| GET | `/staff-login` | Staff sign-in page |
| POST | `/login` | Handles both sign-in forms |
| GET | `/login` | Redirects to `/staff-login` |
| POST | `/logout` | Sign out |
| GET, POST | `/signup` | Only works when `APP_SIGNUP_ENABLED=true` |

## Staff and admin

| Method | Path | Purpose |
|---|---|---|
| GET | `/tickets` | Queue |
| GET | `/tickets/{id}` | Ticket details |
| GET | `/tickets/live-stream` | Live updates |
| GET, POST | `/tickets/new` | Log a ticket for a customer |
| POST | `/tickets/{id}/update` | Update status and notes |
| POST | `/tickets/{id}/claim` | Claim a ticket |
| POST | `/tickets/{id}/resolve` | Close a ticket |
| GET | `/profile` | Own profile |
| POST | `/profile/details` | Update own details |
| POST | `/profile/avatar` | Change photo |
| POST | `/profile/change-password` | Change password |

## Admin only

| Method | Path | Purpose |
|---|---|---|
| GET | `/admin/dashboard` | Dashboard |
| GET | `/admin/tickets`, `/admin/all-tickets` | All tickets |
| GET | `/admin/tickets/{id}` | Read-only ticket view |
| GET | `/admin/staff` | Staff list |
| GET | `/admin/staff/new` | Add staff form |
| POST | `/admin/staff` | Create a staff login |
| POST | `/admin/staff/{id}/disable` | Block a login |
| POST | `/admin/staff/{id}/enable` | Restore a login |
| POST | `/admin/staff/{id}/reset-password` | New temporary password |
| GET | `/tickets/admin/all-tickets` | Old link, shows all tickets |
| GET | `/tickets/admin/dashboard` | Old link, redirects to the dashboard |
| GET | `/tickets/admin/{id}` | Old link, redirects to the ticket view |

## REST API (staff and admin)

| Method | Path | Purpose |
|---|---|---|
| GET, POST | `/api/tickets` | List or create tickets |
| GET, PUT | `/api/tickets/{id}` | Read or update a ticket |
| POST | `/api/tickets/{id}/claim` | Claim a ticket |
| POST | `/api/tickets/{id}/resolve` | Close a ticket |
| GET | `/api/tickets/csrf` | CSRF token for write requests |

## Health check

| Method | Path | Purpose |
|---|---|---|
| GET | `/actuator/health` | Health check, open to anyone |
