# PixelDream feedback and optional model proxy

The production Android app downloads immutable, publisher-hosted artifacts
from its compiled and tested `OfficialModelCatalog`. This Worker primarily
keeps the GitHub issue-reporting PAT off device. Its `/models/*` routes remain
for older clients and use the same pinned model revisions and checksums.

**Deployed at**: `https://pixeldream-model-proxy.charles-h-hartmann1.workers.dev`

## Model routes

The legacy pass-through streams bytes from Hugging Face without storing or
caching a model copy on Cloudflare. `HF_TOKEN` is optional for the production
app and is needed only if an upstream legacy route requires authentication.

## Routes

- `GET /models/manifest.json` — a pinned compatibility manifest. The current
  Android app does not depend on this endpoint.
- `GET /models/hf/<hugging-face-path>` — proxies to
  `https://huggingface.co/<hugging-face-path>`, injecting
  `Authorization: Bearer $HF_TOKEN` and forwarding `Range`/`If-Range`
  headers so the app's resumable `DownloadManager`-based downloads work.

## Optional Hugging Face authentication

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

Without this optional secret, `/models/hf/*` returns `500`; the production app
continues to use its pinned direct downloads.

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
