package com.app.coachup.app.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---------------------------------------------------------------------------
// Training Session Insert
// ---------------------------------------------------------------------------
@Serializable
data class TrainingSessionInsert(
    @SerialName("id")           val id: String? = null,
    @SerialName("user_id")      val userId: String,
    @SerialName("gym_id")       val gymId: String? = null,
    @SerialName("program_id")   val programId: String? = null,
    @SerialName("coach_id")     val coachId: String? = null,
    @SerialName("scheduled_at") val scheduledAt: String,
    @SerialName("started_at")   val startedAt: String? = null,
    @SerialName("status")       val status: String,
    @SerialName("notes")        val notes: String? = null
)

// ---------------------------------------------------------------------------
// Coach Message Insert
// ---------------------------------------------------------------------------
@Serializable
data class CoachMessageInsert(
    @SerialName("user_id")  val userId: String,
    @SerialName("coach_id") val coachId: String,
    @SerialName("message")  val message: String,
    @SerialName("sent_at")  val sentAt: String
)

// ---------------------------------------------------------------------------
// Scheduled Program Insert
// ---------------------------------------------------------------------------
@Serializable
data class ScheduledProgramInsert(
    @SerialName("user_id")        val userId: String,
    @SerialName("program_id")     val programId: String,
    @SerialName("coach_id")       val coachId: String? = null,
    @SerialName("scheduled_date") val scheduledDate: String,
    @SerialName("start_time")     val startTime: String,
    @SerialName("end_time")       val endTime: String,
    @SerialName("status")         val status: String
)

// ---------------------------------------------------------------------------
// QR Entry Insert
// ---------------------------------------------------------------------------
@Serializable
data class QREntryInsert(
    @SerialName("id")         val id: String? = null,
    @SerialName("user_id")    val userId: String,
    @SerialName("gym_id")     val gymId: String,
    /** entry | exit */
    @SerialName("entry_type") val entryType: String,
    /** YYYY-MM-DD */
    @SerialName("entry_date") val entryDate: String,
    /** HH:MM:SS */
    @SerialName("entry_time") val entryTime: String,
    @SerialName("qr_code")    val qrCode: String? = null,
    @SerialName("location")   val location: QREntryLocation? = null
)

// ---------------------------------------------------------------------------
// Exercise History Insert
// ---------------------------------------------------------------------------
@Serializable
data class ExerciseHistoryInsert(
    @SerialName("user_id")     val userId: String,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("weight")      val weight: Double,
    @SerialName("reps")        val reps: Int,
    @SerialName("sets")        val sets: Int,
    @SerialName("notes")       val notes: String? = null,
    @SerialName("recorded_at") val recordedAt: String
)

// ---------------------------------------------------------------------------
// Exercise Result Insert
// ---------------------------------------------------------------------------
@Serializable
data class ExerciseResultInsert(
    @SerialName("user_id")      val userId: String,
    @SerialName("exercise_id")  val exerciseId: String,
    @SerialName("max_weight")   val maxWeight: Double,
    @SerialName("max_reps")     val maxReps: Int,
    @SerialName("one_rep_max")  val oneRepMax: Double,
    @SerialName("last_updated") val lastUpdated: String
)

// ---------------------------------------------------------------------------
// Personal Record Insert
// ---------------------------------------------------------------------------
@Serializable
data class PersonalRecordInsert(
    @SerialName("user_id")     val userId: String,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("weight")      val weight: Double,
    @SerialName("reps")        val reps: Int,
    @SerialName("record_date") val recordDate: String,
    @SerialName("notes")       val notes: String? = null
)

// ---------------------------------------------------------------------------
// User Membership Insert
// ---------------------------------------------------------------------------
@Serializable
data class UserMembershipInsert(
    @SerialName("user_id")     val userId: String,
    @SerialName("plan_id")     val planId: String,
    @SerialName("gym_id")      val gymId: String? = null,
    @SerialName("total_price") val totalPrice: Double? = null,
    @SerialName("notes")       val notes: String? = null,
    @SerialName("coach_id")    val coachId: String? = null,
    @SerialName("start_date")  val startDate: String,
    @SerialName("end_date")    val endDate: String,
    @SerialName("is_active")   val isActive: Boolean,
    @SerialName("auto_renew")  val autoRenew: Boolean
)

