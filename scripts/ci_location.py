#!/usr/bin/env python3
"""Temporarily disable emulator location for fake-location CI UI tests."""
import json
import os
import signal
import subprocess
import sys
from pathlib import Path

STATE_FILE = Path('/tmp/roadcanvas-ci-location-state.json')
USER = '10'


def adb(*args):
    return subprocess.run(
        [f"{os.environ['ANDROID_HOME']}/platform-tools/adb", '-s', os.environ['ANDROID_SERIAL'], *args],
        capture_output=True, check=True, text=True, timeout=30,
    ).stdout.strip()


def current_state():
    state = adb('shell', 'cmd', 'location', 'is-location-enabled', '--user', USER)
    if state not in ('true', 'false'):
        raise RuntimeError(f'위치 설정을 확인할 수 없습니다: {state}')
    return state


def system_server_pid():
    pid = adb('shell', 'pidof', 'system_server')
    if not pid.isdigit():
        raise RuntimeError(f'system_server 프로세스를 확인할 수 없습니다: {pid}')
    return pid


def restore():
    if not STATE_FILE.exists():
        return
    saved = json.loads(STATE_FILE.read_text())
    if saved['serial'] != os.environ['ANDROID_SERIAL'] or saved['state'] not in ('true', 'false'):
        raise RuntimeError('저장된 위치 상태가 현재 기기와 일치하지 않습니다.')
    adb('shell', 'cmd', 'location', 'set-location-enabled', saved['state'], '--user', USER)
    if current_state() != saved['state']:
        raise RuntimeError('원래 위치 설정을 복구하지 못했습니다.')
    STATE_FILE.unlink()
    print('기존 위치 설정 복구 완료', flush=True)


def run(command):
    restore()
    state = current_state()
    STATE_FILE.write_text(json.dumps({'serial': os.environ['ANDROID_SERIAL'], 'state': state}))
    try:
        adb('shell', 'cmd', 'location', 'set-location-enabled', 'false', '--user', USER)
        if current_state() != 'false':
            raise RuntimeError('위치 비활성화를 확인하지 못했습니다.')
        print(f'위치 일시 비활성화 (원래 상태 {state})', flush=True)
        original_pid = system_server_pid()
        result = subprocess.run(command, check=False).returncode
        if system_server_pid() != original_pid:
            raise RuntimeError('테스트 중 system_server가 재시작됐습니다.')
        return result
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
