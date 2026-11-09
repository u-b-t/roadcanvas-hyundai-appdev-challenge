package kr.roadcanvas.app.test

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.account.AccountRegistration
import kr.roadcanvas.app.account.AccountState
import kr.roadcanvas.app.account.AnonymousAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class AnonymousAccountSteps {
    private var failOnce = false
    private val registration = AccountRegistration {
        if (failOnce) {
            failOnce = false
            error("offline")
        }
        AnonymousAccount("uid", "드라이버-uid")
    }

    @Given("익명 인증이 가능하다")
    fun available() {
        failOnce = false
    }

    @Given("익명 인증이 일시적으로 실패한다")
    fun unavailable() {
        failOnce = true
    }

    @When("익명 사용자 등록을 요청한다")
    @When("익명 인증을 다시 시도한다")
    fun register() = runBlocking { registration.register() }

    @Then("사용자 식별자와 닉네임을 받는다")
    fun ready() {
        assertEquals(
            AnonymousAccount("uid", "드라이버-uid"),
            (registration.state.value as AccountState.Ready).account
        )
    }

    @Then("인증 실패 상태가 된다")
    fun failed() {
        assertTrue(registration.state.value is AccountState.Failed)
    }
}
