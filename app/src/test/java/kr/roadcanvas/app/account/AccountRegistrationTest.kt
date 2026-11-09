package kr.roadcanvas.app.account

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRegistrationTest {
    @Test
    fun `인증 실패 후 재시도하면 사용자 정보를 얻는다`() = runTest {
        var calls = 0
        val registration = AccountRegistration {
            if (calls++ == 0) error("offline")
            AnonymousAccount("user", "드라이버-user")
        }
        registration.register()
        assertEquals(AccountState.Failed, registration.state.value)
        registration.register()
        assertEquals(
            AccountState.Ready(AnonymousAccount("user", "드라이버-user")),
            registration.state.value
        )
    }

    @Test
    fun `인증이 실패하면 실패 상태로 남는다`() = runTest {
        val registration = AccountRegistration { error("offline") }
        registration.register()
        assertEquals(AccountState.Failed, registration.state.value)
    }

    @Test
    fun `자동 닉네임은 사용자 식별자 앞 8자로 만든다`() {
        val nickname = defaultNickname("abcdefghijklmnop")
        assertEquals("드라이버-abcdefgh", nickname)
        assertTrue(nickname.length in 1..30)
    }

    @Test
    fun `닉네임을 바꾸면 인증된 계정에 반영하고 인증 전이면 무시한다`() = runTest {
        val registration = AccountRegistration { AnonymousAccount("user", "빠른 고래 1") }
        registration.replaceNickname("도로 화가")
        assertEquals(AccountState.Idle, registration.state.value)
        registration.register()
        registration.replaceNickname("도로 화가")
        assertEquals(
            AccountState.Ready(AnonymousAccount("user", "도로 화가")),
            registration.state.value
        )
    }
}
