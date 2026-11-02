# Design Guide

This describes the design as it exists in the code: every font, color, component and page.

## The idea

SupportDesk is a tool people use all day, so the design stays out of the way:

- **One look everywhere.** The public site and the agent console use the same colors, type and components.
- **Color means something.** Almost everything is neutral. Blue marks actions and links. Blue, amber and green mark ticket status.
- **Plain words.** "Submit a request", not "Lodge an inquiry". "Password", not "Passkey".
- **Nothing decorative.** No gradients, glows or entrance animations.

## Where the design lives

| File | Purpose |
|---|---|
| `static/css/app.css` | The one stylesheet, on top of Bootstrap. All colors are variables at the top. |
| `templates/fragments/layout.html` | Shared pieces: page head, logo, navigation, status label, toast, scripts |
| `static/js/app.js` | Shared behavior: toasts and the live connection |
| `static/js/queue.js` | Queue page only: timers, sorting and quick search |

To change a color or style, edit `app.css` once and every page follows. There is no build step for the CSS.

## Fonts

| Font | Used for | Weights | Source |
|---|---|---|---|
| **Plus Jakarta Sans** | All text | 400, 500, 600, 700 | Google Fonts |
| **System monospace** (`.mono`, `.ticket-id`, `.timer`) | Ticket IDs, usernames, timers | Inherited | Built into the device |

**Why Plus Jakarta Sans:** it's clean and friendly, and stays readable at the small sizes used in tables.

**Why monospace for IDs and timers:** every character has the same width, so `TKT-001` lines up in a column, and a ticking timer doesn't jiggle.

**Fallback:** `system-ui`, `-apple-system`, `Segoe UI`, `sans-serif`.

### Weights

| Weight | Used for |
|---|---|
| 400 | Body text |
| 500 | Customer names, nav links, status labels |
| 600 | Form labels, buttons, table headers, section titles |
| 700 | Page titles and headings, large numbers |

### Sizes

| Element | Size |
|---|---|
| Landing headline | 34 to 52px, grows with the screen |
| Dashboard number | 28px |
| Page title | 22px |
| Section title, body text | 15px |
| Table text, nav links, buttons | 14px |
| Table headers, labels, status, ticket IDs, help text | 13px |

## Colors

### Neutrals and brand

| Variable | Hex | Used for |
|---|---|---|
| `--page` | `#f8fafc` | Page background |
| `--surface` | `#ffffff` | Cards, navigation, inputs |
| `--border` | `#e2e8f0` | Card and table borders |
| `--border-strong` | `#cbd5e1` | Input and outline button borders |
| `--ink` | `#0f172a` | Headings and important values |
| `--ink-soft` | `#334155` | Body text |
| `--muted` | `#64748b` | Secondary text |
| `--primary` | `#2563eb` | Main buttons, logo |
| `--primary-dark` | `#1d4ed8` | Links, ticket IDs, button hover |
| `--primary-soft` | `#eff6ff` | Active nav item, active sort button |

### Ticket status

The same meaning on every page: blue is waiting, amber is being worked on, green is done. Every status has a text label, so color is never the only signal.

| Status | Text | Background | Contrast | Chart color |
|---|---|---|---|---|
| Open | `#1d4ed8` | `#eff6ff` | 6.16 | `#2563eb` |
| In progress | `#92400e` | `#fffbeb` | 6.84 | `#d97706` |
| Resolved / Closed | `#166534` | `#f0fdf4` | 6.81 | `#16a34a` |

### Other

| Use | Value |
|---|---|
| Field errors | `#b91c1c` |
| Error notice | `#991b1b` on `#fef2f2` |
| Live dot | `#16a34a` when connected, `#94a3b8` when not |

## Shape

| Element | Radius |
|---|---|
| Cards | 12px |
| Buttons, inputs, nav items | 8px |
| Status labels | 6px |
| Avatars | Circle |

Cards have a 1px border and no shadow. The only shadow in the product is on the landing page's example queue, to lift it off the page.

## Layout

- Pages use Bootstrap's `.container` with 2rem of space at the top, so content lines up with the navigation.
- Forms use a narrow 640px column (`.page-narrow`), since one column is easier to fill in.
- Sign in and error pages center a 400px card.
- Ticket pages use two columns: the story on the left, actions and customer details on the right.

## Components

| Component | Class | Notes |
|---|---|---|
| Card | `.card-surface` | Base of every page. `.card-body-pad` adds 20px of padding. |
| Button | `.btn-primary`, `.btn-outline`, `.btn-quiet` | One main action per area, the rest stay quiet |
| Status label | `layout :: status(value)` | Turns `IN_PROGRESS` into "In progress" |
| Ticket ID | `.ticket-id` | Monospace link that never wraps |
| Table | `.data-table` | Sentence-case headers and light row lines |
| Toolbar | `.toolbar` | Search, status filter and sort above a table |
| Sort buttons | `.sort-group`, `.sort-btn` | Choice is remembered across live reloads |
| Live status | `.live-status` | Shows the real connection state: Connecting, Live or Reconnecting |
| Timer | `.timer` | Time since the ticket was claimed |
| Note | `.note` | One entry in a ticket's activity list |
| Dashboard tile | `.kpi`, `.kpi-label`, `.kpi-value`, `.kpi-note` | Number with context, always neutral ink. The first three open the matching ticket list. |
| Person | `.person`, `.tag` | Avatar, name and username. Tags mark the role, and red marks a disabled account. |
| Admin sidebar | `.is-sidebar` on the navigation | Admins only, from 992px up. Same links as the top bar, arranged by CSS. |
| Notice | `.notice-error`, `.notice-info`, `.notice-success` | Message inside a page |
| Toast | `layout :: toast(message, type)` | Pop-up at the top right, closes after 4.5 seconds |

