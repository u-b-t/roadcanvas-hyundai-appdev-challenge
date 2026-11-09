#!/usr/bin/env bash
# 로컬 개발용 Pleos 에뮬레이터를 띄우고 콘솔 GPS 연동과 메모리 감시를 함께 관리한다.
#
#   scripts/dev-emulator.sh up [--apk <경로>] [--avd <이름>] [--port 5554]
#     에뮬레이터가 없으면 남은 잠금 파일·크래시 보고 DB를 정리하고 콜드 부팅한 뒤 상태를 확인한다.
#     콘솔 포트(8088)를 연결하고, APK를 지정하면 설치해 사용자 10에게 권한을 준다.
#     console-gps-bridge.py와 qemu 메모리 감시를 백그라운드로 띄운다. 이미 실행 중인 것은 다시 띄우지 않는다.
#   scripts/dev-emulator.sh status [--avd <이름>] [--port 5554]
#     에뮬레이터, car_service, 콘솔, 기어, GPS 연동, 메모리 감시, qemu 메모리를 한 줄씩 보여 준다.
#   scripts/dev-emulator.sh down [--avd <이름>] [--port 5554]
#     GPS 연동과 메모리 감시를 멈추고 에뮬레이터를 종료한다.
#
# 로그와 pid 파일은 ${ROADCANVAS_DEV_DIR:-~/.roadcanvas-dev-emulator}/<포트>/에 둔다.
# 메모리 감시는 1분마다 qemu 메모리(압축·스왑으로 밀려난 메모리 포함)를 기록하고 6GB를 넘으면
# 로그와 macOS 알림으로 경고한다.
# 자동 재시작은 하지 않는다. macOS 기본 bash 3.2에서 동작해야 한다.
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
SCRIPT_PATH="$SCRIPT_DIR/$(basename "$0")"
PORT=5554
AVD=''
APK=''
CONSOLE_PORT=8088
CONSOLE_URL="http://127.0.0.1:$CONSOLE_PORT"
APP_PACKAGE='kr.roadcanvas.app'
APP_USER=10
APP_PERMISSIONS='android.permission.ACCESS_COARSE_LOCATION android.permission.POST_NOTIFICATIONS pleos.car.permission.FUSED_LOCATION android.car.permission.CAR_SPEED'
ADB_TIMEOUT=20
COLD_BOOT_TIMEOUT=600
EXISTING_BOOT_TIMEOUT=300
# 부팅 완료 뒤 user 0 잠금 해제와 car_service 등록을 기다리는 시간
SERVICE_GRACE=90
MEMORY_INTERVAL=60
MEMORY_LIMIT_KB=$((6 * 1024 * 1024))
CRASH_DIALOG_PATTERN='crashdialog'
CRASH_DB_DIR="/tmp/android-${USER:-$(id -un)}"

if [ -t 1 ]; then
  RED=$(printf '\033[31m')
  GREEN=$(printf '\033[32m')
  RESET=$(printf '\033[0m')
else
  RED=''
  GREEN=''
  RESET=''
fi

log() { echo "[dev-emulator] $*"; }
fail() {
  echo "[dev-emulator] 오류: $*" >&2
  exit 1
}

usage() {
  sed -n '2,15p' "$SCRIPT_PATH" | sed 's/^# \{0,1\}//'
  exit "${1:-1}"
}

with_timeout() {
  local seconds=$1
  shift
  perl -e 'alarm shift @ARGV; exec @ARGV or die "exec: $!\n"' "$seconds" "$@"
}

adb_device() { with_timeout "$ADB_TIMEOUT" "$adb" -s "$SERIAL" "$@" < /dev/null; }

