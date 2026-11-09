package kr.roadcanvas.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kr.roadcanvas.app.artwork.ArtworkChunk
import kr.roadcanvas.app.gallery.PublicArtwork
import kr.roadcanvas.app.gallery.PublicArtworkCursor
import kr.roadcanvas.app.gallery.PublicArtworkDetailScreen
import kr.roadcanvas.app.gallery.PublicArtworkDetailViewModel
import kr.roadcanvas.app.gallery.PublicArtworkPage
import kr.roadcanvas.app.gallery.PublicArtworkStore
import kr.roadcanvas.app.gallery.PublicGallery
import kr.roadcanvas.app.gallery.PublicGalleryScreen
import kr.roadcanvas.app.gallery.PublicGalleryViewModel
import kr.roadcanvas.app.ui.theme.RoadCanvasTheme
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteGenerator
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteType

private const val PREVIEW_UID = "preview"

/**
 * 공개 작품 목록·상세를 서버 없이 확인하는 디버그 전용 화면. 모의 경로로 만든 샘플 작품 세 개를 메모리에 둔다.
 * `adb shell am start -n kr.roadcanvas.app/.debug.PublicGalleryPreviewActivity`로 연다.
 * 투표는 이 화면 안에서만 반영되고 앱의 서버 데이터에는 남지 않는다.
 */
class PublicGalleryPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gallery = PublicGallery(SampleArtworkStore(), currentUid = { PREVIEW_UID })
        setContent {
            RoadCanvasTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "list") {
                    composable("list") {
                        val viewModel: PublicGalleryViewModel = viewModel(
                            factory = viewModelFactory {
                                initializer { PublicGalleryViewModel(gallery) }
                            }
                        )
                        val state by viewModel.state.collectAsStateWithLifecycle()
                        PublicGalleryScreen(
                            state = state,
                            onItemVisible = viewModel::onItemVisible,
                            onOpenArtwork = { navController.navigate("detail/$it") },
                            onRefresh = viewModel::refresh,
                            onLoadMore = viewModel::loadMore,
                            onBack = ::finish
                        )
                    }
                    composable("detail/{id}") { entry ->
                        val id = requireNotNull(entry.arguments?.getString("id"))
                        val viewModel: PublicArtworkDetailViewModel = viewModel(
                            key = id,
                            factory = viewModelFactory {
                                initializer { PublicArtworkDetailViewModel(gallery, id) }
                            }
                        )
                        val state by viewModel.state.collectAsStateWithLifecycle()
                        PublicArtworkDetailScreen(
                            state = state,
                            onToggleVote = viewModel::toggleVote,
                            onRetry = viewModel::retry,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

/** 모의 원·찌그러진 원·직선 왕복 경로로 만든 샘플 작품 저장소 */
private class SampleArtworkStore : PublicArtworkStore {
    private class Sample(
        val artwork: PublicArtwork,
        val chunks: List<ArtworkChunk>,
        val voters: MutableSet<String>
    )

    private val samples = listOf(
        sample("circle", "시청 앞 동그라미", "드라이버-3f9a1c20", SimulatedRouteType.CIRCLE, 3, 12),
        sample("squished", "찌그러진 달", "드라이버-8b2d77e1", SimulatedRouteType.SQUISHED_CIRCLE, 2, 5),
        sample("line", "직선 왕복", "드라이버-c41e0a9b", SimulatedRouteType.LINE, 1, 0)
    ).associateBy { it.artwork.id }

    override suspend fun publishedPage(after: PublicArtworkCursor?, limit: Int) =
        PublicArtworkPage(samples.values.map { it.artwork }, next = null)

    override suspend fun artwork(artworkId: String) = samples[artworkId]?.artwork

    override suspend fun chunks(artworkId: String) = samples.getValue(artworkId).chunks

    override suspend fun voteCount(artworkId: String) =
        samples.getValue(artworkId).voters.size.toLong()

    override suspend fun hasVoted(artworkId: String, uid: String) =
        uid in samples.getValue(artworkId).voters

    override suspend fun vote(artworkId: String, uid: String) {
        samples.getValue(artworkId).voters += uid
    }

    override suspend fun unvote(artworkId: String, uid: String) {
        samples.getValue(artworkId).voters -= uid
    }

    private fun sample(
        id: String,
        title: String,
        nickname: String,
        type: SimulatedRouteType,
        order: Int,
        votes: Int
    ): Sample {
        val route = SimulatedRouteGenerator.generate(type)
        val startMillis = 1_762_000_000_000L + order * 3_600_000L
        val chunks = route.chunked(20).mapIndexed { index, points ->
            ArtworkChunk(
                index,
                points.withIndex().joinToString(",", "[", "]") { (offset, point) ->
                    val time = startMillis + (index * 20 + offset) * 1_000L
                    """{"latitude":${point.latitude},"longitude":${point.longitude},"recordedAtMillis":$time}"""
                }
            )
        }
        return Sample(
            PublicArtwork(
                id = id,
                title = title,
                nickname = nickname,
                distanceMeters = route.size * 5.0,
                durationMillis = route.size * 1_000L,
                pointChunkCount = chunks.size,
                createdAtMillis = startMillis
            ),
            chunks,
            MutableList(votes) { "sample-$it" }.toMutableSet()
        )
    }
}
