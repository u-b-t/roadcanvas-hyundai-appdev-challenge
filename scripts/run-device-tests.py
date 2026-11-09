#!/usr/bin/env python3
"""Run Cucumber instrumented tests and reject empty or skipped runs.

Without options every scenario runs. `--feature` (repeatable) and `--tags` pass
Cucumber-Android runner arguments to run only part of the scenarios.
"""
import argparse
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FEATURES_DIR = ROOT / 'app/src/androidTest/assets/features'
RUNNER_ARGUMENT = '-Pandroid.testInstrumentationRunnerArguments.'


def feature_path(name):
    """Turn `free_drawing`, `free_drawing.feature` or a file path into the asset path Cucumber reads."""
    file_name = Path(name).name
    if not file_name.endswith('.feature'):
        file_name += '.feature'
    if not (FEATURES_DIR / file_name).is_file():
        raise ValueError(f'기능 파일이 없습니다: {FEATURES_DIR.relative_to(ROOT) / file_name}')
    return f'features/{file_name}'


def runner_arguments(features, tags):
    arguments = []
    if features:
        paths = list(dict.fromkeys(feature_path(name) for name in features))
        arguments.append(f'{RUNNER_ARGUMENT}features={",".join(paths)}')
    if tags:
        # 공백이 든 태그 식도 그대로 전달된다. 따옴표를 더하면 태그 식에 포함되어 해석에 실패한다.
        arguments.append(f'{RUNNER_ARGUMENT}tags={tags}')
    return arguments


def parse_args(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        '--feature',
        action='append',
        default=[],
        help='실행할 기능 파일 이름(예: free_drawing.feature). 여러 번 지정할 수 있다.'
    )
    parser.add_argument('--tags', help='Cucumber 태그 식(예: "@smoke and not @slow")')
    return parser.parse_args(argv)


def main(argv):
    args = parse_args(argv)
    try:
        extra = runner_arguments(args.feature, args.tags)
    except ValueError as error:
        sys.exit(str(error))

    print('대상 기기:', os.environ.get('ANDROID_SERIAL', '(ANDROID_SERIAL 미지정)'))
    print('실행 범위:', ' '.join(extra) if extra else '전체 시나리오')
    started = time.time()
    command = [str(ROOT / 'gradlew'), ':app:connectedDebugAndroidTest', '--no-daemon', *extra]
    result = subprocess.run(command, cwd=ROOT)
    if result.returncode:
        sys.exit(result.returncode)

    reports = list((ROOT / 'app/build/outputs/androidTest-results/connected').rglob('TEST-*.xml'))
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


if __name__ == '__main__':
    main(sys.argv[1:])
