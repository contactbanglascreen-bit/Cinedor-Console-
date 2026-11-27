// Cinedor "Server 2" worker.
// Deploy as a NEW Cloudflare Worker (Workers & Pages > Create > Worker), paste this file, Deploy.
// Then send the worker link (https://<name>.<you>.workers.dev) so the app can use it as Server 2.
//
// Routes (all GET, JSON):
//   /home                 -> { items: [ {title, path, poster} ] }
//   /search?q=...         -> { items: [...] }
//   /detail?path=...      -> { title, poster, synopsis, referer, streams: [{url, quality, type}],
//                              episodes: [{season, episode, title, path}] }
//   /stream?url=...&ref=  -> the video itself, fetched with the right Referer (for websites)
//   /health               -> { ok: true }

const HOSTS = [
  "https://movielinkbd.com",
  "https://movielinkbd.one",
  "https://movielinkbd.at",
  "https://movielinkbd.tv",
];
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Range, Content-Type",
};

function json(body, status = 200, cacheSeconds = 0) {
  const headers = { ...CORS, "Content-Type": "application/json; charset=utf-8" };
  if (cacheSeconds > 0) headers["Cache-Control"] = `public, max-age=${cacheSeconds}`;
  return new Response(JSON.stringify(body), { status, headers });
}

// A page that is really a bot check ("Just a moment...") is treated as a failure, not content.
function isBotCheck(html) {
  return /<title>\s*Just a moment|cf-chl-|challenge-platform/i.test(html);
}

let lastGoodHost = null;
async function fetchWorking(path) {
  const order = lastGoodHost ? [lastGoodHost, ...HOSTS.filter((h) => h !== lastGoodHost)] : HOSTS;
  const errors = [];
  for (const host of order) {
    try {
      const r = await fetch(host + path, {
        headers: { "User-Agent": UA, Referer: host + "/", Accept: "text/html,application/xhtml+xml" },
        redirect: "follow",
        cf: { cacheTtl: 300, cacheEverything: true },
      });
      const html = await r.text();
      if (r.ok && !isBotCheck(html)) {
        const finalHost = new URL(r.url).origin;
        lastGoodHost = host;
        return { html, host: finalHost };
      }
      errors.push(`${host}: ${r.status}${isBotCheck(html) ? " (bot check)" : ""}`);
    } catch (e) {
      errors.push(`${host}: ${e.message}`);
    }
  }
  throw new Error("All hosts failed: " + errors.join("; "));
}

function abs(u, host) {
  if (!u) return "";
  if (u.startsWith("//")) return "https:" + u;
  if (u.startsWith("http")) return u;
  return host + (u.startsWith("/") ? u : "/" + u);
}

function decode(s) {
  return String(s || "")
    .replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#0?39;|&apos;/g, "'")
    .replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/\s+/g, " ").trim();
}

