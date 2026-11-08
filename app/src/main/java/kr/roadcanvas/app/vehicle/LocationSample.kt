package kr.roadcanvas.app.vehicle

/** 차량 위치 한 건. [timeMillis]는 위치를 받은 시각(Unix epoch 밀리초)이다. */
data class LocationSample(val latitude: Double, val longitude: Double, val timeMillis: Long)
