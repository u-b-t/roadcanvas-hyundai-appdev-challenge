#!/usr/bin/env bash
# CI에서 Pleos 에뮬레이터를 준비하고, 망가진 에뮬레이터를 재시작한다.
#
#   scripts/ci-emulator.sh prepare
#     실행 중인 에뮬레이터가 건강하면 재사용하고, 아니면 종료한 뒤 콜드 부팅한다.
#   scripts/ci-emulator.sh recover <로그 파일 또는 디렉터리>...
#     테스트 실패 후 시스템 크래시 흔적이 있거나 상태가 나쁘면 재시작한다.
#   scripts/ci-emulator.sh check-memory
#     테스트 후 qemu 메모리가 기준을 넘었으면 재시작한다.
#
# 건강한 상태: sys.boot_completed가 1, user 0이 RUNNING_UNLOCKED, car_service가 found.
# 새 에뮬레이터는 RUNNER_TRACKING_ID를 비우고 새 세션으로 분리해 띄운다. 러너가 작업 종료 때
# 정리하지 않으므로 다음 작업이 재사용한다. macOS 기본 bash 3.2에서 동작해야 한다.
# 콜드 부팅 전에는 강제 종료로 남은 크래시 보고 DB와 AVD 잠금 파일을 지우고, 크래시 보고 동의
# 창이 뜨지 않도록 -crash-report-mode disabled로 실행한다.
# 러너에 화면(GUI 세션)이 없으면 창을 만들지 못해 종료하므로 화면 유무와 관계없이 -no-window와
# 소프트웨어 렌더링(-gpu swiftshader_indirect)으로 띄우고, 쓰지 않는 오디오와 부팅 애니메이션을 끈다.
# qemu는 실행하는 동안 메모리를 계속 늘리므로 prepare와 테스트 후 qemu 메모리가 기준
# (PLEOS_EMULATOR_MEMORY_LIMIT_MB, 기본 3072)을 넘으면 종료하고 콜드 부팅한다.
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
# 에뮬레이터가 크래시 보고 동의 창을 띄우고 기다릴 때 남기는 로그
CRASH_DIALOG_PATTERN='crashdialog'
CRASH_DB_DIR="/tmp/android-${USER:-$(id -un)}"
# 렌더링 옵션. 측정 결과는 docs/emulator-memory.md에 있다.
EMULATOR_GPU=${PLEOS_EMULATOR_GPU:-swiftshader_indirect}
# 이 값(MB)을 넘으면 재시작한다. 압축·스왑으로 밀려난 메모리도 포함한다.
MEMORY_LIMIT_MB=${PLEOS_EMULATOR_MEMORY_LIMIT_MB:-3072}
case "$MEMORY_LIMIT_MB" in
  '' | *[!0-9]*) echo "::error::PLEOS_EMULATOR_MEMORY_LIMIT_MB는 MB 단위 정수여야 합니다: $MEMORY_LIMIT_MB" >&2; exit 1 ;;
esac

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
qemu_pid() { pgrep -f -- "qemu-system-[^ ]* .*-avd $avd( |\$)" | head -1 || true; }
# 크래시 보고 DB는 모든 AVD가 함께 쓰므로 어떤 에뮬레이터든 실행 중이면 지우지 않는다.
any_emulator_pids() { pgrep -f -- '(emulator|qemu-system-[^ ]*) .*-avd ' || true; }

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

# top의 MEM(물리 점유량, 압축·스왑으로 밀려난 메모리 포함)을 MEMORY_MB에, 판정에 쓴 값을
# MEMORY_STATE에 남긴다. ps RSS는 압축된 메모리를 빼서 실제보다 작게 보이므로 참고로만 남긴다.
# qemu가 없거나 읽지 못하면 1을 반환한다.
measure_memory() {
  local pid sample rss_kb
  MEMORY_MB=''
  MEMORY_STATE=''
  pid=$(qemu_pid)
  [ -n "$pid" ] || return 1
  sample=$(top -l 1 -pid "$pid" -stats pid,mem,cmprs 2>/dev/null | awk -v pid="$pid" '$1 == pid { print $2, $3 }' || true)
  rss_kb=$(ps -o rss= -p "$pid" 2>/dev/null | tr -d ' ' || true)
  MEMORY_MB=$(echo "$sample" | awk '{ print $1 }' | to_mb)
  [ -n "$MEMORY_MB" ] || return 1
  MEMORY_STATE="qemu pid $pid 메모리=${MEMORY_MB}MB 압축=$(echo "$sample" | awk '{ print $2 }' | to_mb)MB rss=$((${rss_kb:-0} / 1024))MB 기준=${MEMORY_LIMIT_MB}MB"
}

