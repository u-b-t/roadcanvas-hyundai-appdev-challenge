package kr.roadcanvas.app.artwork

import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.account.AccountRegistration
import kr.roadcanvas.app.account.AnonymousAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AccountArtworkAuthorProviderTest {
    @Test
    fun `인증된 익명 계정의 UID와 닉네임을 작성자로 쓴다`() = runTest {
        val registration = AccountRegistration { AnonymousAccount("uid-1", "드라이버-uid-1") }
        registration.register()

        val author = AccountArtworkAuthorProvider(registration).current()

        assertEquals(ArtworkAuthor("uid-1", "드라이버-uid-1"), author)
    }

    @Test
    fun `아직 인증하지 않았으면 인증한 뒤 작성자를 준다`() = runTest {
        var calls = 0
        val registration = AccountRegistration {
            calls++
            AnonymousAccount("uid-1", "드라이버-uid-1")
        }

        val author = AccountArtworkAuthorProvider(registration).current()

        assertEquals("uid-1", author.uid)
        assertEquals(1, calls)
    }

    @Test
    fun `인증에 실패하면 인증 실패로 알린다`() = runTest {
        val registration = AccountRegistration { error("offline") }

        try {
            AccountArtworkAuthorProvider(registration).current()
            fail("인증 실패를 알려야 한다")
        } catch (e: ArtworkStoreException) {
            assertEquals(PublishFailure.AUTH, e.failure)
        }
    }
}