// ---------------------------------------------------------------------------
// Notification Insert
// ---------------------------------------------------------------------------
@Serializable
data class NotificationInsert(
    @SerialName("user_id")    val userId: String,
    @SerialName("title")      val title: String,
    @SerialName("message")    val message: String,
    @SerialName("type")       val type: String,
    @SerialName("is_read")    val isRead: Boolean,
    @SerialName("action_url") val actionUrl: String? = null
)

@Serializable
data class StreakUpdate(
    @SerialName("current_streak")     val currentStreak: Int,
    @SerialName("longest_streak")     val longestStreak: Int,
    @SerialName("last_activity_date") val lastActivityDate: String? = null
)

// ---------------------------------------------------------------------------
// User Activity Insert
// ---------------------------------------------------------------------------
@Serializable
data class UserActivityInsert(
    @SerialName("user_id")         val userId: String,
    @SerialName("activity_date")   val activityDate: String,
    @SerialName("activity_type")   val activityType: String,
    @SerialName("duration")        val duration: Int? = null,
    @SerialName("calories_burned") val caloriesBurned: Int? = null
)

// ---------------------------------------------------------------------------
// User Profile Insert
// ---------------------------------------------------------------------------
@Serializable
data class UserProfileInsert(
    @SerialName("id")                    val id: String,
    @SerialName("email")                 val email: String,
    @SerialName("name")                  val name: String,
    @SerialName("gender")                val gender: String,
    @SerialName("notifications_enabled") val notificationsEnabled: Boolean,
    @SerialName("biometric_enabled")     val biometricEnabled: Boolean,
    @SerialName("default_screen")        val defaultScreen: String,
    @SerialName("weight_unit")           val weightUnit: String,
    @SerialName("role")                  val role: String? = null,
    @SerialName("gym_id")                val gymId: String? = null,
    @SerialName("gym_name")              val gymName: String? = null,
    @SerialName("default_location")      val defaultLocation: String? = null
)

// ---------------------------------------------------------------------------
// Membership Request Insert
// ---------------------------------------------------------------------------
@Serializable
data class MembershipRequestInsert(
    @SerialName("user_id")           val userId: String,
    @SerialName("plan_id")           val planId: String,
    @SerialName("gym_id")            val gymId: String? = null,
    @SerialName("payment_method")    val paymentMethod: String,
    @SerialName("payment_proof_url") val paymentProofUrl: String? = null,
    @SerialName("admin_notes")       val adminNotes: String? = null
)

// ---------------------------------------------------------------------------
// Appointment Insert
// ---------------------------------------------------------------------------
@Serializable
data class AppointmentInsert(
    @SerialName("user_id")    val userId: String,
    @SerialName("coach_id")   val coachId: String? = null,
    @SerialName("gym_id")     val gymId: String? = null,
    @SerialName("date")       val date: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time")   val endTime: String,
    @SerialName("type")       val type: String,
    @SerialName("notes")      val notes: String? = null,
    @SerialName("status")     val status: String
)

// ---------------------------------------------------------------------------
// Class Booking Insert
// ---------------------------------------------------------------------------
@Serializable
data class ClassBookingInsert(
    @SerialName("user_id")      val userId: String,
    @SerialName("class_id")     val classId: String,
    @SerialName("gym_id")       val gymId: String? = null,
    @SerialName("booking_date") val bookingDate: String,
    @SerialName("status")       val status: String
)

// ---------------------------------------------------------------------------
// Nutrition Log Insert
// ---------------------------------------------------------------------------
@Serializable
data class NutritionLogInsert(
    @SerialName("user_id")   val userId: String,
    @SerialName("plan_id")   val planId: String? = null,
    @SerialName("meal_type") val mealType: String,
    @SerialName("calories")  val calories: Int,
    @SerialName("protein")   val protein: Double,
    @SerialName("carbs")     val carbs: Double,
    @SerialName("fat")       val fat: Double,
    @SerialName("notes")     val notes: String? = null,
    @SerialName("logged_at") val loggedAt: String
)

