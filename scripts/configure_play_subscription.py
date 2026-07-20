#!/usr/bin/env python3
"""Create and activate PixelDream's ad-free subscription via the Play Developer API.

Idempotent: if the subscription or its base plan already exists, this only
fills in whatever is missing (creates the base plan if absent, activates it
if it's still in DRAFT) rather than failing or duplicating anything.

Product: pixeldream_ad_free_monthly, base plan "monthly", $1.99 USD/month,
auto-converted to every region Play Billing supports so it's purchasable
worldwide (matches AD_FREE_SUBSCRIPTION_PRODUCT_ID in
core-billing/.../BillingRepository.kt).

Usage:
    configure_play_subscription.py <service-account-json-path>

Environment overrides:
    PLAY_PACKAGE_NAME   (default com.hartmann.pixeldream)
    PLAY_SUBSCRIPTION_PRODUCT_ID  (default pixeldream_ad_free_monthly)
    PLAY_SUBSCRIPTION_BASE_PLAN_ID (default monthly)
    PLAY_SUBSCRIPTION_PRICE_USD_UNITS (default 1)
    PLAY_SUBSCRIPTION_PRICE_USD_NANOS (default 990000000, i.e. $1.99)
"""
import os
import sys

from google.oauth2 import service_account
from googleapiclient import errors
from googleapiclient.discovery import build

PACKAGE = os.environ.get("PLAY_PACKAGE_NAME") or "com.hartmann.pixeldream"
PRODUCT_ID = os.environ.get("PLAY_SUBSCRIPTION_PRODUCT_ID") or "pixeldream_ad_free_monthly"
BASE_PLAN_ID = os.environ.get("PLAY_SUBSCRIPTION_BASE_PLAN_ID") or "monthly"
PRICE_UNITS = int(os.environ.get("PLAY_SUBSCRIPTION_PRICE_USD_UNITS") or "1")
PRICE_NANOS = int(os.environ.get("PLAY_SUBSCRIPTION_PRICE_USD_NANOS") or "990000000")

SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]

LISTING = {
    "languageCode": "en-US",
    "title": "Ad-Free",
    "description": (
        "Remove ads from PixelDream. Image generation, models, and your "
        "gallery already work fully offline and are unaffected either way."
    ),
    "benefits": [
        "No banner or interstitial ads",
        "Everything else in PixelDream stays exactly the same",
        "Cancel anytime from Google Play",
    ],
}


def get_subscription(service):
    try:
        return service.monetization().subscriptions().get(
            packageName=PACKAGE, productId=PRODUCT_ID
        ).execute()
    except errors.HttpError as e:
        if e.resp.status == 404:
            return None
        raise


def create_subscription(service):
    print(f"Subscription '{PRODUCT_ID}' does not exist yet — creating it.")
    usd_price = {
        "currencyCode": "USD",
        "units": str(PRICE_UNITS),
        "nanos": PRICE_NANOS,
    }
    print(f"Converting ${PRICE_UNITS}.{PRICE_NANOS // 10_000_000:02d} USD/month to all Play regions…")
    converted = service.monetization().convertRegionPrices(
        packageName=PACKAGE, body={"price": usd_price}
    ).execute()
    region_version = converted["regionVersion"]["version"]
    regional_prices = converted.get("convertedRegionPrices", {})

    regional_configs = [
        {
            "regionCode": "US",
            "newSubscriberAvailability": True,
            "price": usd_price,
        }
    ]
    for region_code, entry in regional_prices.items():
        if region_code == "US":
            continue
        regional_configs.append(
            {
                "regionCode": region_code,
                "newSubscriberAvailability": True,
                "price": entry["price"],
            }
        )
    print(f"  priced for {len(regional_configs)} regions (region set version {region_version})")

    body = {
        "packageName": PACKAGE,
        "productId": PRODUCT_ID,
        "listings": [LISTING],
        "basePlans": [
            {
                "basePlanId": BASE_PLAN_ID,
                "autoRenewingBasePlanType": {
                    "billingPeriodDuration": "P1M",
                    "gracePeriodDuration": "P3D",
                },
                "regionalConfigs": regional_configs,
            }
        ],
    }
    result = (
        service.monetization()
        .subscriptions()
        .create(
            packageName=PACKAGE,
            productId=PRODUCT_ID,
            body=body,
            regionsVersion_version=region_version,
        )
        .execute()
    )
    print(f"Created subscription '{PRODUCT_ID}' with base plan '{BASE_PLAN_ID}' (state: DRAFT).")
    return result


def ensure_base_plan_active(service, subscription):
    base_plans = subscription.get("basePlans", [])
    plan = next((p for p in base_plans if p.get("basePlanId") == BASE_PLAN_ID), None)
    if plan is None:
        sys.exit(
            f"Subscription '{PRODUCT_ID}' exists but has no base plan "
            f"'{BASE_PLAN_ID}' — inspect it in Play Console and add one manually."
        )
    state = plan.get("state", "STATE_UNSPECIFIED")
    print(f"Base plan '{BASE_PLAN_ID}' state: {state}")
    if state == "ACTIVE":
        print("Already active — nothing to do.")
        return
    if state not in ("DRAFT", "INACTIVE"):
        sys.exit(f"Base plan is in unexpected state '{state}'; not activating automatically.")
    print(f"Activating base plan '{BASE_PLAN_ID}'…")
    service.monetization().subscriptions().basePlans().activate(
        packageName=PACKAGE, productId=PRODUCT_ID, basePlanId=BASE_PLAN_ID, body={}
    ).execute()
    print("Base plan activated.")


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    sa_path = sys.argv[1]
    if not os.path.isfile(sa_path):
        sys.exit(f"service account file not found: {sa_path}")

    creds = service_account.Credentials.from_service_account_file(sa_path, scopes=SCOPES)
    service = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)

    subscription = get_subscription(service)
    if subscription is None:
        subscription = create_subscription(service)
    else:
        print(f"Subscription '{PRODUCT_ID}' already exists — verifying base plan state.")

    ensure_base_plan_active(service, subscription)

    final = service.monetization().subscriptions().get(
        packageName=PACKAGE, productId=PRODUCT_ID
    ).execute()
    plan_states = {
        p.get("basePlanId"): p.get("state") for p in final.get("basePlans", [])
    }
    print(f"Done. '{PRODUCT_ID}' base plan states: {plan_states}")
    if plan_states.get(BASE_PLAN_ID) != "ACTIVE":
        sys.exit(1)


if __name__ == "__main__":
    main()
