# SupportDesk CRM: Interview Guide (Hinglish)

Is file ko padhne ke baad aap project ka har method, har concept aur har "kyun" explain kar paoge.

**Kaise padhein:**

1. Part 1 yaad karo, ye aapka introduction hai.
2. Part 2 aur 3 se flow samjho.
3. Part 4 se har class aur method samjho.
4. Part 5 ke concepts ko examples ke saath samjho, ratta mat maro.
5. Interview se ek din pehle Part 6, 7, 8 aur 9 revise karo.

---

# Part 1: Project ka Introduction

## 30 second wala answer

> "Maine SupportDesk CRM banaya hai, ye ek customer support ticketing system hai. Customer bina login ke complaint daal sakta hai aur reference ID se track kar sakta hai. Agents ek live queue mein tickets handle karte hain, aur admin ko dashboard milta hai. Maine ise Spring Boot, MySQL aur Thymeleaf se banaya hai. Sabse interesting part hai optimistic locking, jo do agents ko ek doosre ka kaam overwrite karne se rokta hai, aur Server-Sent Events, jo bina refresh ke queue update karte hain."

## 2 minute wala answer

> "Problem ye thi ki shared inbox mein support karne par complaints kho jaati hain, do log ek hi ticket par kaam karte hain, aur customer ko pata nahi hota kya chal raha hai.
>
> Maine teen roles banaye: customer, agent aur admin. Customer public portal se ticket banata hai. Agar wahi complaint dobara aaye to duplicate nahi banta, existing ticket return hota hai, aur nayi details follow-up note ban jaati hain.
>
> Do agents ek hi ticket edit karein to JPA `@Version` conflict pakad leta hai aur doosre agent ko warning milti hai. Ticket change hote hi Server-Sent Events se sabki queue refresh ho jaati hai. Emails background thread par jaate hain, aur sirf database commit ke baad, taaki rollback hone wale change ka mail na jaaye.
>
> Pehle SQLite tha, par concurrent writes par poori file lock hone se timeouts aate the, isliye MySQL par shift kiya.
>
> Production ke liye maine security review kiya aur kaafi real issues fix kiye: public tracking mein koi bhi kisi ka ticket padh sakta tha, default admin password code mein tha, aur CSRF band tha. Ab 87 automated tests hain, aur upgrade path maine real MySQL par test kiya hai."

---

# Part 2: Architecture

## Layers

```
Browser (Thymeleaf HTML, Bootstrap, JS)
        |
        |  HTTP request
        v
Spring Security Filter Chain   <-- login, role check, CSRF yahan
        |
        v
Controller      <-- request leta hai, response deta hai
        |
        v
Service         <-- business logic + @Transactional
        |
        v
Repository      <-- database queries
        |
        v
MySQL
```

**Layers kyun?** Har layer ka ek hi kaam hai.

**Example:** Kal Thymeleaf hata kar React lagana ho, to sirf Controller layer badlegi. Dedupe logic Service mein hi rahega. Isi ko **Separation of Concerns** kehte hain.

## Folder structure

| Folder | Kaam |
|---|---|
| `config/` | App setup: `AdminBootstrap`, `CloudinaryConfig` |
| `controller/` | HTTP requests handle karna |
| `dto/` | Request aur response ka data shape |
| `exception/` | Custom exception |
| `model/` | Database tables (entities) |
| `repository/` | Database queries |
| `security/` | Login aur permission rules |
| `service/` | Business logic |
| `templates/` | HTML pages |

---

# Part 3: Main Flows (Step by Step)

## Flow 1: Customer ticket banata hai

```
1. Browser: POST /portal/inquiry (form + _csrf token)
2. CsrfFilter: token check. Galat hua to 403.
3. CustomerPortalController.submitInquiry(): @Valid se validation
4. TicketService.createTicket(): @Transactional shuru
     a. Is email ke active tickets nikaalo
     b. Same subject + description?   -> wahi ticket return (kuch nahi banta)
     c. Same subject, nayi details?   -> follow-up Note add karo
     d. Warna: naya Ticket save, ID se TKT-005 banao
     e. TicketEvent publish karo (abhi kuch hota nahi)
5. Transaction COMMIT
6. TicketEventListener chalta hai:
     - SSE: sab agents ko signal (background thread)
     - Email: customer ko (background thread)
7. Controller: redirect, customer ko reference dikhta hai
```

## Flow 2: Agent ticket update karta hai (conflict ke saath)

```
10:00  Agent A ticket kholta hai  -> form mein version=3
10:01  Agent B ticket kholta hai  -> form mein version=3
10:05  Agent A CLOSE karta hai    -> version match, save, DB mein version=4
10:08  Agent B save karta hai     -> 3 != 4, exception!
       Agent B ko dikhta hai: "Conflict detected"
       Agent A ka kaam safe hai
```

## Flow 3: Live update

```
1. Agent ki dashboard page load hoti hai
2. JS: new EventSource('/tickets/live-stream')
3. Server: SseEmitter banake list mein rakhta hai (connection khula rehta hai)
4. Koi ticket change hua -> commit -> broadcast()
5. Har connected browser ko "TICKET_UPDATED" + ticket ID milta hai
6. JS: window.location.reload() -> page secured route se fresh data laati hai
```

**Agent type kar raha ho to?** Page reload nahi hota, warna likha hua gayab ho jaata. Status mein "New updates" dikhta hai.

**Sirf ID kyun bhejte hain?** Purane code mein poora ticket jaata tha, customer ka email bhi. Aur stream public tha, to koi bhi data collect kar sakta tha. Ab stream ko login chahiye, aur sirf ID jaati hai.

## Flow 4: Login

```
1. POST /login (username, password, _csrf)
2. UsernamePasswordAuthenticationFilter request pakadta hai
3. Hamara UserDetailsService DB se user laata hai
4. BCrypt password match karta hai
5. Sahi hua: user session mein save, JSESSIONID cookie browser ko
6. CustomAuthenticationSuccessHandler: admin -> /admin/dashboard, agent -> /tickets
7. Galat hua: /login?error=true
```

---

# Part 4: Har Class aur Har Method

## 4.1 SupportCrmApplication

```java
@SpringBootApplication
@EnableAsync
public class SupportCrmApplication {
    public static void main(String[] args) {
        SpringApplication.run(SupportCrmApplication.class, args);
    }
}
```

| Cheez | Matlab |
|---|---|
| `main()` | App ka entry point. Tomcat start hota hai, saare beans bante hain. |
| `@SpringBootApplication` | Teen annotations ka combo: `@Configuration`, `@EnableAutoConfiguration`, `@ComponentScan` |
| `@ComponentScan` | Is package ke neeche saari classes scan karta hai |
| `@EnableAsync` | `@Async` ko chalu karta hai. Iske bina `@Async` ignore ho jaata hai. |

**Real bug:** Purana test `com.supportcrm` package mein tha aur main class `com.support.crm` mein. Spring test ke package se upar configuration dhoondhta hai, isliye mila hi nahi aur test kabhi chala hi nahi. Yaad rakho: test ka package main class ke package ke andar hona chahiye.

## 4.2 config package

### AdminBootstrap

Startup par admin account ensure karta hai. `CommandLineRunner` interface ka matlab: app start hone ke baad `run()` ek baar chalega.

| Method | Kya karta hai | Example |
|---|---|---|
| `run()` | Admin hai to `replaceOldPassword`, nahi hai to `createAdmin` | Pehli baar deploy par admin ban jaata hai |
| `createAdmin()` | `ADMIN_PASSWORD` set ho to admin banata hai, warna warning log karta hai | Env var nahi? Koi default password nahi banega |
| `replaceOldPassword()` | Purana `admin123` hai to naye password se badal deta hai | Purane DB par deploy karte hi admin123 band |

**`ifPresentOrElse` example:**

```java
userRepository.findByUsername("admin")
    .ifPresentOrElse(this::replaceOldPassword, this::createAdmin);
```

Mila to pehla method, nahi mila to doosra. Bina `if-else` ke saaf code.

**Password log kyun nahi karte?** Logs bahut logon ko dikhte hain aur kai jagah store hote hain. Purana code `admin / admin123` print karta tha.

### CloudinaryConfig

`cloudinary()` method ek `Cloudinary` object ko **bean** banata hai. `@Configuration` aur `@Bean` ka matlab: is object ko Spring manage karega, aur jahan chahiye inject karega. `CLOUDINARY_URL` diya ho to wo use hota hai, warna teen alag settings.

## 4.3 model package (Entities)

Entity matlab ek Java class jo database table se judi hai. Ek object = ek row.

### Ticket (sabse important)

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;              // MySQL auto-increment

@Column(name = "ticket_id", unique = true, nullable = false)
private String ticketId;      // TKT-001, customer ko yahi dikhta hai

@Enumerated(EnumType.STRING)
private TicketStatus status;  // DB mein "OPEN" text, number nahi

@Version
private Long version;         // optimistic locking

@OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("createdAt ASC")
private List<Note> notes;
```

| Annotation | Kaam | Example |
|---|---|---|
| `@Entity` | Ye class ek table hai | |
| `@Table(indexes=...)` | Table naam aur indexes | `status` par index |
| `@Id` | Primary key | |
| `IDENTITY` | DB khud ID deta hai | Insert hote hi ID mil jaati hai |
| `@Enumerated(STRING)` | Enum text mein save | Kal naya status beech mein add kiya to purana data nahi bigdega. `ORDINAL` mein bigad jaata. |
| `@Version` | Har update par +1 | Conflict pakadne ke liye |
| `@CreationTimestamp` | Banate waqt time set | |
| `@UpdateTimestamp` | Har update par time set | |
| `@OneToMany` | Ek ticket, kai notes | |
| `mappedBy` | Foreign key `Note` table mein hai | |
| `cascade = ALL` | Ticket save/delete to notes bhi | |
| `orphanRemoval` | List se hata to DB se bhi delete | |
| `@OrderBy` | Notes hamesha purane se naye | Timeline sahi dikhti hai |
| `@Builder.Default` | Builder mein bhi default value rakho | Neeche dekho |

**Real bug (`@Builder.Default`):**

```java
private List<Note> notes = new ArrayList<>();
```

Lombok `@Builder` is `= new ArrayList<>()` ko ignore karta hai, isliye builder se bane ticket mein `notes` **null** hota tha. `@Builder.Default` lagane se fix hua.

### Note

Ek ticket ka comment. `@ManyToOne(fetch = LAZY)`: note load hone par ticket tab tak nahi aata jab tak zaroorat na ho. `@JsonIgnore`: JSON banate waqt ticket skip karo, warna ticket se note, note se ticket, infinite loop.

### User

Agent ya admin. Password BCrypt hash ke roop mein save hota hai.

**`@ToString.Exclude` password par kyun?** Kisi ne `log.info(user)` likha to hash logs mein chala jaata. Ye rok deta hai.

### TicketAuditLog

Kisne, kya, kab kiya, ye record karta hai. Example: `RESOLVED_TICKET`, `TKT-005`, `Agent One`, 10:30. Admin dashboard ke "Recent Activity" mein dikhta hai.

### TicketStatus

Enum: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`. Enum se galat value ("OPENN") compile ya request time par hi pakdi jaati hai. `RESOLVED` aur `CLOSED` dono ko code "finished" maanta hai.

## 4.4 repository package

Sirf interface likhte hain, implementation Spring khud banata hai.

```java
public interface TicketRepository extends JpaRepository<Ticket, Long> { ... }
```

`JpaRepository` se free mein milta hai: `save`, `saveAndFlush`, `findById`, `findAll`, `count`, `deleteAll`.

| Repository | Table | Note |
|---|---|---|
| `TicketRepository` | `tickets` | Sabse zyada queries yahin hain |
| `UserRepository` | `users` | Login aur signup checks |
| `TicketAuditLogRepository` | `ticket_audit_logs` | Activity feed |
| `NoteRepository` | `notes` | Koi custom query nahi, free wale methods hi kaafi hain |

### Derived queries (method naam se query)

| Method | Spring kya banata hai | Kahan use |
|---|---|---|
| `findByTicketId(id)` | `WHERE ticket_id = ?` | Ticket kholna |
| `findByTicketIdAndCustomerEmailIgnoreCase(id, email)` | `WHERE ticket_id = ? AND lower(email) = lower(?)` | Public tracking |
| `countByStatus(status)` | `SELECT COUNT(*) WHERE status = ?` | Admin dashboard |
| `findByUsername(name)` | `WHERE username = ?` | Login |
| `existsByUsername(name)` | Sirf true/false | Signup check |
| `existsByEmail(email)` | Sirf true/false | Signup check |
| `existsByMobileNumber(no)` | Sirf true/false | Signup check |
| `findAllByOrderByFullnameAsc()` | Sab users, naam ke order mein | Staff page |
| `existsByEmailAndIdNot(email, id)` | Ye email kisi aur ka to nahi? | Profile edit: apna email duplicate na gine |
| `existsByMobileNumberAndIdNot(no, id)` | Same, mobile ke liye | Profile edit |
| `countByAssignedAgentIdAndStatusIn(id, statuses)` | Is agent ke itne tickets | Profile page |
| `countActiveTicketsPerAgentId()` | Har agent ke active tickets, `GROUP BY` se | Staff page |
| `findTop5ByUserIdOrderByTimestampDesc(id)` | Is user ki latest 5 activities | Profile page |
| `findTop8ByOrderByTimestampDesc()` | `ORDER BY timestamp DESC LIMIT 8` | Activity feed |
| `countByStatusAndAssignedAgentIdIsNull(status)` | `COUNT(*) WHERE status = ? AND assigned_agent_id IS NULL` | Kitne tickets unclaimed hain |
| `findTop5ByStatusAndAssignedAgentIdIsNullOrderByCreatedAtAsc(status)` | Sabse purane 5 unclaimed tickets | "Waiting longest" list |

### @Query (JPQL)

Jab query lambi ho. JPQL table ki jagah class par chalta hai (`Ticket`, `tickets` nahi).

| Method | Kaam |
|---|---|
| `findActiveTicketsByCustomerEmail(email)` | Customer ke OPEN aur IN_PROGRESS tickets, dedupe ke liye |
| `searchTickets(status, search, pageable)` | Filter aur search, sabse naye pehle |
| `countTicketsPerAgent()` | Har agent ke active aur resolved tickets, `SUM(CASE WHEN ...)` aur `GROUP BY` se |
| `countCreatedPerDaySince(since)` | Roz kitne naye tickets, `CAST(t.createdAt AS LocalDate)` se din nikalta hai |
| `countResolvedPerDaySince(since)` | Roz kitne resolve hue |
| `averageResolutionSecondsSince(since)` | `AVG((t.resolvedAt - t.createdAt) BY SECOND)`, resolve hone ka average time |

**`:status IS NULL OR t.status = :status` trick:** Filter optional hai. Status null hai to condition hamesha true, yaani sab tickets. Ek hi query se dono cases handle.

**`Pageable` kyun?** `PageRequest.of(0, 500)` matlab sirf 500 rows. Warna 1 lakh tickets aane par sab memory mein aa jaate.

**SQL Injection se safe kaise?** `:search` ek parameter hai. Koi `' OR 1=1 --` likhe to wo sirf text ki tarah search hoga, SQL ki tarah nahi chalega.

## 4.5 dto package

**DTO kya hai?** Data Transfer Object: API ya form ke liye alag data shape.

**Entity seedha kyun nahi bhejte?**

1. **Security:** `User` entity bhejne par password hash bhi chala jaata.
2. **Mass assignment:** Signup mein `User` entity bind karte to koi `role=ROLE_ADMIN` bhej ke admin ban jaata. `RegisterRequest` mein `role` field hi nahi hai. Ye test bhi hai: `signupCreatesAgentNeverAdmin`.
3. **Lazy loading:** Transaction ke bahar entity ke `notes` access karne par exception aata.

| DTO | Kaam |
|---|---|
| `CreateTicketRequest` | Ticket form ka data |
| `UpdateTicketRequest` | Status, note aur version |
| `RegisterRequest` | Signup form |
| `ChangePasswordRequest` | Password change form |
| `CreateStaffRequest` | Admin ka "Add staff" form |
| `UpdateProfileRequest` | Profile ke name, email, mobile |
| `CreateTicketResponse` | Ticket ID aur time |
| `TicketSummaryResponse` | List ki ek row |
| `TicketDetailResponse` | Poora ticket, `NoteDto` ke saath |
| `AdminAnalyticsResponse` | Dashboard ke numbers |

### Validation annotations

| Annotation | Matlab | Example |
|---|---|---|
| `@NotBlank` | Khali ya sirf spaces nahi | `"   "` reject |
| `@Email` | Email format | `abc` reject |
| `@Size(max=255)` | Lambai limit | 300 character subject reject |
| `@Pattern` | Regex match | Mobile: `^[0-9]{10}$` |
| `@NotNull` | Null nahi | `version` zaroori |

Ye tab chalte hain jab controller mein `@Valid` likha ho.

**`@Size(max=255)` kyun add kiya?** Column `VARCHAR(255)` hai. Lamba subject aata to MySQL error se 500 page aata. Ab user ko saaf message milta hai.

**Password regex samjho:**

```
^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&#])[A-Za-z\d@$!%*?&#]{8,72}$
```

- `(?=.*[a-z])`: kam se kam ek chhota letter (lookahead)
- `(?=.*[A-Z])`: ek bada letter
- `(?=.*\d)`: ek number
- `(?=.*[@$!%*?&#])`: ek special character
- `{8,72}`: 8 se 72 characters

**72 kyun?** BCrypt sirf 72 bytes tak hash kar sakta hai.

## 4.6 service package

### TicketService (dil is project ka)

| Method | Kaam |
|---|---|
| `createTicket(request)` | Dedupe, follow-up, ya naya ticket |
| `getAllTickets(status, search)` | Filter ke saath list |
| `getTicketByTicketId(id)` | Agent ke liye poora ticket |
| `findTicketForCustomer(id, email)` | Customer ke liye, email match par hi |
| `updateTicket(id, request, username)` | Status, note, auto-claim, version check |
| `claimTicket(id, username)` | Agent ticket apne naam karta hai |
| `resolveTicket(id, username)` | Ticket close |
| `findTicket(id)` | Na mile to `TicketNotFoundException` |
| `findAgent(username)` | Na mile to `IllegalArgumentException` |
| `isFinished(status)` | RESOLVED ya CLOSED? |
| `changeStatus(ticket, status)` | Status set, `resolvedAt` sambhale, true return kare jab ticket abhi close hua |
| `logAudit(id, agent, action)` | Audit row save |
| `publishEvent(type, ticket)` | `TicketEvent` publish |
| `toDetailResponse(ticket)` | Entity se DTO |

