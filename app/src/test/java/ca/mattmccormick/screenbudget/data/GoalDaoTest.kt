package ca.mattmccormick.screenbudget.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoalDaoTest {
    private lateinit var database: UsageDatabase
    private lateinit var dao: GoalDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            UsageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.goalDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndQueriesReturnGoalsInDateOrder() {
        val september19 = Goal(LocalDate.of(2026, 9, 19), 50)
        val september26 = Goal(LocalDate.of(2026, 9, 26), 45)
        val october3 = Goal(LocalDate.of(2026, 10, 3), 40)
        dao.insert(october3)
        dao.insert(september19)
        dao.insert(september26)

        assertEquals(september26, dao.forWeek(september26.weekStart))
        assertEquals(
            listOf(september19, september26),
            dao.between(september19.weekStart, september26.weekStart),
        )
        assertEquals(listOf(october3, september26, september19), dao.newestFirst())
    }

    @Test
    fun insertingTheSameWeekReplacesItsGoal() {
        val weekStart = LocalDate.of(2026, 9, 26)
        dao.insert(Goal(weekStart, 45))

        dao.insert(Goal(weekStart, 30))

        assertEquals(Goal(weekStart, 30), dao.forWeek(weekStart))
        assertEquals(listOf(Goal(weekStart, 30)), dao.between(weekStart, weekStart))
    }
}