## Admin dashboard

The page answers an admin's questions in order of urgency, and every number comes from real data.

| Question | Form | Why |
|---|---|---|
| How much work is there? | Four tiles: Open, In progress, Resolved, Average time to resolve | Headline numbers don't need a chart, and each has a line of context |
| Is the backlog growing? | Paired columns of new vs resolved per day, 14 days | Change over time, compared side by side. Hover or focus a day for its exact numbers. |
| How does the work split? | Thin segmented bar with a labeled list | Reads as one whole, and the list carries the figures. A donut is harder to read accurately. |
| Who needs help right now? | "Waiting longest" table: open, unclaimed, oldest first | Turns the page into a to-do list |
| Is anyone overloaded? | Agent table, with active tickets as ranked bars | Counts active tickets only, because closed tickets aren't workload |
| What just happened? | Activity feed, latest 8 | Plain sentences: "Arjun Verma resolved TKT-148" |

Chart rules followed:

- Numbers and labels use text colors, and the colored swatch beside them carries the meaning.
- Columns are at most 12px wide with 4px rounded tops, and each day has a 2px gap between its pair.
- Only the peak and zero are marked on the axis, and only every third date is labeled.
- All charts are plain HTML and CSS with text alternatives for screen readers, so the page loads no chart library.
- Blue and green pass the color check (`dataviz` skill validator) for colorblind readers. The status split's green and amber are closer (6.2), which is allowed because each also has a text label.

## Icons

**Bootstrap Icons** 1.11.3, used sparingly: the logo, search, stopwatch and landing page features. Decorative icons are hidden from screen readers.

The **blue shield with a check mark** is the logo and the browser-tab icon.

## Motion

Almost none. A toast fades in, and timers tick. Everything is switched off for people who set "reduce motion" on their device.

## Live updates

The queue and dashboard refresh when a ticket changes, with one exception: **if an agent is typing in a field, the page doesn't reload**. The status text changes to "New updates, refresh to see them", so nobody loses what they were typing.

## Pages

| Page | Path | Main idea |
|---|---|---|
| Landing | `/` | For customers only: submit or track a request, and how it works. No staff links. |
| Submit a request | `/portal/inquiry` | One short form, no account |
| Track a request | `/portal/track` | Two fields, then status and updates |
| Admin sign in | `/admin-login` | Two panels, dark. Administrators only. |
| Staff sign in | `/staff-login` | Two panels, blue. Staff only. |
| Create agent account | `/signup` | Password rules tick off as they are met |
| Queue | `/tickets` | Table with search, filter and sort |
| New ticket | `/tickets/new` | Form with errors under each field |
| Ticket | `/tickets/{id}` | Story on the left, update form on the right. Other staff see "Being handled" instead of the form. |
| Your profile | `/profile` | Editable details and password on the left, your work on the right. Admins also get shortcuts to admin tools. |
| Staff | `/admin/staff` | Everyone who can sign in, with reset and disable actions. A temporary password appears once, in a blue card. |
| Add staff | `/admin/staff/new` | Short form with a role choice |
| Dashboard | `/admin/dashboard` | What needs attention: key numbers, new vs resolved, longest waits, workload, activity |
| All tickets | `/admin/tickets` | Full list with who it's assigned to |
| Ticket (admin) | `/admin/tickets/{id}` | Read-only view, with a link to edit |
| Errors | any | Clear message for 403, 404, 413 and others, with a way home |

## On phones

Checked at 390px wide, with no sideways page scrolling.

- Navigation collapses into a menu button.
- Filters and number tiles stack.
- Tables hide less important columns so **Ticket, Subject and Status always fit**. Customer hides below 576px, and time in progress and "assigned to" hide below 768px.
- The admin sidebar becomes the normal menu button below 992px. Dashboard cards stack below 1200px, and "Waiting longest" shows Customer only from 1400px, so the wait time is never cut off.

## Accessibility

**Done:**

- Text and status labels all pass the 4.5:1 contrast minimum. Body text is 9.90, secondary text is 4.55 to 4.76, and the main button is 5.17.
- Every form field has a connected label, and errors appear right under the field.
- Forms use real buttons and native inputs, so the keyboard works.
- Toasts and live status are announced to screen readers.
- User-entered text goes through `th:text`, so it is escaped.

**Still to improve:**

- Placeholder text is 2.56:1. Nothing relies on it, since every field also has a label.
- A full screen-reader test hasn't been done.

## Adding a page

1. Use the head and navigation fragments:

   ```html
   <head th:replace="~{fragments/layout :: head('Page title')}"></head>
   <nav th:replace="~{fragments/layout :: appNav('queue')}"></nav>
   ```

2. Put content in `<main class="container page">`, inside `.card-surface` cards.
3. End the body with the scripts fragment:

   ```html
   <th:block th:replace="~{fragments/layout :: scripts}"></th:block>
   ```

4. Add any new styles to `app.css`, not to the page.

## Back links

Every inner page has a back link at the top, using `.back-link`.
