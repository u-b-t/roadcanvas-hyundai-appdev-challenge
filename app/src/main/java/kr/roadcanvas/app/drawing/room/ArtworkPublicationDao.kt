package kr.roadcanvas.app.drawing.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class ArtworkPublicationDao {
    @Query("SELECT * FROM artwork_publication WHERE drawing_id = :drawingId")
    abstract suspend fun find(drawingId: Long): ArtworkPublicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(publication: ArtworkPublicationEntity)
}
