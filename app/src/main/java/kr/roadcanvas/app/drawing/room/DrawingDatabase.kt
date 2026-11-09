package kr.roadcanvas.app.drawing.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kr.roadcanvas.app.drawing.DrawingStorage
import kr.roadcanvas.app.drawing.ROUTE_THUMBNAIL_MAX_POINTS

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
        CompletedThumbnailPointEntity::class,
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
        const val VERSION = 4
        const val NAME = "drawings.db"

        /** 진행 중 기록에 종료 시각을 추가한다. 기존 진행 중 기록은 종료하지 않은 도전으로 남는다. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE in_progress_drawing ADD COLUMN ended_at INTEGER")
            }
        }

        /** 완료 기록에 이름을 추가한다. 기존 완료 기록은 이름이 없는 기록으로 남는다. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE completed_drawing ADD COLUMN name TEXT")
            }
        }

        /**
         * 완료 기록에 썸네일용 축약 경로 테이블을 추가하고, 기존 기록은 저장된 경로에서
         * `routeThumbnail`과 같은 간격 규칙(첫 점부터 일정 간격, 마지막 점 포함)으로 채운다.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `completed_thumbnail_point` (" +
                        "`drawing_id` INTEGER NOT NULL, `seq` INTEGER NOT NULL, " +
                        "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, " +
                        "`time_millis` INTEGER NOT NULL, PRIMARY KEY(`drawing_id`, `seq`), " +
                        "FOREIGN KEY(`drawing_id`) REFERENCES `completed_drawing`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                val max = ROUTE_THUMBNAIL_MAX_POINTS
                db.execSQL(
                    "INSERT INTO completed_thumbnail_point " +
                        "(drawing_id, seq, latitude, longitude, time_millis) " +
                        "SELECT p.drawing_id, p.seq, p.latitude, p.longitude, p.time_millis " +
                        "FROM completed_route_point p JOIN (" +
                        "SELECT drawing_id, COUNT(*) AS n FROM completed_route_point " +
                        "GROUP BY drawing_id) c ON p.drawing_id = c.drawing_id " +
                        "WHERE p.seq % ((c.n + ${max - 1}) / $max) = 0 OR p.seq = c.n - 1"
                )
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

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
