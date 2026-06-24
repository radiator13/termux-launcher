#!/data/data/com.termux/files/usr/bin/python3
"""Example notification digest script for LauncherCtl.

By default this produces a deterministic local digest. With --ai it sends a
small prompt to the local OpenAI-compatible TAI endpoint using the first listed
chat model. If no model is loaded or generation fails, it falls back to the
deterministic digest.
"""

import argparse
import collections
import json
import os
import time
import urllib.error
import urllib.request


def read_config():
    base_dir = os.path.expanduser("~/.launcherctl")
    with open(os.path.join(base_dir, "endpoint"), "r", encoding="utf-8") as handle:
        base_url = next(line.strip() for line in handle if line.strip()).rstrip("/")
    with open(os.path.join(base_dir, "token"), "r", encoding="utf-8") as handle:
        token = next(line.strip() for line in handle if line.strip())
    return base_url, token


def request(method, path, payload=None, timeout=60):
    base_url, token = read_config()
    headers = {"Authorization": "Bearer " + token, "Accept": "application/json"}
    data = None
    if payload is not None:
        data = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(base_url + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as response:
        body = response.read().decode("utf-8")
        return json.loads(body) if body else {}


def notification_text(event):
    note = event.get("notification") or {}
    title = note.get("title") or ""
    text = note.get("text") or ""
    package = note.get("packageName") or "unknown"
    event_type = event.get("eventType") or "event"
    return "%s %s: %s - %s" % (package, event_type, title, text)


def deterministic_digest(events):
    by_package = collections.Counter((event.get("notification") or {}).get("packageName") or "unknown"
                                     for event in events)
    lines = []
    lines.append("Notification digest")
    lines.append("Events: %d" % len(events))
    for package, count in by_package.most_common():
        lines.append("- %s: %d" % (package, count))
    if events:
        latest = events[0]
        lines.append("Latest: %s" % notification_text(latest))
    return "\n".join(lines)


def ai_digest(events):
    models = request("GET", "/v1/models")
    model_id = None
    for model in models.get("data", []):
        if "text_chat" in model.get("_capabilities", []):
            model_id = model.get("id")
            break
    if not model_id:
        raise RuntimeError("no chat model advertised")
    compact_events = [notification_text(event) for event in events[:20]]
    prompt = "Summarize these phone notifications briefly and flag anything important:\n" + "\n".join(compact_events)
    response = request("POST", "/v1/chat/completions", {
        "model": model_id,
        "messages": [{"role": "user", "content": prompt}],
        "max_tokens": 180,
    }, timeout=180)
    return response["choices"][0]["message"]["content"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--hours", type=float, default=24.0)
    parser.add_argument("--limit", type=int, default=100)
    parser.add_argument("--ai", action="store_true")
    args = parser.parse_args()

    since = int((time.time() - (args.hours * 3600.0)) * 1000)
    payload = request("POST", "/v1/notifications/since", {"since": since, "limit": args.limit})
    events = payload.get("events", [])
    if args.ai and events:
        try:
            print(ai_digest(events))
            return
        except Exception as error:
            print("AI digest unavailable: %s" % error)
            print()
    print(deterministic_digest(events))


if __name__ == "__main__":
    main()
