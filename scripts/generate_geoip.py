#!/usr/bin/env python3
"""
GeoIP IPv4 Database Generator for BlockAds.

Source data:
  Provider: sapics/ip-location-db (iptoasn-country-ipv4-num.csv)
  Repository: https://github.com/sapics/ip-location-db
  License: CC0 1.0 Universal (Public Domain)

Binary Format (10 bytes per record, Big-Endian):
  - 4 bytes: start_ip (uint32)
  - 4 bytes: end_ip (uint32)
  - 2 bytes: country_code (ISO 3166-1 alpha-2 ASCII, e.g. "US", "VN")
"""

import os
import socket
import struct
import sys
import urllib.request

import argparse
import gzip
import os
import socket
import struct
import sys
import urllib.request

PINNED_COMMIT = "a6167da0594fa6e3da6b3d9b858122d98fc50638"
DEFAULT_OUTPUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "assets", "preset", "geoip_ipv4.bin"
)


def parse_ip(val: str) -> int:
    val = val.strip()
    if "." in val:
        return struct.unpack("!I", socket.inet_aton(val))[0]
    return int(val)


def main():
    parser = argparse.ArgumentParser(description="GeoIP IPv4 Database Generator for BlockAds")
    parser.add_argument("csv_path", nargs="?", default=None, help="Optional local CSV file path")
    parser.add_argument("-o", "--output", default=DEFAULT_OUTPUT, help="Output .bin file path")
    parser.add_argument("-z", "--gzip", action="store_true", help="Also generate .bin.gz compressed file")
    parser.add_argument("--latest", action="store_true", help="Download latest master commit instead of pinned commit")
    parser.add_argument("--url", default=None, help="Custom data source URL")

    args = parser.parse_args()

    local_csv = args.csv_path or os.environ.get("GEOIP_CSV_PATH")
    output_path = os.path.abspath(args.output)

    if local_csv and os.path.isfile(local_csv):
        print(f"Reading IPv4 GeoIP data from local file: {local_csv}")
        with open(local_csv, "r", encoding="utf-8") as f:
            data = f.read()
    else:
        if args.url:
            data_url = args.url
        elif args.latest:
            data_url = "https://raw.githubusercontent.com/sapics/ip-location-db/master/user-country/user-country-ipv4.csv"
        else:
            data_url = f"https://raw.githubusercontent.com/sapics/ip-location-db/{PINNED_COMMIT}/user-country/user-country-ipv4.csv"

        print(f"Downloading IPv4 GeoIP data from: {data_url}...")
        req = urllib.request.Request(data_url, headers={"User-Agent": "BlockAds-GeoIP-Builder/1.0"})
        with urllib.request.urlopen(req) as resp:
            data = resp.read().decode("utf-8")

    lines = data.strip().splitlines()
    ranges = []
    for line in lines:
        parts = line.strip().split(",")
        if len(parts) >= 3:
            try:
                start_ip = parse_ip(parts[0])
                end_ip = parse_ip(parts[1])
                cc = parts[2].strip().upper()
                if len(cc) == 2 and start_ip <= end_ip:
                    ranges.append((start_ip, end_ip, cc))
            except Exception:
                pass

    # Sort deterministically by start_ip
    ranges.sort(key=lambda x: (x[0], x[1]))

    # Merge adjacent or overlapping ranges with identical country
    merged = []
    for r in ranges:
        if merged and merged[-1][2] == r[2] and merged[-1][1] + 1 >= r[0]:
            merged[-1] = (merged[-1][0], max(merged[-1][1], r[1]), r[2])
        else:
            merged.append(r)

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "wb") as f:
        for s, e, cc in merged:
            f.write(struct.pack(">II2s", s, e, cc.encode("ascii")))

    file_size = os.path.getsize(output_path)
    print(f"Successfully generated {output_path} with {len(merged)} records ({file_size} bytes).")

    if args.gzip:
        gz_path = output_path + ".gz" if not output_path.endswith(".gz") else output_path
        with open(output_path, "rb") as f_in, gzip.open(gz_path, "wb", compresslevel=9) as f_out:
            f_out.write(f_in.read())
        gz_size = os.path.getsize(gz_path)
        print(f"Successfully compressed {gz_path} ({gz_size} bytes).")


if __name__ == "__main__":
    main()