#### createTicket example

```
Pehle se ticket: aarav@x.com, "Login broken", "Cannot log in"

Input 1: aarav@x.com, "LOGIN BROKEN", "cannot log in"
  -> Same ticket return. Naya ticket nahi, naya email nahi.

Input 2: aarav@x.com, "Login broken", "Now error 500"
  -> Same ticket par follow-up note: "Now error 500"

Input 3: aarav@x.com, "Refund", "Paise wapas chahiye"
  -> Naya ticket
```

#### Ticket number ki kahani

```java
// Purana code (BUG):
long nextIndex = ticketRepository.count() + 1;  // dono requests ko 5
// Dono TKT-005 banate. Doosre ko unique constraint error, HTTP 500.

// Naya code:
Ticket saved = ticketRepository.save(... "PENDING-" + UUID ...);
saved.setTicketId(String.format("TKT-%03d", saved.getId()));
```

Database ka auto-increment ID kabhi repeat nahi hota, chahe 100 requests ek saath aayein. `IDENTITY` mein insert turant hota hai, isliye ID usi waqt mil jaati hai. `%03d` matlab kam se kam 3 digit: 5 ban jaata hai `005`.

**Side effect:** Naya ticket version 1 se shuru hota hai, 0 se nahi, kyunki insert ke baad ticketId update hota hai.

#### updateTicket: `save` nahi, `saveAndFlush` kyun?

**Real bug:** `save()` ke baad version turant nahi badhta, wo commit par badhta hai. Response mein purana version chala jaata tha, aur client agli baar us version se save karta to **jhootha conflict** aata. `saveAndFlush()` SQL abhi chala deta hai, isliye sahi version milta hai. Test: `updateReturnsTheNewVersionSoNextSaveDoesNotFalselyConflict`.

#### `setUpdatedAt` manually kyun?

Note add karne se `Ticket` table ka koi column nahi badalta (foreign key `notes` table mein hai). Hibernate ko lagta ticket change hi nahi hua, aur version bhi nahi badhta. `setUpdatedAt` ticket ko "dirty" bana deta hai, to version badhta hai aur baaki agents ko pata chalta hai.

#### changeStatus: email sirf ek baar

```java
boolean wasFinished = isFinished(ticket.getStatus());
boolean nowFinished = isFinished(newStatus);
...
return nowFinished && !wasFinished;
```

| Pehle | Ab | Return | Email? |
|---|---|---|---|
| OPEN | CLOSED | true | Haan |
| CLOSED | CLOSED | false | Nahi |
| CLOSED | OPEN | false | Nahi, `resolvedAt` clear |

### AdminDashboardService

Dashboard ka saara logic yahan hai, taaki `TicketService` chhota rahe aur ticket ke kaam par focus kare.

| Method | Kaam |
|---|---|
| `getAdminAnalytics()` | Saare dashboard numbers ek `AdminAnalyticsResponse` mein |
| `getRecentAuditLogs()` | Latest 8 activities |
| `averageResolutionTime()` | Last 30 din ka average, jaise `22h 34m` |
| `agentLoads()` | Har agent ke active aur resolved, busiest pehle |
| `trend()` | Last 14 din, har din ek entry |
| `toDayMap(rows)` | Query rows ko din ke map mein badalta hai |
| `waitingTickets()` | Sabse purane 5 unclaimed tickets, wait time ke saath |
| `describe(duration)` | 90 second ko `1m`, 26 ghante ko `1d 2h` banata hai |

**Purana:** `findAll()` karke Java mein count karte the, yaani sab tickets memory mein. **Naya:** `countByStatus` aur `GROUP BY`, database hi ginta hai.

**`trend()` mein loop kyun?** Jis din koi ticket nahi aaya, query us din ki row deti hi nahi. Loop har din ke liye entry banata hai aur `getOrDefault(day, 0L)` se 0 bharta hai, warna chart mein din gayab ho jaate.

**`(t.resolvedAt - t.createdAt) BY SECOND`:** Hibernate ki date math. Ye MySQL aur H2 dono par chalti hai, isliye database-specific `TIMESTAMPDIFF` nahi likhna pada.

`AdminAnalyticsResponse` ke helpers: `percentOfTotal(count)` percent deta hai (total 0 ho to 0, taaki divide by zero na ho), `getBusiestAgentCount()` aur `getTrendPeak()` batate hain sabse lambi bar kitni hogi, `getNewInTrend()` aur `getResolvedInTrend()` 14 din ka total dete hain.

### StaffService

**Problem:** Public signup band karne ke baad staff add karne ka koi raasta nahi bacha tha. Khula signup rakhna galat tha, kyunki har agent saare customer tickets padh sakta hai. Isliye admin hi staff banata hai.

| Method | Kaam |
|---|---|
| `getAllStaff()` | Sab users, naam ke order mein |
| `getActiveTicketCounts()` | User ID ke hisaab se active tickets |
| `createStaff(request)` | Account banata hai, temporary password return karta hai |
| `setDisabled(id, disabled, admin)` | Login band ya chalu. Band karne par open sessions bhi khatam. |
| `resetPassword(id, admin)` | Naya password, aur sab jagah sign out |
| `findStaff(id)` | ID se user, na mile to error |
| `endSessions(username)` | `SessionRegistry` se us user ke sessions expire karta hai |
| `emptyToNull(value)` | Khali mobile ko null banata hai, kyunki column unique hai |
| `generatePassword()` | `SecureRandom` se temporary password |

**`SecureRandom` kyun, `Random` kyun nahi?** `Random` predict ho sakta hai, aur password ke liye ye khatarnak hai. `SecureRandom` cryptographically strong hai.

**Delete kyun nahi, disable kyun?** Delete karne par purane tickets aur audit log ka record bigadta hai. Disable mein history bachi rehti hai, aur galti se ho to wapas chalu ho jaata hai.

**Sirf login band karna kaafi kyun nahi?** Agent ne pehle se login kiya hai to uska session chalta rehta, aur nikala hua banda data dekhta rehta. `SessionRegistry` se `expireNow()` karke agli request par hi bahar kar dete hain.

**Temporary password DB mein?** Sirf BCrypt hash. Asli password ek baar flash attribute se screen par dikhta hai, aur logs mein kabhi nahi jaata.

**Admin khud ko disable kyun nahi kar sakta?** Warna akela admin apne aap ko lock out kar leta aur staff manage karne wala koi nahi bachta.

### TicketEvent aur TicketEventListener

```java
public record TicketEvent(Type type, String ticketId, String customerEmail, String subject) { }
```

**`record` kya hai?** Java 16+ ka short data class. Constructor, getters, equals sab apne aap.

```java
@TransactionalEventListener
public void onTicketEvent(TicketEvent event) { ... }
```

`onTicketEvent()` commit ke **baad** chalta hai: SSE broadcast karta hai, aur event type dekh ke email bhejta hai.

**Kyun?** Purane code mein broadcast transaction ke andar tha:

```
1. Ticket update
2. Agents ko signal bheja       <-- yahan bataya
3. Commit fail (rollback)       <-- par change hua hi nahi!
```

Ab pehle commit, phir signal. Rollback hua to event hi nahi chalta. Test: `rolledBackUpdateSendsNoEmail`.

**`switch` expression:** `case CREATED -> ...` naya Java syntax hai, `break` ki zaroorat nahi.

### NotificationService

| Method | Kaam |
|---|---|
| `sendTicketCreatedNotification(...)` | Confirmation email |
| `sendTicketResolvedNotification(...)` | Resolution email |
| `trackingLink(ref)` | `APP_BASE_URL` se link banata hai |
| `send(to, subject, body)` | Asli sending, errors pakad ke log karta hai |

**`@Async` kyun?** Brevo ko HTTP call mein time lagta hai. Bina `@Async` customer utna wait karta. Ab request turant khatam, email background thread par.

**`try/catch` kyun?** Brevo down hai to ticket creation fail nahi hona chahiye. Ticket zaroori hai, email secondary.

**Important catch:** `@Async` tabhi kaam karta hai jab method **doosri class** se call ho, kyunki Spring proxy se kaam karta hai. Same class ke andar call karne par proxy bypass ho jaata hai aur method normal chalta hai. Yahi rule `@Transactional` par bhi lagta hai.

**Timeouts kyun?** `setReadTimeout(5000)`. Brevo hang hua to thread hamesha ke liye ruk jaata. Ab 5 second mein chhod deta hai.

### SseNotificationService

| Method | Kaam |
|---|---|
| `subscribe()` | Naya `SseEmitter` banata hai, list mein rakhta hai, "connected" bhejta hai |
| `broadcast(type, ticketId)` | Sabko event bhejta hai, tuta connection hata deta hai |

```java
private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
```

**`CopyOnWriteArrayList` kyun?** Ek thread list par loop chala raha hai, doosra usi waqt remove kar raha hai. Normal `ArrayList` mein `ConcurrentModificationException` aata. Ye write par copy banata hai, isliye loop safe rehta hai. Reads zyada aur writes kam hon, tab best hai, aur yahi hamara case hai.

**30 minute timeout kyun?** Purana `Long.MAX_VALUE` tha: mare hue connections hamesha list mein rehte (memory leak). Browser ka `EventSource` khud reconnect kar leta hai, to timeout safe hai.

