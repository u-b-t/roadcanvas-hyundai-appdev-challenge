package kr.roadcanvas.app.territory

import com.google.firebase.firestore.FirebaseFirestoreException.Code
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Firestore 오류 코드 enum은 Android 클래스를 써서 Robolectric에서 실행한다. */
@RunWith(RobolectricTestRunner::class)
class FirestoreTerritoryStoreTest {
    @Test
    fun `칸 version 규칙 거부와 트랜잭션 충돌은 다시 실행할 경합으로 본다`() {
        assertEquals(TerritoryFailure.CONFLICT, territoryFailureOf(Code.PERMISSION_DENIED))
        assertEquals(TerritoryFailure.CONFLICT, territoryFailureOf(Code.ABORTED))
    }

    @Test
    fun `연결과 인증 오류를 실패 이유로 바꾼다`() {
        assertEquals(TerritoryFailure.NETWORK, territoryFailureOf(Code.UNAVAILABLE))
        assertEquals(TerritoryFailure.NETWORK, territoryFailureOf(Code.DEADLINE_EXCEEDED))
        assertEquals(TerritoryFailure.AUTH, territoryFailureOf(Code.UNAUTHENTICATED))
        assertEquals(TerritoryFailure.UNKNOWN, territoryFailureOf(Code.INVALID_ARGUMENT))
    }
}
