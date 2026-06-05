#!/usr/bin/env python3
import re
import sys
from dataclasses import dataclass
from pathlib import Path


VERSION_RE = re.compile(
    r'^\s*versionName\s+System\.getenv\("VERSION_NAME"\)\s*\?:\s*"([^"]+)"\s*$',
    re.MULTILINE,
)

SEMVER_RE = re.compile(
    r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)"
    r"(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?"
    r"(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$"
)


@dataclass(frozen=True)
class SemVer:
    major: int
    minor: int
    patch: int
    prerelease: tuple[str, ...]


def read_version(path: Path) -> str:
    try:
        text = path.read_text(encoding="utf-8-sig")
    except UnicodeDecodeError:
        text = path.read_text(encoding="utf-16")
    match = VERSION_RE.search(text)
    if not match:
        raise ValueError(f"Could not find versionName fallback in {path}")
    return match.group(1)


def parse_semver(value: str) -> SemVer:
    match = SEMVER_RE.match(value)
    if not match:
        raise ValueError(f"{value!r} is not valid SemVer")
    prerelease = tuple((match.group(4) or "").split(".")) if match.group(4) else ()
    return SemVer(
        major=int(match.group(1)),
        minor=int(match.group(2)),
        patch=int(match.group(3)),
        prerelease=prerelease,
    )


def compare_ident(left: str, right: str) -> int:
    left_num = left.isdigit()
    right_num = right.isdigit()
    if left_num and right_num:
        return (int(left) > int(right)) - (int(left) < int(right))
    if left_num != right_num:
        return -1 if left_num else 1
    return (left > right) - (left < right)


def compare(left: SemVer, right: SemVer) -> int:
    core_left = (left.major, left.minor, left.patch)
    core_right = (right.major, right.minor, right.patch)
    if core_left != core_right:
        return (core_left > core_right) - (core_left < core_right)

    if not left.prerelease and not right.prerelease:
        return 0
    if not left.prerelease:
        return 1
    if not right.prerelease:
        return -1

    for left_part, right_part in zip(left.prerelease, right.prerelease):
        result = compare_ident(left_part, right_part)
        if result:
            return result
    return (len(left.prerelease) > len(right.prerelease)) - (
        len(left.prerelease) < len(right.prerelease)
    )


def main() -> int:
    if len(sys.argv) != 3:
        print(
            "Usage: check_version_bump.py <base app/build.gradle> <head app/build.gradle>",
            file=sys.stderr,
        )
        return 2

    base_version = read_version(Path(sys.argv[1]))
    head_version = read_version(Path(sys.argv[2]))
    base = parse_semver(base_version)
    head = parse_semver(head_version)

    if compare(head, base) <= 0:
        print(
            f"Version must increase: base is {base_version}, PR is {head_version}. "
            "Bump at least the patch version.",
            file=sys.stderr,
        )
        return 1

    print(f"Version bump OK: {base_version} -> {head_version}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
