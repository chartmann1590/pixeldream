# pixeldream-model-proxy

A Cloudflare Worker that proxies model downloads live from Google's official
Hugging Face repos (`litert-community` org), injecting a Hugging Face access
token server-side so the app never needs its own HF credentials and never
downloads a copy we re-host ourselves.

**Deployed at**: `https://pixeldream-model-proxy.charles-h-hartmann1.workers.dev`

## Why this exists

Google's official Gemma distribution is a *gated* Hugging Face repo — every
unauthenticated request to the real weight files returns `401 GatedRepo`
(verified directly with `curl`, not assumed). This is Google's actual Gemma
license enforcement mechanism, not a hosting choice, and it applies uniformly
across every official Gemma repo checked (`litert-community/Gemma3-1B-IT`,
`litert-community/gemma-3-270m-it`, `litert-community/gemma-4-E2B-it-litert-lm`).
A consumer Android app can't require every end user to hold a Hugging Face
account and accept a license just to use onboarding, so this Worker does the
one-time auth on the developer's behalf and streams the response straight
through — the app talks to our domain, but every byte in the response body
originates from `huggingface.co` on that same request. Nothing is stored,
cached as a copy, or re-served from Cloudflare storage.

## Routes

- `GET /models/manifest.json` — the model manifest `ModelManifestFetcher`
  reads at onboarding time. `url` fields point back at this Worker's own
  `/models/hf/...` route.
- `GET /models/hf/<hugging-face-path>` — proxies to
  `https://huggingface.co/<hugging-face-path>`, injecting
  `Authorization: Bearer $HF_TOKEN` and forwarding `Range`/`If-Range`
  headers so the app's resumable `DownloadManager`-based downloads work.

## Required setup (one-time, human action)

This is the one step that genuinely cannot be done by an agent: accepting a
license is an act tied to a real identity, and Google's Gemma Prohibited Use
Policy is something a real person/company needs to agree to.

1. Go to <https://huggingface.co/litert-community/gemma-3-270m-it> and accept
   the Gemma Terms of Use (one click, requires a free Hugging Face account).
2. Generate a **read-only** access token at
   <https://huggingface.co/settings/tokens>.
3. Set it as a Worker secret:
   ```
   npx wrangler secret put HF_TOKEN --name pixeldream-model-proxy
   ```
   (or via Cloudflare dashboard → Workers & Pages → pixeldream-model-proxy →
   Settings → Variables → add `HF_TOKEN` as an encrypted secret).

Until this secret is set, `/models/hf/*` returns `500` with a clear
"Server misconfigured" message (verified — this is the actual current state).
The manifest endpoint (`/models/manifest.json`) works today without it.

## After the secret is set

Fill in the real `sha256` for `models_manifest.json`'s Gemma entry (currently
blank in `worker.js`) by downloading the file once yourself and running
`sha256sum`, then redeploy. `ModelStorage.verify()` will reject downloads
that don't match, so this isn't optional for the download flow to succeed.

## Redeploying after editing worker.js

```
npx wrangler deploy --name pixeldream-model-proxy
```

(This Worker was initially deployed via the Cloudflare API directly rather
than the CLI, but `worker.js` in this directory is the source of truth going
forward — keep them in sync.)

## Diffusion model

Not proxied here because there is nothing to proxy: Google has no
pre-converted, downloadable on-device diffusion artifact at all (their own
docs say to convert a Stable Diffusion v1.5 checkpoint yourself). See
`docs/models/README.md` in the repo root.

## GitHub feedback proxy

The same Worker exposes `/github/repos/<owner>/<repo>/...` for the in-app bug
reporter. It only permits issue creation/status, issue comments, and PNG writes
under `feedback-assets/` for the single configured repository. Paths and payload
sizes are validated. Set `GH_API_TOKEN`, `GH_REPO_OWNER`, and `GH_REPO_NAME` as
encrypted Worker secrets with `wrangler secret put`.

The Android build receives only `FEEDBACK_PROXY_URL` and the non-secret repo
coordinates. Never add the GitHub PAT to `BuildConfig`; APK values are readable.
