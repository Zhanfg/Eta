#!/usr/bin/env python3
"""Host smoke tests for ETA workspace bridge (run as root in CI).

The bridge deliberately implements no display or app-control endpoints. A client
with the allowed Android-like UID can query capabilities; other UIDs cannot.
"""
import json
import os
import socket
import subprocess
import sys
import time

ADDRESS = "\0eta.workspace.bridge.v1"
UID = 10000

def request(command):
    s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    s.settimeout(2)
    try:
        s.connect(ADDRESS)
        s.sendall(command + b"\n")
        chunks = []
        while True:
            data = s.recv(512)
            if not data:
                break
            chunks.append(data)
            if b"\n" in data:
                break
        return b"".join(chunks)
    except (BrokenPipeError, ConnectionResetError):
        return b""
    finally:
        s.close()

def client_tests():
    for payload, success in [
        (b"HELLO 1", True),
        (b"CAPS", True),
        (b"PING", True),
        (b"LAUNCH com.example.fake", False),
        (b"HELLO 2", False),
    ]:
        result = json.loads(request(payload))
        assert result["ok"] is success, (payload, result)
        if payload == b"CAPS":
            assert result["display_backend"] is False
            assert result["app_launch"] is False
            assert result["targeted_input"] is False
            assert result["max_displays"] == 0
    print("PASS: bounded versioned protocol, unsupported opcodes fail closed")
    return 0

def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--client":
        return client_tests()
    if os.geteuid() != 0:
        raise SystemExit("Host SO_PEERCRED test requires root (sudo)")
    binary = sys.argv[1]
    server = subprocess.Popen([binary, "--app-uid", str(UID)], stdout=subprocess.DEVNULL)
    try:
        for _ in range(100):
            try:
                s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
                s.settimeout(.2)
                s.connect(ADDRESS)
                s.close()
                break
            except OSError:
                time.sleep(.02)
        else:
            raise AssertionError("bridge socket never appeared")
        assert request(b"PING") == b"", "root UID was not rejected"
        print("PASS: unauthorized peer UID rejected")
        result = subprocess.run(
            [sys.executable, __file__, "--client"],
            user=UID, group=UID, check=False,
        )
        assert result.returncode == 0
        print("PASS: authorized Android-like peer UID can query capabilities")
    finally:
        server.terminate()
        server.wait(timeout=3)
    return 0

if __name__ == "__main__":
    sys.exit(main())
