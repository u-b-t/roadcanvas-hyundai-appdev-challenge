package kr.roadcanvas.app.test

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.account.AccountRegistration
import kr.roadcanvas.app.account.AccountState
import kr.roadcanvas.app.account.AnonymousAccount
import kr.roadcanvas.app.account.readableNickname
import kr.roadcanvas.app.profile.NicknameValidation
import kr.roadcanvas.app.profile.validateNickname
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class AnonymousAccountSteps {
    private var failOnce = false
    private var newUid = ""
    private var generated = ""
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

    @When("새 사용자 {string}의 자동 닉네임을 만든다")
    fun generate(uid: String) {
        newUid = uid
        generated = readableNickname(uid)
    }

    @Then("자동 닉네임은 \"형용사 명사 숫자\" 형식이고 닉네임 규칙을 지킨다")
    fun readable() {
        assertTrue(generated, Regex("\\S+ \\S+ [1-9][0-9]?").matches(generated))
        assertEquals(NicknameValidation.Valid(generated), validateNickname(generated))
    }

    @Then("같은 사용자의 자동 닉네임은 다시 만들어도 같다")
    fun stable() {
        assertEquals(generated, readableNickname(newUid))
    }
}
