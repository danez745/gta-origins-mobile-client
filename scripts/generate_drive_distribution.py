#!/usr/bin/env python3
"""Generate launcher distribution.json from public Google Drive cache folders."""
from __future__ import annotations

import hashlib
import html
import json
import pathlib
import re
import time
import urllib.error
import urllib.request
from collections import deque
from typing import Any

ROOT_STANDARD = "1Tk6Uhtf96_z_MwqyvJts7CXNxCr14AT3"
ROOT_SNOW = "1J7O73eX8xzfMaD8AXMuXS9xodyjWB24G"
RELEASE_TAG = "cache-drive-b774c0b"
LAUNCHER_ASSET_NAME = "gta-origins-cache-drive-release.apk"
OUTPUT = pathlib.Path("distribution/distribution.json")
APK_PATH = pathlib.Path("android/app/build/outputs/apk/release/app-release.apk")
APP_VERSION = "0.0.16"

HEADERS = {"User-Agent": "Mozilla/5.0 (Hermes Work Assistant)"}
ROW_RE = re.compile(
    r'(?s)<tr[^>]*data-selectable[^>]*data-id="([^"]+)"[^>]*aria-selected="false"[^>]*>(.*?)</tr>'
)
ARIA_RE = re.compile(r'aria-label="([^"]+)"')
NAME_RE = re.compile(r'<strong class="DNoYtb">([^<]+)</strong>')

GPU_A = {
    "txd.dxt.toc", "txd.dxt.tmb", "txd.dxt.dat",
    "samp.dxt.toc", "samp.dxt.tmb", "samp.dxt.dat",
    "mobile.dxt.toc", "mobile.dxt.tmb", "mobile.dxt.dat",
    "gta3.dxt.dat", "gta3.dxt.tmb", "gta3.dxt.toc",
    "gta_int.dxt.toc", "gta_int.dxt.tmb", "gta_int.dxt.dat",
    "touch.dxt.toc", "touch.dxt.tmb", "touch.dxt.dat",
}
GPU_M = {
    "txd.etc.toc", "txd.etc.tmb", "txd.etc.dat",
    "samp.etc.toc", "samp.etc.tmb", "samp.etc.dat",
    "mobile.etc.toc", "mobile.etc.tmb", "mobile.etc.dat",
    "gta3.etc.dat", "gta3.etc.tmb", "gta3.etc.toc",
    "gta_int.etc.toc", "gta_int.etc.tmb", "gta_int.etc.dat",
    "touch.etc.toc", "touch.etc.tmb", "touch.etc.dat",
}
GPU_PT = {
    "txd.pvr.toc", "txd.pvr.tmb", "txd.pvr.dat",
    "samp.pvr.toc", "samp.pvr.tmb", "samp.pvr.dat",
    "mobile.pvr.toc", "mobile.pvr.tmb", "mobile.pvr.dat",
    "gta3.pvr.dat", "gta3.pvr.tmb", "gta3.pvr.toc",
    "gta_int.pvr.toc", "gta_int.pvr.tmb", "gta_int.pvr.dat",
    "touch.pvr.toc", "touch.pvr.tmb", "touch.pvr.dat",
}


def request(url: str, method: str = "GET") -> Any:
    req = urllib.request.Request(url, headers=HEADERS, method=method)
    return urllib.request.urlopen(req, timeout=90)


def fetch_folder_html(folder_id: str) -> str:
    with request(f"https://drive.google.com/drive/folders/{folder_id}") as response:
        return response.read().decode("utf-8", errors="replace")


def parse_folder(folder_html: str) -> list[dict]:
    items = []
    for drive_id, body in ROW_RE.findall(folder_html):
        aria_match = ARIA_RE.search(body)
        if not aria_match:
            continue
        aria = html.unescape(aria_match.group(1))
        name_match = NAME_RE.search(body)
        name = html.unescape(name_match.group(1)) if name_match else aria.split(" Binary")[0].split(" Shared folder")[0]
        is_folder = "Shared folder" in aria
        items.append({"id": drive_id, "name": name, "is_folder": is_folder})
    return items


def direct_url(file_id: str) -> str:
    return f"https://drive.usercontent.google.com/download?id={file_id}&export=download&authuser=0&confirm=t"


