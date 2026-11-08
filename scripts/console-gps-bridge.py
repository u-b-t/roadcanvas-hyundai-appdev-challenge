#!/usr/bin/env python3
"""Virtual Console 주행을 에뮬레이터 위치(adb emu geo fix)로 전달한다.

Virtual Console은 차량 신호만 바꾸고 GNSS 위치는 바꾸지 않는다. 이 개발 도구는 콘솔의 기어·속도·핸들 각도를
자전거 모델로 적분해 위치를 계산하고 에뮬레이터에 보낸다. 콘솔의 비공식 로컬 HTTP 인터페이스를 쓰므로
콘솔 버전에 따라 달라질 수 있다.
"""
import argparse
import json
import math
import subprocess
import time
import urllib.request

METERS_PER_DEGREE_LAT = 111_320.0


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--lat", type=float, default=37.4979, help="시작 위도(기본: 강남역)")
    parser.add_argument("--lon", type=float, default=127.0276, help="시작 경도(기본: 강남역)")
    parser.add_argument("--heading", type=float, default=90.0, help="시작 방위각(도, 북쪽 0, 시계 방향)")
    parser.add_argument("--interval", type=float, default=0.5, help="콘솔 조회 간격(초)")
    parser.add_argument("--steering-ratio", type=float, default=15.0, help="핸들 각도 대 바퀴 각도 비")
    parser.add_argument("--wheelbase", type=float, default=2.9, help="축간거리(m)")
    parser.add_argument("--max-wheel-deg", type=float, default=35.0, help="최대 바퀴 조향각(도)")
    parser.add_argument("--max-lateral-accel", type=float, default=4.0, help="회전율을 제한할 최대 횡가속도(m/s²)")
    parser.add_argument("--invert-steering", action="store_true", help="핸들 방향이 반대로 반영될 때 사용")
    parser.add_argument("--adb", default="adb", help="adb 실행 파일 경로")
    parser.add_argument("--serial", default="emulator-5554", help="대상 에뮬레이터")
    parser.add_argument("--console", default="http://127.0.0.1:8088", help="Virtual Console 주소")
    return parser.parse_args()


def read_state(console):
    with urllib.request.urlopen(f"{console}/vehicle/state", timeout=2) as response:
        return json.load(response)


def signed_speed(gear, speed_kph):
    speed = speed_kph / 3.6
    if gear == "Drive":
        return speed
    if gear == "Reverse":
        return -speed
    return 0.0


def yaw_rate(speed, steering_deg, args):
    wheel = max(-args.max_wheel_deg, min(args.max_wheel_deg, steering_deg / args.steering_ratio))
    rate = speed * math.tan(math.radians(wheel)) / args.wheelbase
    if speed != 0:
        limit = args.max_lateral_accel / abs(speed)
        rate = max(-limit, min(limit, rate))
    return rate


def move(lat, lon, heading, distance):
    lat += distance * math.cos(heading) / METERS_PER_DEGREE_LAT
    lon += distance * math.sin(heading) / (METERS_PER_DEGREE_LAT * math.cos(math.radians(lat)))
    return lat, lon


def main():
    args = parse_args()
    lat, lon = args.lat, args.lon
    heading = math.radians(args.heading)
    sent = 0
    last_status = None

    def geo_fix():
        subprocess.run(
            [args.adb, "-s", args.serial, "emu", "geo", "fix", f"{lon:.7f}", f"{lat:.7f}"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )

    def report(status):
        nonlocal last_status
        if status != last_status:
            print(status, flush=True)
            last_status = status

    geo_fix()
    print(f"시작 위치 {lat:.6f}, {lon:.6f}, 방위 {args.heading:.0f}°", flush=True)
    previous = time.monotonic()
    last_log = previous
    while True:
        time.sleep(args.interval)
        now = time.monotonic()
        dt, previous = now - previous, now
        try:
            state = read_state(args.console)
        except Exception as error:
            report(f"콘솔 조회 실패: {error}")
            continue
        controls = state.get("controls") or {}
        if not controls.get("emulator_active"):
            report("콘솔 페이지가 비활성 상태입니다. 브라우저에서 콘솔을 열어 두세요.")
            continue
        gear = ((state.get("gear") or {}).get("actual") or {}).get("value")
        speed = signed_speed(gear, float(controls.get("speed_kph") or 0))
        steering = float(controls.get("steering_deg") or 0)
        if args.invert_steering:
            steering = -steering
        report("콘솔 주행을 위치로 전달하는 중입니다.")
        heading += yaw_rate(speed, steering, args) * dt
        distance = speed * dt
        if distance:
            lat, lon = move(lat, lon, heading, distance)
            geo_fix()
            sent += 1
        if now - last_log >= 5:
            last_log = now
            print(
                f"기어 {gear} 속도 {abs(speed) * 3.6:.0f}km/h 핸들 {steering:.0f}° "
                f"위치 {lat:.6f},{lon:.6f} 방위 {math.degrees(heading) % 360:.0f}° 전송 {sent}회",
                flush=True,
            )


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
