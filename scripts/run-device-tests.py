#!/usr/bin/env python3
"""Run Cucumber instrumented tests and reject empty or skipped runs."""
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parent.parent
started = time.time()
result = subprocess.run([str(root / 'gradlew'), ':app:connectedDebugAndroidTest', '--no-daemon'], cwd=root)
if result.returncode:
    sys.exit(result.returncode)

reports = list((root / 'app/build/outputs/androidTest-results/connected').rglob('TEST-*.xml'))
reports = [report for report in reports if report.stat().st_mtime >= started - 2]
if not reports:
    sys.exit('실행 결과가 없습니다. 에뮬레이터와 테스트 발견 여부를 확인하세요.')

totals = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}
for report in reports:
    suite = ET.parse(report).getroot()
    for key in totals:
        totals[key] += int(suite.get(key, '0'))
print('Android 테스트 결과:', totals)
if totals['tests'] == 0 or totals['failures'] or totals['errors'] or totals['skipped']:
    sys.exit('테스트가 없거나 실패·오류·건너뛴 시나리오가 있습니다.')