function parseCards(html, host) {
  const items = [];
  const seen = new Set();
  const parts = html.split(/<div\s+class=["'][^"']*movie-card[^"']*["'][^>]*>/i).slice(1);
  for (const chunk of parts.slice(0, 60)) {
    const href = (chunk.match(/href=["']([^"']+)["']/i) || [])[1] || "";
    const title = decode((chunk.match(/<img[^>]*\b(?:title|alt)=["']([^"']+)["']/i) || [])[1] || "");
    const img = (chunk.match(/(?:data-src|data-lazy-src|src)=["']([^"']+\.(?:jpe?g|png|webp)[^"']*)["']/i) || [])[1] || "";
    if (!href || !title) continue;
    const path = href.startsWith("http") ? new URL(href).pathname + new URL(href).search : href;
    if (seen.has(path)) continue;
    seen.add(path);
    items.push({ title, path, poster: abs(img, host) });
  }
  return items;
}

// Find video links anywhere inside the player's data (whatever shape it has).
function collectStreams(node, out = [], label = "") {
  if (node == null) return out;
  if (typeof node === "string") {
    const s = node.trim();
    if (/^https?:\/\/\S+\.(mp4|m3u8)(\?\S*)?$/i.test(s) || /^https?:\/\/\S+\/(hls|manifest)\S*$/i.test(s)) {
      const type = /m3u8|hls|manifest/i.test(s) ? "hls" : "mp4";
      const q = (label.match(/(2160|1440|1080|720|480|360|240)p?/i) || s.match(/(2160|1440|1080|720|480|360|240)p/i) || [])[1];
      out.push({ url: s, quality: q ? q + "p" : (type === "hls" ? "Auto" : "HD"), type });
    }
    return out;
  }
  if (Array.isArray(node)) { node.forEach((v) => collectStreams(v, out, label)); return out; }
  if (typeof node === "object") {
    for (const [k, v] of Object.entries(node)) {
      const lbl = typeof node.label === "string" ? node.label : typeof node.quality === "string" ? node.quality : k;
      collectStreams(v, out, lbl);
    }
  }
  return out;
}

// Episode links on a series page, e.g. ".../season-1-episode-3" or "?ep=3".
function parseEpisodes(html, host) {
  const eps = [];
  const seen = new Set();
  const re = /<a[^>]+href=["']([^"']+)["'][^>]*>([\s\S]{0,200}?)<\/a>/gi;
  let m;
  while ((m = re.exec(html)) && eps.length < 400) {
    const href = m[1];
    const text = decode(m[2].replace(/<[^>]+>/g, " "));
    const hay = href + " " + text;
    const ep = (hay.match(/(?:episode|ep)[\s\-_=.]*(\d{1,4})/i) || [])[1];
    if (!ep) continue;
    const se = (hay.match(/(?:season|s)[\s\-_=.]*(\d{1,3})/i) || [])[1] || "1";
    const path = href.startsWith("http") ? new URL(href).pathname + new URL(href).search : href;
    const key = se + ":" + ep;
    if (seen.has(key)) continue;
    seen.add(key);
    eps.push({ season: +se, episode: +ep, title: text || `Episode ${ep}`, path });
  }
  return eps.sort((a, b) => a.season - b.season || a.episode - b.episode);
}

async function detail(path) {
  const { html, host } = await fetchWorking(path);
  const title = decode((html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)/i) || html.match(/<title>([^<]+)/i) || [])[1] || "");
  const poster = abs((html.match(/<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)/i) || [])[1] || "", host);
  const synopsis = decode((html.match(/<meta[^>]+(?:property=["']og:description["']|name=["']description["'])[^>]+content=["']([^"']+)/i) || [])[1] || "");
  const episodes = parseEpisodes(html, host);

  let streams = collectStreams(html.match(/https?:\/\/[^"'\s<>]+\.(?:mp4|m3u8)[^"'\s<>]*/gi) || []);
  let referer = host;
  const iframe = (html.match(/<iframe[^>]*\bsrc=["']([^"']+)["']/i) || [])[1];
  if (iframe) {
    const pUrl = abs(iframe, host);
    try {
      const pRes = await fetch(pUrl, { headers: { "User-Agent": UA, Referer: host + path } });
      const pHtml = await pRes.text();
      referer = new URL(pUrl).origin;
      const match = pHtml.match(/window\.__PD2\s*=\s*(\{[\s\S]*?\});/);
      if (match) {
        try { streams = streams.concat(collectStreams(JSON.parse(match[1]))); } catch (e) {}
      }
      streams = streams.concat(collectStreams(pHtml.match(/https?:\/\/[^"'\s<>]+\.(?:mp4|m3u8)[^"'\s<>]*/gi) || []));
    } catch (e) {}
  }
  const uniq = [];
  const seen = new Set();
  for (const s of streams) if (!seen.has(s.url)) { seen.add(s.url); uniq.push(s); }
  return { title, poster, synopsis, referer, streams: uniq, episodes };
}

// Only real public https video addresses may be proxied (no open proxy to anything).
function allowedStreamTarget(u) {
  try {
    const x = new URL(u);
    if (x.protocol !== "https:") return false;
    if (/^(localhost|127\.|10\.|192\.168\.|169\.254\.|0\.)/.test(x.hostname)) return false;
    return /\.(mp4|m3u8|ts|m4s|key)(\?|$)/i.test(x.pathname + (x.search ? "?" : "")) || /\/(hls|video|stream)/i.test(x.pathname);
  } catch (e) { return false; }
}

export default {
  async fetch(request) {
    if (request.method === "OPTIONS") return new Response(null, { headers: CORS });
    const url = new URL(request.url);
    try {
      if (url.pathname === "/health") return json({ ok: true });
      if (url.pathname === "/home") {
        const { html, host } = await fetchWorking("/");
        return json({ items: parseCards(html, host) }, 200, 600);
      }
      if (url.pathname === "/search") {
        const q = (url.searchParams.get("q") || "").trim();
        if (!q) return json({ items: [] });
        const { html, host } = await fetchWorking("/search?q=" + encodeURIComponent(q));
        return json({ items: parseCards(html, host) }, 200, 600);
      }
      if (url.pathname === "/detail") {
        const path = url.searchParams.get("path") || "/";
        if (!path.startsWith("/")) return json({ error: "Bad path" }, 400);
        return json(await detail(path), 200, 300);
      }
      if (url.pathname === "/stream") {
        const target = url.searchParams.get("url") || "";
        if (!allowedStreamTarget(target)) return json({ error: "Not a video link" }, 400);
        const headers = { "User-Agent": UA, Referer: url.searchParams.get("ref") || HOSTS[0] + "/" };
        const range = request.headers.get("Range");
        if (range) headers.Range = range;
        const v = await fetch(target, { headers });
        const out = new Headers(v.headers);
        Object.entries(CORS).forEach(([k, val]) => out.set(k, val));
        return new Response(v.body, { status: v.status, headers: out });
      }
      return json({ error: "Not found" }, 404);
    } catch (e) {
      return json({ error: String(e && e.message || e) }, 502);
    }
  },
};