**Pehla "connected" message kyun?** Spring pehle message tak headers nahi bhejta. Proxy ko lag sakta hai connection atka hai.

### CloudinaryService

`uploadAvatar(file, userId)`: file check karta hai (khali, 5MB, sirf JPEG/PNG/WebP), phir Cloudinary par upload karke HTTPS URL return karta hai.

**SVG allow kyun nahi?** SVG mein JavaScript ho sakta hai, jisse XSS ho sakta hai.

**`public_id = user_avatar_ + userId`, `overwrite=true`:** Naya photo purane ko replace karta hai, kachra jama nahi hota.

## 4.7 controller package

**`@Controller` vs `@RestController`:** `@Controller` HTML page ka naam return karta hai. `@RestController` seedha JSON.

### CustomerPortalController (public)

| Method | Route | Kaam |
|---|---|---|
| `openInquiryForm` | GET `/portal/inquiry` | Khali form |
| `submitInquiry` | POST `/portal/inquiry` | Ticket banao, redirect |
| `openTrackForm` | GET `/portal/track` | Form, email link se ref pehle se bhara |
| `trackTicket` | POST `/portal/track` | Ref + email match par ticket |

**Track POST kyun, GET kyun nahi?** GET mein email URL mein jaata, browser history aur server logs mein save hota. POST mein body mein.

**Galat ref aur galat email par same message kyun?** Alag message hota to attacker jaan leta ki `TKT-005` exist karta hai. Same message, koi jaankari nahi.

### TicketWebController (agent pages)

| Method | Route | Kaam |
|---|---|---|
| `rootRedirect` | GET `/` | Landing page |
| `dashboard` | GET `/tickets` | Queue |
| `adminAllTicketsView` | GET `/tickets/admin/all-tickets` | `/admin/tickets` par forward |
| `returnToAdminDashboard` | GET `/tickets/admin/dashboard` | Redirect |
| `adminInspectFallback` | GET `/tickets/admin/{id}` | Redirect |
| `createForm` | GET `/tickets/new` | Form |
| `submitCreate` | POST `/tickets/new` | Agent ticket banata hai |
| `viewDetail` | GET `/tickets/{id}` | Detail page + update form |
| `updateTicket` | POST `/tickets/{id}/update` | Save, conflict par toast |
| `claimTicket` | POST `/tickets/{id}/claim` | Claim |
| `resolveTicket` | POST `/tickets/{id}/resolve` | Close |
| `liveStream` | GET `/tickets/live-stream` | SSE connection |
| `addToast` | - | Success/error message set karta hai |

**Forward vs Redirect:**

| | Forward | Redirect |
|---|---|---|
| Browser ko pata? | Nahi, URL same | Haan, URL badalta hai |
| Query params | Rehte hain | Khud bhejne padte hain |
| Requests | 1 | 2 |

Isi liye alias mein `forward:` use kiya, taaki `?status=OPEN` bacha rahe.

### AdminController

| Method | Route | Kaam |
|---|---|---|
| `adminDashboard` | GET `/admin/dashboard` | `AdminDashboardService` se analytics + activity |
| `viewAllTickets` | GET `/admin/tickets` | Sab tickets |
| `inspectTicketDetail` | GET `/admin/tickets/{id}` | Read-only view |

### AdminStaffController

Sab `/admin/staff` ke neeche hai, isliye `hasRole("ADMIN")` khud lag jaata hai.

| Method | Route | Kaam |
|---|---|---|
| `listStaff` | GET `/admin/staff` | Sab staff, active tickets ke saath |
| `newStaffForm` | GET `/admin/staff/new` | Add staff form |
| `createStaff` | POST `/admin/staff` | Validate, duplicate check, account banao |
| `disableStaff` | POST `/admin/staff/{id}/disable` | Login band |
| `enableStaff` | POST `/admin/staff/{id}/enable` | Login wapas chalu |
| `resetPassword` | POST `/admin/staff/{id}/reset-password` | Naya temporary password |
| `changeAccess` | - | Disable aur enable ka common code |
| `showTemporaryPassword` | - | Password flash attribute mein rakhta hai |
| `addToast` | - | Success ya error message |

### AuthController

| Method | Kaam |
|---|---|
| `loginPage` | Login form, error ya logout message ke saath |
| `signupPage` | Signup form, signup band ho to login par bhejta hai |
| `processSignup` | Validate, duplicate check, BCrypt, `ROLE_AGENT` ke saath save |

**POST wala login kahan hai?** Controller mein nahi hai, Spring Security ka filter khud handle karta hai.

**Page aur form dono par signup check kyun?** Sirf page band karte to koi `curl` se seedha POST kar deta.

### ProfileController

| Method | Kaam |
|---|---|
| `viewProfile` | Profile page |
| `uploadAvatar` | Cloudinary upload, URL save |
| `updateDetails` | Name, email, mobile save, duplicate check ke saath |
| `findUser` | Logged-in user laata hai |
| `changePassword` | Current password check, naya hash save |

**Real bug (`.trim()`):** Purana code naye password ko trim karke save karta tha, par login par trim nahi hota. Password `"abc "` set karte to login kabhi nahi hota. Fix: trim hata diya.

### TicketRestController (JSON API)

| Method | Route | Response |
|---|---|---|
| `createTicket` | POST `/api/tickets` | 201 |
| `getTickets` | GET `/api/tickets` | 200 |
| `getTicket` | GET `/api/tickets/{id}` | 200 ya 404 |
| `updateTicket` | PUT `/api/tickets/{id}` | 200, 400 ya 409 |
| `claimTicket` | POST `/api/tickets/{id}/claim` | 200 ya 409 |
| `resolveTicket` | POST `/api/tickets/{id}/resolve` | 200 |
| `csrfToken` | GET `/api/tickets/csrf` | Token |

### ApiExceptionHandler

`@RestControllerAdvice` se saare API errors ek jagah. Controller mein `try/catch` ki zaroorat nahi.

| Method | Exception | Status |
|---|---|---|
| `handleNotFound` | `TicketNotFoundException` | 404 |
| `handleConflict` | `OptimisticLockingFailureException` | 409 |
| `handleInvalidState` | `IllegalStateException` | 409 |
| `handleBadRequest` | `IllegalArgumentException` | 400 |
| `handleValidation` | `MethodArgumentNotValidException` | 400 |
| `error` | Helper, JSON banata hai | - |

`assignableTypes = TicketRestController.class`: sirf API par lagu. HTML pages `error.html` dikhate hain.

### GlobalUserControllerAdvice

`@ModelAttribute` methods har page ke model mein data daalte hain:

| Method | Kaam |
|---|---|
| `populateCurrentUser` | Navbar ke liye logged-in user |
| `signupEnabled` | "Register" link dikhana ya nahi |

Warna har controller method mein ye do lines likhni padti.

## 4.8 security package

### SecurityConfig

| Method | Kaam |
|---|---|
| `passwordEncoder()` | `BCryptPasswordEncoder` bean |
| `userDetailsService()` | Username se DB user, Spring ke format mein. `.disabled(...)` se disabled account login nahi kar pata. |
| `sessionRegistry()` | Yaad rakhta hai kaun signed in hai |
| `httpSessionEventPublisher()` | Session khatam hone par registry ko batata hai |
| `securityFilterChain()` | Saare rules: kaun kya dekh sakta hai |

**Rules upar se neeche check hote hain, pehla match jeetta hai:**

| Route | Kaun |
|---|---|
| `/`, `/login`, `/signup`, `/portal/**`, `/actuator/health` | Sab |
| `/admin/**`, `/tickets/admin/**` | Sirf ADMIN |
| `/tickets/**`, `/api/**` | AGENT ya ADMIN |
| Baaki sab (`/profile`) | Login zaroori |

**Agar `/tickets/**` ko `/tickets/admin/**` se upar likhte?** Agent admin page khol leta! Order matter karta hai.

**`hasRole("ADMIN")`:** Spring khud `ROLE_` prefix jodta hai, yaani `ROLE_ADMIN` check hota hai.

**`dispatcherTypeMatchers(ASYNC).permitAll()`:** SSE khatam hone par Spring request ko dobara dispatch karta hai. Tab tak session expire ho sakta hai, aur bina iske bekaar access error aata. Pehli request pehle hi check ho chuki hai.

**Entry points:**

- `/api/**`: 401 status, kyunki API client ko HTML login page ka koi kaam nahi.
- Baaki: `/login` par redirect.

**Real bug:** Pehle maine sirf API wala entry point add kiya, to browser ke bina wale clients ko bhi 401 aane laga. Test ne pakda, tab doosra rule explicit add kiya.

### CustomAuthenticationSuccessHandler

`onAuthenticationSuccess()`: login ke baad role dekh kar page chunta hai.

## 4.9 Frontend (Thymeleaf aur JS)

### Thymeleaf syntax