// ---------------------------------------------------------------------------
// Progress Log Insert
// ---------------------------------------------------------------------------
@Serializable
data class ProgressLogInsert(
    @SerialName("user_id")             val userId: String,
    @SerialName("gym_id")              val gymId: String? = null,
    @SerialName("weight")              val weight: Double? = null,
    @SerialName("body_fat_percentage") val bodyFatPercentage: Double? = null,
    @SerialName("chest")               val chest: Double? = null,
    @SerialName("waist")               val waist: Double? = null,
    @SerialName("hips")                val hips: Double? = null,
    @SerialName("biceps_left")         val bicepsLeft: Double? = null,
    @SerialName("biceps_right")        val bicepsRight: Double? = null,
    @SerialName("thigh_left")          val thighLeft: Double? = null,
    @SerialName("thigh_right")         val thighRight: Double? = null,
    @SerialName("bmi")                 val bmi: Double? = null,
    @SerialName("notes")               val notes: String? = null,
    @SerialName("log_date")            val logDate: String,
    @SerialName("arms")                val arms: Double? = null,
    @SerialName("legs")                val legs: Double? = null,
    @SerialName("measured_at")         val measuredAt: String,
    @SerialName("source")              val source: String? = "self",
    @SerialName("recorded_by_name")     val recordedByName: String? = null
)

// ---------------------------------------------------------------------------
// Goal Insert
// ---------------------------------------------------------------------------
@Serializable
data class GoalInsert(
    @SerialName("user_id")       val userId: String,
    @SerialName("gym_id")        val gymId: String? = null,
    @SerialName("type")          val type: String,
    @SerialName("title")         val title: String,
    @SerialName("target_value")  val targetValue: Double? = null,
    @SerialName("current_value") val currentValue: Double? = null,
    @SerialName("unit")          val unit: String? = null,
    @SerialName("start_date")    val startDate: String,
    @SerialName("target_date")   val targetDate: String? = null,
    @SerialName("status")        val status: String
)

// ---------------------------------------------------------------------------
// Survey Response Insert
// answers is a free-form JSON object; JsonElement covers any JSON value type.
// ---------------------------------------------------------------------------
@Serializable
data class SurveyResponseInsert(
    @SerialName("survey_id") val surveyId: String,
    @SerialName("user_id")   val userId: String,
    @SerialName("gym_id")    val gymId: String? = null,
    @SerialName("answers")   val answers: Map<String, JsonElement>
)

// ---------------------------------------------------------------------------
// Progress Photo Insert
// ---------------------------------------------------------------------------
@Serializable
data class ProgressPhotoInsert(
    @SerialName("user_id")    val userId: String,
    @SerialName("gym_id")     val gymId: String? = null,
    @SerialName("photo_url")  val photoUrl: String,
    @SerialName("photo_type") val photoType: String,
    @SerialName("notes")      val notes: String? = null,
    @SerialName("taken_at")   val takenAt: String
)

// ---------------------------------------------------------------------------
// Reservation Insert
// ---------------------------------------------------------------------------
@Serializable
data class ReservationInsert(
    @SerialName("area_id")          val areaId: String,
    @SerialName("user_id")          val userId: String,
    @SerialName("gym_id")           val gymId: String? = null,
    @SerialName("reservation_date") val reservationDate: String,
    @SerialName("start_time")       val startTime: String,
    @SerialName("end_time")         val endTime: String,
    @SerialName("status")           val status: String,
    @SerialName("notes")            val notes: String? = null
)

// ---------------------------------------------------------------------------
// Workout Set Insert
// ---------------------------------------------------------------------------
@Serializable
data class WorkoutSetInsert(
    @SerialName("session_id")   val sessionId: String,
    @SerialName("exercise_id")  val exerciseId: String,
    @SerialName("user_id")      val userId: String,
    @SerialName("set_number")   val setNumber: Int,
    @SerialName("reps")         val reps: Int,
    @SerialName("weight")       val weight: Double? = null,
    @SerialName("is_completed") val isCompleted: Boolean,
    @SerialName("completed_at") val completedAt: String? = null
)

