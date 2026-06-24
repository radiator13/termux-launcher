#!/data/data/com.termux/files/usr/bin/python3
"""Example local CLI agent using LauncherCtl tools.

Examples:
  ./example-agentic-client.py route "open termux"
  ./example-agentic-client.py execute capabilities.get '{}'
  ./example-agentic-client.py execute memory.write '{"namespace":"demo","key":"note","value":"hello"}' --confirm
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request


def read_config():
    base_dir = os.path.expanduser("~/.launcherctl")
    with open(os.path.join(base_dir, "endpoint"), "r", encoding="utf-8") as handle:
        base_url = next(line.strip() for line in handle if line.strip()).rstrip("/")
    with open(os.path.join(base_dir, "token"), "r", encoding="utf-8") as handle:
        token = next(line.strip() for line in handle if line.strip())
    return base_url, token


def request(method, path, payload=None):
    base_url, token = read_config()
    headers = {
        "Authorization": "Bearer " + token,
        "Accept": "application/json",
    }
    data = None
    if payload is not None:
        data = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(base_url + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            body = response.read().decode("utf-8")
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", "replace")
        try:
            return json.loads(body)
        except json.JSONDecodeError:
            return {"ok": False, "error": "http_error", "status": error.code, "message": body}


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="cmd", required=True)

    route = sub.add_parser("route")
    route.add_argument("text")

    execute = sub.add_parser("execute")
    execute.add_argument("tool")
    execute.add_argument("arguments", nargs="?", default="{}")
    execute.add_argument("--confirm", action="store_true")

    sub.add_parser("tools")

    args = parser.parse_args()

    if args.cmd == "tools":
        result = request("GET", "/v1/agent/tools")
    elif args.cmd == "route":
        result = request("POST", "/v1/agent/route", {"request": args.text})
    else:
        try:
            tool_args = json.loads(args.arguments)
        except json.JSONDecodeError as error:
            print("invalid arguments JSON: %s" % error, file=sys.stderr)
            return 2
        result = request("POST", "/v1/agent/execute", {
            "tool": args.tool,
            "arguments": tool_args,
            "confirm": args.confirm,
        })

    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0 if result.get("ok", True) else 1


if __name__ == "__main__":
    sys.exit(main())
