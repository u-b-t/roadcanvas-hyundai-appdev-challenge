package kr.roadcanvas.app.drawing.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import kr.roadcanvas.app.drawing.DrawingStorage

/**
 * 그림 기록을 앱 전용 내부 저장소에 보관하는 DB.
 *
 * 스키마를 바꾸면 [VERSION]을 올리고 [MIGRATIONS]에 이전 버전에서 올라오는 마이그레이션을 추가한다.
 * 버전별 스키마는 app/schemas에 기록되며 androidTest에서 마이그레이션을 검증하는 데 쓴다.
 */
@Database(
    entities = [
        CompletedDrawingEntity::class,
        CompletedRoutePointEntity::class,
        CompletedMetricEntity::class,
        InProgressDrawingEntity::class,
        InProgressRoutePointEntity::class
    ],
    version = DrawingDatabase.VERSION,
    exportSchema = true
)
abstract class DrawingDatabase : RoomDatabase() {
    abstract fun completedDrawingDao(): CompletedDrawingDao

    abstract fun inProgressDrawingDao(): InProgressDrawingDao

    companion object {
        const val VERSION = 1
        const val NAME = "drawings.db"

        val MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context, name: String = NAME): DrawingDatabase =
            Room.databaseBuilder(context, DrawingDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                .build()

        fun inMemory(context: Context): DrawingDatabase =
            Room.inMemoryDatabaseBuilder(context, DrawingDatabase::class.java).build()
    }
}

fun DrawingDatabase.toStorage() = DrawingStorage(
    completed = RoomCompletedDrawingRepository(completedDrawingDao()),
    inProgress = RoomInProgressDrawingRepository(inProgressDrawingDao())
)
