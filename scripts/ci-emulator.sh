#!/usr/bin/env bash
# CI에서 Pleos 에뮬레이터를 준비하고, 망가진 에뮬레이터를 재시작한다.
#
#   scripts/ci-emulator.sh prepare
#     실행 중인 에뮬레이터가 건강하면 재사용하고, 아니면 종료한 뒤 콜드 부팅한다.
#   scripts/ci-emulator.sh recover <로그 파일 또는 디렉터리>...
#     테스트 실패 후 시스템 크래시 흔적이 있거나 상태가 나쁘면 재시작한다.
#
# 건강한 상태: sys.boot_completed가 1, user 0이 RUNNING_UNLOCKED, car_service가 found.
# 새 에뮬레이터는 RUNNER_TRACKING_ID를 비우고 새 세션으로 분리해 띄운다. 러너가 작업 종료 때
# 정리하지 않으므로 다음 작업이 재사용한다. macOS 기본 bash 3.2에서 동작해야 한다.
set -euo pipefail

SERIAL='emulator-5554'
PORT=5554
EMULATOR_LOG=${PLEOS_EMULATOR_LOG:-/tmp/pleos-ci-emulator.log}
ADB_TIMEOUT=20
# 이미 실행 중인 에뮬레이터가 부팅을 마칠 때까지 기다리는 시간
EXISTING_BOOT_TIMEOUT=300
# 새로 콜드 부팅한 에뮬레이터가 부팅을 마칠 때까지 기다리는 시간
COLD_BOOT_TIMEOUT=600
# 부팅 완료 뒤 user 0 잠금 해제와 car_service 등록을 기다리는 시간
SERVICE_GRACE=90
CRASH_PATTERN='System has crashed|DeadSystemException'

log() { echo "[ci-emulator] $*"; }
fail() {
  echo "::error::$*" >&2
  exit 1
}

with_timeout() {
  local seconds=$1
  shift
  perl -e 'alarm shift @ARGV; exec @ARGV or die "exec: $!\n"' "$seconds" "$@"
}

adb_device() { with_timeout "$ADB_TIMEOUT" "$adb" -s "$SERIAL" "$@" < /dev/null; }

find_sdk() {
  local candidate
  for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" /opt/homebrew/share/android-commandlinetools; do
    if [ -n "$candidate" ] && [ -x "$candidate/platform-tools/adb" ] && [ -x "$candidate/emulator/emulator" ]; then
      sdk=$candidate
      adb=$sdk/platform-tools/adb
      emulator=$sdk/emulator/emulator
      export ANDROID_HOME="$sdk" ANDROID_SDK_ROOT="$sdk"
      if [ -n "${GITHUB_ENV:-}" ]; then
        {
          echo "ANDROID_HOME=$sdk"
          echo "ANDROID_SDK_ROOT=$sdk"
          echo "ANDROID_SERIAL=$SERIAL"
        } >> "$GITHUB_ENV"
      fi
      return 0
    fi
  done
  fail 'Android SDK와 에뮬레이터 도구를 찾을 수 없습니다.'
}

find_avd() {
  local candidate config
  avd=''
  while IFS= read -r candidate; do
    config="$HOME/.android/avd/$candidate.avd/config.ini"
    if [ -n "$candidate" ] && [ -f "$config" ] && grep -qi 'pleos' "$config"; then
      if [ -n "$avd" ]; then
        fail 'Pleos AVD가 여러 개입니다. 대상을 지정해야 합니다.'
      fi
      avd=$candidate
    fi
  done < <("$emulator" -list-avds)
  if [ -z "$avd" ]; then
    "$emulator" -list-avds
    fail 'Pleos AVD를 찾을 수 없습니다.'
  fi
  log "AVD: $avd"
}

emulator_pids() { pgrep -f -- "-avd $avd( |\$)" || true; }

# 상태를 HEALTH_BOOT, HEALTH_USER0, HEALTH_CAR, HEALTH_STATE에 남기고 건강하면 0을 반환한다.
check_health() {
  local car_output
  HEALTH_BOOT=$(adb_device shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  HEALTH_USER0=''
  HEALTH_CAR=''
  if [ "$HEALTH_BOOT" = 1 ]; then
    HEALTH_USER0=$(adb_device shell dumpsys user 2>/dev/null | tr -d '\r' |
      sed -n 's/.*Started users state: \[\(.*\)\].*/\1/p' | head -1 | tr ',' '\n' | tr -d ' ' |
      awk -F= '$1 == "0" { print $2; exit }' || true)
    car_output=$(adb_device shell service check car_service 2>/dev/null | tr -d '\r' || true)
    case "$car_output" in
      *'not found'*) HEALTH_CAR='not found' ;;
      *': found'*) HEALTH_CAR='found' ;;
      *) HEALTH_CAR='unknown' ;;
    esac
  fi
  HEALTH_STATE="boot_completed=${HEALTH_BOOT:-none} user0=${HEALTH_USER0:-unknown} car_service=${HEALTH_CAR:-unknown}"
  [ "$HEALTH_BOOT" = 1 ] && [ "$HEALTH_USER0" = RUNNING_UNLOCKED ] && [ "$HEALTH_CAR" = found ]
}

