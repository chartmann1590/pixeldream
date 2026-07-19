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
- **Gating**: this repository is a *gated* Hugging Face repo — downloading
  requires an authenticated account that has accepted the Gemma Terms of Use.
  This is a Google licensing requirement, not a hosting choice, and applies
  to every official Gemma distribution channel (Hugging Face or Kaggle
  Models). A consumer app cannot require every end user to hold a Hugging
  Face account, so **PixelDream mirrors the unmodified file** to our own
  Firebase Storage bucket after accepting the license once as the publisher.
  The mirrored file is never modified, only re-served.

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

`models_manifest.json` (hosted at
`https://storage.googleapis.com/pixeldream-app.firebasestorage.app/models/models_manifest.json`)
lists the current mirrored URL, sha256, size, and minimum RAM for each model
kind, fetched fresh at onboarding time so a model can be updated without an
app release. See `ModelManifestFetcher`.
