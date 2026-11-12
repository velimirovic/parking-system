#!/usr/bin/env python3
"""
Simulacija klijenta PSP-a (umesto parking-service-a) - salje potpisane zahteve na merchant API.

Primeri:
  python scripts/psp_merchant.py init --amount 120 --currency RSD --description "Parking - Zona 1, 60 min"
  python scripts/psp_merchant.py status PRK-20261112-AB12CD

Podrazumevano koristi demo parking klijenta (MRC-00042) i PSP na http://localhost:8082.
Promenljive okruzenja: PSP_URL, PSP_MERCHANT_ID, PSP_API_KEY, PSP_SIGNING_SECRET.
"""
import argparse
import hashlib
import hmac
import json
import os
import secrets
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

PSP_URL = os.environ.get("PSP_URL", "http://localhost:8082")
MERCHANT_ID = os.environ.get("PSP_MERCHANT_ID", "MRC-00042")
API_KEY = os.environ.get("PSP_API_KEY", "sk_test_parking_demo_0000000000000000")
SIGNING_SECRET = os.environ.get("PSP_SIGNING_SECRET", "whsec_parking_demo_00000000000000000")


def sign(method: str, path: str, body: str) -> dict:
    """X-Signature = hex(HMAC-SHA256(tajna, timestamp \\n METHOD \\n path \\n sha256_hex(body)))."""
    timestamp = str(int(time.time()))
    body_hash = hashlib.sha256(body.encode("utf-8")).hexdigest()
    canonical = f"{timestamp}\n{method}\n{path}\n{body_hash}"
    signature = hmac.new(SIGNING_SECRET.encode("utf-8"), canonical.encode("utf-8"), hashlib.sha256).hexdigest()
    return {
        "X-Merchant-Id": MERCHANT_ID,
        "X-Api-Key": API_KEY,
        "X-Timestamp": timestamp,
        "X-Signature": signature,
    }


def call(method: str, path: str, payload: dict | None = None):
    body = "" if payload is None else json.dumps(payload, separators=(",", ":"))
    headers = sign(method, path, body)
    if payload is not None:
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(PSP_URL + path, data=body.encode("utf-8") if payload is not None else None,
                                     method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return response.status, json.loads(response.read() or b"null")
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read() or b"null")


def main() -> int:
    parser = argparse.ArgumentParser(description="Simulacija klijenta PSP-a")
    sub = parser.add_subparsers(dest="command", required=True)

    init = sub.add_parser("init", help="inicijalizacija transakcije")
    init.add_argument("--amount", default="120.00")
    init.add_argument("--currency", default="RSD")
    init.add_argument("--order-id", help="MERCHANT_ORDER_ID (podrazumevano nasumican)")
    init.add_argument("--description")

    status = sub.add_parser("status", help="status transakcije po MERCHANT_ORDER_ID")
    status.add_argument("order_id")

    args = parser.parse_args()
    if args.command == "init":
        order_id = args.order_id or "PRK-" + datetime.now().strftime("%Y%m%d") + "-" + secrets.token_hex(3).upper()
        payload = {
            "merchant_order_id": order_id,
            "merchant_timestamp": datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z"),
            "amount": args.amount,
            "currency": args.currency,
        }
        if args.description:
            payload["description"] = args.description
        code, body = call("POST", "/transactions", payload)
    else:
        code, body = call("GET", "/transactions/" + args.order_id)

    print(f"HTTP {code}")
    print(json.dumps(body, indent=2, ensure_ascii=False))
    return 0 if code < 400 else 1


if __name__ == "__main__":
    sys.exit(main())
