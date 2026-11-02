# The Problem, How I Tackled It, and the Solution

## The problem in one line

When customer support runs on a shared inbox or a shared spreadsheet, requests get lost, two people work on the same issue, and customers have no idea what is happening.

## Who it hurts

| Person | What goes wrong |
|---|---|
| **Customer** | Sends a complaint and hears nothing. Sends it again because there is no way to check progress. |
| **Support agent** | Doesn't know who is handling what. Two agents reply to the same customer, or one overwrites the other's work. |
| **Team lead** | Can't answer basic questions: how many tickets are open, who is overloaded, what happened on a ticket. |

## The specific problems, and what I did about each

### 1. Customers had no simple way to ask for help or follow up

- **Problem:** Asking for login-gated support adds friction, and customers with no visibility send the same complaint repeatedly.
- **How I tackled it:** I made a public portal that needs no account, and gave every request a readable reference like `TKT-001`.
- **Solution:** The customer submits a form, gets a reference by email, and tracks progress on `/portal/track`.

### 2. Duplicate complaints flooded the queue

- **Problem:** The same customer submitting the same issue twice creates double work.
- **How I tackled it:** Before creating a ticket, I compare the new request with that customer's active tickets.
- **Solution:**
  - Same email, subject and description: return the existing ticket, create nothing new.
  - Same email and subject but new details: add them to the existing ticket as a follow-up note.
  - Anything else: create a new ticket.

### 3. Two agents could silently overwrite each other

- **Problem:** Agent A loads a ticket, Agent B loads the same ticket. A closes it with a refund note. B saves a few minutes later from the stale page, and the ticket moves back to "In Progress" with no warning.
- **How I tackled it:** I rejected locking the ticket while someone is reading it, because agents read far more than they write and a lock would block them. Conflicts are rare, so it is better to detect them at save time.
- **Solution:** Optimistic locking with JPA `@Version`. The second save is rejected and the agent sees a "Conflict detected" message. The REST API returns HTTP 409.

### 4. Agents had to keep refreshing to see new work

- **Problem:** A manual refresh delays the first response to a customer.
- **How I tackled it:** The server only needs to push small signals to browsers in one direction, so Server-Sent Events fit better than WebSockets or polling.
- **Solution:** When a ticket changes, every connected agent's queue refreshes by itself.

### 5. Customers heard nothing after submitting

- **Problem:** A ticket being saved in a database tells the customer nothing. Sending email during the request also made ticket creation slow.
- **How I tackled it:** I send email on a background thread, and only after the database save has succeeded.
- **Solution:** A confirmation email goes out on creation and a resolution email when the ticket closes, both with a tracking link.

### 6. SQLite could not handle real concurrent use

- **Problem:** SQLite locks the whole database file during a write. Under concurrent traffic this caused connection timeouts and HTTP 500 errors. Its file also disappears when a cloud container restarts.
- **How I tackled it:** I moved to a server database with row-level locking, hosted outside the container.
- **Solution:** MySQL 8 (InnoDB) with a HikariCP connection pool.

### 7. Team leads were working blind

- **Solution:** An admin dashboard with live counts, new vs resolved over 14 days, average time to resolve, the tickets waiting longest, agent workload, and an audit trail of who did what and when.

## Making it production ready

A review of the whole codebase found that the features worked, but the app was not safe to run in public. These were the real problems, most serious first.

