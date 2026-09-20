#!/usr/bin/env python3
"""Emit one GitHub Actions error annotation per failing unit test.

The raw Gradle test log is not downloadable from every environment, so this
reads the JUnit XML Gradle writes under app/build/test-results and turns each
failed/errored testcase into a `::error` annotation visible on the check run.
"""
import glob
import sys
import xml.etree.ElementTree as ET

PATTERN = "app/build/test-results/testDebugUnitTest/TEST-*.xml"


def main() -> int:
    files = sorted(glob.glob(PATTERN))
    if not files:
        print(
            "::error title=Unit tests::no test-result XML found under "
            + PATTERN
            + " - the tests may never have run (check test.log)"
        )
        return 0

    failures = 0
    for path in files:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as exc:
            print(f"::error title=Unit tests::unparseable result file {path}: {exc}")
            failures += 1
            continue
        for case in root.iter("testcase"):
            problems = list(case.iter("failure")) + list(case.iter("error"))
            if not problems:
                continue
            cls = case.get("classname", "?")
            name = case.get("name", "?")
            message = problems[0].get("message") or "no message"
            message = " ".join(message.split())[:400]
            print(f"::error title=Test failed::{cls} > {name} - {message}")
            failures += 1

    if failures == 0:
        print("::notice title=Unit tests::result XML present but no failures parsed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
