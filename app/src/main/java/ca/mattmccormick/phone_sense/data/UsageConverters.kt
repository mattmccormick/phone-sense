package ca.mattmccormick.phone_sense.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

class UsageConverters {
    @TypeConverter
    fun localDateToString(value: LocalDate): String = value.toString()

    @TypeConverter
    fun stringToLocalDate(value: String): LocalDate = LocalDate.parse(value)

    @TypeConverter
    fun instantToLong(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun sourceToString(value: Source): String = value.name

    @TypeConverter
    fun stringToSource(value: String): Source = Source.valueOf(value)
}