def content_length(file_id: str) -> int:
    url = direct_url(file_id)
    for attempt in range(4):
        try:
            with request(url, method="HEAD") as response:
                length = response.headers.get("Content-Length")
                content_type = response.headers.get("Content-Type", "")
                if length and "text/html" not in content_type:
                    return int(length)
        except Exception:
            if attempt == 3:
                raise
            time.sleep(1 + attempt)
    raise RuntimeError(f"No Content-Length for {file_id}")


def crawl(root_id: str) -> dict[str, dict]:
    queue = deque([("", root_id)])
    seen_folders: set[str] = set()
    files: dict[str, dict] = {}
    while queue:
        prefix, folder_id = queue.popleft()
        if folder_id in seen_folders:
            continue
        seen_folders.add(folder_id)
        for item in parse_folder(fetch_folder_html(folder_id)):
            rel_path = f"{prefix}/{item['name']}".lstrip("/")
            if item["is_folder"]:
                queue.append((rel_path, item["id"]))
            else:
                files[rel_path] = {
                    "id": item["id"],
                    "url": direct_url(item["id"]),
                    "bytes": content_length(item["id"]),
                }
    return files


def gpu_for(filename: str) -> str:
    if filename in GPU_A:
        return "A"
    if filename in GPU_M:
        return "M"
    if filename in GPU_PT:
        return "PT"
    return ""


def md5_file(path: pathlib.Path) -> str:
    h = hashlib.md5()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def make_cache(standard: dict[str, dict], snow: dict[str, dict]) -> tuple[list[dict], list[dict]]:
    cache = []
    cache_mode = []
    for idx, rel_path in enumerate(sorted(standard), 1):
        path_obj = pathlib.PurePosixPath(rel_path)
        name = path_obj.name
        folder = "" if str(path_obj.parent) == "." else str(path_obj.parent)
        std = standard[rel_path]
        snow_item = snow.get(rel_path)
        item = {
            "id": idx,
            "name": name,
            "path": folder,
            "bytes": [std["bytes"], snow_item["bytes"] if snow_item else std["bytes"]],
            "gpu": gpu_for(name),
            "url": std["url"],
        }
        if snow_item:
            item["snowUrl"] = snow_item["url"]
            cache_mode.append(item.copy())
        cache.append(item)
    return cache, cache_mode


def main() -> None:
    print("Crawling standard cache...")
    standard = crawl(ROOT_STANDARD)
    print(f"standard files: {len(standard)}")
    print("Crawling snow cache...")
    snow = crawl(ROOT_SNOW)
    print(f"snow override files: {len(snow)}")
    missing = sorted(set(snow) - set(standard))
    if missing:
        raise RuntimeError(f"Snow files missing in standard cache: {missing[:10]}")

    cache, cache_mode = make_cache(standard, snow)
    launcher_name = LAUNCHER_ASSET_NAME
    launcher_bytes = APK_PATH.stat().st_size if APK_PATH.exists() else 0
    launcher_hash = md5_file(APK_PATH) if APK_PATH.exists() else ""

    distribution = {
        "cache": cache,
        "cacheMode": cache_mode,
        "projectName": "Touch Mobile",
        "packageName": "com.touch.mobile.dark",
        "versionHash": str(int(time.time())),
        "rss": "https://touch-rp.com/api/launcer/news",
        "cdnCache": "",
        "cdnLauncher": f"https://github.com/danez745/gta-origins-mobile-client/releases/download/{RELEASE_TAG}",
        "filesContinue": ["settings.ini", "gta_sa.set", "svconfig.ini"],
        "launcher": {
            "appVersion": APP_VERSION,
            "name": launcher_name,
            "hash": launcher_hash,
            "mtime": int(time.time()),
            "bytes": launcher_bytes,
        },
        "servers": [
            {
                "id": 1,
                "show": True,
                "version": "1.0",
                "icon": "https://game.touch-rp.com/mobile/image/pro_icon.jpeg",
                "events": [
                    {"title": "Roleplay", "style": "red"},
                    {"title": "Indonesia", "style": "blue"},
                ],
                "slot": 50,
                "bonus": True,
                "name": "GTA: Origins",
                "description": "English/Indonesia Roleplay",
                "address": "208.76.40.98:7778",
                "sampVersion": "0.3.7",
            }
        ],
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(distribution, indent=2, ensure_ascii=False) + "\n")
    print(f"Wrote {OUTPUT} ({len(cache)} cache files, {len(cache_mode)} snow overrides)")


if __name__ == "__main__":
    main()
