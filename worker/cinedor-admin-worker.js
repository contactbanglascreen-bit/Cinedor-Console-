/**
 * Cinedor Admin Worker (Cloudflare Workers)
 *
 * Secure backend for the Cinedor admin panel:
 *   POST /send    -> sends an FCM push (text or poster) to every phone (topic "all")
 *   GET  /config  -> reads console/app-config.json from the app repo
 *   POST /config  -> saves console/app-config.json (popups, premium, maintenance...)
 *   GET  /health  -> "ok"
 *
 * Every request (except /health) needs "Authorization: Bearer <Firebase ID token>" and the
 * token's user id must equal ADMIN_UID. Secrets never leave the worker.
 *
 * Secrets (wrangler secret put ...):
 *   FCM_SERVICE_ACCOUNT  full JSON of the Firebase service-account private key
 *   GITHUB_TOKEN         fine-grained token with Contents: Read & Write on the app repo
 * Vars (wrangler.toml):
 *   ADMIN_UID, FIREBASE_PROJECT_ID, CONFIG_REPO, CONFIG_PATH, CONFIG_REFS, ALLOWED_ORIGIN
 */

const JWKS_URL =
  "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

// Built-in defaults, so the only thing to add in Cloudflare is the FCM_SERVICE_ACCOUNT secret.
const DEFAULTS = {
  ADMIN_UID: "xCbK5jp6PNf8UvTIE4mZYYLhpBj2",
  FIREBASE_PROJECT_ID: "cinedor-60aac",
  CONFIG_REPO: "contactbanglascreen-bit/Baizid-",
  CONFIG_PATH: "console/app-config.json",
  CONFIG_REFS: "main,ccr-c4b9015d-7d590g",
};

let jwksCache = { keys: null, until: 0 };
let fcmTokenCache = { token: null, until: 0 };

function cors(env) {
  return {
    "Access-Control-Allow-Origin": env.ALLOWED_ORIGIN || "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Authorization, Content-Type",
    "Access-Control-Max-Age": "86400",
  };
}

function json(env, status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...cors(env) },
  });
}

function b64urlToBytes(s) {
  s = s.replace(/-/g, "+").replace(/_/g, "/");
  while (s.length % 4) s += "=";
  const bin = atob(s);
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

function bytesToB64url(bytes) {
  let bin = "";
  const arr = new Uint8Array(bytes);
  for (let i = 0; i < arr.length; i++) bin += String.fromCharCode(arr[i]);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function strToB64url(s) {
  return bytesToB64url(new TextEncoder().encode(s));
}

async function googleJwks() {
  if (jwksCache.keys && Date.now() < jwksCache.until) return jwksCache.keys;
  const r = await fetch(JWKS_URL);
  if (!r.ok) throw new Error("Could not load Google signing keys");
  const data = await r.json();
  const maxAge = Number((r.headers.get("cache-control") || "").match(/max-age=(\d+)/)?.[1] || 3600);
  jwksCache = { keys: data.keys, until: Date.now() + maxAge * 1000 };
  return data.keys;
}

/** Verifies a Firebase Auth ID token and returns its claims. */
async function verifyIdToken(token, env) {
  const parts = (token || "").split(".");
  if (parts.length !== 3) throw new Error("Bad token");
  const header = JSON.parse(new TextDecoder().decode(b64urlToBytes(parts[0])));
  const claims = JSON.parse(new TextDecoder().decode(b64urlToBytes(parts[1])));
  if (header.alg !== "RS256") throw new Error("Bad token algorithm");
  const jwk = (await googleJwks()).find((k) => k.kid === header.kid);
  if (!jwk) throw new Error("Unknown signing key");
  const key = await crypto.subtle.importKey(
    "jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]
  );
  const ok = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5", key, b64urlToBytes(parts[2]),
    new TextEncoder().encode(parts[0] + "." + parts[1])
  );
  if (!ok) throw new Error("Invalid signature");
  const now = Math.floor(Date.now() / 1000);
  const pid = env.FIREBASE_PROJECT_ID;
  if (claims.aud !== pid) throw new Error("Wrong project");
  if (claims.iss !== `https://securetoken.google.com/${pid}`) throw new Error("Wrong issuer");
  if (!claims.exp || claims.exp < now) throw new Error("Session expired, log in again");
  if (claims.iat && claims.iat > now + 300) throw new Error("Token from the future");
  return claims;
}

async function requireAdmin(request, env, perm = "notify") {
  const auth = request.headers.get("Authorization") || "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  const claims = await verifyIdToken(token, env);
  if (claims.sub === env.ADMIN_UID) return claims;
  // Moderators: staff/{uid} must be active and have the permission
  const access = await fcmAccessToken(env);
  const r = await fetch(
    `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents/staff/${claims.sub}`,
    { headers: { Authorization: "Bearer " + access } }
  );
  if (r.ok) {
    const f = (await r.json()).fields || {};
    const active = f.active?.booleanValue === true;
    const perms = (f.perms?.arrayValue?.values || []).map((v) => v.stringValue);
    if (active && perms.includes(perm)) return claims;
  }
  throw new Error("This account is not allowed to do this (admin)");
}

/** OAuth access token for FCM, signed with the service-account private key. */
async function fcmAccessToken(env) {
  if (fcmTokenCache.token && Date.now() < fcmTokenCache.until) return fcmTokenCache.token;
  if (!env.FCM_SERVICE_ACCOUNT) throw new Error("FCM_SERVICE_ACCOUNT secret is not set");
  const sa = JSON.parse(env.FCM_SERVICE_ACCOUNT);
  const now = Math.floor(Date.now() / 1000);
  const header = strToB64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = strToB64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging https://www.googleapis.com/auth/datastore",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]
  );
  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(header + "." + payload)
  );
  const assertion = header + "." + payload + "." + bytesToB64url(sig);
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + assertion,
  });
  const data = await r.json();
  if (!r.ok) throw new Error("Google login failed: " + (data.error_description || data.error));
  fcmTokenCache = { token: data.access_token, until: Date.now() + (data.expires_in - 120) * 1000 };
  return data.access_token;
}

