#!/usr/bin/env python3
"""Verify the packaged Android core against the binary built in this run."""
import argparse
import hashlib
from pathlib import Path
import struct
import zipfile


def verify_elf(data):
    if len(data) < 64 or data[:7] != b"\x7fELF\x02\x01\x01":
        raise ValueError("Expected a complete little-endian ELF64 header")
    kind, machine = struct.unpack_from("<HH", data, 16)
    if kind != 3 or machine != 183:
        raise ValueError("Expected an Android AArch64 PIE executable")
    offset = struct.unpack_from("<Q", data, 32)[0]
    header_size, entry_size, count = struct.unpack_from("<HHH", data, 52)
    if header_size != 64 or entry_size != 56 or not 0 < count <= 4096:
        raise ValueError("Invalid ELF program header layout")
    if offset < header_size or offset + count * entry_size > len(data):
        raise ValueError("Truncated ELF program header table")
    interpreters = []
    executable_load = False
    for index in range(count):
        entry = offset + index * entry_size
        segment, flags, start = struct.unpack_from("<IIQ", data, entry)
        size, memory_size = struct.unpack_from("<QQ", data, entry + 32)
        if start + size > len(data):
            raise ValueError("Truncated ELF segment")
        if segment == 1:
            if memory_size < size:
                raise ValueError("Invalid ELF load segment")
            executable_load |= bool(flags & 1) and size > 0
        elif segment == 3:
            interpreters.append(data[start:start + size])
    if interpreters != [b"/system/bin/linker64\x00"] or not executable_load:
        raise ValueError("Missing executable load segment or Android interpreter")


def verify_apk(apk_path, reference_path):
    reference = Path(reference_path).read_bytes()
    verify_elf(reference)
    with zipfile.ZipFile(apk_path) as archive:
        name = "lib/arm64-v8a/libwbcore.so"
        if archive.namelist().count(name) != 1:
            raise ValueError("APK must contain exactly one ARM64 WorkBuddy core")
        if archive.testzip() is not None:
            raise ValueError("APK ZIP integrity check failed")
        packaged = archive.read(name)
    verify_elf(packaged)
    if packaged != reference:
        raise ValueError("Packaged core differs from the binary built in this run")
    return hashlib.sha256(packaged).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk")
    parser.add_argument("reference_core")
    args = parser.parse_args()
    try:
        digest = verify_apk(args.apk, args.reference_core)
    except (ValueError, OSError, zipfile.BadZipFile, struct.error):
        # Never include archive filenames, binary bytes or arbitrary exception text.
        parser.exit(1, "FAIL: WorkBuddy APK/core verification failed\n")
    print("PASS: APK contains the exact Android ARM64 PIE core built in this run")
    print("core_sha256=" + digest)


if __name__ == "__main__":
    main()
