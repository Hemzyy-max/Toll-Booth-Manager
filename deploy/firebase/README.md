# Firebase deployment files

Everything needed to publish this project on a public `https://<project>.web.app`
address. The complete explanation, with the phone-only path and all the
limitations, is in [`../../docs/DEPLOY_PUBLIC.md`](../../docs/DEPLOY_PUBLIC.md).

| File | Purpose |
|---|---|
| `firebase.json` | **Option A** — Hosting serves `public/` and forwards `/api/**` to the Cloud Run service `tollbooth-web` in `asia-south1` |
| `firebase.demo.json` | **Option B** — Hosting only, serves `public-demo/` (page + browser demo backend) |
| `.firebaserc.example` | Copy to `.firebaserc` and put your project id inside (`.firebaserc` is git-ignored) |
| `sync-public.sh` | Copies `resources/static/*` into `public/` and `public-demo/` and adds the demo script tag |
| `deploy-full.sh` | Option A : `gcloud run deploy --source ../..` + `firebase deploy` |
| `deploy-demo.sh` | Option B : `firebase deploy --only hosting --config firebase.demo.json` |
| `demo-backend.js` | The browser backend used by option B (answers `/api/...` without a server) |
| `public/` | The real page, deployed by option A |
| `public-demo/` | The same page plus `demo-backend.js`, deployed by option B |

Quick start:

```bash
cd deploy/firebase
FIREBASE_PROJECT=YOUR-PROJECT-ID ./deploy-demo.sh     # free static demo
FIREBASE_PROJECT=YOUR-PROJECT-ID ./deploy-full.sh     # real Java backend (Cloud Run, billing needed)
```

Nothing here contains a password, token or key, so the folder is safe to commit.