async function sendPush(body, env) {
  const title = String(body.title || "").trim();
  const text = String(body.body || "").trim();
  const image = String(body.image || "").trim();
  const link = String(body.link || "").trim();
  const topic = String(body.topic || "all").replace(/[^a-zA-Z0-9-_.~%]/g, "") || "all";
  if (!title) throw new Error("Title is required");
  if (image && !/^https:\/\//i.test(image)) throw new Error("Poster link must start with https://");

  const notification = { title, body: text };
  if (image) notification.image = image;
  const message = {
    topic,
    notification,
    data: { title, body: text, image, link },
    android: {
      priority: "HIGH",
      notification: { channel_id: "cinedor_news", color: "#E50914", ...(image ? { image } : {}) },
    },
  };
  const token = await fcmAccessToken(env);
  const r = await fetch(
    `https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`,
    {
      method: "POST",
      headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
      body: JSON.stringify({ message }),
    }
  );
  const data = await r.json();
  if (!r.ok) throw new Error("FCM error: " + (data.error?.message || r.status));
  // Keep a copy for the app's 🔔 inbox (only messages sent to everyone).
  if (topic === "all") {
    try {
      await fetch(
        `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents/notifications`,
        {
          method: "POST",
          headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
          body: JSON.stringify({
            fields: {
              title: { stringValue: title },
              body: { stringValue: text },
              image: { stringValue: image },
              link: { stringValue: link },
              at: { timestampValue: new Date().toISOString() },
            },
          }),
        }
      );
    } catch (_) {}
  }
  return data.name;
}

function gh(env, url, init = {}) {
  if (!env.GITHUB_TOKEN) throw new Error("GITHUB_TOKEN secret is not set");
  return fetch(url, {
    ...init,
    headers: {
      Authorization: "Bearer " + env.GITHUB_TOKEN,
      Accept: "application/vnd.github+json",
      "User-Agent": "cinedor-admin-worker",
      ...(init.headers || {}),
    },
  });
}

/** Finds which branch holds the config the app reads (same order as the app). */
async function findConfig(env) {
  const refs = (env.CONFIG_REFS || "main").split(",").map((s) => s.trim()).filter(Boolean);
  for (const ref of refs) {
    const r = await gh(env, `https://api.github.com/repos/${env.CONFIG_REPO}/contents/${env.CONFIG_PATH}?ref=${encodeURIComponent(ref)}`);
    if (r.ok) {
      const data = await r.json();
      const bytes = Uint8Array.from(atob(data.content.replace(/\n/g, "")), (c) => c.charCodeAt(0));
      return { ref, sha: data.sha, text: new TextDecoder().decode(bytes) };
    }
  }
  throw new Error("Config file not found in " + refs.join(", "));
}

async function saveConfig(text, env) {
  JSON.parse(text); // must be valid JSON
  const cur = await findConfig(env);
  const bytes = new TextEncoder().encode(text);
  let bin = "";
  bytes.forEach((b) => (bin += String.fromCharCode(b)));
  const r = await gh(env, `https://api.github.com/repos/${env.CONFIG_REPO}/contents/${env.CONFIG_PATH}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      message: "Update Cinedor settings from admin panel",
      content: btoa(bin),
      sha: cur.sha,
      branch: cur.ref,
    }),
  });
  if (!r.ok) throw new Error("GitHub save failed (" + r.status + ")");
  return cur.ref;
}

export default {
  async fetch(request, rawEnv) {
    const env = { ...DEFAULTS, ...rawEnv };
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, { headers: cors(env) });
    if (url.pathname === "/health") return json(env, 200, { ok: true });
    try {
      await requireAdmin(request, env, url.pathname === "/send" ? "notify" : "popups");
      if (url.pathname === "/send" && request.method === "POST") {
        const id = await sendPush(await request.json(), env);
        return json(env, 200, { ok: true, id });
      }
      if (url.pathname === "/config" && request.method === "GET") {
        const c = await findConfig(env);
        return json(env, 200, { ok: true, ref: c.ref, config: c.text });
      }
      if (url.pathname === "/config" && request.method === "POST") {
        const body = await request.json();
        const ref = await saveConfig(String(body.config || ""), env);
        return json(env, 200, { ok: true, ref });
      }
      return json(env, 404, { ok: false, error: "Not found" });
    } catch (e) {
      const msg = String(e?.message || e);
      const status = /admin|token|signature|expired|issuer|project|signing key/i.test(msg) ? 401 : 400;
      return json(env, status, { ok: false, error: msg });
    }
  },
};
