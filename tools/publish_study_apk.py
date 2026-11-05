#!/usr/bin/env python3
"""Build and publish a Study update using the local, already trusted signing key."""

import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time


REPOSITORY = "fastmandotcom-creator/duosub-study"
APK_NAME = "DualSub-Study.apk"
TAG_PATTERN = re.compile(r"^study-(\d+)\.(\d+)\.(\d+)(?:\.(\d+))?$")


def command(*args, input_text=None):
    result = subprocess.run(
        args,
        input=input_text,
        text=True,
        capture_output=True,
    )
    if result.returncode:
        raise RuntimeError(
            f"Command failed ({' '.join(args)}): {result.stderr.strip() or result.stdout.strip()}"
        )
    return result.stdout.strip()


def api(method, path, data):
    return json.loads(
        command(
            "gh",
            "api",
            f"repos/{REPOSITORY}/{path}",
            "--method",
            method,
            "--input",
            "-",
            input_text=json.dumps(data),
        ),
    )


def release_version(tag):
    match = TAG_PATTERN.fullmatch(tag)
    if not match:
        return None
    return tuple(int(value or 0) for value in match.groups())


def study_releases():
    releases = json.loads(
        command("gh", "api", f"repos/{REPOSITORY}/releases?per_page=100"),
    )
    return [(release_version(item["tag_name"]), item) for item in releases]


def ensure_release_tag(tag, source_sha):
    existing = command("git", "ls-remote", "origin", f"refs/tags/{tag}").split()
    if existing:
        if existing[0] != source_sha:
            raise RuntimeError(f"Release tag {tag} already points to a different commit.")
        return
    api("POST", "git/refs", {
        "ref": f"refs/tags/{tag}",
        "sha": source_sha,
    })


def android_sdk():
    configured = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not configured:
        configured = str(Path.home() / "Library" / "Android" / "sdk")
    sdk = Path(configured)
    if not sdk.is_dir():
        raise RuntimeError(f"Android SDK not found: {sdk}")
    return sdk


def read_badging(aapt, apk):
    return command(str(aapt), "dump", "badging", str(apk)).splitlines()[0]


def signing_certificate(apksigner, apk):
    output = command(str(apksigner), "verify", "--print-certs", str(apk))
    match = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)", output)
    if not match:
        raise RuntimeError(f"Could not read the signing certificate from {apk}")
    return match.group(1).lower()


