package kr.roadcanvas.app.drawing

import kr.roadcanvas.app.vehicle.LocationSample

/** 기록한 경로의 점 하나. [timeMillis]는 위치를 받은 시각(Unix epoch 밀리초)이다. */
data class RoutePoint(val latitude: Double, val longitude: Double, val timeMillis: Long)

fun LocationSample.toRoutePoint() = RoutePoint(latitude, longitude, timeMillis)
