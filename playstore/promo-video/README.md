# PixelDream promo video

`pixeldream-promo.mp4` — 1920×1080, H.264/AAC, ~20 seconds, ready to upload to YouTube
and link from the Play Console store listing's promo video field.

Built entirely from local tools (no cloud video/TTS APIs):

- **Visuals**: four scenes rendered from `source/sceneN.png` (HTML/CSS composited via a
  headless browser), using the real cropped app screenshots from
  `../assets/screenshots/phone/` inside a phone-frame mockup.
- **Narration**: Windows SAPI text-to-speech (`Microsoft Zira Desktop` voice),
  one `.wav` file per scene in `source/`.
- **Captions**: burned in from `source/captions.srt` via ffmpeg's `subtitles` filter.
- **Assembly**: each scene = `zoompan` (slow Ken Burns zoom) + its narration clip,
  concatenated with ffmpeg, then captions burned in on the final pass.

## Script

1. "PixelDream turns a short idea into a picture." — Create screen
2. "An on-device AI paints it, right on your phone. No internet needed." — Generating screen
3. "Your prompts and your pictures never leave your device." — Settings screen
4. "Get PixelDream, free on Google Play." — logo card

## Regenerating

The `source/` folder keeps the source scenes, narration, and caption file so the
video can be re-cut without redoing the screenshot/TTS work — see the ffmpeg
commands used in this project's history, or re-run:

```bash
# per scene: still image + narration -> timed clip with a slow zoom
ffmpeg -loop 1 -i sceneN.png -i narrN.wav \
  -filter_complex "[0:v]scale=1920:1080,zoompan=z='min(zoom+0.0007,1.08)':d=150:s=1920x1080:fps=25,format=yuv420p[v];[1:a]apad[a]" \
  -map "[v]" -map "[a]" -t <scene_duration> -c:v libx264 -c:a aac -b:a 160k segN.mp4

# concat all segments, then burn in captions.srt
ffmpeg -f concat -safe 0 -i concat.txt -c copy joined.mp4
ffmpeg -i joined.mp4 -vf "subtitles=captions.srt:force_style='FontName=Arial,FontSize=19,PrimaryColour=&H00FFFFFF,OutlineColour=&HC0120a1f,BorderStyle=3,Outline=2,MarginV=55,Bold=1'" \
  -c:v libx264 -crf 18 -c:a aac pixeldream-promo.mp4
```

## Uploading

1. Upload `pixeldream-promo.mp4` to YouTube (unlisted is fine — Play Console just
   needs a public-enough URL to embed it).
2. Paste the YouTube URL into Play Console → Store presence → Main store listing →
   Promo video.
