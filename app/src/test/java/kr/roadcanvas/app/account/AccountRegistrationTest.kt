package kr.roadcanvas.app.account

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
