#!/usr/bin/env python3
"""Push/list/rollback main.js over the adb-forwarded TCP port."""
import argparse
import json
import socket
import sys
from pathlib import Path


def request(host: str, port: int, token: str, payload: dict) -> dict:
    message = {"token": token, **payload}
    encoded = (json.dumps(message, ensure_ascii=False) + "\n").encode("utf-8")
    with socket.create_connection((host, port), timeout=10) as sock:
        sock.sendall(encoded)
        chunks = []
        while True:
            data = sock.recv(65536)
            if not data:
                break
            chunks.append(data)
            if b"\n" in data:
                break
    raw = b"".join(chunks).split(b"\n", 1)[0]
    if not raw:
        raise RuntimeError("Device closed the connection without a response")
    result = json.loads(raw.decode("utf-8"))
    if not result.get("ok"):
        raise RuntimeError(result.get("error", "request failed"))
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--token", required=True, help="Pairing token shown on app home screen")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("status")
    sub.add_parser("list")
    push = sub.add_parser("push")
    push.add_argument("script", type=Path, nargs="?", default=Path("app/src/main/assets/main.js"))
    push.add_argument("--note", default="CLI upload")
    rollback = sub.add_parser("rollback")
    rollback.add_argument("version", type=int)
    args = parser.parse_args()

    if args.command == "push":
        payload = {"cmd": "push", "source": args.script.read_text(encoding="utf-8"), "note": args.note}
    elif args.command == "rollback":
        payload = {"cmd": "rollback", "version": args.version}
    else:
        payload = {"cmd": args.command}

    result = request(args.host, args.port, args.token, payload)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"sync error: {exc}", file=sys.stderr)
        raise SystemExit(1)