# 건강해질 때까지 기다린다. 부팅이 끝난 뒤 SERVICE_GRACE가 지나도 건강하지 않으면 실패한다.
wait_for_health() {
  local boot_timeout=$1 started booted_at='' last_state='' now
  started=$(date +%s)
  while true; do
    if check_health; then
      log "정상: $HEALTH_STATE"
      return 0
    fi
    if [ "$HEALTH_STATE" != "$last_state" ]; then
      log "대기 중: $HEALTH_STATE"
      last_state=$HEALTH_STATE
    fi
    now=$(date +%s)
    if [ -z "$(emulator_pids)" ]; then
      log '에뮬레이터 프로세스가 없습니다.'
      return 1
    fi
    if [ "$HEALTH_BOOT" = 1 ]; then
      booted_at=${booted_at:-$now}
      if [ $((now - booted_at)) -ge "$SERVICE_GRACE" ]; then
        log "부팅 후 ${SERVICE_GRACE}초가 지나도 비정상: $HEALTH_STATE"
        return 1
      fi
    elif [ $((now - started)) -ge "$boot_timeout" ]; then
      log "${boot_timeout}초 안에 부팅이 끝나지 않았습니다: $HEALTH_STATE"
      return 1
    fi
    sleep 5
  done
}

stop_emulator() {
  local pids attempt
  pids=$(emulator_pids)
  if [ -z "$pids" ]; then
    return 0
  fi
  log "에뮬레이터 종료: pid $(echo "$pids" | tr '\n' ' ')"
  adb_device emu kill > /dev/null 2>&1 || true
  for attempt in 1 2 3 4 5 6 7 8 9 10 11 12; do
    if [ -z "$(emulator_pids)" ]; then
      break
    fi
    if [ "$attempt" = 6 ]; then
      # shellcheck disable=SC2046 # pid 목록을 나눠 넘긴다.
      kill $(emulator_pids) 2>/dev/null || true
    fi
    sleep 5
  done
  pids=$(emulator_pids)
  if [ -n "$pids" ]; then
    log '강제 종료합니다.'
    # shellcheck disable=SC2086 # pid 목록을 나눠 넘긴다.
    kill -9 $pids 2>/dev/null || true
    sleep 3
  fi
  [ -z "$(emulator_pids)" ] || fail '에뮬레이터를 종료하지 못했습니다.'
}

cold_boot() {
  log "콜드 부팅: $avd (로그 $EMULATOR_LOG)"
  # 러너는 작업이 끝나면 RUNNER_TRACKING_ID가 같은 프로세스를 종료한다. 값을 비우고
  # 새 세션으로 분리해 단계와 작업이 끝나도 에뮬레이터가 남게 한다.
  RUNNER_TRACKING_ID='' nohup perl -MPOSIX -e 'POSIX::setsid(); exec @ARGV or die "exec: $!\n"' \
    "$emulator" -avd "$avd" -no-snapshot -port "$PORT" < /dev/null > "$EMULATOR_LOG" 2>&1 &
  sleep 5
  with_timeout "$ADB_TIMEOUT" "$adb" start-server > /dev/null 2>&1 || true
  if ! wait_for_health "$COLD_BOOT_TIMEOUT"; then
    tail -100 "$EMULATOR_LOG" || true
    fail "콜드 부팅한 에뮬레이터가 정상 상태가 되지 않았습니다: $HEALTH_STATE"
  fi
}

restart() {
  echo "::warning::에뮬레이터를 재시작합니다: $*"
  stop_emulator
  cold_boot
}

prepare() {
  local others
  others=$(with_timeout "$ADB_TIMEOUT" "$adb" devices | awk '$1 ~ /^emulator-/ {print $1}' | grep -vx "$SERIAL" || true)
  if [ -n "$others" ]; then
    fail "다른 에뮬레이터가 실행 중입니다: $others"
  fi
  if [ -z "$(emulator_pids)" ]; then
    log '실행 중인 Pleos 에뮬레이터가 없습니다.'
    cold_boot
    return 0
  fi
  log '실행 중인 Pleos 에뮬레이터 상태를 확인합니다.'
  if wait_for_health "$EXISTING_BOOT_TIMEOUT"; then
    return 0
  fi
  restart "비정상 상태 $HEALTH_STATE"
}

recover() {
  local reason=''
  if [ $# -gt 0 ] && grep -rqsE "$CRASH_PATTERN" "$@"; then
    reason='테스트 로그에 시스템 크래시 기록'
    grep -rhsE "$CRASH_PATTERN" "$@" | head -5
  elif [ -z "$(emulator_pids)" ]; then
    reason='에뮬레이터 프로세스 없음'
  elif ! check_health; then
    reason="비정상 상태 $HEALTH_STATE"
  fi
  if [ -z "$reason" ]; then
    log "시스템 크래시 기록이 없고 상태가 정상입니다: $HEALTH_STATE"
    return 0
  fi
  restart "$reason"
}

command=${1:-}
shift || true
find_sdk
find_avd
case "$command" in
  prepare) prepare ;;
  recover) recover "$@" ;;
  *) fail "사용법: $0 prepare | recover <로그>..." ;;
esac
