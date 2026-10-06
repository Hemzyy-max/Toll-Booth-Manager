# Real Time Web Application — Login System and Notification Centre

This document explains the web layer that was added on top of the OOPJ console
project: a **real time login system** with a **notification centre** for the toll
payments, written in **Core Java only** and deployed as a public web application.

> **No Spring Boot, no Node.js, no database, no external library.**
> The web server is the one that is built into the JDK
> (`com.sun.net.httpserver`), the passwords are hashed with `javax.crypto`
> (PBKDF2), and the live updates use Server Sent Events, which every browser
> understands. **Not a single dependency has to be downloaded.**

---

## Table of contents

1. [What was added](#1-what-was-added)
2. [Architecture](#2-architecture)
3. [How the real time part works](#3-how-the-real-time-part-works)
4. [The login system](#4-the-login-system)
5. [The notification centre](#5-the-notification-centre)
6. [The payment flow, end to end](#6-the-payment-flow-end-to-end)
7. [REST API reference](#7-rest-api-reference)
8. [Live events reference](#8-live-events-reference)
9. [Roles and permissions](#9-roles-and-permissions)
10. [How to run it](#10-how-to-run-it)
11. [How it is deployed in public](#11-how-it-is-deployed-in-public)
12. [Files created at run time](#12-files-created-at-run-time)
13. [Security checklist](#13-security-checklist)
14. [Verification performed](#14-verification-performed)
15. [Screens of the application](#15-screens-of-the-application)
16. [Viva questions about the web layer](#16-viva-questions-about-the-web-layer)

---

## 1. What was added

| Requirement | How it is met |
|---|---|
| **Real time login system** | `/api/login` verifies a PBKDF2 salted hash, creates a random session token, and every browser is told about the login instantly through a live event. Brute force attempts are counted and locked, sessions have idle + absolute expiry, and every login / logout is written to an audit file. |
| **Notification centre for the payment** | Every payment (success or failure), every login, every password change and every FASTag event creates a notification. It appears in the bell panel, on the notification page and as a toast in every open browser **at the same moment**. |
| **Deployed in public** | The server binds `0.0.0.0:<PORT>` (port from `PORT`, `--port` or 3000) and serves one self-contained site, so it can be published by any host or reverse proxy. In this workspace it is live on the sandbox preview URL. |
| **Still OOPJ** | The toll logic is exactly the classes of the academic project: `Vehicle` + `Car`/`Bike`/`Truck`/`Bus`, `calculateToll()`, `PaymentProcessor` + `CashPayment`/`UPIPayment`/`FastagPayment`, `TollBooth`, `TollTransaction`, `Barrier`, `transactions.txt`. |

The console application is **untouched and still works**:

```bash
java -cp out tollbooth.TollBoothManager      # menu driven console project
java -cp out tollbooth.web.WebLauncher       # real time web application
```

---

## 2. Architecture

```text
                BROWSER (single page application)
   index.html + styles.css + app.js   (plain HTML/CSS/JS, no framework)
        |                                   ^
        |  fetch()  POST /api/login ...      |  EventSource  GET /api/events
        v                                   |  (Server Sent Events, live push)
   ==================================================================
                     JAVA WEB SERVER (JDK HttpServer)
   ApiHandlers : LoginHandler LogoutHandler SessionHandler PasswordHandler
                 EventsHandler PaymentsHandler StatsHandler VehiclesHandler
                 RfidSimulateHandler NotificationsHandler
                 UsersHandler AuditHandler        StaticHandler
   ==================================================================
        |                     |                         |
        v                     v                         v
   WebContext  ------>  UserStore (PBKDF2 hashes)   AuditLog (data/audit.log)
        |                SessionManager (tokens)
        |                LoginGuard (brute force)
        |                EventHub  (live connections)
        |                NotificationCentre
        |
        v
   TollPaymentService  ------>  TollBoothEngine
        |                              |
        |                              v
        |                    OOPJ CONSOLE PROJECT (unchanged)
        |                    TollBooth  Vehicle/Car/Bike/Truck/Bus
        |                    PaymentProcessor implementations  Barrier
        |                    HashMap<String,Vehicle>  ArrayList<TollTransaction>
        |                    transactions.txt
```

Two design rules keep the code clean:

* **Separation of duties** — the handlers only read the request and write the
  answer; all the logic is in the service classes (`TollPaymentService`,
  `UserStore`, `SessionManager`, `NotificationCentre`).
* **Reuse instead of rewrite** — the web layer calls `TollBooth.calculateToll()`,
  `TollBooth.recordTransaction()` and the `PaymentProcessor` objects, so the
  web application cannot disagree with the console application about a toll.

---

## 3. How the real time part works

### Server Sent Events (SSE)

The browser opens **one** long lived HTTP request:

```javascript
const stream = new EventSource('/api/events?token=' + token);
stream.addEventListener('payment', (e) => { const p = JSON.parse(e.data); ... });
stream.addEventListener('notification', (e) => { ... });
```

The Java side (`EventHub`) keeps every open connection in a
`CopyOnWriteArrayList` and writes a small frame whenever something happens:

```text
id: 42
event: payment
data: {"receiptId":"RCPT1002","vehicleNumber":"TN01AB1234","paidAmountText":"Rs.50", ...}
```

Why SSE and not WebSocket?

| Point | SSE | WebSocket |
|---|---|---|
| Library needed | none (part of HTML5 + JDK) | extra library for Java |
| Direction | server → browser (what a notification centre needs) | both directions |
| Reconnect | automatic in the browser | manual |
| Fits "no framework" rule | yes | no |

Every connected browser is served by **its own thread** which copies frames from
a per-client queue (`LinkedBlockingQueue`), so one slow tab cannot slow down the
others; a queue that overflows disconnects that tab only (memory protection).
A comment line (`: ping`) is written every second so that proxies do not close an
idle stream, and the last 100 events are replayed for a browser that reconnects.

### What is pushed live

| Event name | When |
|---|---|
| `payment` | a toll was collected (receipt, vehicle, amount, mode, operator) |
| `payment-failed` | a payment was refused (barrier stayed **CLOSED**) |
| `notification` | a new entry of the notification centre |
| `notification-cleared` / `notifications-read` | the centre was emptied / marked read |
| `login` / `logout` | somebody signed in or out |
| `login-failed` | a wrong password was typed (administrators are warned) |
| `rfid-scan` | the RFID reader detected a tag |
| `vehicle-changed` | a FASTag wallet was recharged, blocked or unblocked |
| `shutdown` | the server is stopping |

---

## 4. The login system

### Passwords

```java
public static Hash hash(String password)                  // new random salt + PBKDF2
public static boolean verify(String password, String salt, String hash, int iterations)
```

* Algorithm : **PBKDF2WithHmacSHA256**, 120 000 iterations, 16 byte random salt,
  256 bit key (from `javax.crypto`, part of the JDK).
* The password is **never stored**, only `salt + hash` in `data/users.json`.
* The comparison uses `MessageDigest.isEqual()` (constant time), so the answer
  time cannot be used to guess the hash.
* A wrong username and a wrong password give the **same** message, so nobody can
  discover which accounts exist (and a dummy hash is computed to keep the timing
  equal).

### Sessions

| Property | Value |
|---|---|
| Token | 32 random bytes from `SecureRandom`, URL safe base64 |
| Stored | in the server memory only (`ConcurrentHashMap`) |
| Sent by the browser | `Authorization: Bearer <token>` and, for the live stream, `?token=` |
| Idle timeout | 30 minutes without a request |
| Absolute timeout | 8 hours, however active |
| Revoked when | logout, password change, role change, account disabled, server stop |

### Brute force protection (`LoginGuard`)

5 wrong passwords for the same **username + IP address** → the pair is locked for
60 seconds (HTTP 429 with the seconds left). A successful login clears the
counter. Old counters are removed every minute, so the map cannot grow.

### Password policy

Minimum 8 characters, at least one letter and one digit. An administrator can
create an account with a temporary password and mark it
`mustChangePassword` — the user then gets a private notification telling him to
change it, and `POST /api/password` allows that change (with the other sessions
of that user closed automatically).

### Default accounts (created on the first start only)

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@123` | ADMIN |
| `operator` | `Operator@123` | OPERATOR |

They can be replaced with environment variables, so no password lives in the code
history of a real deployment:

```bash
TOLLBOOTH_ADMIN_PASSWORD='...' TOLLBOOTH_OPERATOR_PASSWORD='...' java -cp out tollbooth.web.WebLauncher
```

---

## 5. The notification centre

A notification has a **level** (`INFO`, `SUCCESS`, `WARNING`, `DANGER`), a
**category** (`payment`, `security`, `fastag`, `system`) and an **audience**
(`ALL`, `ADMIN`, or one `USER`). The audience is checked on the server, so an
operator can never read an administrator-only alert.

| Notification | Level | Audience | Created when |
|---|---|---|---|
| Toll collected | SUCCESS | ALL | every successful payment |
| Payment failed — barrier stayed closed | WARNING | ALL | short cash, empty FASTag wallet, … |
| FASTag wallet almost empty | DANGER | ADMIN | balance under Rs.150 |
| Collection milestone reached | INFO | ALL | every Rs.1000 collected |
| User signed in / signed out | INFO | ALL | login / logout |
| Failed login attempt | DANGER | ADMIN | wrong password |
| Password changed / reset | SUCCESS / WARNING | ADMIN | password events |
| New account created, account enabled/disabled, role changed | INFO / WARNING | ALL | administrator actions |
| New business day (counters reset) | INFO | ALL | date change |

The centre keeps the last 300 notifications in memory, pushes each new one to all
open browsers, writes it to `data/audit.log`, shows an unread counter on the bell
icon, and offers *mark all read*, *filter by level* and (administrators only)
*empty the centre*.

---

## 6. The payment flow, end to end

```text
OPERATOR presses "Collect toll and open barrier"
        |
        v
POST /api/payments  { rfidTag, paymentMode, amount }
        |
        v
ApiHandlers.PaymentsHandler.requireSession()       401 if not logged in
        |
        v
TollPaymentService.processPayment()                synchronized (one vehicle at a time)
  1. TollBooth.searchVehicleByRFID()              HashMap lookup, UnknownRFID -> 400
  2. vehicle type rule                            a Car/Bike cannot use FASTag -> 402
  3. TollBooth.calculateToll(vehicle)             runtime polymorphism -> Car 50, Truck 150
  4. amount validation                            short cash -> 402, barrier stays closed
  5. PaymentProcessor.processPayment()            Cash / UPI / FASTag (interface)
  6. TollBooth.recordTransaction()                TXN id + ArrayList + transactions.txt
  7. TollBooth.openBarrier() / closeBarrier()     the Barrier device class
  8. PaymentRecord                                dashboard ledger
  9. EventHub.broadcast("payment", ...)           LIVE to every browser
 10. NotificationCentre.publish(...)              bell + toast + audit log
 11. AuditLog.record("PAYMENT", ...)              data/audit.log
        |
        v
201 Created { payment, receipt }  ->  the receipt window opens in the browser
```

When a payment is refused, `payment-failed` is pushed instead, a `FAILED` row is
appended to `transactions.txt`, a WARNING notification is created, and the
barrier output says **CLOSED** — exactly the behaviour required by the academic
project.

---

## 7. REST API reference

All answers are JSON. `Auth` shows who may call the end point.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/login` | – | `{username, password}` → `{token, user, expiresInMinutes}` |
| POST | `/api/logout` | any | end the session |
| GET | `/api/session` | any | current user, tab count, unread count |
| POST | `/api/password` | any | `{currentPassword, newPassword}` |
| GET | `/api/events` | any | **the SSE live stream** |
| GET | `/api/payments?limit=&search=` | any | recent payments + dashboard stats |
| POST | `/api/payments` | any | collect a toll |
| GET | `/api/stats` | any | KPIs, hourly counts, modes, sessions (admin) |
| GET | `/api/vehicles` | any | vehicles, FASTag balances, rates |
| POST | `/api/vehicles/fastag` | recharge: any · enable/disable: admin | wallet actions |
| GET | `/api/rfid/simulate` | any | the reader detects a tag by itself |
| GET | `/api/notifications?limit=` | any | my notifications + unread count |
| POST | `/api/notifications/read` | any | mark mine as read |
| POST | `/api/notifications/clear` | admin | empty the centre |
| GET | `/api/users` | admin | accounts |
| POST | `/api/users` | admin | `action = create \| status \| role \| reset` |
| GET | `/api/audit?limit=` | admin | the security log |

Example session with `curl`:

```bash
# 1. log in and keep the token
TOKEN=$(curl -s -X POST http://localhost:3000/api/login \
        -H 'Content-Type: application/json' \
        -d '{"username":"admin","password":"Admin@123"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)

# 2. collect a FASTag toll for the truck (10% discount)
curl -s -X POST http://localhost:3000/api/payments \
     -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d '{"rfidTag":"RFID1003","paymentMode":"FASTag"}'

# 3. watch the live stream (press Ctrl+C to stop)
curl -N "http://localhost:3000/api/events?token=$TOKEN"

# 4. read the notification centre
curl -s http://localhost:3000/api/notifications -H "Authorization: Bearer $TOKEN"
```

A refused payment answers with a useful message and the right HTTP code:

```json
{ "error": "Payment refused : Cash given (Rs.20) is less than the toll amount (Rs.100). Shortage : Rs.80" }
```

---

## 8. Live events reference

| Event | Payload (important fields) |
|---|---|
| `payment` | receiptId, transactionId, rfidTag, vehicleNumber, vehicleType, baseToll, paidAmount, discount, paymentMode, operator, paidTime |
| `payment-failed` | vehicleNumber, rfidTag, paymentMode, amount, `reason`, barrier: "CLOSED" |
| `notification` | id, level, title, message, category, audience, createdAt |
| `login` / `logout` | username, displayName, role, at |
| `login-failed` | username, attemptsLeft, clientAddress |
| `rfid-scan` | rfidTag, scannedBy, at |
| `vehicle-changed` | vehicle (with the new FASTag balance) |

---

## 9. Roles and permissions

| Action | OPERATOR | ADMIN |
|---|---|---|
| Collect a toll, view payments / stats / vehicles | ✅ | ✅ |
| Recharge a FASTag wallet | ✅ | ✅ |
| Block or unblock a FASTag wallet | ❌ 403 | ✅ |
| Read the notification centre, mark as read | ✅ (own + ALL) | ✅ (+ ADMIN alerts) |
| Empty the notification centre | ❌ 403 | ✅ |
| Create / disable accounts, change roles, reset passwords | ❌ 403 | ✅ |
| Read the audit log | ❌ 403 | ✅ |

The check happens **on the server** (`requireSession()` / `requireAdmin()`), not
only by hiding buttons in the page.

---

## 10. How to run it

### Linux / macOS

```bash
cd Toll-Booth-Manager
./run-web.sh                 # compiles, copies the web page, starts on port 3000
PORT=8080 ./run-web.sh       # another port
```

### Windows

```bat
cd Toll-Booth-Manager
run-web.bat
```

### Manually (any operating system)

```bash
javac -d out $(find tollbooth -name "*.java")     # Windows: see compile-and-run.bat
cp -r resources/static out/static                 # Windows: xcopy /E /I /Y resources\static out\static
java -cp out tollbooth.web.WebLauncher --port 3000
```

Options : `--port <n>`, `--quiet` (no request log), `--no-simulator`
(the automatic vehicle loop can be switched off for a calm demo).

Then open `http://localhost:3000` and sign in with `admin / Admin@123`
(or `operator / Operator@123`).

---

## 11. How it is deployed in public

The application is a **single self-contained process** : one JAR-less class
folder, no database, no other service. That is why it can be published in public
in several simple ways:

1. **This sandbox (already running)** — the workspace exposes the process as a
   live preview. The platform proxies the public address
   `https://<port>-<sandbox-id>.e2b.app` to port 3000 of this machine.
   Everything the page needs is loaded with **relative URLs** (`/styles.css`,
   `/api/...`), so the same build works behind any domain without a rebuild.
2. **Any small VPS / cloud virtual machine**

   ```bash
   git clone <repository> && cd Toll-Booth-Manager
   ./run-web.sh                      # listens on 0.0.0.0:3000
   ```

   Put nginx (or Caddy) in front of it for HTTPS and the certificate:

   ```nginx
   location / {
       proxy_pass http://127.0.0.1:3000;
       proxy_buffering off;                     # needed for the live event stream
       proxy_read_timeout 1h;
   }
   ```

3. **A platform that starts a process** — give it the start command
   `java -cp out tollbooth.web.WebLauncher` and the build command
   `javac -d out $(find tollbooth -name "*.java") && cp -r resources/static out/static`.
   Platforms that set a `PORT` variable work without any change, because the
   launcher reads `PORT` first.

Deployment notes that matter for this application:

* `0.0.0.0` and not `127.0.0.1`, otherwise the outside world cannot reach it.
* The live stream needs a proxy that does **not** buffer (`proxy_buffering off`).
* The server sends **no** `X-Frame-Options` header on purpose, so the page also
  works inside an embedded preview window, while the Content Security Policy
  still restricts the page to its own resources.
* Runtime data (`data/`, `transactions.txt`) is written to the working directory;
  on a host with an ephemeral disk, mount a volume there to keep the accounts and
  the audit log across restarts.

---

## 12. Files created at run time

| File | Content | Written by |
|---|---|---|
| `data/users.json` | accounts with `passwordSalt`, `passwordHash`, `passwordIterations`, role, status | `UserStore` |
| `data/audit.log` | `time \| EVENT \| actor \| address \| details` — logins, payments, resets … | `AuditLog` |
| `transactions.txt` | the toll transactions of the OOPJ project (`TXN1001;TN01AB1234;Car;45.00;FASTag;...`) | `TransactionFileManager` |

None of them is committed to Git (see `.gitignore`), so a fresh clone starts with
the two default accounts and an empty audit trail.

---

## 13. Security checklist

| Risk | Protection in this application |
|---|---|
| Password theft from the user file | PBKDF2-SHA256, random salt per user, 120 000 iterations, no plaintext anywhere |
| Timing attack on the hash comparison | `MessageDigest.isEqual()` |
| Username discovery | identical error message and equal timing for unknown user and wrong password |
| Automatic password guessing | `LoginGuard`: 5 failures per username+IP → 60 second lock (HTTP 429) |
| Session hijacking by guessing | 32 random bytes from `SecureRandom` |
| Session left open forever | sliding idle timeout 30 min + absolute 8 h + cleanup thread |
| Session stays valid after a password change / role change / disable | all sessions of that user are revoked |
| Cross site request forgery | Bearer token (never sent automatically by the browser) + origin check on unsafe methods |
| Clickjacking / injected scripts | Content Security Policy `default-src 'self'`, `object-src 'none'`, `base-uri 'none'`; no inline script, no `eval` |
| MIME confusion | `X-Content-Type-Options: nosniff` |
| Path traversal in the web server | the static handler rejects `..`, `\` and absolute paths |
| Huge request body (DoS) | request body limited to 1 MB (HTTP 413) |
| One slow browser blocking others | one thread per connection, per-client bounded queue |
| Corrupted totals by parallel requests | `synchronized` payment service and `synchronized` toll booth methods |
| Privilege escalation | role checks on the server for every admin end point |
| Silent abuse | every security relevant action is written to `data/audit.log` |

**Known limitations (honest list for the viva).** The sessions live in the memory
of one process, so a restart logs everybody out (a real system would use a
database or a shared cache). The application speaks plain HTTP and relies on the
host / reverse proxy for HTTPS. There is no e-mail or SMS, so "notification"
means an in-app real time notification. User accounts are stored in a JSON file,
which is fine for a laboratory project but not for thousands of users.

---

## 14. Verification performed

Everything in this document was executed and not just written:

| Test | Result |
|---|---|
| Compile (Java 17 and `-source 8` for older JDKs) | 0 errors |
| `GET /`, `/styles.css`, `/app.js`, `/favicon.svg` | 200 with the correct content type |
| Login with a wrong password | 401, `4 attempt(s) left` → `3 attempt(s) left` → lock after 5 |
| Login with the right password, `GET /api/session` | token issued, user, role and counters correct |
| `GET /api/stats` without a token | 401 "Please log in to continue." |
| Cash payment for a car | 201, receipt `RCPT1002`, `TXN1002`, Rs.50 |
| FASTag payment on a **car** | 402 "A Car does not carry a FASTag" |
| Cash **less** than the toll | 402 "Shortage : Rs.80", `FAILED` line in `transactions.txt`, barrier CLOSED |
| FASTag payment on a truck | 201, Rs.150 → **Rs.135** (10% discount) |
| Unknown RFID / negative amount | 400 with the custom exception message |
| **Live stream while a payment happens** | the open SSE connection received `payment`, `notification`, `payment-failed` frames immediately |
| Operator calling `/api/users` and `/api/audit` | 403 |
| Operator disabling a FASTag wallet | 403 |
| Foreign origin POST without a token | 403 (CSRF guard) |
| Browser test with a simulated DOM (20 checks: login, KPIs, charts, toll preview, real payment, receipt window, notification list, audit list, user creation) | 20/20 passed, **0 JavaScript errors** |
| Every element id used by `app.js` exists in `index.html` | 94/94 |
| `data/users.json` contains no plaintext password | confirmed |

---

## 15. Screens of the application

| Page | Content |
|---|---|
| **Sign in** | username, password with a show/hide button, the lockout message, and a short explanation of what the system does |
| **Dashboard** | payments today, collected amount and average ticket, registered vehicles and RFID scans, barrier state, hourly bar chart, payment mode doughnut, vehicle types, latest payment, recent payments table |
| **Toll payments** | RFID selector + *Simulate scan* (the reader detects by itself), Cash/UPI/FASTag segmented control (FASTag is disabled for cars and bikes), live toll preview with the discount, *Collect toll and open barrier*, receipt window, searchable payment history |
| **Vehicles & FASTag** | one card per vehicle with owner, tag, toll, trips, wallet balance, recharge / block buttons |
| **Notification centre** | filter chips by level, unread highlighting, mark all read, empty (admin) |
| **Audit log** (admin) | the last lines of `data/audit.log` with the file path, failed events marked in red |
| **User accounts** (admin) | create account form, list of accounts with enable/disable, change role, reset password |
| **My account** | profile, last login, password change form |
| **Everywhere** | the bell icon with the unread counter and the drop-down panel, connection indicator of the live stream, online user count, server clock, toast messages, and the receipt/print window |

---

## 16. Viva questions about the web layer

**1. Which web server did you use?**
The HTTP server built into the JDK (`com.sun.net.httpserver.HttpServer`). No
external framework is needed, so the project stays "Core Java only".

**2. How does the browser get live updates?**
With Server Sent Events: one `EventSource` connection to `/api/events`. The
server keeps the connection open and writes a small JSON frame for every event.
The JavaScript adds a listener per event name, so a payment appears in other
browsers within milliseconds.

**3. Why is the session token sent in the query string for the live stream?**
Because the browser `EventSource` API cannot send custom headers. Every other
call uses the standard `Authorization: Bearer` header.

**4. How are passwords stored?**
Never as text: PBKDF2WithHmacSHA256 with a random 16 byte salt and 120 000
iterations. Only the salt and the hash are kept in `data/users.json`.

**5. What happens after five wrong passwords?**
`LoginGuard` locks that username + IP address for 60 seconds and answers HTTP 429
with the remaining seconds; an administrator also receives a DANGER notification.

**6. How is one request of a logged in user checked?**
`ApiHandler.requireSession()` reads the token, asks `SessionManager` for a valid
session (idle + absolute expiry) and returns it. `requireAdmin()` adds the role
check. Both are written **once** in the parent class, so every end point is
protected by the same code.

**7. Where do the toll rates of the web page come from?**
From the same Java classes as the console project. The browser only shows the
preview; the amount is always calculated again on the server by
`TollBooth.calculateToll()`.

**8. Why is `processPayment()` synchronized?**
Because the automatic lane simulator thread and the web requests can pay at the
same moment. Inside the method the collection totals and the FASTag balance are
updated, and the barrier is opened; `synchronized` keeps those updates correct.

**9. How is a notification different from an audit line?**
A notification is for the users of the application (bell, toast, page) and can be
marked read or cleared. An audit line is permanent proof in `data/audit.log` and
cannot be changed from the page.

**10. How would you deploy this on a real server?**
Compile, copy the class files and `resources/static` (or the `out` folder) to the
machine, start `java -cp out tollbooth.web.WebLauncher` (the port comes from the
`PORT` variable), and put nginx or Caddy in front for HTTPS with
`proxy_buffering off` so the live stream keeps flowing.
