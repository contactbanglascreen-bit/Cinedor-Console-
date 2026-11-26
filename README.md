# Cinedor Admin Panel

Web admin panel for the Cinedor app:

- 🔔 **Notifications** – send a text or poster (big picture) push to every phone (FCM topic `all`)
- ⚙️ **App settings** – Premium popup, announcement, Join us (Telegram/Facebook), share popup,
  Audio & Dubs, maintenance mode, force update
- Login with Firebase Authentication; only the admin UID can use it

```
index.html                      ← the panel (host on GitHub Pages)
worker/cinedor-admin-worker.js  ← secure backend (Cloudflare Worker)
worker/wrangler.toml            ← worker settings
```

The private key and GitHub token live **only in the Worker as secrets** – never in this repo or the page.

## Setup (one time)

### 1. Firebase
1. Firebase Console → **Authentication → Sign-in method** → enable **Email/Password**.
2. The admin user (UID `xCbK5jp6PNf8UvTIE4mZYYLhpBj2`) must exist in Authentication → Users.
3. Project settings → **Service accounts** → **Generate new private key** → a `.json` file downloads.
   Keep it secret. Do not upload it to GitHub or send it in chat.
4. (If login says the key is blocked) Project settings → **Add app → Web** and use its `apiKey`
   in the panel's Connection settings.

### 2. Cloudflare Worker
Dashboard way: Workers & Pages → Create → Worker → paste `worker/cinedor-admin-worker.js` → Deploy.
Then Settings → Variables:

| Type | Name | Value |
|---|---|---|
| Text | `ADMIN_UID` | `xCbK5jp6PNf8UvTIE4mZYYLhpBj2` |
| Text | `FIREBASE_PROJECT_ID` | `cinedor-60aac` |
| Text | `CONFIG_REPO` | `contactbanglascreen-bit/Baizid-` |
| Text | `CONFIG_PATH` | `console/app-config.json` |
| Text | `CONFIG_REFS` | `main,ccr-c4b9015d-7d590g` |
| Secret | `FCM_SERVICE_ACCOUNT` | whole content of the service-account `.json` |
| Secret | `GITHUB_TOKEN` | fine-grained token, repo `Baizid-`, Contents: Read and write |

CLI way: `cd worker && npx wrangler deploy && npx wrangler secret put FCM_SERVICE_ACCOUNT && npx wrangler secret put GITHUB_TOKEN`

Your existing API worker does not need any change.

### 3. Panel
Repo Settings → **Pages** → Deploy from branch `main` / root. Open the Pages address,
put the Worker address in **Connection settings**, log in.
Optionally set `ALLOWED_ORIGIN` in the Worker to your Pages address.