| # | What was wrong | What could happen | What it does now |
|---|---|---|---|
| 1 | `admin / admin123` was created automatically, and the password was printed in the logs and visible in the source code | Anyone could log in as admin | The password comes from the `ADMIN_PASSWORD` setting. An existing `admin123` is replaced at startup. |
| 2 | Anyone could read any ticket with `/portal/track?ref=TKT-001`, and IDs are sequential | A stranger could read every customer's complaint and notes by counting up | Tracking needs the reference **and** the email on the ticket |
| 3 | The live stream was public and sent the full ticket, including name and email | Anyone could silently collect customer data | The stream needs a login and sends only the ticket ID |
| 4 | Anyone could sign up as an agent | Instant access to all customer tickets | Public signup is off. The admin creates staff logins, and staff and admins have separate login pages. |
| 5 | CSRF protection was disabled, and logout was a plain link | Another website could trigger actions using an agent's session | CSRF is on for every form and API write. Logout is a POST. |
| 6 | Email links always pointed to `localhost:8080` | Customers received broken links | Links use the `APP_BASE_URL` setting |
| 7 | The resolution email was written but never called | Customers were never told a ticket was closed | It is sent exactly once, when a ticket first closes |
| 8 | Ticket numbers came from `count() + 1` | Two customers at the same moment could get the same number, giving one of them a 500 error | The number comes from the database's unique ID |
| 9 | The update API returned the old version number | The next save would fail with a false conflict | The response now carries the real new version |
| 10 | Live updates and emails fired before the database commit | Agents and customers could be told about a change that was then rolled back | They fire only after a successful commit |
| 11 | Duplicate submissions re-sent the confirmation email | A repeat form submission meant a repeat email | Only a truly new ticket sends one |
| 12 | Missing tickets and bad input gave a raw 500 page | Confusing errors for users, misleading alerts for the team | A friendly error page, and clean JSON errors from the API |
| 13 | The admin dashboard loaded every ticket into memory to count them | It gets slower as data grows | The database does the counting |
| 14 | The only test was in the wrong package and had never been able to run | No safety net at all | 87 automated tests |
| 15 | IDE files, an old build folder and a user's photo were committed to git | A noisy repository, with personal data in it | Removed from tracking and added to `.gitignore` |

The full list of changes is in the git history.

## How the result was verified

- **87 automated tests** pass, covering services, security rules and page rendering.
- **The upgrade path was tested on real MySQL 8.0.46.** The old version ran first and created data, then the new version started on that same database. The old ticket survived, new numbering continued correctly, new indexes were added, and `admin123` stopped working.
- **47 HTTP checks** ran against the running app, including CSRF, a stale-save conflict, the live stream, and file uploads.
- **73 checks in a real browser** ran before the latest login, Brevo and abuse-protection changes. Those changes are covered by automated tests, plus a fake Brevo server, but haven't been clicked through in a browser or sent through real Brevo yet.

## What is still open

These are known limits, not hidden problems.

| Limit | Why it matters | Likely next step |
|---|---|---|
| No rate limiting on login | Someone could guess passwords | Add a limiter, or a proxy such as Cloudflare |
| Public form limits are kept in memory, and IP addresses can be faked | A determined attacker with many addresses can still get through some requests, up to the overall limit | Add a CAPTCHA such as Cloudflare Turnstile |
| Database schema is managed by Hibernate `ddl-auto=update` | It cannot safely handle renames or data changes | Move to Flyway migrations |
| Live updates work on one server only | With two servers, an agent on server B misses events from server A | Use Redis Pub/Sub |
| Failed emails are logged but not retried | A Brevo outage means that customer's email is lost | Use an outbox table with retries |
| Temporary staff passwords aren't forced to change at first sign-in | The admin knows the password until the person changes it | Force a change at first sign-in |
| Disabling an agent leaves their active tickets assigned to them | Those tickets need handing over by hand | Add a reassign action |
| Ticket lists show the newest 500 | Older tickets need search to find | Add real pagination |
| The Docker image has not been built during this work | The Docker daemon was not running on the machine used for testing | Build it once before deploying |

## Handling repeat and abusive requests

| Situation | What happens |
|---|---|
| One person changes every field, many times in a minute | Stopped after 3 per minute, or 10 per hour, from one IP address |
| Same complaint under different names and emails | Stopped after 3 per hour for the same text |
| Many requests to one email, including Gmail tricks like `a.b+1@gmail.com` | Stopped after 5 per hour for that inbox |
| Someone faking many IP addresses | Stopped after 30 new requests per minute across the whole site |
| Bots filling in every field | A hidden trap field catches them, and nothing is saved |
| Same details while the ticket is still open | No new ticket. The page says "You already have an open request" and shows the reference. |
| Same subject with new details while open | Added to the open ticket as a note |
| Same details after the ticket closed | A new ticket, because the problem came back |
| Double click on submit | The button disables, and the server handles one at a time |
| Empty, spaces-only or invalid fields | Refused in the browser and again on the server |
| Guessing references on the tracking page | Stopped after 10 tries per minute |
