package kr.roadcanvas.app.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.roadcanvas.app.account.AccountRegistration
import kr.roadcanvas.app.account.AccountState
import kr.roadcanvas.app.account.AnonymousAccount
import kr.roadcanvas.app.profile.fake.FakeProfileStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileTest {
    private val store = FakeProfileStore()
    private var authAvailable = true
    private val registration = AccountRegistration {
        check(authAvailable) { "offline" }
        AnonymousAccount("me", "빠른 고래 12")
    }
    private val profile = Profile(registration, store)

    private val currentNickname
        get() = (registration.state.value as AccountState.Ready).account.nickname

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `인증 전이면 인증한 뒤 닉네임과 요약을 읽는다`() = runTest {
        store.putSummary("me", ProfileSummary(90, 12, 2))
        val result = profile.load() as ProfileResult.Success
        assertEquals("빠른 고래 12", result.account.nickname)
        assertEquals(ProfileSummary(90, 12, 2), result.summary)
    }

    @Test
    fun `인증하지 못하면 계정 없이 인증 실패다`() = runTest {
        authAvailable = false
        assertEquals(ProfileResult.Failure(null, ProfileFailure.AUTH), profile.load())
        assertEquals(
            NicknameChangeResult.Failure(ProfileFailure.AUTH),
            profile.changeNickname("새 닉네임")
        )
    }

    @Test
    fun `요약을 읽지 못해도 닉네임은 알려준다`() = runTest {
        store.networkFailure = true
        val result = profile.load() as ProfileResult.Failure
        assertEquals("빠른 고래 12", result.account?.nickname)
        assertEquals(ProfileFailure.NETWORK, result.reason)
    }

    @Test
    fun `닉네임을 바꾸면 서버에 저장하고 계정에 반영한다`() = runTest {
        val result = profile.changeNickname("  도로 화가 ")
        assertEquals(NicknameChangeResult.Changed("도로 화가"), result)
        assertEquals("도로 화가", store.nicknames["me"])
        assertEquals("도로 화가", currentNickname)
    }

    @Test
    fun `검사를 통과하지 못하면 서버에 보내지 않는다`() = runTest {
        assertEquals(
            NicknameChangeResult.Invalid(NicknameValidation.TooShort),
            profile.changeNickname("가")
        )
        assertEquals(
            NicknameChangeResult.Invalid(NicknameValidation.ForbiddenCharacter),
            profile.changeNickname("고래!")
        )
        assertTrue(store.nicknames.isEmpty())
    }

    @Test
    fun `서버 저장에 실패하면 계정의 닉네임을 바꾸지 않는다`() = runTest {
        registration.register()
        store.networkFailure = true
        assertEquals(
            NicknameChangeResult.Failure(ProfileFailure.NETWORK),
            profile.changeNickname("도로 화가")
        )
        assertEquals("빠른 고래 12", currentNickname)
        assertNull(store.nicknames["me"])
    }

    @Test
    fun `화면은 현재 닉네임으로 입력창을 채우고 저장 결과를 보여준다`() {
        store.putSummary("me", ProfileSummary(null, 0, 1))
        val viewModel = ProfileViewModel(profile)
        assertEquals("빠른 고래 12", viewModel.state.value.input)
        assertEquals(
            ProfileSummaryState.Loaded(ProfileSummary(null, 0, 1)),
            viewModel.state.value.summary
        )

        viewModel.onInputChange("가")
        viewModel.save()
        assertEquals(NicknameValidation.TooShort, viewModel.state.value.inputError)

        viewModel.onInputChange("도로 화가")
        store.networkFailure = true
        viewModel.save()
        assertEquals(
            NicknameSaveState.Failed(ProfileFailure.NETWORK),
            viewModel.state.value.save
        )

        store.networkFailure = false
        viewModel.save()
        assertEquals(NicknameSaveState.Saved, viewModel.state.value.save)
        assertEquals("도로 화가", viewModel.state.value.account?.nickname)
    }
}
