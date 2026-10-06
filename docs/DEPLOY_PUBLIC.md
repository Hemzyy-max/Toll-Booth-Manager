# Publishing the Toll Booth Manager on the Internet with Firebase

This document is the complete, honest guide for putting the real-time web
application on a public `https://` address with a **free Google account**.
It also lists every important limitation, so nothing on the public link can
mislead an examiner, a teacher or a visitor.

Two ways are described. Choose **one**:

| | Option A — the real application | Option B — the free static demo |
|---|---|---|
| Link shape | `https://YOUR-PROJECT.web.app` | `https://YOUR-PROJECT.web.app` |
| Backend | the real **Java** server on Cloud Run | none: `demo-backend.js` runs in the browser |
| Login | real (PBKDF2 hashes, session tokens, audit file) | checked in the browser, **not** real security |
| Cost | needs a Cloud Billing account (free quota is normally enough) | free plan, no card |
| Tools | `gcloud` + `firebase` CLI | `firebase` CLI only |
| Use it for | the real project link of the report | a quick, always-on demo that never sleeps |

> **Why are there two options?** Firebase Hosting can only serve **static
> files** (HTML, CSS, JavaScript, images). It cannot run a Java program. To keep
> the real Java backend, the page must be served by Hosting and every `/api/**`
> request forwarded to **Cloud Run**, which is exactly what option A does.

---

## 0. What the two options do with your files

```
deploy/firebase/
    firebase.json         option A : Hosting + rewrite /api/** -> Cloud Run (asia-south1)
    firebase.demo.json    option B : Hosting only, serves public-demo/
    .firebaserc.example   copy to .firebaserc and put your project id inside
    sync-public.sh        copies resources/static/* into public/ and public-demo/
    deploy-full.sh        option A : Cloud Run + Hosting in two commands
    deploy-demo.sh        option B : one command, free plan
    public/               the REAL page (talks to the Java server through /api/**)
    public-demo/          the same page + demo-backend.js (browser demo backend)
    demo-backend.js       the offline demo backend (source of truth)
Dockerfile                builds the Java application into a container (Cloud Run)
.dockerignore             keeps the container build small
```

Nothing in these files contains a password or a key, so the whole folder can be
committed and pushed. `.firebaserc`, `.firebase/` and `firebase-debug.log` stay
outside git (see `.gitignore`).

---

## 1. One-time preparation (both options)

1. Create the project:
   * open <https://console.firebase.google.com> → **Add project** → give it a
     name (for example `toll-booth-manager`) → Google Analytics can be disabled;
   * Firebase creates a Google Cloud project with the **same name**, and the
     *project id* (for example `toll-booth-manager-1a2b3`) is what the commands
     need. It is shown in **Project settings → General → Project ID**.
2. Install the Firebase CLI on a computer — or, **fully from a phone**, open
   **Cloud Shell**: <https://shell.cloud.google.com>. It is a terminal in the
   browser, already logged in with your Google account, and both `gcloud` and
   `firebase` are normally already installed there.
3. Get the project onto the machine that runs the commands:
   `git clone <your repository>` and then `cd Toll-Booth-Manager`, or upload the
   project folder with the Cloud Shell **Upload** button (the ⋮ menu).

Log in once:

```bash
firebase login              # normal computer: opens the browser
firebase login --no-localhost   # Cloud Shell / phone: prints a link, you open it
                                # and paste the code that Google shows back here
```

`firebase projects:list` must now print your project.

---

## 2. Option A — the real Java application in public

### 2.1 What you get

```
https://YOUR-PROJECT.web.app/            -> the page   (Firebase Hosting, static)
https://YOUR-PROJECT.web.app/api/health  -> {"status":"UP", ...}
https://YOUR-PROJECT.web.app/api/login   -> the REAL login (Java, PBKDF2 hashes)
```

Both parts are in the **same project** and the **same URL**, so the browser
never hits a cross-origin problem.

### 2.2 Switch on billing for the project

Cloud Run belongs to the Google Cloud side of the project and needs a
**Cloud Billing account** (a card). The free quota (2 million requests per
month, 180 000 vCPU-seconds) is far more than a college demo uses, but billing
must be linked before Cloud Run can start:

* Firebase console → ⚙ **Usage and billing** → **Details & settings** →
  **Modify plan** → **Blaze (pay as you go)** → choose the billing account.
* In the Google Cloud console the menu item is **Billing → Link a billing
  account**.

### 2.3 Deploy (two commands, or two scripts)

