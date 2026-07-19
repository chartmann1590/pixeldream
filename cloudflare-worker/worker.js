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
 */

const MANIFEST = {
  models: [
    {
      kind: "GEMMA_PROMPT_ENHANCER",
      version: "gemma-3-270m-it-q4_0",
      // Proxied through this Worker's /models/hf/ route, which forwards to
      // https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q4_0-web.task
      url: "__WORKER_BASE_URL__/models/hf/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q4_0-web.task",
      sha256: "", // TODO: fill in from `sha256sum` after first successful download -- see README.md
      sizeBytes: 249000000,
      minRamMb: 3072,
    },
    // Diffusion model intentionally omitted: Google has no pre-converted,
    // downloadable on-device diffusion artifact (see docs/models/README.md
    // in the main repo). Nothing to proxy until one is converted and hosted.
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

    return new Response("Not found", { status: 404 });
  },
};

async function proxyToHuggingFace(request, url, env) {
  if (!env.HF_TOKEN) {
    return new Response(
      "Server misconfigured: HF_TOKEN secret not set. See cloudflare-worker/README.md.",
      { status: 500 },
    );
  }

  const hfPath = url.pathname.replace("/models/hf/", "");
  const hfUrl = `https://huggingface.co/${hfPath}${url.search}`;

  const proxyHeaders = new Headers();
  proxyHeaders.set("Authorization", `Bearer ${env.HF_TOKEN}`);
  // Forward range/conditional headers so the app's resumable downloads work.
  for (const header of ["range", "if-range", "if-none-match"]) {
    const value = request.headers.get(header);
    if (value) proxyHeaders.set(header, value);
  }

  const hfResponse = await fetch(hfUrl, {
    method: "GET",
    headers: proxyHeaders,
    redirect: "follow",
  });

  const responseHeaders = new Headers(hfResponse.headers);
  responseHeaders.delete("set-cookie");

  return new Response(hfResponse.body, {
    status: hfResponse.status,
    headers: responseHeaders,
  });
}