| Syntax | Matlab | Example |
|---|---|---|
| `${...}` | Model se value | `${ticket.subject}` |
| `*{...}` | `th:object` ki field | `*{customer_name}` |
| `@{...}` | URL banata hai | `@{/portal/track}` |
| `~{...}` | Fragment | `~{fragments/layout :: appNav('queue')}` |
| `th:text` | Text, **escaped** | XSS safe |
| `th:utext` | Text, escaped nahi | Khatarnak, is project mein use nahi |
| `th:if` | Condition | `th:if="${signupEnabled}"` |
| `th:each` | Loop | `th:each="t : ${tickets}"` |
| `th:field` | Form field bind | name, id, value set |
| `th:errors` | Validation error | |
| `th:classappend` | Class jodna | Status colour |
| `th:action` | Form URL + **CSRF token** | |
| `th:replace` | Fragment daalna | Navigation, status label, toast |
| `#temporals.format` | Date format | `dd MMM, hh:mm a` |
| `#lists.isEmpty` | List khali? | |
| `#strings.toLowerCase` | Lowercase | |

**XSS example:** Customer subject mein `<script>steal()</script>` likhe. `th:text` use `&lt;script&gt;` bana deta hai, to browser text dikhata hai, chalata nahi.

### PRG Pattern (Post/Redirect/Get)

```java
return "redirect:/portal/inquiry?success=true";
```

Bina redirect, form submit ke baad F5 dabane par browser form dobara bhejta, aur duplicate ban sakta tha. Redirect ke baad F5 sirf GET karta hai.

**Flash attributes:** `addFlashAttribute("createdRef", id)` session mein rakhta hai sirf **agli** request ke liye, aur uske baad khud hat jaata hai. URL gandi nahi hoti.

### JavaScript

| Feature | Kahan | Kaise |
|---|---|---|
| Live refresh | `static/js/app.js` | `new EventSource(...)`, event par reload, par typing ke waqt nahi |
| Stopwatch | `static/js/queue.js` | `setInterval` har second, `claimed_at` se farq |
| Sort | `static/js/queue.js` | Rows array mein sort, phir `appendChild`. Choice `sessionStorage` mein yaad rehti hai. |
| Default oldest first | `static/js/queue.js` | Page load par `applySort('oldest')` |
| Quick search | `static/js/queue.js` | Row text match, baaki hide |
| Charts | `admin/dashboard.html` | Koi JS nahi, sirf HTML aur CSS width |
| Password rules | `signup.html` | Har rule regex se check, poora hote hi tick |
| Image preview | `profile/view.html` | `FileReader` |
| 5MB check | `profile/view.html` | Upload se pehle `file.size` |

**`appendChild` se sort kaise hota hai?** Jo element pehle se DOM mein hai, `appendChild` use **move** karta hai. Sorted order mein sab append kiya, order badal gaya.

**Client-side validation kaafi kyun nahi?** JS band karke ya `curl` se bypass ho jaata hai. Server par `@Valid` asli suraksha hai, JS sirf achhe experience ke liye.

## 4.10 Configuration aur Deployment

### application.properties

| Setting | Kyun |
|---|---|
| `server.port=${PORT:8080}` | Render `PORT` deta hai, warna 8080 |
| `${DB_URL}` | Default nahi. Na mile to app start hi nahi hogi (fail fast). |
| `forward-headers-strategy=framework` | Proxy ke peeche app ko pata chale request HTTPS thi |
| `session.cookie.secure=true` | Cookie sirf HTTPS par |
| `session.cookie.same-site=lax` | Doosri site se POST par cookie nahi jaati |
| `open-in-view=false` | Connection jaldi free, DB kaam sirf service mein |
| `ddl-auto=update` | Missing tables aur columns add karta hai |
| `max-swallow-size=20MB` | Bada upload padh ke saaf error do, connection reset nahi |
| `exposure.include=health` | Sirf health public, `env` jaisi cheezein nahi |

**`dev` profile:** `application-dev.properties` local ke liye: SQL logs on, cookie secure off, signup on. **Default production safe hai**, dev mein khud opt-in karna padta hai. Isse galti se production mein dev settings nahi lagti.

### Dockerfile

| Line | Kyun |
|---|---|
| Do `FROM` | Multi-stage: build mein Maven, final mein sirf JRE. Chhoti image. |
| `COPY pom.xml` phir `dependency:go-offline` | Layer cache: code badle to dependencies dobara download nahi |
| `adduser`, `USER crm` | Root nahi. Hack hua to damage kam. |
| `MaxRAMPercentage=75.0` | Java container ki memory limit samjhe |
| `HEALTHCHECK` | Docker ko pata chale app zinda hai |
| `exec java` | Java main process bane, stop signal mile, clean shutdown ho |

### CI (GitHub Actions)

Har push par `./mvnw -B verify` chalta hai. Test fail to pata chal jaata hai deploy se pehle.

### Tests

| File | Kya test karta hai |
|---|---|
| `TicketServiceTest` | Dedupe, concurrency, version, emails, analytics |
| `WebSecurityTest` | Access rules, CSRF, tracking, pages render, API errors |
| `ProductionSettingsTest` | Signup band, admin password replace |
| `StaffManagementTest` | Staff add, disable, reset, permissions, profile edit |
| `CustomerProtectionTest` | Form abuse, alag login pages, ticket lock, emails, Brevo call |
| `SupportCrmApplicationTests` | App start hoti hai ya nahi |

| Tool | Kaam |
|---|---|
| `@SpringBootTest` | Poori app start karta hai |
| `@ActiveProfiles("test")` | H2 database wali settings |
| `MockMvc` | Bina server HTTP request simulate |
| `@WithMockUser` | Logged-in user maan lo |
| `.with(csrf())` | Valid CSRF token daalo |
| `@MockitoBean` | asli Brevo ki jagah fake mail sender |
| `timeout(3000)` | Async kaam ke liye 3 second tak wait |
| `after(300)` | 300ms ruk ke check karo ki kuch **nahi** hua |

---

# Part 5: Concepts Deep Dive

## 5.1 IoC aur Dependency Injection

**Bina DI:**

```java
public class TicketService {
    private TicketRepository repo = new TicketRepositoryImpl(); // khud banaya, tightly coupled
}
```

**DI ke saath:**

```java
@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository; // Spring dega
}
```

**Example:** Restaurant mein aap khud khana nahi banate, order karte ho aur mil jaata hai. **IoC** matlab control Spring ke paas. **Bean** matlab Spring ka banaya hua object.

**Constructor injection kyun, `@Autowired` field kyun nahi?** `final` rakh sakte ho, dependency chhupti nahi, aur test mein mock dena aasan hai. `@RequiredArgsConstructor` (Lombok) ye constructor khud banata hai.

**Bean scope:** Default singleton, poori app mein ek object. Isi liye `TicketService` mein request-specific data field mein nahi rakhte, warna threads mix ho jaate.

## 5.2 @Transactional aur ACID

Ya to sab hoga, ya kuch nahi.

**Example:** `updateTicket` mein note, ticket update aur audit log, teeno save hote hain. Audit log fail hua to teeno rollback.

| ACID | Matlab | Example |
|---|---|---|
| Atomicity | Sab ya kuch nahi | Upar wala |
| Consistency | Rules hamesha sahi | `ticket_id` unique |
| Isolation | Transactions ek doosre ko disturb na karein | Do agents |
| Durability | Commit ke baad data safe | Server crash ke baad bhi |

**`@Transactional(readOnly = true)`:** Hibernate dirty check skip karta hai, thoda fast.

**Default rollback:** Sirf `RuntimeException` par. Checked exception (`IOException`) par rollback **nahi** hota.

**Self-invocation trap:**

```java
public void a() { b(); }        // proxy bypass
@Transactional public void b() {} // transaction nahi lagega!
```

**Isolation levels:** MySQL InnoDB ka default `REPEATABLE READ` hai.

| Level | Rokta hai |
|---|---|
| READ UNCOMMITTED | Kuch nahi |
| READ COMMITTED | Dirty read |
| REPEATABLE READ | Dirty + non-repeatable read |
| SERIALIZABLE | Sab |

## 5.3 Optimistic vs Pessimistic Locking

**Lost update problem:**

```
A padhta hai: balance 100        B padhta hai: balance 100
A likhta hai: 100 + 50 = 150     B likhta hai: 100 - 20 = 80
Result 80. A ka +50 gayab.
```

| | Optimistic | Pessimistic |
|---|---|---|
| Idea | Conflict kam hota hai, save par check karo | Conflict hoga, pehle hi lock lo |
| Kaise | `@Version` | `SELECT ... FOR UPDATE` |
| Padhne wale ruke? | Nahi | Haan |
| Best for | Reads zyada, conflict kam | Conflict zyada, bank transfer |
| Conflict par | Exception, user retry kare | Doosra wait kare |

**Example:** Google Docs ka "someone edited" (optimistic) vs library ki ek hi kitaab issue hona (pessimistic).

**Asli SQL jo Hibernate chalata hai:**

```sql
UPDATE tickets SET status='CLOSED', version=4 WHERE id=7 AND version=3;
```

0 rows update hui, matlab kisi ne version badal diya. Hibernate `ObjectOptimisticLockingFailureException` throw karta hai.

**Hamare code mein do layers kyun?**

```java
if (!ticket.getVersion().equals(request.getVersion())) { throw ... }
```

1. **Manual check:** Form 10 minute purana hai. Kaam shuru karne se pehle hi reject.
2. **`@Version` at flush:** Dono requests milliseconds ke andar aayin, dono ne same version padha. Sirf ek jeetegi.

Sirf `@Version` kaafi nahi hota, kyunki request aane par ticket fresh load hota hai, aur tab version naya hi dikhta. Client ka bheja version compare karna zaroori hai.

## 5.4 Lazy Loading aur N+1 Problem