```bash
cd deploy/firebase
FIREBASE_PROJECT=YOUR-PROJECT-ID ./deploy-full.sh
```

The script runs exactly these two steps:

```bash
# STEP 1 : the Java application -> a container image -> Cloud Run (Mumbai)
gcloud run deploy tollbooth-web \
    --source ../.. --project YOUR-PROJECT-ID --region asia-south1 \
    --allow-unauthenticated --port 8080 --memory 512Mi --cpu 1 \
    --timeout 3600 --max-instances 1 --quiet

# STEP 2 : the page -> Firebase Hosting, with the /api/** rewrite of firebase.json
firebase deploy --only hosting --config firebase.json --project YOUR-PROJECT-ID
```

`--source ../..` means *Google builds the `Dockerfile` for you on Cloud Build*,
so Docker does not have to be installed on your own machine. The first build
takes a few minutes.

Useful variants:

```bash
KEEP_WARM=1 FIREBASE_PROJECT=YOUR-PROJECT-ID ./deploy-full.sh   # min-instances 1
REGION=us-central1 FIREBASE_PROJECT=YOUR-PROJECT-ID ./deploy-full.sh
```

* `KEEP_WARM=1` keeps one container running, so the accounts created at run time
  and the audit log survive idle periods (a little money per month).
* `asia-south1` (Mumbai) is supported for Hosting → Cloud Run rewrites and is the
  closest region to India; `us-central1` is the default of many examples.

### 2.4 Check it

```bash
curl https://YOUR-PROJECT.web.app/api/health      # {"status":"UP", ...}
```

Then open `https://YOUR-PROJECT.web.app` on the phone, sign in with
`admin / Admin@123`, pay a toll and watch the notification bell. Open
`https://YOUR-PROJECT.web.app/api/payments` — it must answer `401`, which proves
the request really reached the Java server (a static page could not answer that).

### 2.5 Limitations you must know (and can tell the examiner)

| Limitation | Reason | Effect |
|---|---|---|
| The live stream reconnects about every 60 seconds | Hosting closes any dynamic request after **60 s** (Cloud Run itself allows an hour) | the page reconnects by itself (`EventSource` does that automatically); the board stays correct, but the Cloud Run log shows a 499/504 line per minute |
| Sessions and created accounts disappear when the container is replaced | the container's `/tmp` is ephemeral and the app keeps its state in memory | use `KEEP_WARM=1` to make restarts rare; the two built-in accounts always exist again after a restart |
| Two browser tabs may be served by two containers | Cloud Run starts more instances when traffic grows (this deploy sets `--max-instances 1`, which avoids it) | with `--max-instances 1` both tabs share one process; without it, each instance has its own counter |
| The passwords `Admin@123` / `Operator@123` are known to everybody | they are the documented demo accounts | change them (see below) before sharing the link publicly |
| Anyone with the link can pay a toll | `--allow-unauthenticated` on purpose, so a phone can open the demo without a Google login | it is a coursework booth, not a payment gateway; never enter real data |

Change the starting passwords on the Cloud Run service (Variables & secrets):

```
TOLLBOOTH_ADMIN_PASSWORD    = a strong password (>= 8 characters, letter + digit)
TOLLBOOTH_OPERATOR_PASSWORD = a strong password (>= 8 characters, letter + digit)
```

Or from the CLI:

```bash
gcloud run services update tollbooth-web --region asia-south1 \
    --set-env-vars TOLLBOOTH_ADMIN_PASSWORD=YourStrongPass1,TOLLBOOTH_OPERATOR_PASSWORD=OtherStrongPass2
```

---

## 3. Option B — the free static demo (no backend, no billing)

Firebase Hosting serves the **same page** (`index.html`, `app.js`, `styles.css`)
with one extra file, `demo-backend.js`, which replaces `window.fetch` and
`window.EventSource` inside the browser. Every `/api/...` call is then answered
locally with the same data, the same toll rules and the same live events, so the
whole interface — login screen, dashboard, charts, toll form, receipt window,
notification centre, audit list, user list — works with no server at all.

### 3.1 Deploy

```bash
npm install -g firebase-tools
firebase login
cd deploy/firebase
FIREBASE_PROJECT=YOUR-PROJECT-ID ./deploy-demo.sh
```

`deploy-demo.sh` calls `sync-public.sh` (which copies the real page next to
`demo-backend.js`) and then:

```bash
firebase deploy --only hosting --config firebase.demo.json --project YOUR-PROJECT-ID
```

