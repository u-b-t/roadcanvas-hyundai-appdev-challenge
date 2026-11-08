package kr.roadcanvas.app.upload

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.Date
import kotlinx.coroutines.tasks.await
import kr.roadcanvas.app.account.AnonymousAccount
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import org.json.JSONArray
import org.json.JSONObject

/** 같은 로컬 ID를 다시 보내면 같은 문서를 사용한다. */
class FirestoreDrawingUploader : DrawingUploader {
    override suspend fun upload(account: AnonymousAccount, drawing: CompletedDrawing) {
        if (drawing.mode != DrawingMode.CIRCLE) return
        val db = FirebaseFirestore.getInstance()
        val id = drawing.id.toString()
        val doc = db.collection("users").document(account.uid).collection("drawings").document(id)
        if (!doc.get(Source.SERVER).await().exists()) {
            val chunks = drawing.path.chunked(100)
            chunks.forEachIndexed { index, points ->
                val data = JSONArray()
                points.forEach {
                    data.put(
                        JSONObject().put("latitude", it.latitude).put("longitude", it.longitude)
                            .put("recordedAtMillis", it.timeMillis)
                    )
                }
                doc.collection("chunks").document(index.toString()).set(
                    mapOf("index" to index, "pointsJson" to data.toString())
                ).await()
            }
            doc.set(
                mapOf(
                    "mode" to "CIRCLE",
                    "startedAt" to
                        Timestamp(
                            Date(
                                (drawing.completedAtMillis - drawing.durationMillis).coerceAtLeast(
                                    0
                                )
                            )
                        ),
                    "finishedAt" to Timestamp(Date(drawing.completedAtMillis)),
                    "distanceMeters" to drawing.distanceMeters,
                    "durationMillis" to drawing.durationMillis,
                    "pointChunkCount" to chunks.size,
                    "score" to drawing.score,
                    "algorithmVersion" to "1"
                )
            ).await()
        }
        val entry = db.collection(
            "leaderboards"
        ).document("circle").collection("entries").document(account.uid)
        db.runTransaction { tx ->
            val current = tx.get(entry)
            val score = current.getLong("score")
            if (score == null || drawing.score > score) {
                tx.set(
                    entry,
                    mapOf(
                        "nickname" to account.nickname,
                        "score" to drawing.score,
                        "achievedAt" to Timestamp(Date(drawing.completedAtMillis)),
                        "drawingId" to id,
                        "algorithmVersion" to "1"
                    )
                )
            }
        }.await()
    }
}