**LAZY:** Data tab load ho jab maanga jaaye. **EAGER:** Turant.

**N+1 example:**

```java
List<Ticket> tickets = repo.findAll();      // 1 query
for (Ticket t : tickets) t.getNotes().size(); // har ticket ki 1 query
```

100 tickets = 101 queries. **Fix:** `JOIN FETCH`, `@EntityGraph`, ya notes load hi mat karo.

**Hamare project mein:** List page sirf `TicketSummaryResponse` banata hai, jismein notes nahi hote, to N+1 nahi hota.

**`open-in-view=false`:** Default `true` mein DB session page render hone tak khula rehta hai, jo lazy queries chhupa deta hai aur connection zyada der pakde rakhta hai. `false` mein transaction ke bahar lazy access par `LazyInitializationException` aata hai, jo bug ko jaldi dikha deta hai. Hum DTO service ke andar hi banate hain, to problem nahi hoti.

## 5.5 @Async aur Thread Pool

**Bina async:** Request thread email ke liye 3 second ruka. **Async:** Kaam queue mein gaya, request turant wapas.

**Thread pool kyun?** Har kaam par naya thread banana mehnga hai, aur limit nahi hoti. Pool threads reuse karta hai. Logs mein `task-2` jaisa naam dikhta hai, ye pool ka thread hai.

**Hotel example:** Har order par naya waiter hire nahi karte, fixed waiters hote hain aur orders line mein lagte hain.

**Risk:** Queue memory mein hai. Server crash hua to pending emails gaye. Solution: outbox pattern (Part 7 dekho).

## 5.6 SSE vs WebSocket vs Polling

| | Polling | SSE | WebSocket |
|---|---|---|---|
| Direction | Client poochta hai | Server se client | Dono taraf |
| Protocol | HTTP | HTTP | Alag (`ws://`) |
| Reconnect | - | Automatic | Khud likho |
| Best for | Simple, kam updates | Notifications, feeds | Chat, games |

**Hamara case:** Agent ko sirf sunna hai, bhejna kuch nahi. SSE best.

**Format:**

```
event:TICKET_UPDATED
data:TKT-003

```

Khali line message ka ant batati hai.

**Limit:** Purane HTTP/1.1 par browser ek domain ke liye ~6 connections deta hai, aur har tab ek leta hai. HTTP/2 par ye problem kam hai.

## 5.7 CSRF

**Attack:**

```html
<!-- evil.com par hidden form -->
<form action="https://crm.com/api/tickets/TKT-001/resolve" method="post"></form>
<script>document.forms[0].submit()</script>
```

Agent logged in hai aur evil.com khola. Browser cookie apne aap bhejta hai, aur ticket close ho gaya, agent ko pata bhi nahi!

**Defense:**

1. Server har form mein ek random **CSRF token** daalta hai.
2. POST par token match karta hai.
3. Evil.com token nahi padh sakta (same-origin policy).

**Hamare project mein:** `th:action` token khud daalta hai. API ko `X-CSRF-TOKEN` header dena hota hai (`GET /api/tickets/csrf` se milta hai). Saath mein `SameSite=Lax` doosri layer hai.

**GET logout kyun galat?** `<img src="https://crm.com/logout">` kisi bhi site par daal do, aur user logout. GET se kabhi state nahi badalni chahiye.

**JWT APIs CSRF disable kyun karti hain?** Wahan token `Authorization` header mein jaata hai, browser khud nahi bhejta. Hum cookie session use karte hain, to CSRF zaroori hai. Purane code ne band kar rakha tha, ye galat tha.

## 5.8 IDOR

**Insecure Direct Object Reference:** ID badal ke kisi aur ka data dekhna.

```
/portal/track?ref=TKT-001   -> mera ticket
/portal/track?ref=TKT-002   -> kisi aur ka! Bas number badla.
```

**Fix:** Reference ke saath email bhi match karo, aur message same rakho.

## 5.9 Authentication vs Authorization

| | Authentication | Authorization |
|---|---|---|
| Sawal | Aap kaun ho? | Aap kya kar sakte ho? |
| Example | Airport par passport | Boarding pass se seat |
| Yahan | Login | `hasRole("ADMIN")` |
| Fail status | 401 | 403 |

## 5.10 Sessions

1. Login sahi: server session banata hai aur `JSESSIONID` cookie deta hai.
2. Har request par cookie aati hai, server pehchan leta hai.
3. Logout: session delete.

| Cookie flag | Rokta hai |
|---|---|
| `HttpOnly` | JS cookie nahi padh sakta (XSS se chori mushkil) |
| `Secure` | Sirf HTTPS |
| `SameSite=Lax` | Cross-site POST par cookie nahi |

**Session vs JWT:**

| | Session | JWT |
|---|---|---|
| Data kahan | Server par | Token mein |
| Logout | Turant | Mushkil (expiry tak valid) |
| Multiple servers | Shared store chahiye | Aasan |

Hamare liye session sahi hai: server-rendered pages, ek server.

## 5.11 BCrypt

**Plain text kyun nahi?** DB leak hua to sabke password gaye.

**SHA-256 kyun nahi?** Bahut fast hai. Attacker GPU se arbon guess per second kar sakta hai.

**BCrypt ki khoobiyan:**

- **Salt:** Har password ke saath random data. Do logon ka same password, alag hash. Rainbow tables bekaar.
- **Slow by design:** Work factor 10 matlab 2^10 rounds.

```
$2a$10$N9qo8uLOickgx2ZMRZoMye...
 |   |  |
 |   |  salt aur hash
 |   work factor
 algorithm
```

**Login par compare kaise?** Decrypt nahi hota (hash one-way hai). `matches()` hash se salt nikaalta hai, diye gaye password ko hash karta hai, phir compare.

## 5.12 Database Indexing

**Bina index:** Poori table scan. **Index ke saath:** Kitaab ke index jaisa, seedha page par.

| Index | Query |
|---|---|
| `ticket_id` (unique) | Ticket kholna |
| `status` | Status count aur filter |
| `created_at` | Newest first sorting |

**Har jagah index kyun nahi?** Har insert aur update par index bhi update hota hai, to writes slow hote hain, aur space bhi lagta hai.

**Real learning:** Maine `customer_email` par bhi index lagaya tha, phir hataya. Query `LOWER(t.customerEmail)` use karti hai, aur column par function lagate hi normal index use nahi hota. Bekaar index sirf writes slow karta. `LIKE '%abc%'` par bhi yahi hai, shuruaat mein `%` ho to index nahi chalta. Bade scale par search ke liye Elasticsearch ya full-text index lagega.

## 5.13 HTTP Status Codes

| Code | Matlab | Yahan kab |
|---|---|---|
| 200 | OK | Sab theek |
| 201 | Created | Naya ticket |
| 302 | Redirect | Form ke baad |
| 400 | Galat request | Validation fail |
| 401 | Login nahi | API bina login |
| 403 | Permission nahi | Agent admin page par, CSRF fail |
| 404 | Nahi mila | Ticket nahi hai |
| 409 | Conflict | Purana version, double claim |
| 413 | Bahut bada | 5MB se badi file |
| 500 | Server error | Bug |

**PUT vs POST:** PUT idempotent hai (baar baar same result). POST nahi (har baar naya ban sakta hai).

## 5.14 Connection Pool

Naya connection banane mein TCP aur login lagta hai, jo slow hai. Hikari 10 connections ready rakhta hai. Request ek leta hai, kaam karke wapas.

**Pool khatam ho jaye?** `connection-timeout=20000`: 20 second wait, phir error.

**Pool size kitna?** Zyada hamesha better nahi. DB ke CPU limited hain.

## 5.15 ORM aur JPA

| Term | Matlab |
|---|---|
| **ORM** | Object aur table ko jodne ka concept |
| **JPA** | Standard rules (interface) |
| **Hibernate** | JPA ka implementation |
| **Spring Data JPA** | Upar ki layer, repository interface deta hai |
| **JPQL** | Class par query |

**`ddl-auto` values:**

| Value | Kya karta hai | Kab |
|---|---|---|
| `create-drop` | Banao, band par hatao | Tests (hum use karte hain) |
| `create` | Har baar naya | Kabhi production nahi |
| `update` | Missing add | Small projects (hum use karte hain) |
| `validate` | Sirf check | Flyway ke saath |
| `none` | Kuch nahi | Migration tool ke saath |

## 5.16 12-Factor Config

Secrets aur settings code mein nahi, environment mein.

```properties
spring.datasource.password=${DB_PASSWORD}       # zaroori
server.port=${PORT:8080}                        # default 8080
```

Same Docker image dev aur production dono jagah, sirf env alag.

## 5.17 CORS

Browser ka rule: `site-a.com` ki JS `site-b.com` ka response nahi padh sakti, jab tak B allow na kare.

**Humne `@CrossOrigin("*")` kyun hataya?** UI aur API same domain par hain, to CORS ki zaroorat hi nahi thi. Jo chahiye nahi, wo khula nahi rakhna.

**CORS vs CSRF:** CORS response **padhne** ko rokta hai. CSRF mein attacker ko padhna nahi, request **bhejna** kaafi hai. Isliye CORS CSRF ko nahi rokta.

## 5.18 Spring Events

```
TicketService --publish--> TicketEvent --> TicketEventListener
```