// ---------------------------------------------------------------------------
// Profile / Settings Update DTOs
// ---------------------------------------------------------------------------

@Serializable
data class NotificationSettingUpdate(
    @SerialName("notifications_enabled") val notificationsEnabled: Boolean
)

@Serializable
data class BiometricSettingUpdate(
    @SerialName("biometric_enabled") val biometricEnabled: Boolean
)

@Serializable
data class DefaultScreenUpdate(
    @SerialName("default_screen") val defaultScreen: String
)

@Serializable
data class WeightUnitUpdate(
    @SerialName("weight_unit") val weightUnit: String
)

@Serializable
data class StatusUpdate(
    @SerialName("status") val status: String
)

@Serializable
data class BookingCountUpdate(
    @SerialName("current_bookings") val currentBookings: Int
)

@Serializable
data class ExitTimeUpdate(
    @SerialName("exit_time") val exitTime: String
)

@Serializable
data class IsReadUpdate(
    @SerialName("is_read") val isRead: Boolean
)

@Serializable
data class MembershipStatusUpdate(
    @SerialName("is_active") val isActive: Boolean
)

@Serializable
data class AutoRenewUpdate(
    @SerialName("auto_renew") val autoRenew: Boolean
)

@Serializable
data class MembershipCancelUpdate(
    @SerialName("is_active")  val isActive: Boolean,
    @SerialName("auto_renew") val autoRenew: Boolean
)

@Serializable
data class SessionCompleteUpdate(
    @SerialName("completed_at") val completedAt: String,
    @SerialName("status")       val status: String,
    @SerialName("notes")        val notes: String? = null
)

@Serializable
data class ExerciseResultUpdate(
    @SerialName("max_weight")   val maxWeight: Double? = null,
    @SerialName("max_reps")     val maxReps: Int? = null,
    @SerialName("one_rep_max")  val oneRepMax: Double? = null,
    @SerialName("last_updated") val lastUpdated: String
)

/** Used when updating personal info — birth_date as ISO-8601 String (nullable). */
@Serializable
data class ProfileFieldsUpdateRequest(
    @SerialName("name")       val name: String,
    @SerialName("surname")    val surname: String,
    @SerialName("email")      val email: String,
    @SerialName("gender")     val gender: String,
    @SerialName("birth_date") val birthDate: String? = null
)

// ---------------------------------------------------------------------------
// Admin Update DTOs
// ---------------------------------------------------------------------------

@Serializable
data class UserGymUpdate(
    @SerialName("gym_id") val gymId: String? = null
)

@Serializable
data class UserBanUpdate(
    @SerialName("is_banned")  val isBanned: Boolean,
    @SerialName("ban_reason") val banReason: String? = null,
    @SerialName("banned_at")  val bannedAt: String? = null
)

@Serializable
data class ActiveStatusUpdate(
    @SerialName("is_active") val isActive: Boolean
)

// ---------------------------------------------------------------------------
// Appointment Update DTOs
// ---------------------------------------------------------------------------

@Serializable
data class AppointmentCancelUpdate(
    @SerialName("status")        val status: String,
    @SerialName("cancel_reason") val cancelReason: String? = null
)

// ---------------------------------------------------------------------------
// Goal Update DTOs
// ---------------------------------------------------------------------------

@Serializable
data class GoalProgressUpdate(
    @SerialName("current_value")       val currentValue: Double? = null,
    @SerialName("progress_percentage") val progressPercentage: Int,
    @SerialName("notes")               val notes: String? = null
)

// ---------------------------------------------------------------------------
// Workout Set Update
// ---------------------------------------------------------------------------

@Serializable
data class WorkoutSetUpdate(
    @SerialName("reps")         val reps: Int,
    @SerialName("weight")       val weight: Double? = null,
    @SerialName("is_completed") val isCompleted: Boolean,
    @SerialName("completed_at") val completedAt: String? = null
)
