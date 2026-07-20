# Model sourcing

PixelDream downloads publisher-hosted artifacts directly during onboarding.
Both entries are pinned by exact byte count and SHA-256 in
`OfficialModelCatalog`; the app does not depend on an application-owned model
proxy or remote manifest.

## Gemma 4 prompt enhancer

- Official distribution: `litert-community/gemma-4-E2B-it-litert-lm`
- Artifact: `gemma-4-E2B-it.litertlm`
- Pinned revision: `9262660a1676eed6d0c477ab1a86344430854664`
- Size: `2,588,147,712` bytes
- SHA-256: `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`

The repository model card identifies this as Google's Android-ready Gemma 4
distribution. The app loads it with Google's current LiteRT-LM Android runtime;
the legacy MediaPipe `.task` path is not used for Gemma 4.

## Stable Diffusion image generator

- Runtime distribution: `second-state/stable-diffusion-v1-5-GGUF`
- Artifact: `stable-diffusion-v1-5-pruned-emaonly-Q8_0.gguf`
- Pinned revision: `031b5f5df991f511b3f5fa8fed6d99048ababb69`
- Size: `1,763,578,176` bytes
- SHA-256: `d0555243938c62faeefb4ac93f6c7a053ad373a4290c5256bce229aeb193bf94`

The previous raw PyTorch checkpoint could not be executed by the Android app.
PixelDream now downloads the higher-quality Q8 GGUF conversion and executes it
fully offline through the bundled `stable-diffusion.cpp` ARM64 runtime. The
runtime source is pinned as a Git submodule at commit
`c97702e1057c2fe13a7074cd9069cb9dd6edc1bf`.

## Download behavior

Android `DownloadManager` owns transfers, follows the publishers' signed CDN
redirects, allows metered networks, persists active download IDs, and resumes
across process restarts. Downloads run serially to avoid competing multi-GB
transfers. Verification runs on an IO dispatcher and an invalid file is never
reported as ready.
