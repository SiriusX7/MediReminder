package com.medassist.data.local

import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromString(value: String?): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value ?: "[]", listType)
    }

    @TypeConverter
    fun fromList(list: List<String>?): String {
        return gson.toJson(list ?: emptyList<String>())
    }
}

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String = "local_user",
    val name: String,
    val formLabel: String = "Pill",
    val reason: String = "",
    val frequencyType: String = "Everyday",
    val daysOfWeek: List<String> = emptyList(),
    val interval: Int = 1,
    val unitLabel: String = "Days",
    val startDate: String = "",
    val time24: String = "",
    val quantity: Int = 1,
    val stock: Int = 0,
    val refillAt: Int = 0,
    val colorArgb: Int = 0,
    val paleArgb: Int = 0,
    val takenDates: List<String> = emptyList(),
    val skippedDates: List<String> = emptyList(),
    val isOcrScanned: Boolean = false,
    val isVerified: Boolean = true,
    val syncStatus: String = "PENDING"
)

@Entity(
    tableName = "intake_logs",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("medicationId")]
)
data class IntakeLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String = "local_user",
    val medicationId: Int,
    val timestamp: Long,
    val status: String, // e.g., "TAKEN", "SKIPPED", "SNOOZED"
    val syncStatus: String = "PENDING"
)

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val uid: String = "local_user",
    val email: String = "",
    val displayName: String? = null,
    val firstName: String = "Maya",
    val lastName: String = "Baker",
    val age: Int = 28,
    val gender: String = "Female",
    val bloodGroup: String = "O+",
    val medicalConditions: String = "Vitamin D Deficiency",
    val emergencyContact: String = "+1 (555) 019-2834"
) {
    val name: String
        get() = "$firstName $lastName".trim()

    val initials: String
        get() {
            val f = firstName.trim().take(1).uppercase()
            val l = lastName.trim().take(1).uppercase()
            return when {
                f.isNotEmpty() && l.isNotEmpty() -> "$f$l"
                f.isNotEmpty() -> f
                l.isNotEmpty() -> l
                else -> "U"
            }
        }
}

@Dao
interface MedicationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(medication: Medication): Long

    @Update
    suspend fun update(medication: Medication)

    @Delete
    suspend fun delete(medication: Medication)

    @Query("SELECT * FROM medications WHERE id = :id AND userId = :uid")
    suspend fun getMedicationById(id: Int, uid: String): Medication?

    @Query("SELECT * FROM medications WHERE userId = :uid ORDER BY name ASC")
    fun getAllMedications(uid: String): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE syncStatus = 'PENDING' AND userId = :uid")
    suspend fun getPendingSyncMedications(uid: String): List<Medication>

    @Query("DELETE FROM medications WHERE userId = :uid")
    suspend fun clearAllForUser(uid: String)
}

@Dao
interface IntakeLogDao {
    @Insert
    suspend fun insert(log: IntakeLog)

    @Update
    suspend fun update(log: IntakeLog)

    @Query("SELECT * FROM intake_logs WHERE medicationId = :medicationId AND userId = :uid ORDER BY timestamp DESC")
    fun getLogsForMedication(medicationId: Int, uid: String): Flow<List<IntakeLog>>

    @Query("SELECT * FROM intake_logs WHERE userId = :uid ORDER BY timestamp DESC")
    fun getAllLogs(uid: String): Flow<List<IntakeLog>>

    @Query("SELECT * FROM intake_logs WHERE syncStatus = 'PENDING' AND userId = :uid")
    suspend fun getPendingSyncLogs(uid: String): List<IntakeLog>

    @Query("DELETE FROM intake_logs WHERE userId = :uid")
    suspend fun clearAllForUser(uid: String)
}

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: UserProfile)

    @Query("SELECT * FROM user_profile WHERE uid = :uid LIMIT 1")
    suspend fun getUserProfile(uid: String): UserProfile?

    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getAnyUserProfile(): UserProfile?

    @Query("DELETE FROM user_profile WHERE uid = :uid")
    suspend fun deleteProfile(uid: String)
}

@Database(entities = [Medication::class, IntakeLog::class, UserProfile::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class MediReminderDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun intakeLogDao(): IntakeLogDao
    abstract fun userProfileDao(): UserProfileDao
}
