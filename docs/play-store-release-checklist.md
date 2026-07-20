# Google Play release checklist

This repository covers the binary-side requirements. Complete the Play Console declarations below before promoting beyond internal testing.

## Artifact and technical checks

- Upload the signed `app-release.aab`, not a locally test-configured APK.
- Confirm target API 36 and 16 KB native-library alignment in the release workflow.
- Confirm the release artifact contains the production AdMob app, banner, and interstitial IDs and none of Google's sample ad-unit IDs.
- Review Android vitals, Crashlytics, and Performance Monitoring before each staged rollout.
- Use a staged rollout and halt it for any new fatal crash or ANR regression.

## App content declarations

- Privacy policy URL: `https://chartmann1590.github.io/pixeldream/privacy/`
- Ads: declare that the app contains ads.
- App access: no login or restricted access is required.
- Target audience: do not select children under 13. If younger age groups are later selected, rework ads, identifiers, content, and Families-policy declarations first.
- Content rating: answer for user-entered prompts and AI-generated images, including the possibility of mature or disturbing output despite filtering.
- AI-generated content: describe the on-device prompt filtering and the image viewer's in-app offensive-content report, which submits to the developer without leaving the app.
- Data safety: include collection/handling performed by Firebase Analytics, Firebase Crashlytics, Firebase Performance Monitoring, Google Mobile Ads, Cloudflare, and GitHub. Locally processed prompts/images are not collected unless the user deliberately attaches an image to a report or shares it.
- Data deletion: explain local deletion/uninstall behavior and the public-issue removal request documented in the privacy policy.

## Store listing accuracy

- Describe the app as offline **after the initial model downloads**. Ads, analytics, diagnostics, privacy choices, and optional feedback still use the network.
- Disclose the approximate 4.1 GB model download and device/RAM requirements before users install.
- Do not claim that generated content is guaranteed safe, private after export, or factually accurate.
- Include model/runtime notices and comply with the Gemma, Stable Diffusion/OpenRAIL, LiteRT-LM, and stable-diffusion.cpp license and acceptable-use terms.

These answers must be reviewed against the actual production configuration at submission time; repository code cannot submit or certify Play Console declarations on the account owner's behalf.
