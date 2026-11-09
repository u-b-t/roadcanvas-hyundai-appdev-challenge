package kr.roadcanvas.app.artwork

import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Firestore 오류 코드 enum은 Android 클래스를 써서 Robolectric에서 실행한다. */
@RunWith(RobolectricTestRunner::class)
class FirestoreArtworkStoreTest {
    @Test
    fun `Firestore 오류 코드를 공개 실패 이유로 바꾼다`() {
        assertEquals(
            PublishFailure.NETWORK,
            publishFailureOf(FirebaseFirestoreException.Code.UNAVAILABLE)
        )
        assertEquals(
            PublishFailure.NETWORK,
            publishFailureOf(FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
        )
        assertEquals(
            PublishFailure.AUTH,
            publishFailureOf(FirebaseFirestoreException.Code.UNAUTHENTICATED)
        )
        assertEquals(
            PublishFailure.UNKNOWN,
            publishFailureOf(FirebaseFirestoreException.Code.PERMISSION_DENIED)
        )
    }
}
