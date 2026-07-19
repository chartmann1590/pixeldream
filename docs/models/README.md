# Model sourcing

PixelDream does not train or own any model weights. Both on-device models are
official Google artifacts, downloaded during onboarding and verified against
a SHA-256 checksum before use. Neither is bundled in the APK.

## Gemma (prompt enhancer)

- **Official source**: [`litert-community/gemma-3-270m-it`](https://huggingface.co/litert-community/gemma-3-270m-it)
  on Hugging Face — Google's own LiteRT-converted distribution of Gemma 3 270M,
  chosen for its small footprint (~250 MB int4 quantized) given its job here
  is short prompt rewriting, not open-ended chat.
- **File**: `gemma3-270m-it-q4_0-web.task`
- **Gating**: this repository is a *gated* Hugging Face repo — verified
  directly with `curl` (unauthenticated requests to the actual `.task` file
  return `401` with `X-Error-Code: GatedRepo`), across every official Gemma
  repo checked. This is a Google licensing requirement enforced at the HTTP
  level, not a hosting choice, and applies to every official Gemma
  distribution channel (Hugging Face or Kaggle Models) — there is no
  zero-auth public download URL for the real weights.
- **How PixelDream handles this without hosting a copy**: a small Cloudflare
  Worker (`cloudflare-worker/`, deployed at
  `pixeldream-model-proxy.charles-h-hartmann1.workers.dev`) holds one
  developer-side Hugging Face access token as a secret and transparently
  proxies requests to Hugging Face's real resolve URLs, injecting the
  `Authorization` header server-side. The app never sees or holds any
  credential. This is a **live pass-through, not a re-hosted copy** — no
  model bytes are stored on Cloudflare or any infrastructure we control;
  every byte in the response to the app is streamed directly from
  `huggingface.co` on that same request. See `cloudflare-worker/README.md`
  for the one unavoidable human step (accepting the Gemma license once,
  since that's tied to a real identity and can't be done by an agent).

## Diffusion (image generator)

- **Official guidance**: [MediaPipe Image Generator, Android guide](https://developers.google.com/edge/mediapipe/solutions/vision/image_generator/android).
  Google does **not** host a ready-to-download on-device diffusion model.
  Their own docs state models must "match the
  `stable-diffusion-v1-5/stable-diffusion-v1-5 EMA-only` model format" and be
  produced by running Google's conversion script
  (`tools/image_generator_converter/convert.py` in
  [google-ai-edge/mediapipe-samples](https://github.com/google-ai-edge/mediapipe-samples))
  against a Stable Diffusion v1.5-architecture checkpoint you supply.
- Google's docs explicitly say: *"For production deployment, host the
  converted model on a server and download it during runtime. The model is
  too large to be bundled in an APK."* — i.e. self-hosting the conversion
  output is the intended, documented production pattern, not a deviation
  from it.
- **Status**: not yet converted/hosted. This is a manual, one-time step
  (Python/PyTorch conversion environment, ~4-5GB checkpoint download, GPU
  recommended) that needs to happen outside of app code before the diffusion
  path in Phase 3 can be exercised end-to-end. Until then, `ModelRepository`
  will report `DownloadState.Failed` for this model kind if a manifest entry
  isn't present.
- **License**: whichever SD1.5 checkpoint is used carries its own license
  (CreativeML OpenRAIL-M for the canonical `runwayml`/`stable-diffusion-v1-5`
  checkpoint) with use-based restrictions — see the in-app Licenses screen
  requirement in the project plan.

## Manifest

`models_manifest.json` is served by the same Cloudflare Worker at
`https://pixeldream-model-proxy.charles-h-hartmann1.workers.dev/models/manifest.json`
(verified live, returns real JSON). It lists the current proxy URL, sha256,
size, and minimum RAM for each model kind, fetched fresh at onboarding time
so a model can be updated without an app release. See `ModelManifestFetcher`.

Nothing model-related is hosted on Firebase. Firebase Storage was considered
and explicitly rejected in favor of the Worker-proxy approach above.