parse_options() {
  while [ $# -gt 0 ]; do
    case "$1" in
      --apk)
        [ $# -ge 2 ] || fail '--apk에 경로가 필요합니다.'
        APK=$2
        shift 2
        ;;
      --avd)
        [ $# -ge 2 ] || fail '--avd에 이름이 필요합니다.'
        AVD=$2
        shift 2
        ;;
      --port)
        [ $# -ge 2 ] || fail '--port에 번호가 필요합니다.'
        PORT=$2
        shift 2
        ;;
      -h | --help) usage 0 ;;
      *) fail "알 수 없는 옵션: $1" ;;
    esac
  done
  case "$PORT" in
    '' | *[!0-9]*) fail "포트는 숫자여야 합니다: $PORT" ;;
  esac
  SERIAL="emulator-$PORT"
  STATE_DIR="${ROADCANVAS_DEV_DIR:-$HOME/.roadcanvas-dev-emulator}/$PORT"
  EMULATOR_LOG="$STATE_DIR/emulator.log"
  BRIDGE_LOG="$STATE_DIR/gps-bridge.log"
  BRIDGE_PID="$STATE_DIR/gps-bridge.pid"
  MEMORY_LOG="$STATE_DIR/memory.log"
  MEMORY_PID="$STATE_DIR/memory-watch.pid"
}

find_sdk() {
  local candidate
  for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" /opt/homebrew/share/android-commandlinetools; do
    if [ -n "$candidate" ] && [ -x "$candidate/platform-tools/adb" ] && [ -x "$candidate/emulator/emulator" ]; then
      adb=$candidate/platform-tools/adb
      emulator=$candidate/emulator/emulator
      export ANDROID_HOME="$candidate" ANDROID_SDK_ROOT="$candidate"
      return 0
    fi
  done
  fail 'Android SDK와 에뮬레이터 도구를 찾을 수 없습니다. ANDROID_HOME을 설정하세요.'
}

# --avd가 없으면 config.ini에 pleos가 들어간 AVD 하나를 고른다.
find_avd() {
  local candidate config found=''
  if [ -n "$AVD" ]; then
    [ -f "$HOME/.android/avd/$AVD.avd/config.ini" ] || fail "AVD를 찾을 수 없습니다: $AVD"
    return 0
  fi
  while IFS= read -r candidate; do
    config="$HOME/.android/avd/$candidate.avd/config.ini"
    if [ -n "$candidate" ] && [ -f "$config" ] && grep -qi 'pleos' "$config"; then
      [ -z "$found" ] || fail "Pleos AVD가 여러 개입니다. --avd로 지정하세요: $found, $candidate"
      found=$candidate
    fi
  done < <("$emulator" -list-avds)
  [ -n "$found" ] || fail 'Pleos AVD를 찾을 수 없습니다. --avd로 지정하세요.'
  AVD=$found
}

# 이 포트의 에뮬레이터 프로세스. -port가 없으면 기본 포트 5554로 본다.
# 인자로 qemu를 주면 메모리를 차지하는 qemu-system 프로세스만 고른다.
emulator_pids() {
  ps -axo pid=,command= | awk -v port="$PORT" -v only="${1:-}" '
    $0 ~ /(emulator|qemu-system-[^ ]*) .*-avd / {
      if (only == "qemu" && $0 !~ /qemu-system-/) next
      p = 5554
      if (match($0, / -port [0-9]+/)) p = substr($0, RSTART + 7, RLENGTH - 7)
      if (p == port) print $1
    }'
}
qemu_pids() { emulator_pids qemu; }
avd_pids() { pgrep -f -- "-avd $AVD( |\$)" || true; }
# 크래시 보고 DB는 모든 AVD가 함께 쓰므로 어떤 에뮬레이터든 실행 중이면 지우지 않는다.
any_emulator_pids() { pgrep -f -- '(emulator|qemu-system-[^ ]*) .*-avd ' || true; }

