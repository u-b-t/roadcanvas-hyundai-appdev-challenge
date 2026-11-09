package kr.roadcanvas.app.modeselect

import kr.roadcanvas.app.drawing.DrawingMode

/** 화면에 보여줄 모드 이름. 메인 화면 섹션의 이름과 같다. */
val DrawingMode.displayName: String
    get() = when (this) {
        DrawingMode.CIRCLE -> CIRCLE_CHALLENGE_NAME
        DrawingMode.FREE_DRAWING -> FREE_DRAW_NAME
        DrawingMode.TERRITORY -> TERRITORY_NAME
    }
