package ca.mattmccormick.screenbudget.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey val appKey: String,
    val label: String,
    val excluded: Boolean,
)

@Dao
interface AppRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(rule: AppRule)

    @Query("SELECT * FROM app_rules ORDER BY appKey")
    fun all(): List<AppRule>

    @Query("SELECT appKey FROM app_rules WHERE excluded = 1 ORDER BY appKey")
    fun excludedKeys(): List<String>
}