**Fayda:** `TicketService` ko email aur SSE ke baare mein kuch pata nahi. Kal SMS add karna ho to sirf listener badlega. Ye **loose coupling** hai, aur **Observer pattern** ka example.

## 5.19 Design Patterns jo project mein hain

| Pattern | Kahan |
|---|---|
| **Layered Architecture** | Controller, Service, Repository |
| **Dependency Injection** | Poori app |
| **Repository** | `TicketRepository` |
| **DTO** | `dto` package |
| **Builder** | `Ticket.builder()` |
| **Observer** | Spring Events, SSE |
| **Proxy** | `@Transactional`, `@Async` |
| **Front Controller** | `DispatcherServlet` |
| **Template View** | Thymeleaf |
| **Singleton** | Spring beans |

---

# Part 6: Interview Questions aur Answers

### Project

**Q1. Sabse mushkil part kya tha?**
Concurrent updates. Sirf `@Version` kaafi nahi hota, kyunki ye tabhi pakadta hai jab dono transactions ek saath chalein. Agent B ka form 10 minute purana ho to request aane par ticket fresh load hota hai, aur version match dikhta hai. Isliye client se version bhej ke compare karna pada. Aur `save()` ke baad version turant nahi badhta, isliye `saveAndFlush` use kiya.

**Q2. Duplicate tickets kaise rokte ho?**
Customer ke active tickets laake subject aur description compare karta hoon, case ignore karke. Sab same to wahi ticket, sirf subject same to follow-up note. Do exact same requests milliseconds mein aayein to dono ban sakte hain, ye known limit hai. Pakka rokna ho to hash column par unique constraint lagega.

**Q3. SQLite se MySQL kyun?**
SQLite write par poori file lock karta hai. Multiple threads par connection timeouts aa rahe the. Aur container restart par file chali jaati hai. MySQL row-level lock deta hai aur alag service hai.

**Q4. Testing kaise ki?**
87 automated tests hain: service logic, security rules aur page rendering. H2 use kiya taaki CI mein MySQL na chahiye. Par H2 aur MySQL mein farq ho sakta hai, isliye upgrade path real MySQL 8 par bhi check kiya: purana version chala ke data banaya, phir naya version usi DB par chalaya.

**Q5. Project mein security issues kya the?**
Sabse bade: default `admin123`, public tracking se koi bhi ticket padh lena, public SSE stream se customer data leak, aur CSRF band. Sabko fix kiya aur test likhe.

### Spring

**Q6. `@Component`, `@Service`, `@Repository`, `@Controller` mein farq?**
Sab beans bante hain. `@Service` aur `@Controller` meaning batate hain. `@Repository` DB exceptions ko Spring ki `DataAccessException` mein convert bhi karta hai. `@Controller` web requests handle karta hai.

**Q7. `@Transactional` kaise kaam karta hai?**
Spring class ka proxy banata hai. Method call se pehle transaction shuru, baad mein commit, `RuntimeException` par rollback. Isi liye same class ke andar call par kaam nahi karta, kyunki proxy bypass ho jaata hai.

**Q8. `@ControllerAdvice` kya hai?**
Sab controllers ke liye common code: `@ExceptionHandler` se errors, `@ModelAttribute` se common data. Hamara `ApiExceptionHandler` aur `GlobalUserControllerAdvice` yahi hain.

**Q9. `@RequestParam` vs `@PathVariable` vs `@RequestBody`?**
`/tickets?status=OPEN` mein `@RequestParam`. `/tickets/TKT-001` mein `@PathVariable`. JSON body ke liye `@RequestBody`.

**Q10. `@ModelAttribute` vs `@RequestBody`?**
`@ModelAttribute` form data ke liye (HTML forms). `@RequestBody` JSON ke liye (API).

**Q11. Spring Boot auto-configuration kya hai?**
Classpath dekh ke setup karta hai. `mysql-connector` mila aur `spring.datasource.url` diya to DataSource ban gaya. `spring-boot-starter-thymeleaf` mila to Thymeleaf ka template engine ban gaya.

**Q12. Request ka poora flow batao.**
Tomcat request leta hai, filters (security) chalte hain, `DispatcherServlet` sahi controller dhoondhta hai, controller service call karta hai, service repository. Wapas aake controller view naam deta hai, Thymeleaf HTML banata hai, response jaata hai.

### Database

**Q13. Optimistic locking mein retry kyun nahi kiya?**
Agent ko pata hona chahiye ki kisi ne ticket badla hai, kyunki ho sakta hai uska kaam ab bekaar ho. Apne aap retry karte to doosre ka kaam overwrite ho jaata, aur yahi to rokna tha.

**Q14. Audit log mein `ticketId` string kyun, foreign key kyun nahi?**
Audit record hamesha rehna chahiye, chahe ticket delete ho. Purana naam bhi `userName` mein rakhte hain, taaki agent ka naam badalne par bhi history sahi rahe. Is denormalization ka matlab hai purana data waisa hi rehta hai.

**Q15. `assignedAgentName` Ticket mein copy kyun?**
List par JOIN nahi karna padta, to query fast aur simple. Trade-off: agent naam badle to purane tickets par purana naam rahega.

**Q16. Agar `ddl-auto=update` kharab hai to kyun use kiya?**
Is project ke liye kaafi hai aur simple hai, par rename aur data change nahi sambhalta. Real team mein Flyway lagata. Maine ise known limit ki tarah document kiya hai.

### Security

**Q17. CSRF token kahan store hota hai?**
Session mein. Form ke hidden field mein aata hai aur POST par compare hota hai.

**Q18. SQL injection se kaise bache?**
Parameterized queries. JPQL mein `:search` value alag jaati hai, query ka hissa nahi banti.

**Q19. XSS se kaise bache?**
`th:text` HTML escape karta hai, `th:utext` kahin use nahi kiya. Cookie `HttpOnly` hai. SVG upload allowed nahi.

**Q20. Sequential IDs safe hain?**
Akele nahi, isliye tracking mein email bhi maangta hoon. UUID bhi option tha, par `TKT-001` phone par batana aasan hai. Strong secret chahiye to email ki jagah signed link better hoga.

**Q21. Public signup default off kyun?**
Agent role se saare customer tickets dikhte hain. Khula signup matlab koi bhi customer data dekh sakta. Demo ke liye env var se khulta hai.

### Scaling aur Design

**Q22. 2 servers par chalana ho to kya tutega?**
Teen cheezein:
1. **SSE:** Emitters memory mein hain. Server A ka event server B ke agents ko nahi milega. Fix: Redis Pub/Sub.
2. **Sessions:** Memory mein hain. Fix: Spring Session + Redis, ya sticky sessions.
3. **Async emails:** Crash par gum. Fix: outbox.

Optimistic locking sahi chalega, kyunki wo database mein hai.

**Q23. 10 lakh tickets ho jayein to?**
`LIKE '%x%'` slow hoga, Elasticsearch lagega. Real pagination lagegi. Purane tickets archive honge. Read replica aur dashboard counts ka cache bhi.

**Q24. Email fail ho jaye to?**
Abhi log hota hai aur gum jaata hai. Sahi tareeka **Outbox Pattern**: same transaction mein `outbox` table mein row likho, ek job bheje aur retry kare. Commit hua to email pakka jayega.

**Q25. Agar kal SMS bhi chahiye?**
`TicketEventListener` mein ek line aur `SmsService`. `TicketService` nahi badlega. Events ka yahi fayda hai.

**Q26. Rate limiting kaise lagaoge?**
Login aur public form par. Bucket4j se Spring filter, ya Cloudflare par. Abhi nahi hai, ye known gap hai. Isse abuse ho sakta hai, jaise form se kisi aur ke email par mails bhejna.

**Q27. WebSocket kyun nahi?**
Client ko bhejna kuch nahi. SSE simple hai, HTTP par chalta hai aur khud reconnect karta hai.

**Q27a. Admin aur staff ke login alag kaise hain?**
`/admin-login` aur `/staff-login` alag pages hain, jo hidden `portal` field bhejte hain. `CustomAuthenticationSuccessHandler` role check karta hai: staff ne admin page se login kiya to session band aur normal "invalid" message. Asli suraksha phir bhi URL rules mein hai (`hasRole("ADMIN")`), isliye koi `portal` field badal ke bhi admin page nahi khol sakta.

**Q27a1. Naya staff kaun add karta hai?**
Admin, Staff page se. App temporary password banata hai, jo ek baar dikhta hai, aur admin use privately share karta hai. Staff login karke profile se apna password badal leta hai. Abhi password change force nahi hota, ye known limit hai.

**Q27a2. Dashboard par kya dikhana hai, ye kaise decide kiya?**
Har section ek sawal ka jawab hai: kitna kaam hai, backlog badh raha hai ya nahi, kaun sabse der se wait kar raha hai, kaun overloaded hai, abhi kya hua. Sirf numbers dikhana kaafi nahi, admin ko agla kadam pata chalna chahiye.

**Q27a3. Workload mein closed tickets kyun nahi gine?**
Pehle ginte the, jo galat tha: jisne 24 tickets close kiye wo overloaded dikhta, jabki uske paas 0 active kaam hai. Ab Active aur Resolved alag columns hain, `SUM(CASE WHEN ...)` se ek hi query mein.

**Q27b. Dashboard mein donut chart kyun nahi?**
Donut mein slices ka size aankh se sahi compare karna mushkil hota hai. Split bar ke saath percent list aur ranked bars zyada saaf hain. Aur ye plain HTML/CSS hai, to koi chart library load nahi karni padti.