# pid 파일의 프로세스가 살아 있고 명령줄에 표식이 있으면 pid를 출력한다.
running_pid() {
  local file=$1 marker=$2 pid
  [ -f "$file" ] || return 1
  pid=$(cat "$file" 2>/dev/null || true)
  case "$pid" in
    '' | *[!0-9]*) return 1 ;;
  esac
  if kill -0 "$pid" 2>/dev/null && ps -o command= -p "$pid" 2>/dev/null | grep -q -- "$marker"; then
    echo "$pid"
    return 0
  fi
  return 1
}

# 상태를 HEALTH_BOOT, HEALTH_USER0, HEALTH_CAR, HEALTH_STATE에 남기고 정상이면 0을 반환한다.
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
    [ -n "$(emulator_pids)" ] || {
      log '에뮬레이터 프로세스가 없습니다.'
      return 1
    }
    if [ "$HEALTH_BOOT" = 1 ]; then
      booted_at=${booted_at:-$now}
      if [ $((now - booted_at)) -ge "$SERVICE_GRACE" ]; then
        log "부팅 후 ${SERVICE_GRACE}초가 지나도 비정상: $HEALTH_STATE"
        return 1
      fi
    elif [ -n "$log_file" ] && grep -qs "$CRASH_DIALOG_PATTERN" "$log_file"; then
      log "크래시 보고 동의 창에서 멈췄습니다: $HEALTH_STATE"
      return 1
    elif [ $((now - started)) -ge "$boot_timeout" ]; then
      log "${boot_timeout}초 안에 부팅이 끝나지 않았습니다: $HEALTH_STATE"
      return 1
    fi
    sleep 5
  done
}

clean_stale_state() {
  local path removed=''
  [ -z "$(avd_pids)" ] || fail "AVD가 다른 포트로 실행 중입니다: $AVD"
  for path in "$HOME/.android/avd/$AVD.avd/"*.lock; do
    if [ -e "$path" ]; then
      rm -rf "$path"
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
  [ -z "$removed" ] || log "남은 상태 정리:$removed"
}

start_emulator() {
  if [ -n "$(emulator_pids)" ]; then
    log "실행 중인 에뮬레이터($SERIAL) 상태를 확인합니다."
    wait_for_health "$EXISTING_BOOT_TIMEOUT" || fail "실행 중인 에뮬레이터가 비정상입니다: $HEALTH_STATE. down 후 다시 up 하세요."
    return 0
  fi
  find_avd
  clean_stale_state
  log "콜드 부팅: $AVD 포트 $PORT (로그 $EMULATOR_LOG)"
  nohup perl -MPOSIX -e 'POSIX::setsid(); exec @ARGV or die "exec: $!\n"' \
    "$emulator" -avd "$AVD" -no-snapshot -port "$PORT" -crash-report-mode disabled \
    < /dev/null > "$EMULATOR_LOG" 2>&1 &
  sleep 5
  with_timeout "$ADB_TIMEOUT" "$adb" start-server > /dev/null 2>&1 || true
  if ! wait_for_health "$COLD_BOOT_TIMEOUT" "$EMULATOR_LOG"; then
    tail -30 "$EMULATOR_LOG" || true
    fail "콜드 부팅한 에뮬레이터가 정상 상태가 되지 않았습니다: $HEALTH_STATE"
  fi
}

install_apk() {
  local permission
  [ -n "$APK" ] || return 0
  [ -f "$APK" ] || fail "APK가 없습니다: $APK"
  log "APK 설치: $APK"
  with_timeout 180 "$adb" -s "$SERIAL" install -r "$APK" < /dev/null || fail 'APK 설치에 실패했습니다.'
  for permission in $APP_PERMISSIONS; do
    if adb_device shell pm grant --user "$APP_USER" "$APP_PACKAGE" "$permission" > /dev/null 2>&1; then
      log "권한 부여: $permission"
    else
      log "${RED}권한 부여 실패${RESET}: $permission (사용자 $APP_USER)"
    fi
  done
}

