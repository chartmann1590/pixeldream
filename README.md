# PixelDream

PixelDream is a private, offline-first Android image generator. Gemma 4 expands
a short idea into a visual prompt, then Stable Diffusion 1.5 renders the image
on the phone. Prompts and generated images do not leave the device.

## Features

- Offline Stable Diffusion image generation through `stable-diffusion.cpp`
- On-device Gemma 4 prompt enhancement through Google LiteRT-LM
- Resumable model downloads with byte-count and SHA-256 verification
- Private Room-backed gallery with export and sharing
- Settings for model download, redownload, deletion, quality, size, and About
- Local safety checks before and after prompt enhancement
- System-bar-safe Material 3 interface

## Requirements

- Android 10 / API 29 or newer
- ARM64 device
- 6 GB RAM minimum; 8 GB or more recommended
- Approximately 5 GB free storage for setup

The Gemma and diffusion models consume approximately 4.1 GB together.

## Model runtimes

- Gemma 4 E2B: official `litert-community` `.litertlm` artifact using LiteRT-LM
- Stable Diffusion 1.5: Q8 GGUF conversion using `stable-diffusion.cpp`

Exact URLs, revisions, sizes, and checksums are documented in
[`docs/models/README.md`](docs/models/README.md) and pinned in
`OfficialModelCatalog.kt`.

## Build

Prerequisites are JDK 17, Android SDK 35, Android NDK 27.0.12077973, and CMake
3.22.1. Clone recursively because the native diffusion runtime is a submodule.

```bash
git clone --recurse-submodules https://github.com/charles-hartmann/pixeldream.git
cd pixeldream
./gradlew assembleDebug
```

Install on a connected ARM64 device:

```bash
./gradlew installDebug
```

## Modules

| Module | Responsibility |
|---|---|
| `app` | Compose UI, navigation, generation workflow, gallery, and settings |
| `core-model` | Downloads, verification, Gemma and diffusion session lifecycle |
| `stablediffusion` | JNI wrapper and native ARM64 diffusion build |
| `core-data` | Room generation history |
| `core-ui` | Shared design system |
| `core-billing` | Optional ad-free entitlement |

The Cloudflare Worker is retained as optional infrastructure, but the Android
app uses pinned publisher-hosted downloads directly.