**Q27c. CSS ek hi file mein kyun?**
Pehle har page mein apna CSS tha, to ek style badalne ke liye kai files badalni padti thi. Ab `app.css` mein colors variables hain, aur repeated HTML `fragments/layout.html` mein. Ye DRY principle hai: ek cheez, ek jagah.

**Q28. Microservices kyun nahi?**
Ek chhoti team aur ek hi domain hai. Monolith simple hai, debug aasan hai. Network calls aur distributed transactions ki complexity ka koi fayda nahi. Zaroorat padne par notifications pehle alag ho sakte hain.

### Java

**Q29. `Optional` kyun?**
`null` ka khatra saaf dikhta hai. `orElseThrow`, `map`, `ifPresentOrElse` se saaf code.

**Q30. `record` vs class?**
Record immutable data ke liye: constructor, getters, `equals` sab free. `TicketEvent` isi liye record hai, event badalna nahi chahiye.

**Q31. `equals` aur `==` mein farq, code mein kahan?**
`==` reference compare, `equals` value. `ticket.getVersion().equals(request.getVersion())` mein `Long` hain. `==` chhote numbers par (-128 se 127 cache) sahi lagta aur bade par fail hota. Bahut classic bug hai.

**Q32. Stream API ka example?**
`getAllTickets` mein `tickets.stream().map(...).collect(...)`, har entity ko DTO banata hai.

---

# Part 7: Honest Limitations

Interviewer ko khud batao, ye maturity dikhata hai.

| Limit | Risk | Solution |
|---|---|---|
| Login par rate limiting nahi | Password guessing | Bucket4j ya Cloudflare |
| Form limits memory mein hain | Fake IPs se kuch requests nikal sakti hain | CAPTCHA, jaise Turnstile |
| `ddl-auto=update` | Rename aur data change nahi | Flyway |
| SSE ek server par | Scale nahi | Redis Pub/Sub |
| Emails retry nahi | Outage par gum | Outbox pattern |
| 500 tickets ki limit | Purane sirf search se | Real pagination |
| Har change par page reload | Busy queue par jhatke | Sirf row update karna |
| Temporary password change force nahi hota | Admin ko password pata rehta hai jab tak staff na badle | First login par change force karna |
| Disable par active tickets reassign nahi hote | Handover haath se karna padta hai | Reassign action |
| Notes customer ko bhi dikhte hain | Sach mein internal note nahi hai | `internal` flag |
| Docker image test nahi hui | Daemon band tha | Deploy se pehle ek baar build |

---

# Part 8: Bug Stories (STAR format)

"Koi interesting bug batao?" par ye bolo.

### Story 1: Koi bhi kisi ka ticket padh sakta tha

- **Situation:** Public tracking page sirf reference ID se ticket dikhata tha, aur IDs sequential hain.
- **Task:** Customer ke liye login-free rakhna tha, par data safe.
- **Action:** Reference ke saath email match lagaya. Email URL mein na jaye, isliye form POST kiya. Galat ref aur galat email par same message, taaki kuch pata na chale. Email links ab sirf reference pre-fill karte hain.
- **Result:** Test se confirm: galat email par kuch nahi dikhta.

### Story 2: Rollback ke baad bhi notification

- **Situation:** Event transaction ke andar bhej rahe the. Commit fail hone par bhi agents ko signal jaa chuka hota.
- **Action:** `@TransactionalEventListener` lagaya, jo commit ke baad chalta hai.
- **Result:** Test: rollback par koi email nahi.

### Story 3: Jhootha conflict

- **Situation:** API response ke version se dobara save karne par conflict aata tha, jabki koi aur edit nahi kar raha tha.
- **Root cause:** `save()` ke baad version flush par badhta hai, response mein purana jaata tha.
- **Action:** `saveAndFlush()`.
- **Result:** Test: v2 se v3, aur usi version se dobara save theek.

### Story 4: Ticket number race condition

- **Situation:** `count() + 1` do requests ko same number deta tha, aur ek ko 500 error milta tha.
- **Action:** Database ke unique auto-increment ID se number banaya.
- **Result:** Test: 12 threads ek saath, 12 unique IDs.

### Story 6: Jab "tests pass" kaafi nahi tha

- **Situation:** UI redo ke baad saare browser checks pass the, phir bhi screenshots mein login card lamba khicha hua tha aur phone par content screen ke kinare se chipka tha.
- **Root cause:** Global `main { flex: 1 }` rule login card par bhi lag gaya tha, aur `.page { padding: 2rem 0 }` ne Bootstrap ki side padding hata di thi.
- **Action:** Screens khud dekhe, CSS rules sahi jagah limit kiye, aur phone par Status column dikhna pakka kiya.
- **Learning:** Tests behaviour check karte hain, look nahi. UI change ke baad screen dekhna zaroori hai.

### Story 5: Handler jo kabhi chala hi nahi

- **Situation:** 5MB se badi file ke liye `@ExceptionHandler` likha, par real server par raw 413 aata tha. MockMvc mein size limit hoti hi nahi, to unit test ye pakad nahi sakta tha.
- **Action:** Debug logs se dekha ki CSRF filter multipart body padhte waqt hi request fail kar deta hai, MVC tak pahunchti hi nahi. Handler hataya, `error.html` mein 413 ka message dala, aur browser mein size check lagaya.
- **Learning:** Real server par test karna zaroori hai. Sirf unit test kaafi nahi.

---

# Part 9: Last Minute Cheat Sheet

| Sawal | Ek line |
|---|---|
| Stack? | Java 21, Spring Boot 4, MySQL 8, Thymeleaf, Bootstrap, Docker |
| Collision protection? | JPA `@Version`, optimistic locking, HTTP 409 |
| Real-time? | SSE, `SseEmitter`, `EventSource` |
| Non-blocking email? | `@Async` |
| Commit ke baad? | `@TransactionalEventListener` |
| Password? | BCrypt, salt + slow |
| CSRF? | Token + `SameSite=Lax` |
| XSS? | `th:text` escaping |
| SQL injection? | JPQL parameters |
| Public tracking? | Reference + email |
| Thread-safe list? | `CopyOnWriteArrayList` |
| Duplicate ticket? | Email + subject + description compare |
| Ticket number? | DB `IDENTITY` ID se |
| Tests? | 87: JUnit, Mockito, MockMvc, H2 |
| Deploy? | Docker multi-stage, Render, env vars |
| Scale problem? | SSE aur session memory mein, Redis chahiye |

## Commands

```powershell
# Local chalana (support-crm folder se)
.\mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"

# Tests
.\mvnw test

# Jar banana
.\mvnw clean package
```

**All the best!** Yaad rakho: interviewer ko perfect project nahi chahiye. Use aisa insaan chahiye jo trade-offs samjhe aur apni limits khud bata sake.

---

# Part 10: Naye Features

## BrevoEmailClient

`send(message)` Brevo ke HTTP API ko `RestClient` se POST karta hai, `api-key` header ke saath.

**SMTP kyun nahi?** Render ka free plan ports 25, 465 aur 587 block karta hai. HTTP API port 443 par chalti hai, jo khula hai.

`EmailMessage` ek record hai: to, subject, text. Test mein fake HTTP server se check kiya ki request sahi jaati hai.

## RequestLimiter

| Method | Kaam |
|---|---|
| `allowNewRequest(ip, email, subject, description)` | Sab rules check karta hai |
| `allowTrackAttempt(ip)` | Tracking guess rokta hai |
| `mailboxOf(email)` | `a.b+1@gmail.com` ko `ab@gmail.com` banata hai |
| `allow(key, max, window)` | Sliding window: purane time hatao, count check karo, naya time jodo |

**Example:** Ek banda 1 minute mein naam aur email badal ke 10 requests bheje. IP rule 3 ke baad 429 de dega. VPN badle to same text wala rule pakdega, aur text bhi badle to site-wide limit bachi hai.

**`ConcurrentHashMap` + `synchronized`:** Map thread-safe hai, par "count check karke jodna" do steps hain, isliye har key ke deque par lock lagta hai. Isi ko **check-then-act race** kehte hain.

**Honest limit:** Ye memory mein hai, to restart par reset hota hai aur har server ka alag hota hai. Bade scale par Redis lagega.

## Ticket lock: requireHandler

`TicketService.requireHandler(ticket, agent)`: ticket kisi aur ke paas hai aur aap admin nahi ho to `IllegalStateException`, jo web par toast aur API par 409 banta hai.

**`@Version` ke saath kyun?** Ye dono alag kaam karte hain. `requireHandler` business rule hai: "A kaam kar raha hai to B nahi." `@Version` race pakadta hai: A aur B milliseconds mein ek saath unclaimed ticket save karein to sirf ek jeetega.

## intakeLock aur hidden field

`synchronized (intakeLock)`: public ticket ek ek karke bante hain, to double submit ko pehla ticket mil jaata hai. Ye sirf ek server par kaam karta hai.

`website` field: CSS se screen ke bahar hai, insaan ko dikhti nahi. Bhari hui mili to bot hai, kuch save nahi hota. Isi ko **honeypot** kehte hain.

## normalizeSpaces

`"My   problem"` aur `"my problem"` ek hi maane jaate hain, warna extra space se duplicate check bypass ho jaata.

## Saare Endpoints

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