start_bridge() {
  local pid
  if pid=$(running_pid "$BRIDGE_PID" console-gps-bridge.py); then
    log "GPS 연동이 이미 실행 중입니다: pid $pid"
    return 0
  fi
  nohup python3 -u "$SCRIPT_DIR/console-gps-bridge.py" --adb "$adb" --serial "$SERIAL" \
    --console "$CONSOLE_URL" < /dev/null >> "$BRIDGE_LOG" 2>&1 &
  echo $! > "$BRIDGE_PID"
  log "GPS 연동 시작: pid $! (로그 $BRIDGE_LOG)"
}

start_memory_watch() {
  local pid
  if pid=$(running_pid "$MEMORY_PID" watch-memory); then
    log "메모리 감시가 이미 실행 중입니다: pid $pid"
    return 0
  fi
  nohup bash "$SCRIPT_PATH" watch-memory --port "$PORT" < /dev/null >> "$MEMORY_LOG" 2>&1 &
  echo $! > "$MEMORY_PID"
  log "메모리 감시 시작: pid $! (로그 $MEMORY_LOG)"
}

# qemu 메모리 합계(KB). 없으면 빈 값. top의 MEM은 압축·스왑으로 밀려난 메모리를 포함한다.
# ps RSS는 압축된 메모리를 빼서 누수가 커져도 작게 보인다.
qemu_memory_kb() {
  local pids pid
  pids=$(qemu_pids)
  [ -n "$pids" ] || return 0
  for pid in $pids; do
    top -l 1 -pid "$pid" -stats pid,mem 2>/dev/null | awk -v pid="$pid" '$1 == pid { print $2 }'
  done | awk '
    {
      value = $1
      gsub(/[+-]$/, "", value)
      unit = substr(value, length(value))
      number = value + 0
      if (unit == "G") number *= 1024 * 1024
      else if (unit == "M") number *= 1024
      else if (unit == "B") number /= 1024
      sum += number
    }
    END { if (NR) printf "%d\n", sum }'
}

format_kb() { awk -v kb="$1" 'BEGIN { printf "%.1fGB", kb / 1024 / 1024 }'; }

watch_memory() {
  local rss warned=0 message
  echo "$(date '+%F %T') 감시 시작: 포트 $PORT, 경고 기준 $(format_kb "$MEMORY_LIMIT_KB")"
  while true; do
    rss=$(qemu_memory_kb)
    if [ -z "$rss" ]; then
      echo "$(date '+%F %T') qemu 프로세스가 없어 감시를 끝냅니다."
      exit 0
    fi
    if [ "$rss" -gt "$MEMORY_LIMIT_KB" ]; then
      message="qemu 메모리 $(format_kb "$rss")가 $(format_kb "$MEMORY_LIMIT_KB")를 넘었습니다($SERIAL)."
      echo "$(date '+%F %T') 경고: $message"
      # 기준을 넘은 동안 처음 한 번만 알린다.
      if [ "$warned" = 0 ] && command -v osascript > /dev/null 2>&1; then
        osascript -e "display notification \"$message\" with title \"Pleos 에뮬레이터 메모리\"" > /dev/null 2>&1 || true
      fi
      warned=1
    else
      echo "$(date '+%F %T') qemu 메모리 $(format_kb "$rss")"
      warned=0
    fi
    sleep "$MEMORY_INTERVAL"
  done
}

stop_pid_file() {
  local file=$1 marker=$2 name=$3 pid
  if pid=$(running_pid "$file" "$marker"); then
    kill "$pid" 2>/dev/null || true
    log "$name 종료: pid $pid"
  fi
  rm -f "$file"
}

up() {
  mkdir -p "$STATE_DIR"
  start_emulator
  with_timeout "$ADB_TIMEOUT" "$adb" -s "$SERIAL" forward "tcp:$CONSOLE_PORT" "tcp:$CONSOLE_PORT" > /dev/null ||
    fail "콘솔 포트 연결에 실패했습니다: $CONSOLE_PORT"
  log "콘솔 포트 연결: $CONSOLE_URL (브라우저에서 열어 두어야 차량 입력과 GPS 연동이 동작합니다)"
  install_apk
  start_bridge
  start_memory_watch
  echo
  status
}

