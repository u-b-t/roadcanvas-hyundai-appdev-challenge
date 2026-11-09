package kr.roadcanvas.app.account

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** 같은 익명 사용자에 대해 프로필을 한 번만 만든다. 이미 있는 닉네임은 바꾸지 않는다. */
class FirebaseAccountProvider : AccountProvider {
    override suspend fun getOrCreate(): AnonymousAccount {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: requireNotNull(auth.signInAnonymously().await().user)
        val db = FirebaseFirestore.getInstance()
        val profile = db.collection("users").document(user.uid)
        val nickname = db.runTransaction { transaction ->
            val snapshot = transaction.get(profile)
            val current = snapshot.getString("nickname")
            if (current != null) {
                current
            } else {
                val generated = readableNickname(user.uid)
                transaction.set(
                    profile,
                    mapOf(
                        "nickname" to generated,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                generated
            }
        }.await()
        return AnonymousAccount(user.uid, nickname)
    }
}
