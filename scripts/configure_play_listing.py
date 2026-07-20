#!/usr/bin/env python3
"""Configure the Google Play store listing for PixelDream via the Play Developer API.

Idempotent: clears and re-uploads images, then patches the localized listing and
app details (contact info / privacy policy). Safe to re-run on every publish.

Usage:
    configure_play_listing.py <service-account-json-path>

Environment overrides:
    PLAY_PACKAGE_NAME      (default com.hartmann.pixeldream)
    PLAY_DEFAULT_LANGUAGE  (default en-US)
    PLAY_PROMO_VIDEO       (default YouTube promo URL)
    PLAY_CONTACT_EMAIL     (default maintainer email)
    PLAY_CONTACT_WEBSITE   (default GitHub Pages site)
    PLAY_PRIVACY_POLICY_URL
"""
import os
import sys

from google.oauth2 import service_account
from googleapiclient import http
from googleapiclient.discovery import build

PACKAGE = os.environ.get("PLAY_PACKAGE_NAME") or "com.hartmann.pixeldream"
DEFAULT_LANG = os.environ.get("PLAY_DEFAULT_LANGUAGE") or "en-US"
PROMO_VIDEO = os.environ.get("PLAY_PROMO_VIDEO") or (
    "https://www.youtube.com/watch?v=xuy7Br6eYnQ"
)
CONTACT_EMAIL = os.environ.get("PLAY_CONTACT_EMAIL") or "charles.h.hartmann1@gmail.com"
CONTACT_WEBSITE = os.environ.get(
    "PLAY_CONTACT_WEBSITE"
) or "https://chartmann1590.github.io/pixeldream/"
PRIVACY_POLICY_URL = os.environ.get(
    "PLAY_PRIVACY_POLICY_URL"
) or "https://chartmann1590.github.io/pixeldream/privacy/"

LISTING_DIR = "playstore/listing"
ASSETS_DIR = "playstore/assets"

SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]

# Play image-type enum (camelCase) -> local directory
IMAGE_SETS = [
    ("icon", f"{ASSETS_DIR}/icon"),
    ("featureGraphic", f"{ASSETS_DIR}/feature-graphic"),
    ("phoneScreenshots", f"{ASSETS_DIR}/screenshots/phone"),
    ("sevenInchScreenshots", f"{ASSETS_DIR}/screenshots/tablet-7in"),
    ("tenInchScreenshots", f"{ASSETS_DIR}/screenshots/tablet-10in"),
]


def read_listing(name):
    with open(os.path.join(LISTING_DIR, name), encoding="utf-8") as f:
        return f.read().strip()


def upload_image_set(service, edit_id, image_type, directory):
    cleared = False
    try:
        service.edits().images().deleteall(
            packageName=PACKAGE,
            editId=edit_id,
            language=DEFAULT_LANG,
            imageType=image_type,
        ).execute()
        cleared = True
    except Exception:
        # No images of this type yet — that is fine.
        pass
    if not os.path.isdir(directory):
        print(f"  [{image_type}] skipped (no directory {directory})")
        return
    files = sorted(
        f for f in os.listdir(directory) if f.lower().endswith((".png", ".jpg", ".jpeg"))
    )
    if not files:
        print(f"  [{image_type}] no images found in {directory}")
        return
    for fname in files:
        path = os.path.join(directory, fname)
        media = http.MediaFileUpload(path, mimetype="image/png", resumable=False)
        result = service.edits().images().upload(
            packageName=PACKAGE,
            editId=edit_id,
            language=DEFAULT_LANG,
            imageType=image_type,
            media_body=media,
        ).execute()
        sha = result.get("image", {}).get("sha256", "")[:12]
        print(f"  [{image_type}] uploaded {fname} (sha {sha}…) cleared_prior={cleared}")


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    sa_path = sys.argv[1]
    if not os.path.isfile(sa_path):
        sys.exit(f"service account file not found: {sa_path}")

    creds = service_account.Credentials.from_service_account_file(sa_path, scopes=SCOPES)
    service = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)

    edit = service.edits().insert(packageName=PACKAGE, body={}).execute()
    edit_id = edit["id"]
    print(f"Created edit {edit_id} for {PACKAGE}")

    try:
        # 1) Images (icon, feature graphic, screenshots).
        print("Uploading graphic assets…")
        for image_type, directory in IMAGE_SETS:
            upload_image_set(service, edit_id, image_type, directory)

        # 2) Localized store listing (title + descriptions + promo video).
        print("Patching store listing…")
        listing_body = {
            "title": read_listing("title.txt"),
            "shortDescription": read_listing("short-description.txt"),
            "fullDescription": read_listing("full-description.txt"),
            "video": PROMO_VIDEO,
        }
        # patch upserts the localized listing.
        service.edits().listings().patch(
            packageName=PACKAGE,
            editId=edit_id,
            language=DEFAULT_LANG,
            body=listing_body,
        ).execute()
        print(f"  listing '{listing_body['title']}' (video={PROMO_VIDEO})")

        # 3) App details (contact info, default language).
        #    NOTE: privacy policy URL is not a field on AppDetails in the v3
        #    edits API; it is set under Play Console → App content → Privacy
        #    Policy (one-time). The canonical URL is:
        #    https://chartmann1590.github.io/pixeldream/privacy/
        print("Patching app details…")
        details_body = {
            "contactEmail": CONTACT_EMAIL,
            "contactWebsite": CONTACT_WEBSITE,
            "defaultLanguage": DEFAULT_LANG,
        }
        service.edits().details().patch(
            packageName=PACKAGE, editId=edit_id, body=details_body
        ).execute()
        print(f"  contactWebsite={CONTACT_WEBSITE} contactEmail={CONTACT_EMAIL}")
        print(f"  (privacy policy URL must be set in Play Console -> App content: "
              f"{PRIVACY_POLICY_URL})")

        # 4) Validate before commit so a bad image/listing fails clearly.
        print("Validating edit…")
        service.edits().validate(packageName=PACKAGE, editId=edit_id).execute()

        print("Committing edit…")
        service.edits().commit(packageName=PACKAGE, editId=edit_id).execute()
        print("Store listing configured and committed.")
    except Exception as e:
        print(f"Listing edit failed, deleting edit {edit_id}: {e}")
        try:
            service.edits().delete(packageName=PACKAGE, editId=edit_id).execute()
        except Exception:
            pass
        raise


if __name__ == "__main__":
    main()
