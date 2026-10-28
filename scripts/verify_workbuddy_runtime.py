#!/usr/bin/env python3
"""Reject empty, skipped, failed or incomplete Android backend acceptance runs."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

EXPECTED = {
    "fiveAccountsPersistOfflineAndCoreRestartsWithPrivateState",
    "importCannotStopAnActiveAgentRequest",
    "legacyPureGoResolverReproducesAndroidDnsFailure",
    "bionicDnsAndSystemTlsWorkInsideTheAndroidApplicationSandbox",
}
CLASS = "io.github.mangi.eta.data.workbuddy.WorkBuddyRuntimeDeviceTest"


def verify(root: Path) -> None:
    cases = []
    for report in root.rglob("TEST-*.xml"):
        cases.extend(case for case in ET.parse(report).getroot().iter("testcase")
                     if case.get("classname") == CLASS)
    if len(cases) != len(EXPECTED) or {case.get("name") for case in cases} != EXPECTED:
        raise ValueError("Expected exactly four named WorkBuddy Android tests; empty/incomplete run rejected")
    if any(case.find(tag) is not None for case in cases for tag in ("failure", "error", "skipped")):
        raise ValueError("WorkBuddy Android acceptance includes a failed or skipped test")
    print("PASS: all four required WorkBuddy Android runtime tests executed, with no failures or skips")


if __name__ == "__main__":
    try:
        verify(Path(sys.argv[1] if len(sys.argv) > 1 else
                    "app/build/outputs/androidTest-results/connected"))
    except (ValueError, ET.ParseError, OSError) as error:
        sys.exit(str(error))
