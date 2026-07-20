# Monetization setup

PixelDream ships wired up to Google's official AdMob test IDs so the ad
integration is real and testable before a production AdMob account exists.

## Before release, replace:

- `app/src/main/AndroidManifest.xml`: `com.google.android.gms.ads.APPLICATION_ID`
  meta-data value — currently Google's published test App ID
  (`ca-app-pub-3940256099942544~3347511713`).
- `app/src/main/java/com/hartmann/pixeldream/ads/AdIds.kt`:
  `INTERSTITIAL_TEST_ID` — currently Google's published test interstitial
  unit ID (`ca-app-pub-3940256099942544/1033173712`).

Both come from an AdMob account with an app registered for
`com.hartmann.pixeldream` and an interstitial ad unit created under it.

## Subscription

`core-billing/.../BillingRepository.kt`: `AD_FREE_SUBSCRIPTION_PRODUCT_ID`
(`pixeldream_ad_free_monthly`) matches a $1.99 USD/month subscription with a
single `monthly` base plan, created and activated in Play Console via
`scripts/configure_play_subscription.py` (see
`.github/workflows/configure-play-subscription.yml` — manual `workflow_dispatch`,
safe/idempotent to re-run). It is priced across every region Play Billing
supports.

Play Billing only resolves real products once the app is uploaded to at
least an internal testing track and the product exists — it cannot be
tested against `debug` builds installed via `adb install` alone. To verify:
upload a build to internal testing, add your Google account as a license
tester in Play Console, then open the Ad-free screen in the app.

## Ad cadence

`GENERATIONS_PER_AD` in `GenerationViewModel.kt` (currently 3) controls how
many generations a free-tier user gets between interstitials.
