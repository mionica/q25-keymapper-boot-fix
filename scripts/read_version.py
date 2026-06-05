#!/usr/bin/env python3
import re
import sys
from pathlib import Path


VERSION_RE = re.compile(
    r'^\s*versionName\s+System\.getenv\("VERSION_NAME"\)\s*\?:\s*"([^"]+)"\s*$',
    re.MULTILINE,
)


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: read_version.py <app/build.gradle>", file=sys.stderr)
        return 2

    text = Path(sys.argv[1]).read_text(encoding="utf-8-sig")
    match = VERSION_RE.search(text)
    if not match:
        print(f"Could not find versionName fallback in {sys.argv[1]}", file=sys.stderr)
        return 1

    print(match.group(1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
