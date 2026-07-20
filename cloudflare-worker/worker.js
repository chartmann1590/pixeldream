/**
 * PixelDream model proxy.
 *
 * Google's official Gemma distribution on Hugging Face (litert-community org)
 * is a gated repo -- downloading requires an authenticated account that has
 * accepted the Gemma Terms of Use (verified: unauthenticated requests return
 * 401 GatedRepo). A consumer app can't ask every end user to hold a Hugging
 * Face account, so this Worker holds ONE developer-side HF token (set as a
 * Cloudflare secret, never shipped in the app) and transparently forwards
 * requests to Hugging Face's real resolve URLs, injecting auth server-side.
 *
 * This is a live pass-through proxy, not a re-hosted copy: no model bytes
 * are stored on Cloudflare or anywhere else we control. Every byte the app
 * receives comes directly from huggingface.co in that request.
 *
 * Routes:
 *   GET /models/manifest.json      -> the app's model manifest
 *   GET /models/hf/<...hf-path>    -> proxies to https://huggingface.co/<...hf-path>
 *   /github/repos/<owner>/<repo>/... -> restricted issue-reporter GitHub proxy
 */

const MANIFEST = {
  models: [
    {
      kind: "GEMMA_PROMPT_ENHANCER",
      version: "gemma-4-e2b-it-litertlm",
      url: "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/9262660a1676eed6d0c477ab1a86344430854664/gemma-4-E2B-it.litertlm",
      sha256: "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
      sizeBytes: 2588147712,
      minRamMb: 6144,
    },
    {
      kind: "DIFFUSION_IMAGE_GENERATOR",
      version: "stable-diffusion-v1-5-q8-gguf",
      url: "https://huggingface.co/second-state/stable-diffusion-v1-5-GGUF/resolve/031b5f5df991f511b3f5fa8fed6d99048ababb69/stable-diffusion-v1-5-pruned-emaonly-Q8_0.gguf",
      sha256: "d0555243938c62faeefb4ac93f6c7a053ad373a4290c5256bce229aeb193bf94",
      sizeBytes: 1763578176,
      minRamMb: 6144,
    },
  ],
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === "/models/manifest.json") {
      const manifest = JSON.parse(
        JSON.stringify(MANIFEST).replaceAll("__WORKER_BASE_URL__", url.origin),
      );
      return new Response(JSON.stringify(manifest), {
        headers: { "content-type": "application/json", "cache-control": "public, max-age=300" },
      });
    }

    if (url.pathname.startsWith("/models/hf/")) {
      return proxyToHuggingFace(request, url, env);
    }

    if (url.pathname.startsWith("/github/repos/")) {
      return proxyFeedbackToGitHub(request, url, env);
    }

    return new Response("Not found", { status: 404 });
  },
};

const GITHUB_API = "https://api.github.com";
const MAX_FEEDBACK_BODY_BYTES = 7 * 1024 * 1024;

/**
 * A deliberately narrow GitHub proxy. It only permits issue creation/status,
 * comments, and PNG attachment writes under feedback-assets/ for one repo.
 * The PAT stays in Cloudflare's encrypted Worker secrets and is never returned.
 */