The link is `https://YOUR-PROJECT.web.app`. It works on the free **Spark** plan.

### 3.2 What is real and what is not, in this mode

Real: the whole user interface, the toll preview, the toll rules and rates
(Bike 30, Car 50, Bus 100, Truck 150, FASTag 10 % for Truck and Bus only), the
low-balance alert, the milestone alert, the receipt text, the tables, the charts
and the live notification stream inside one tab.

Not real: the login (it is checked in the browser — no PBKDF2, no session token,
no audit file), and the data (it lives in that one tab; nothing is shared between
visitors or devices). A **DEMO MODE** badge is therefore always displayed at the
bottom of the page, and `#loginHint` explains it on the login screen.

### 3.3 Check it

```bash
curl -I https://YOUR-PROJECT.web.app           # HTTP/2 200
```

and open it on the phone: the DEMO MODE badge must be visible, `admin/Admin@123`
must open the dashboard, and a payment must add a row and a notification.

There is also an automatic test (it uses a simulated browser, so nothing is
installed except `jsdom`):

```bash
npm install jsdom
python3 -m http.server 4010 --directory deploy/firebase/public-demo   # terminal 1
node tools/demo-smoke-test.js                                         # terminal 2
# 13/13 demo checks passed
```

---

## 4. Updating the site later

After ANY change in `resources/static/` (page, styles or script):

```bash
cd deploy/firebase
./sync-public.sh                                  # copy the page again
FIREBASE_PROJECT=YOUR-PROJECT-ID ./deploy-demo.sh # option B, or
FIREBASE_PROJECT=YOUR-PROJECT-ID ./deploy-full.sh # option A (Cloud Run + Hosting)
```

## 5. Security rules to keep

* Never put a password, token or key in the repository — the deploy scripts only
  use the project id, which is public information.
* Change the two demo passwords when the link is public; the password policy
  (at least 8 characters, one letter and one digit) is enforced by the app.
* The page sends `Authorization: Bearer <token>` only to its own origin, and the
  server refuses a state-changing request that comes from a foreign origin
  without a token; the Content Security Policy keeps the page on its own files.
* Do not publish real vehicle, owner or payment data on a coursework link.

## 6. Troubleshooting

| Message | Reason | Fix |
|---|---|---|
| `Error: Failed to get project` / `must be logged in` | the Firebase CLI has no session | `firebase login` (phone/Cloud Shell: `firebase login --no-localhost`) |
| `HTTP 404` on `/api/health` | only Hosting is deployed (option B), or the rewrite is missing | use `firebase.json` (option A) and make sure Cloud Run runs in `asia-south1` |
| `HTTP 504` after 60 seconds on `/api/events` | the Hosting request timeout, normal for a live stream | nothing to fix: `EventSource` reconnects; use `--timeout 3600` on Cloud Run so only Hosting is the limit |
| `Error: Permission denied` / `billing account` when deploying to Cloud Run | the project has no Cloud Billing account | link one (Blaze plan), then redeploy |
| `Hosting rewrite to a Cloud Run service in region X is not supported` | the region cannot be used in a rewrite | use `asia-south1` or `us-central1` |
| `404 Not Found` from Cloud Run | the service was deployed in another region or another project | `gcloud run services list --project YOUR-PROJECT-ID` |
| Preview shows **sandbox not found** | that is the Arena preview window of the sandbox, not the application | the public link from Firebase is the way to share it |

---

## 7. Ten lines you can say in the viva about the deployment

1. Firebase Hosting serves the interface; it can only serve static files, so it
   cannot run Java.
2. The real backend is packaged with the `Dockerfile` and published on Cloud Run,
   which supports Java containers.
3. A Hosting rewrite forwards `/api/**` to that container, so one public URL
   serves both the page and the API.
4. The region is `asia-south1` (Mumbai), the nearest supported region.
5. The page uses relative URLs (`/api/...`), so no code had to change for the
   deployment.
6. Live updates use Server-Sent Events; a proxy must not buffer them.
7. Hosting ends a dynamic request after 60 seconds, so the stream reconnects
   about once a minute; the page restores itself automatically.
8. The application keeps its counters in memory, so Cloud Run is deployed with
   one instance, and `KEEP_WARM=1` keeps that instance alive.
9. Accounts, sessions and the audit log live in `data/`; on a container host that
   folder is temporary, which is exactly why the two demo accounts always exist.
10. On the free plan the same page also runs as a browser demo
    (`demo-backend.js`), clearly marked DEMO MODE, so the interface can be shown
    even without a billing account.