ok() { printf '  %s: %s%s%s\n' "$1" "$GREEN" "$2" "$RESET"; }
bad() { printf '! %s: %s%s%s\n' "$1" "$RED" "$2" "$RESET"; }

status() {
  local pid rss state active gear
  if [ -z "$(emulator_pids)" ]; then
    bad '에뮬레이터' "꺼짐 ($SERIAL)"
  elif check_health; then
    ok '에뮬레이터' "정상 ($SERIAL)"
  else
    bad '에뮬레이터' "비정상 ($SERIAL $HEALTH_STATE)"
  fi
  if [ "${HEALTH_CAR:-}" = found ]; then ok 'car_service' 'found'; else bad 'car_service' "${HEALTH_CAR:-unknown}"; fi
  state=$(curl -fsS -m 2 "$CONSOLE_URL/vehicle/state" 2>/dev/null || true)
  if [ -z "$state" ]; then
    bad '콘솔' "응답 없음 ($CONSOLE_URL, 포트 연결 확인)"
    bad '기어' '알 수 없음'
  else
    active=$(printf '%s' "$state" | python3 -c 'import json,sys; print(str((json.load(sys.stdin).get("controls") or {}).get("emulator_active")).lower())' 2>/dev/null || echo unknown)
    gear=$(printf '%s' "$state" | python3 -c 'import json,sys; g=json.load(sys.stdin).get("gear") or {}; print("%s (bridge_ready=%s)" % ((g.get("actual") or {}).get("value"), str(g.get("bridge_ready")).lower()))' 2>/dev/null || echo unknown)
    if [ "$active" = true ]; then ok '콘솔' '활성'; else bad '콘솔' "비활성 (브라우저에서 $CONSOLE_URL 을 여세요)"; fi
    case "$gear" in
      *bridge_ready=true*) ok '기어' "$gear" ;;
      *) bad '기어' "$gear" ;;
    esac
  fi
  if pid=$(running_pid "$BRIDGE_PID" console-gps-bridge.py); then ok 'GPS 연동' "실행 중 pid $pid"; else bad 'GPS 연동' '꺼짐'; fi
  if pid=$(running_pid "$MEMORY_PID" watch-memory); then ok '메모리 감시' "실행 중 pid $pid"; else bad '메모리 감시' '꺼짐'; fi
  rss=$(qemu_memory_kb)
  if [ -z "$rss" ]; then
    bad 'qemu 메모리' '프로세스 없음'
  elif [ "$rss" -gt "$MEMORY_LIMIT_KB" ]; then
    bad 'qemu 메모리' "$(format_kb "$rss") (기준 $(format_kb "$MEMORY_LIMIT_KB") 초과)"
  else
    ok 'qemu 메모리' "$(format_kb "$rss")"
  fi
}

down() {
  local attempt pids
  stop_pid_file "$BRIDGE_PID" console-gps-bridge.py 'GPS 연동'
  stop_pid_file "$MEMORY_PID" watch-memory '메모리 감시'
  if [ -z "$(emulator_pids)" ]; then
    log "실행 중인 에뮬레이터가 없습니다: $SERIAL"
    return 0
  fi
  log "에뮬레이터 종료: $SERIAL"
  adb_device emu kill > /dev/null 2>&1 || true
  for attempt in 1 2 3 4 5 6 7 8 9 10 11 12; do
    [ -n "$(emulator_pids)" ] || break
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
  log '종료했습니다.'
}

command=${1:-}
shift || true
parse_options "$@"
case "$command" in
  up | status | down) find_sdk ;;
esac
case "$command" in
  up) up ;;
  status) status ;;
  down) down ;;
  watch-memory) watch_memory ;;
  -h | --help) usage 0 ;;
  *) usage 1 ;;
esac
