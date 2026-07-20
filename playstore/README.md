# PixelDream — Google Play submission package

Everything needed for the Play Store listing lives in this folder.

```
playstore/
├── listing/
│   └── store-listing.md      Title, short/full description, category, copy-paste ready
├── assets/
│   ├── icon/                 512×512 hi-res icon (Play Console "App icon")
│   ├── feature-graphic/      1024×500 feature graphic
│   └── screenshots/
│       ├── phone/            3 screenshots, 1080×1920
│       ├── tablet-7in/       Same 3 screens, 7" tablet canvas
│       └── tablet-10in/      Same 3 screens, 10" tablet canvas
└── promo-video/
    ├── pixeldream-promo.mp4  ~20s YouTube-ready promo video (see its own README)
    └── source/               Source scenes, narration, and captions used to build it
```

## Before you submit

1. **Read `listing/store-listing.md` first** — it has the exact copy-paste text
   for every field in Play Console's Main store listing page.
2. **Upload `promo-video/pixeldream-promo.mp4` to YouTube** and paste the URL
   into the Promo video field (see `promo-video/README.md`).
3. **Pick one canonical privacy policy URL.** This package uses
   `https://chartmann1590.github.io/pixeldream/` (the new GitHub Pages site,
   already live) consistently in the app's Settings screen, this listing, and
   `docs/play-store-release-checklist.md`. If you'd rather keep the older
   Firebase-hosted site (`pixeldream-app.web.app`) as canonical, revert those
   three places instead.
4. **Read `../docs/play-store-release-checklist.md`.** It covers everything
   that isn't a visual asset: data safety declarations, content rating, ads
   declaration, target audience — things only the account owner can certify.

## Known issue to fix before launch

On-device image generation currently produces visibly corrupted output (a
tiled/glitched pattern in the lower portion of generated images) on the test
devices used to prepare these assets — see the conversation summary / code
review notes for details. This is why the screenshots and promo video show
the Create, Generating, and Settings screens rather than a finished generated
image. **Do not submit to Play until this is fixed and verified**, or store
listing screenshots and reviews will not match what users actually get.