# top의 크기 표기(예: 8126M+, 1.2G, 512K)를 MB 정수로 바꾼다.
to_mb() {
  awk '{
    value = $1
    gsub(/[+-]$/, "", value)
    unit = substr(value, length(value))
    number = value + 0
    if (unit == "G") number *= 1024
    else if (unit == "K") number /= 1024
    else if (unit == "B") number /= 1024 * 1024
    else if (unit != "M") next
    printf "%d\n", number
  }'
}

# 메모리가 기준 이하이거나 확인할 수 없으면 0, 기준을 넘으면 1을 반환한다.
check_memory() {
  if ! measure_memory; then
    log 'qemu 메모리를 확인하지 못했습니다.'
    return 0
  fi
  if [ "$MEMORY_MB" -gt "$MEMORY_LIMIT_MB" ]; then
    log "메모리 기준 초과: $MEMORY_STATE"
    return 1
  fi
  log "메모리 정상: $MEMORY_STATE"
}

# 건강해질 때까지 기다린다. 부팅이 끝난 뒤 SERVICE_GRACE가 지나도 건강하지 않으면 실패한다.
wait_for_health() {
  local boot_timeout=$1 log_file=${2:-} started booted_at='' last_state='' now
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
    elif [ -n "$log_file" ] && grep -qs "$CRASH_DIALOG_PATTERN" "$log_file"; then
      log "크래시 보고 동의 창에서 멈췄습니다. adb 포트가 열리지 않아 부팅이 진행되지 않습니다: $HEALTH_STATE"
      grep -s "$CRASH_DIALOG_PATTERN" "$log_file" | head -5
      return 1
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

# 강제 종료로 남은 크래시 보고 DB가 있으면 다음 실행이 동의 창에서 멈추고, 잠금 파일이 남으면
# AVD가 사용 중으로 판단될 수 있다. 실행 중인 에뮬레이터가 없을 때만 지운다.
clean_stale_state() {
  local path removed=''
  if [ -n "$(emulator_pids)" ]; then
    fail "에뮬레이터가 실행 중이라 잠금 파일을 지우지 않습니다: $avd"
  fi
  for path in "$HOME/.android/avd/$avd.avd/"*.lock; do
    if [ -e "$path" ]; then
      rm -f "$path"
      removed="$removed ${path##*/}"
    fi
  done
  if [ -n "$(any_emulator_pids)" ]; then
    log '다른 에뮬레이터가 실행 중이라 크래시 보고 DB를 지우지 않습니다.'
  else
    for path in "$CRASH_DB_DIR"/emu-crash-*.db; do
      if [ -e "$path" ]; then
        rm -rf "$path"
        removed="$removed $path"
      fi
    done
  fi
  if [ -n "$removed" ]; then
    log "남은 상태 정리:$removed"
  fi
}

cold_boot() {
  clean_stale_state
  log "콜드 부팅: $avd -gpu $EMULATOR_GPU (로그 $EMULATOR_LOG)"
  # 러너는 작업이 끝나면 RUNNER_TRACKING_ID가 같은 프로세스를 종료한다. 값을 비우고
  # 새 세션으로 분리해 단계와 작업이 끝나도 에뮬레이터가 남게 한다.
  RUNNER_TRACKING_ID='' nohup perl -MPOSIX -e 'POSIX::setsid(); exec @ARGV or die "exec: $!\n"' \
    "$emulator" -avd "$avd" -no-snapshot -port "$PORT" -crash-report-mode disabled \
    -no-window -gpu "$EMULATOR_GPU" -no-audio -no-boot-anim \
    < /dev/null > "$EMULATOR_LOG" 2>&1 &
  sleep 5
  with_timeout "$ADB_TIMEOUT" "$adb" start-server > /dev/null 2>&1 || true
  if ! wait_for_health "$COLD_BOOT_TIMEOUT" "$EMULATOR_LOG"; then
    tail -100 "$EMULATOR_LOG" || true
    fail "콜드 부팅한 에뮬레이터가 정상 상태가 되지 않았습니다: $HEALTH_STATE"
  fi
  if measure_memory; then
    log "부팅 직후 $MEMORY_STATE"
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
  if ! check_memory; then
    restart "qemu 메모리 ${MEMORY_MB}MB가 기준 ${MEMORY_LIMIT_MB}MB 초과"
    return 0
  fi
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

after_test_memory() {
  if [ -z "$(emulator_pids)" ]; then
    log '실행 중인 Pleos 에뮬레이터가 없습니다. 다음 prepare가 콜드 부팅합니다.'
    return 0
  fi
  if ! check_memory; then
    restart "qemu 메모리 ${MEMORY_MB}MB가 기준 ${MEMORY_LIMIT_MB}MB 초과"
  fi
}

command=${1:-}
shift || true
find_sdk
find_avd
case "$command" in
  prepare) prepare ;;
  recover) recover "$@" ;;
  check-memory) after_test_memory ;;
  *) fail "사용법: $0 prepare | recover <로그>... | check-memory" ;;
esac