def main():
    branch = command("git", "branch", "--show-current")
    source_sha = command("git", "rev-parse", "HEAD")
    remote_sha = command("git", "ls-remote", "origin", "refs/heads/main").split()[0]
    if branch != "main" or source_sha != remote_sha:
        raise RuntimeError("Push the current commit to origin/main before publishing.")

    source = Path("app/build.gradle.kts").read_text(encoding="utf-8")
    version_match = re.search(r'^val appVersionName = "(\d+\.\d+\.\d+)"$', source, re.M)
    if not version_match:
        raise RuntimeError("Cannot read appVersionName from app/build.gradle.kts")
    app_base = tuple(map(int, version_match.group(1).split(".")))

    releases = study_releases()
    if not releases:
        raise RuntimeError("No previous Study release was found.")
    latest_public = max(
        (
            item for item in releases
            if item[0] is not None and not item[1]["draft"] and not item[1]["prerelease"]
        ),
        key=lambda item: item[0],
        default=None,
    )
    if latest_public is None:
        raise RuntimeError("No published Study release was found.")
    tagged_releases = [item for item in releases if item[0] is not None]
    if max(item[0] for item in tagged_releases)[:3] > app_base:
        raise RuntimeError("appVersionName is older than the latest Study release.")

    matching_draft = max(
        (
            item for item in tagged_releases
            if item[1]["draft"] and item[0][:3] == app_base
        ),
        key=lambda item: item[0],
        default=None,
    )
    if not matching_draft:
        base = app_base
        build = max(
            (item[0][3] for item in tagged_releases if item[0][:3] == base),
            default=0,
        ) + 1
        tag = f"study-{base[0]}.{base[1]}.{base[2]}.{build}"
        version_name = f"{app_base[0]}.{app_base[1]}.{app_base[2]}-study.{build}"
        matching_draft = next(
            (
                item for item in releases
                if item[1]["draft"] and item[1]["name"] == f"DualSub Study {version_name}"
            ),
            None,
        )
    else:
        tag = matching_draft[1]["tag_name"]
        build = matching_draft[0][3]

    version_name = f"{app_base[0]}.{app_base[1]}.{app_base[2]}-study.{build}"
    notes = (
        "Video gốc phát từng câu, sau đó đọc tiếng Việt rồi tiếng Anh đúng số lần đã chọn.\n\n"
        f"Built from main commit {source_sha}.\n\n<!-- study-source:{source_sha} -->"
    )
    ensure_release_tag(tag, source_sha)
    if matching_draft:
        release_id = matching_draft[1]["id"]
        api("PATCH", f"releases/{release_id}", {
            "tag_name": tag,
            "name": f"DualSub Study {version_name}",
            "body": notes,
        })
    else:
        draft = api("POST", "releases", {
            "tag_name": tag,
            "target_commitish": source_sha,
            "name": f"DualSub Study {version_name}",
            "body": notes,
            "draft": True,
            "prerelease": False,
        })
        release_id = draft["id"]

    sdk = android_sdk()
    aapt = sdk / "build-tools" / "36.0.0" / "aapt"
    apksigner = sdk / "build-tools" / "36.0.0" / "apksigner"
    if not aapt.is_file() or not apksigner.is_file():
        raise RuntimeError("Android build-tools 36.0.0 are required.")

    with tempfile.TemporaryDirectory(prefix="duosub-study-release-") as temporary:
        directory = Path(temporary)
        command(
            "gh",
            "release",
            "download",
            latest_public[1]["tag_name"],
            "--repo",
            REPOSITORY,
            "--pattern",
            APK_NAME,
            "--dir",
            str(directory),
        )
        previous_apk = directory / APK_NAME
        previous_code = re.search(r"versionCode='(\d+)'", read_badging(aapt, previous_apk))
        if not previous_code:
            raise RuntimeError("Could not read the current Study APK version code.")
        version_code = max(43, int(time.time()) - 1_700_000_000, int(previous_code[1]) + 1)

        android_environment = os.environ.copy()
        android_environment["ANDROID_HOME"] = str(sdk)
        android_environment["ANDROID_SDK_ROOT"] = str(sdk)
        subprocess.run(
            [
                "./gradlew",
                "assembleDebug",
                f"-PpreviewVersionCode={version_code}",
                f"-PpreviewVersionNameSuffix=-study.{build}",
                "-PpreviewApplicationIdSuffix=.study",
            ],
            env=android_environment,
            check=True,
        )

        apk = Path("app/build/outputs/apk/debug/app-debug.apk")
        badging = read_badging(aapt, apk)
        if (
            "name='duosub.study.study'" not in badging
            or f"versionName='{version_name}'" not in badging
            or f"versionCode='{version_code}'" not in badging
        ):
            raise RuntimeError(f"Built APK has the wrong package or version: {badging}")
        if signing_certificate(apksigner, apk) != signing_certificate(apksigner, previous_apk):
            raise RuntimeError("Local signing key does not match the currently published Study APK.")

        command(
            "gh",
            "release",
            "upload",
            tag,
            str(apk),
            "--repo",
            REPOSITORY,
            "--clobber",
        )
        api("PATCH", f"releases/{release_id}", {
            "draft": False,
            "prerelease": False,
            "make_latest": "true",
        })

    print(f"Published https://github.com/{REPOSITORY}/releases/tag/{tag}")


if __name__ == "__main__":
    main()
