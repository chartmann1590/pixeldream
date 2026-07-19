# PixelDream

An Android app for generating AI images fully on-device — no server required after setup.

PixelDream uses a small on-device LLM (Gemma, via Google's LiteRT/AI Edge runtime) to enhance your prompts, and an on-device diffusion model (SD-Turbo/SDXS-class, via MediaPipe's Image Generation task) to turn them into images. Both models download once during onboarding; after that, everything works offline.

## Status

Early scaffolding — see `docs/` for the implementation plan as it lands.

## License

TBD.