async function proxyFeedbackToGitHub(request, url, env) {
  if (!env.GH_API_TOKEN || !env.GH_REPO_OWNER || !env.GH_REPO_NAME) {
    return jsonError("Feedback service is not configured", 503);
  }

  const prefix = "/github/repos/";
  const parts = url.pathname.slice(prefix.length).split("/").map(decodeURIComponent);
  const [owner, repo, resource, number, subresource, ...rest] = parts;
  if (owner !== env.GH_REPO_OWNER || repo !== env.GH_REPO_NAME || resource !== "issues") {
    // Contents uploads use a separate allowed shape below.
    if (!(owner === env.GH_REPO_OWNER && repo === env.GH_REPO_NAME && resource === "contents")) {
      return jsonError("Not found", 404);
    }
  }

  const method = request.method.toUpperCase();
  let allowed = false;
  if (resource === "issues") {
    allowed =
      (method === "POST" && !number) ||
      (method === "GET" && /^\d+$/.test(number || "") && !subresource) ||
      (["GET", "POST"].includes(method) && /^\d+$/.test(number || "") && subresource === "comments" && rest.length === 0);
  } else if (resource === "contents") {
    const assetPath = [number, subresource, ...rest].filter(Boolean).join("/");
    allowed = method === "PUT" && /^feedback-assets\/issue-[a-zA-Z0-9._-]+\.png$/.test(assetPath);
  }
  if (!allowed) return jsonError("Operation not allowed", 405);

  const length = Number(request.headers.get("content-length") || 0);
  if (length > MAX_FEEDBACK_BODY_BYTES) return jsonError("Attachment or report is too large", 413);

  let body;
  if (method === "POST" || method === "PUT") {
    const raw = await request.text();
    if (new TextEncoder().encode(raw).byteLength > MAX_FEEDBACK_BODY_BYTES) {
      return jsonError("Attachment or report is too large", 413);
    }
    try {
      const parsed = JSON.parse(raw);
      if (resource === "issues" && !number) {
        if (typeof parsed.title !== "string" || typeof parsed.body !== "string" || parsed.title.length > 180 || parsed.body.length > 30000) {
          return jsonError("Invalid issue payload", 400);
        }
        parsed.title = parsed.title.startsWith("[Feedback]") ? parsed.title : `[Feedback] ${parsed.title}`;
      } else if (resource === "issues") {
        if (typeof parsed.body !== "string" || parsed.body.length > 15000) return jsonError("Invalid comment payload", 400);
      } else {
        if (typeof parsed.content !== "string" || parsed.content.length > 7_000_000) return jsonError("Invalid attachment payload", 400);
        parsed.message = "Add PixelDream feedback attachment";
      }
      body = JSON.stringify(parsed);
    } catch {
      return jsonError("Invalid JSON", 400);
    }
  }

  const githubPath = url.pathname.slice("/github".length);
  const response = await fetch(`${GITHUB_API}${githubPath}`, {
    method,
    headers: {
      "accept": "application/vnd.github+json",
      "authorization": `Bearer ${env.GH_API_TOKEN}`,
      "content-type": "application/json",
      "user-agent": "PixelDream-Feedback-Worker/1.0",
      "x-github-api-version": "2022-11-28",
    },
    body,
  });
  const headers = new Headers(response.headers);
  headers.delete("set-cookie");
  return new Response(response.body, { status: response.status, headers });
}

function jsonError(message, status) {
  return new Response(JSON.stringify({ message }), {
    status,
    headers: { "content-type": "application/json", "cache-control": "no-store" },
  });
}

const HUGGING_FACE_HOST = "huggingface.co";
const MAX_REDIRECTS = 5;

async function proxyToHuggingFace(request, url, env) {
  if (!env.HF_TOKEN) {
    return new Response(
      "Server misconfigured: HF_TOKEN secret not set. See cloudflare-worker/README.md.",
      { status: 500 },
    );
  }

  const hfPath = url.pathname.replace("/models/hf/", "");
  let currentUrl = new URL(`https://${HUGGING_FACE_HOST}/${hfPath}${url.search}`);

  const forwardedRequestHeaders = new Headers();
  // Forward range/conditional headers so the app's resumable downloads work.
  for (const header of ["range", "if-range", "if-none-match"]) {
    const value = request.headers.get(header);
    if (value) forwardedRequestHeaders.set(header, value);
  }

  // Large files are typically served from HF's LFS CDN via a redirect to a
  // different host with a pre-signed URL that doesn't need (and shouldn't
  // receive) our token. Follow redirects manually so the Authorization
  // header is only ever attached when the request target is actually
  // huggingface.co, never a redirect destination -- fetch()'s automatic
  // `redirect: "follow"` would otherwise resend it to whatever host the
  // redirect points at.
  for (let redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
    const headers = new Headers(forwardedRequestHeaders);
    if (currentUrl.hostname === HUGGING_FACE_HOST) {
      headers.set("Authorization", `Bearer ${env.HF_TOKEN}`);
    }

    const response = await fetch(currentUrl.toString(), {
      method: "GET",
      headers,
      redirect: "manual",
    });

    if (response.status >= 300 && response.status < 400) {
      const location = response.headers.get("location");
      if (!location) return response;
      currentUrl = new URL(location, currentUrl);
      continue;
    }

    const responseHeaders = new Headers(response.headers);
    responseHeaders.delete("set-cookie");
    return new Response(response.body, { status: response.status, headers: responseHeaders });
  }

  return new Response("Too many redirects", { status: 502 });
}
