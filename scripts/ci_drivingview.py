#!/usr/bin/env python3
"""Run CI UI tests while the emulator's crashing DrivingView is temporarily disabled."""
import json
import os
import re
import signal
import subprocess
import sys
from pathlib import Path

PACKAGE = 'ai.umos.drivingview'
USER = '10'
STATE_FILE = Path('/tmp/roadcanvas-ci-drivingview-state.json')
COMMANDS = {'0': 'default-state', '1': 'enable', '2': 'disable', '3': 'disable-user', '4': 'disable-until-used'}


def adb(*args):
    sdk = os.environ['ANDROID_HOME']
    serial = os.environ['ANDROID_SERIAL']
    return subprocess.run(
        [f'{sdk}/platform-tools/adb', '-s', serial, *args],
        check=True, capture_output=True, text=True, timeout=30,
    ).stdout


def current_state():
    output = adb('shell', 'dumpsys', 'package', PACKAGE)
    match = re.search(r'^\s*User 10:.*?\benabled=(\d+)\b', output, re.MULTILINE)
    if not match or match.group(1) not in COMMANDS:
        raise RuntimeError('DrivingView 활성 상태를 확인할 수 없습니다.')
    return match.group(1)


def restore():
    if not STATE_FILE.exists():
        return
    saved = json.loads(STATE_FILE.read_text())
    if saved['serial'] != os.environ['ANDROID_SERIAL'] or saved['state'] not in COMMANDS:
        raise RuntimeError('저장된 DrivingView 상태가 현재 기기와 일치하지 않습니다.')
    adb('shell', 'pm', COMMANDS[saved['state']], '--user', USER, PACKAGE)
    if current_state() != saved['state']:
        raise RuntimeError('DrivingView 활성 상태를 복구하지 못했습니다.')
    STATE_FILE.unlink()
    print('DrivingView 원래 활성 상태 복구 완료', flush=True)


def run(command):
    restore()
    state = current_state()
    STATE_FILE.write_text(json.dumps({'serial': os.environ['ANDROID_SERIAL'], 'state': state}))
    try:
        adb('shell', 'pm', 'disable-user', '--user', USER, PACKAGE)
        if current_state() != '3':
            raise RuntimeError('DrivingView 비활성화를 확인하지 못했습니다.')
        print(f'DrivingView 일시 비활성화 (원래 상태 {state})', flush=True)
        return subprocess.run(command, check=False).returncode
    finally:
        restore()


def interrupt(signum, frame):
    raise KeyboardInterrupt(f'signal {signum}')


def main():
    signal.signal(signal.SIGTERM, interrupt)
    command = sys.argv[1:]
    if command and command[0] == '--':
        command = command[1:]
    if not command:
        raise ValueError('실행할 테스트 명령을 지정해야 합니다.')
    return run(command)


if __name__ == '__main__':
    sys.exit(main())
