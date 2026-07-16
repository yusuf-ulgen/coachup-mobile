package com.app.coachup.app.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

enum class Gender(val value: String, val dbValue: String) {
    MALE("Erkek", "male"),
    FEMALE("Kadın", "female");

    companion object {
        fun fromValue(value: String): Gender? = entries.find { it.value == value }

        fun fromDbValue(value: String?): Gender? = when (value?.lowercase()) {
            "male", "erkek" -> MALE
            "female", "kadın", "kadin" -> FEMALE
            else -> fromValue(value ?: "")
        }

        fun toDisplayValue(value: String?): String =
            fromDbValue(value)?.value ?: value ?: MALE.value

        fun toDbValue(value: String): String =
            fromDbValue(value)?.dbValue ?: fromValue(value)?.dbValue ?: value.lowercase()
    }
}

enum class TrainingDifficulty(val value: String) {
    BEGINNER("beginner"),
    INTERMEDIATE("intermediate"),
    ADVANCED("advanced");

    companion object {
        fun fromValue(value: String): TrainingDifficulty? = entries.find { it.value == value }
    }
}

enum class SessionStatus(val value: String) {
    SCHEDULED("scheduled"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    companion object {
        fun fromValue(value: String): SessionStatus? = entries.find { it.value == value }
    }
}

enum class MembershipRequestStatus(val value: String) {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    CANCELLED("cancelled");

    val displayName: String
        get() = when (this) {
            PENDING   -> "Beklemede"
            APPROVED  -> "Onaylandı"
            REJECTED  -> "Reddedildi"
            CANCELLED -> "İptal Edildi"
        }

    companion object {
        fun fromValue(value: String): MembershipRequestStatus? = entries.find { it.value == value }
    }
}

enum class PaymentMethod(val value: String) {
    LOCAL("local"),
    CREDIT_CARD("credit_card"),
    BANK_TRANSFER("bank_transfer");

    val displayName: String
        get() = when (this) {
            LOCAL         -> "Salon'da Ödeme"
            CREDIT_CARD   -> "Kredi Kartı"
            BANK_TRANSFER -> "Havale/EFT"
        }

    companion object {
        fun fromValue(value: String): PaymentMethod? = entries.find { it.value == value }
    }
}

// ---------------------------------------------------------------------------
// UserProfile — maps to "users" table
// ---------------------------------------------------------------------------
@Serializable
data class UserProfile(
    @SerialName("id")                     val id: String,
    @SerialName("email")                  val email: String? = null,
    @SerialName("name")                   val name: String? = null,
    @SerialName("surname")                val surname: String? = null,
    @SerialName("phone")                  val phone: String? = null,
    @SerialName("birth_date")             val birthDate: String? = null,
    @SerialName("gender")                 val gender: String? = null,
    @SerialName("height")                 val height: Double? = null,
    @SerialName("weight")                 val weight: Double? = null,
    @SerialName("profile_image_url")      val profileImageUrl: String? = null,
    @SerialName("role")                   val role: String? = null,
    @SerialName("notifications_enabled") val notificationsEnabled: Boolean = true,
    @SerialName("biometric_enabled")      val biometricEnabled: Boolean = false,
    @SerialName("default_screen")         val defaultScreen: String = "home",
    @SerialName("weight_unit")            val weightUnit: String = "kg",
    @SerialName("current_streak")         val currentStreak: Int = 0,
    @SerialName("longest_streak")         val longestStreak: Int = 0,
    @SerialName("last_activity_date")     val lastActivityDate: String? = null,
    @SerialName("gym_id")                 val gymId: String? = null,
    @SerialName("gym_name")               val gymName: String? = null,
    @SerialName("default_location")       val defaultLocation: String? = null,
    @SerialName("is_banned")              val isBanned: Boolean? = null,
    @SerialName("ban_reason")             val banReason: String? = null,
    @SerialName("banned_at")              val bannedAt: String? = null,
    @SerialName("created_at")             val createdAt: String = "",
    @SerialName("updated_at")             val updatedAt: String = ""
) {
    val isIndividual: Boolean get() = role == "individual"
}

// ---------------------------------------------------------------------------
// Address — maps to "addresses" table
// ---------------------------------------------------------------------------
@Serializable
data class Address(
    @SerialName("id")            val id: String,
    @SerialName("user_id")       val userId: String,
    @SerialName("title")         val title: String,
    @SerialName("city")          val city: String,
    @SerialName("district")      val district: String,
    @SerialName("neighborhood")  val neighborhood: String? = null,
    @SerialName("street")        val street: String? = null,
    @SerialName("building_no")   val buildingNo: String? = null,
    @SerialName("apartment_no")  val apartmentNo: String? = null,
    @SerialName("postal_code")   val postalCode: String? = null,
    @SerialName("is_default")    val isDefault: Boolean = false,
    @SerialName("created_at")    val createdAt: String = "",
    @SerialName("updated_at")    val updatedAt: String = ""
)

// ---------------------------------------------------------------------------
// EmergencyContact — maps to "emergency_contacts" table
// ---------------------------------------------------------------------------
@Serializable
data class EmergencyContact(
    @SerialName("id")           val id: String,
    @SerialName("user_id")      val userId: String,
    @SerialName("name")         val name: String,
    @SerialName("phone")        val phone: String,
    @SerialName("relationship") val relationship: String? = null,
    @SerialName("created_at")   val createdAt: String = "",
    @SerialName("updated_at")   val updatedAt: String = ""
)

// ---------------------------------------------------------------------------
// Coach — maps to "coaches" table
// ---------------------------------------------------------------------------
@Serializable
data class Coach(
    @SerialName("id")                val id: String,
    @SerialName("gym_id")            val gymId: String? = null,
    @SerialName("name")              val name: String,
    @SerialName("surname")           val surname: String,
    @SerialName("email")             val email: String? = null,
    @SerialName("phone")             val phone: String? = null,
    @SerialName("gender")            val gender: String,
    @SerialName("specialty")         val specialty: String? = null,
    @SerialName("bio")               val bio: String? = null,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
    @SerialName("rating")            val rating: Double = 0.0,
    @SerialName("experience_years")  val experienceYears: Int = 0,
    @SerialName("is_active")         val isActive: Boolean = true,
    @SerialName("created_at")        val createdAt: String = "",
    @SerialName("updated_at")        val updatedAt: String = ""
) {
    val fullName: String get() = "$name $surname"
}

// ---------------------------------------------------------------------------
// CoachSchedule — maps to "coach_schedules" table
// ---------------------------------------------------------------------------
@Serializable
data class CoachSchedule(
    @SerialName("id")               val id: String,
    @SerialName("coach_id")         val coachId: String,
    /** 0 = Monday … 6 = Sunday */
    @SerialName("day_of_week")      val dayOfWeek: Int,
    @SerialName("start_time")       val startTime: String, // HH:mm
    @SerialName("end_time")         val endTime: String,
    @SerialName("is_available")     val isAvailable: Boolean = true,
    @SerialName("max_capacity")     val maxCapacity: Int? = null,
    @SerialName("current_bookings") val currentBookings: Int? = null
)

// ---------------------------------------------------------------------------
// Exercise — maps to "exercises" table
// ---------------------------------------------------------------------------
@Serializable
data class Exercise(
    @SerialName("id")            val id: String,
    @SerialName("name")          val name: String,
    @SerialName("category")      val category: String,
    @SerialName("description")   val description: String? = null,
    @SerialName("icon_name")     val iconName: String? = null,
    @SerialName("muscle_groups") val muscleGroups: List<String>? = null,
    @SerialName("video_url")     val videoUrl: String? = null,
    @SerialName("image_url")     val imageUrl: String? = null,
    @SerialName("equipment")     val equipment: String? = null,
    @SerialName("instructions")  val instructions: String? = null,
    @SerialName("created_at")    val createdAt: String = ""
)

// ---------------------------------------------------------------------------
// ExerciseResult — maps to "exercise_results" table
// ---------------------------------------------------------------------------
@Serializable
data class ExerciseResult(
    @SerialName("id")           val id: String,
    @SerialName("user_id")      val userId: String,
    @SerialName("exercise_id")  val exerciseId: String,
    @SerialName("max_weight")   val maxWeight: Double,
    @SerialName("max_reps")     val maxReps: Int,
    @SerialName("one_rep_max")  val oneRepMax: Double,
    @SerialName("last_updated") val lastUpdated: String,
    @SerialName("exercise")     val exercise: Exercise? = null
)

// ---------------------------------------------------------------------------
// ExerciseHistory — maps to "exercise_history" table
// ---------------------------------------------------------------------------
@Serializable
data class ExerciseHistory(
    @SerialName("id")          val id: String,
    @SerialName("user_id")     val userId: String,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("weight")      val weight: Double,
    @SerialName("reps")        val reps: Int,
    @SerialName("sets")        val sets: Int,
    @SerialName("notes")       val notes: String? = null,
    @SerialName("recorded_at") val recordedAt: String
)

// ---------------------------------------------------------------------------
// TrainingProgram — maps to "training_programs" table
// ---------------------------------------------------------------------------
@Serializable
data class TrainingProgram(
    @SerialName("id")             val id: String,
    @SerialName("name")           val name: String,
    @SerialName("description")    val description: String? = null,
    @SerialName("category")       val category: String? = null,
    /** Duration in minutes */
    @SerialName("duration")       val duration: Int? = null,
    @SerialName("difficulty")     val difficulty: String? = null,
    @SerialName("calories_burn")  val caloriesBurn: Int? = null,
    @SerialName("exercise_count") val exerciseCount: Int? = null,
    @SerialName("icon_name")      val iconName: String? = null,
    @SerialName("is_active")      val isActive: Boolean = true,
    @SerialName("created_at")     val createdAt: String = "",
    @SerialName("gym_id")         val gymId: String? = null,
    @SerialName("coach_id")       val coachId: String? = null,
    @SerialName("image_url")      val imageUrl: String? = null
)

// ---------------------------------------------------------------------------
// TrainingSession — maps to "training_sessions" table
// ---------------------------------------------------------------------------
@Serializable
data class TrainingSession(
    @SerialName("id")           val id: String,
    @SerialName("user_id")      val userId: String,
    @SerialName("program_id")   val programId: String? = null,
    @SerialName("coach_id")     val coachId: String? = null,
    @SerialName("scheduled_at") val scheduledAt: String,
    @SerialName("started_at")   val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    /** scheduled | in_progress | completed | cancelled */
    @SerialName("status")       val status: String,
    @SerialName("notes")        val notes: String? = null,
    @SerialName("program")      val program: TrainingProgram? = null,
    @SerialName("coach")        val coach: Coach? = null
) {
    /** Yerleşik aktivite kategorisi — notes, builtin program_id veya program.category üzerinden. */
    val resolvedActivityCategory: TrainingCategory?
        get() {
            SessionWorkoutMeta.builtinCategoryDbValue(notes)?.let {
                return TrainingCategory.fromDbValue(it)
            }
            programId?.let { id ->
                BuiltInActivities.categoryForProgramId(id)?.let { return it }
            }
            program?.category?.let { cat ->
                val category = TrainingCategory.fromDbValue(cat)
                if (category in TrainingCategory.defaultModules) return category
            }
            return null
        }

    /** Yerleşik aktivite oturumlarında doğru başlık (ör. Koşu, Fitness). */
    val activityDisplayName: String
        get() = resolvedActivityCategory?.label ?: program?.name ?: "Antrenman"

    val recordedDurationSeconds: Int?
        get() {
            SessionWorkoutMeta.durationSeconds(notes)?.let { return it }
            if (completedAt.isNullOrBlank() || startedAt.isNullOrBlank()) return null
            return try {
                val completed = java.time.Instant.parse(completedAt)
                val started = java.time.Instant.parse(startedAt)
                java.time.Duration.between(started, completed).seconds.toInt().coerceAtLeast(0)
            } catch (_: Exception) {
                null
            }
        }

    val recordedDistanceKm: Double?
        get() = SessionWorkoutMeta.distanceKm(notes)
}

// ---------------------------------------------------------------------------
// MembershipPlan — maps to "membership_plans" table
// ---------------------------------------------------------------------------
@Serializable
data class MembershipPlan(
    @SerialName("id")               val id: String,
    @SerialName("gym_id")           val gymId: String? = null,
    @SerialName("name")             val name: String,
    @SerialName("description")      val description: String? = null,
    @SerialName("price")            val price: Double = 0.0,
    @SerialName("duration_months")  val durationMonths: Int = 0,
    @SerialName("features")         val features: List<String> = emptyList(),
    @SerialName("is_popular")       val isPopular: Boolean = false,
    @SerialName("is_active")        val isActive: Boolean = true,
    @SerialName("created_at")       val createdAt: String = ""
)

// ---------------------------------------------------------------------------
// UserMembership — maps to "user_memberships" table
// ---------------------------------------------------------------------------
@Serializable
data class UserMembership(
    @SerialName("id")          val id: String,
    @SerialName("user_id")     val userId: String,
    @SerialName("plan_id")     val planId: String? = null,
    @SerialName("gym_id")      val gymId: String? = null,
    @SerialName("total_price") val totalPrice: Double? = null,
    @SerialName("notes")       val notes: String? = null,
    @SerialName("coach_id")    val coachId: String? = null,
    @SerialName("start_date")  val startDate: String? = null,
    @SerialName("end_date")    val endDate: String? = null,
    @SerialName("is_active")   val isActive: Boolean = false,
    @SerialName("auto_renew")  val autoRenew: Boolean = false,
    @SerialName("created_at")  val createdAt: String = "",
    @SerialName("plan")        val plan: MembershipPlan? = null
)

// ---------------------------------------------------------------------------
// AppNotification — maps to "notifications" table
// ---------------------------------------------------------------------------
@Serializable
data class AppNotification(
    @SerialName("id")         val id: String,
    @SerialName("user_id")    val userId: String,
    @SerialName("title")      val title: String,
    @SerialName("message")    val message: String,
    /** workout | membership | system | coach */
    @SerialName("type")       val type: String,
    @SerialName("is_read")    val isRead: Boolean = false,
    @SerialName("action_url") val actionUrl: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

// ---------------------------------------------------------------------------
// ScheduledProgramDB — maps to "scheduled_programs" table
// ---------------------------------------------------------------------------
@Serializable
data class ScheduledProgramDB(
    @SerialName("id")             val id: String,
    @SerialName("user_id")        val userId: String,
    @SerialName("program_id")     val programId: String,
    @SerialName("coach_id")       val coachId: String? = null,
    @SerialName("scheduled_date") val scheduledDate: String,
    @SerialName("start_time")     val startTime: String,
    @SerialName("end_time")       val endTime: String,
    /** scheduled | completed | cancelled */
    @SerialName("status")         val status: String,
    @SerialName("program")        val program: TrainingProgram? = null,
    @SerialName("coach")          val coach: Coach? = null
) {
    val programName: String? get() = program?.name
    val coachName: String? get() = coach?.let { "${it.name} ${it.surname}" }
    val iconName: String? get() = program?.iconName
}

// ---------------------------------------------------------------------------
// UserActivity — maps to "user_activities" table
// ---------------------------------------------------------------------------
@Serializable
data class UserActivity(
    @SerialName("id")              val id: String,
    @SerialName("user_id")         val userId: String,
    @SerialName("activity_date")   val activityDate: String,
    @SerialName("activity_type")   val activityType: String,
    /** Duration in minutes */
    @SerialName("duration")        val duration: Int? = null,
    @SerialName("calories_burned") val caloriesBurned: Int? = null,
    @SerialName("created_at")      val createdAt: String = ""
)

// ---------------------------------------------------------------------------
// QREntryLocation — embedded JSON inside qr_entries.location
// ---------------------------------------------------------------------------
@Serializable
data class QREntryLocation(
    @SerialName("name")      val name: String? = null,
    @SerialName("latitude")  val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null
)

// ---------------------------------------------------------------------------
// QREntry — maps to "qr_entries" table
// ---------------------------------------------------------------------------
@Serializable
data class QREntry(
    @SerialName("id")              val id: String,
    @SerialName("user_id")         val userId: String,
    @SerialName("gym_id")          val gymId: String? = null,
    /** entry | exit */
    @SerialName("entry_type")      val entryType: String = "entry",
    /** YYYY-MM-DD — nullable; some rows may have NULL */
    @SerialName("entry_date")      val entryDate: String? = null,
    /** HH:MM:SS — nullable; some rows may have NULL */
    @SerialName("entry_time")      val entryTime: String? = null,
    @SerialName("qr_code")         val qrCode: String? = null,
    @SerialName("location")        val location: JsonElement? = null,
    @SerialName("entry_timestamp") val entryTimestamp: String? = null,
    @SerialName("created_at")      val createdAt: String? = null
)

// ---------------------------------------------------------------------------
// PersonalRecord — maps to "personal_records" table
// ---------------------------------------------------------------------------
@Serializable
data class PersonalRecord(
    @SerialName("id")          val id: String,
    @SerialName("user_id")     val userId: String,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("weight")      val weight: Double,
    @SerialName("reps")        val reps: Int,
    @SerialName("record_date") val recordDate: String,
    @SerialName("notes")       val notes: String? = null,
    @SerialName("exercise")    val exercise: Exercise? = null
)

// ---------------------------------------------------------------------------
// CoachMessage — maps to "coach_messages" table
// ---------------------------------------------------------------------------
@Serializable
data class CoachMessage(
    @SerialName("id")            val id: String,
    @SerialName("user_id")       val userId: String,
    @SerialName("coach_id")      val coachId: String,
    @SerialName("message")       val message: String,
    @SerialName("is_from_coach") val isFromCoach: Boolean = false,
    @SerialName("is_read")       val isRead: Boolean = false,
    @SerialName("sent_at")       val sentAt: String,
    @SerialName("coach")         val coach: Coach? = null,
    @SerialName("user")          val user: UserProfile? = null
)

// ---------------------------------------------------------------------------
// Gym — maps to "gyms" table
// ---------------------------------------------------------------------------
@Serializable
data class Gym(
    @SerialName("id")         val id: String,
    @SerialName("name")       val name: String,
    @SerialName("address")    val address: String? = null,
    @SerialName("city")       val city: String? = null,
    @SerialName("phone")      val phone: String? = null,
    @SerialName("email")      val email: String? = null,
    @SerialName("capacity")   val capacity: Int? = null,
    @SerialName("is_active")  val isActive: Boolean = true,
    @SerialName("qr_code")    val qrCode: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

// ---------------------------------------------------------------------------
// AuditLog — maps to "audit_logs" table
// Changes and metadata are free-form JSON, represented as JsonElement.
// ---------------------------------------------------------------------------
@Serializable
data class AuditLog(
    @SerialName("id")            val id: String,
    @SerialName("user_id")       val userId: String? = null,
    @SerialName("user_email")    val userEmail: String? = null,
    @SerialName("user_role")     val userRole: String? = null,
    @SerialName("action")        val action: String,
    @SerialName("resource_type") val resourceType: String,
    @SerialName("resource_id")   val resourceId: String? = null,
    @SerialName("description")   val description: String? = null,
    @SerialName("changes")       val changes: JsonElement? = null,
    @SerialName("metadata")      val metadata: JsonElement? = null,
    @SerialName("gym_id")        val gymId: String? = null,
    @SerialName("status")        val status: String,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("created_at")    val createdAt: String = ""
)

// ---------------------------------------------------------------------------
// MembershipRequest — maps to "membership_requests" table
// ---------------------------------------------------------------------------
@Serializable
data class MembershipRequest(
    @SerialName("id")                val id: String,
    @SerialName("user_id")           val userId: String,
    @SerialName("plan_id")           val planId: String,
    @SerialName("request_date")      val requestDate: String,
    /** pending | approved | rejected | cancelled */
    @SerialName("status")            val status: String,
    /** local | credit_card | bank_transfer */
    @SerialName("payment_method")    val paymentMethod: String,
    @SerialName("payment_proof_url") val paymentProofUrl: String? = null,
    @SerialName("admin_notes")       val adminNotes: String? = null,
    @SerialName("approved_by")       val approvedBy: String? = null,
    @SerialName("approved_at")       val approvedAt: String? = null,
    @SerialName("rejected_reason")   val rejectedReason: String? = null,
    @SerialName("created_at")        val createdAt: String = "",
    @SerialName("updated_at")        val updatedAt: String = "",
    @SerialName("plan")              val plan: MembershipPlan? = null
)

// ---------------------------------------------------------------------------
// Appointment — maps to "appointments" table
// ---------------------------------------------------------------------------
@Serializable
data class Appointment(
    @SerialName("id")            val id: String,
    @SerialName("user_id")       val userId: String,
    @SerialName("coach_id")      val coachId: String? = null,
    @SerialName("gym_id")        val gymId: String? = null,
    @SerialName("date")          val date: String,
    @SerialName("start_time")    val startTime: String,
    @SerialName("end_time")      val endTime: String,
    /** pending | confirmed | cancelled | completed */
    @SerialName("status")        val status: String,
    /** personal_training | consultation | assessment */
    @SerialName("type")          val type: String,
    @SerialName("notes")         val notes: String? = null,
    @SerialName("cancel_reason") val cancelReason: String? = null,
    @SerialName("created_at")    val createdAt: String = "",
    @SerialName("updated_at")    val updatedAt: String = ""
)

// ---------------------------------------------------------------------------
// GroupClass — maps to "group_classes" table
// ---------------------------------------------------------------------------
@Serializable
data class GroupClass(
    @SerialName("id")                   val id: String,
    @SerialName("gym_id")               val gymId: String? = null,
    @SerialName("coach_id")             val coachId: String? = null,
    @SerialName("name")                 val name: String,
    @SerialName("description")          val description: String? = null,
    @SerialName("instructor_name")      val instructorName: String? = null,
    @SerialName("day_of_week")          val dayOfWeek: Int,
    @SerialName("start_time")           val startTime: String,
    @SerialName("end_time")             val endTime: String,
    @SerialName("capacity")             val capacity: Int,
    @SerialName("current_participants") val currentParticipants: Int = 0,
    @SerialName("is_active")            val isActive: Boolean = true,
    @SerialName("location")             val location: String? = null,
    @SerialName("created_at")           val createdAt: String = "",
    @SerialName("updated_at")           val updatedAt: String = ""
)

// ---------------------------------------------------------------------------
// ClassBooking — maps to "class_bookings" table
// ---------------------------------------------------------------------------
@Serializable
data class ClassBooking(
    @SerialName("id")           val id: String,
    @SerialName("user_id")      val userId: String,
    @SerialName("class_id")     val classId: String,
    @SerialName("gym_id")       val gymId: String? = null,
    @SerialName("booking_date") val bookingDate: String,
    /** booked | cancelled | attended */
    @SerialName("status")       val status: String,
    @SerialName("created_at")   val createdAt: String = ""
)

// ---------------------------------------------------------------------------
// NutritionPlan — maps to "nutrition_plans" table
// ---------------------------------------------------------------------------
@Serializable
data class NutritionPlan(
    @SerialName("id")               val id: String,
    @SerialName("gym_id")           val gymId: String? = null,
    @SerialName("name")             val name: String,
    @SerialName("description")      val description: String? = null,
    @SerialName("target_calories")  val targetCalories: Int,
    @SerialName("meal_count")       val mealCount: Int,
    @SerialName("category")         val category: String? = null,
    @SerialName("created_at")       val createdAt: String = "",
    @SerialName("updated_at")       val updatedAt: String = ""
)

// ---------------------------------------------------------------------------
// NutritionMeal — maps to "nutrition_meals" table
// ---------------------------------------------------------------------------
@Serializable
data class NutritionMeal(
    @SerialName("id")          val id: String,
    @SerialName("plan_id")     val planId: String,
    @SerialName("name")        val name: String,
    /** kahvalti | ogle | aksam | ara_ogun */
    @SerialName("meal_type")   val mealType: String,
    @SerialName("calories")    val calories: Int,
    @SerialName("protein")     val protein: Double,
    @SerialName("carbs")       val carbs: Double,
    @SerialName("fat")         val fat: Double,
    @SerialName("description") val description: String? = null,
    @SerialName("order_index") val orderIndex: Int
)

// ---------------------------------------------------------------------------
// UserNutritionPlan — maps to "user_nutrition_plans" table
// ---------------------------------------------------------------------------
@Serializable
data class UserNutritionPlan(
    @SerialName("id")         val id: String,
    @SerialName("user_id")    val userId: String,
    @SerialName("plan_id")    val planId: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date")   val endDate: String? = null,
    /** active | completed | cancelled */
    @SerialName("status")     val status: String,
    @SerialName("notes")      val notes: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

// ---------------------------------------------------------------------------
// NutritionLog — maps to "nutrition_logs" table
// ---------------------------------------------------------------------------
@Serializable
data class NutritionLog(
    @SerialName("id")         val id: String,
    @SerialName("user_id")    val userId: String,
    @SerialName("plan_id")    val planId: String? = null,
    @SerialName("meal_type")  val mealType: String,
    @SerialName("calories")   val calories: Int,
    @SerialName("protein")    val protein: Double,
    @SerialName("carbs")      val carbs: Double,
    @SerialName("fat")        val fat: Double,
    @SerialName("notes")      val notes: String? = null,
    @SerialName("logged_at")  val loggedAt: String,
    @SerialName("created_at") val createdAt: String = ""
)

// ---------------------------------------------------------------------------
// UserProgressLog — maps to "user_progress_logs" table
// ---------------------------------------------------------------------------
@Serializable
data class UserProgressLog(
    @SerialName("id")                   val id: String,
    @SerialName("user_id")              val userId: String,
    @SerialName("gym_id")               val gymId: String? = null,
    @SerialName("weight")               val weight: Double? = null,
    @SerialName("body_fat_percentage")  val bodyFatPercentage: Double? = null,
    @SerialName("chest")                val chest: Double? = null,
    @SerialName("waist")                val waist: Double? = null,
    @SerialName("hips")                 val hips: Double? = null,
    @SerialName("biceps_left")          val bicepsLeft: Double? = null,
    @SerialName("biceps_right")         val bicepsRight: Double? = null,
    @SerialName("thigh_left")           val thighLeft: Double? = null,
    @SerialName("thigh_right")          val thighRight: Double? = null,
    @SerialName("bmi")                  val bmi: Double? = null,
    @SerialName("notes")                val notes: String? = null,
    @SerialName("log_date")             val logDate: String? = null,
    @SerialName("arms")                 val arms: Double? = null,
    @SerialName("legs")                 val legs: Double? = null,
    @SerialName("measured_at")          val measuredAt: String? = null,
    /** self | coach | admin */
    @SerialName("source")               val source: String? = null,
    @SerialName("recorded_by_name")    val recordedByName: String? = null,
    @SerialName("created_at")           val createdAt: String = ""
) {
    val displayDate: String
        get() = logDate?.take(10) ?: measuredAt?.take(10) ?: createdAt.take(10)

    val displayArms: Double?
        get() = arms ?: listOfNotNull(bicepsLeft, bicepsRight).takeIf { it.isNotEmpty() }?.average()

    val displayLegs: Double?
        get() = legs ?: listOfNotNull(thighLeft, thighRight).takeIf { it.isNotEmpty() }?.average()

    val sourceLabel: String
        get() = when (source?.lowercase()) {
            "self", "member", "user" -> "Kendi ekledi"
            "coach" -> recordedByName?.let { "$it (koç)" } ?: "Koç ölçümü"
            "admin" -> recordedByName?.let { "$it (panel)" } ?: "Panel ölçümü"
            else -> when {
                !recordedByName.isNullOrBlank() -> recordedByName
                source.isNullOrBlank() -> ""
                else -> source
            }
        }
}

// ---------------------------------------------------------------------------
// ProgressPhoto — maps to "progress_photos" table
// ---------------------------------------------------------------------------
@Serializable
data class ProgressPhoto(
    @SerialName("id")         val id: String,
    @SerialName("user_id")    val userId: String,
    @SerialName("gym_id")     val gymId: String? = null,
    @SerialName("photo_url")  val photoUrl: String,
    /** before | after | front | back | side_left | side_right */
    @SerialName("photo_type") val photoType: String,
    @SerialName("notes")      val notes: String? = null,
    @SerialName("taken_at")   val takenAt: String,
    @SerialName("created_at") val createdAt: String = ""
)

// ---------------------------------------------------------------------------
// UserGoal — maps to "user_goals" table
// ---------------------------------------------------------------------------
@Serializable
data class UserGoal(
    @SerialName("id")                  val id: String,
    @SerialName("user_id")             val userId: String,
    @SerialName("gym_id")              val gymId: String? = null,
    /** weight_loss | weight_gain | muscle_gain | endurance | flexibility | attendance | custom */
    @SerialName("type")                val type: String,
    @SerialName("title")               val title: String,
    @SerialName("target_value")        val targetValue: Double? = null,
    @SerialName("current_value")       val currentValue: Double? = null,
    @SerialName("unit")                val unit: String? = null,
    @SerialName("start_date")          val startDate: String,
    @SerialName("target_date")         val targetDate: String? = null,
    /** in_progress | completed | cancelled */
    @SerialName("status")              val status: String,
    @SerialName("progress_percentage") val progressPercentage: Int = 0,
    @SerialName("notes")               val notes: String? = null,
    @SerialName("created_at")          val createdAt: String = "",
    @SerialName("updated_at")          val updatedAt: String = ""
)

// ---------------------------------------------------------------------------
// FinancialTransaction — maps to "financial_transactions" table
// ---------------------------------------------------------------------------
@Serializable
data class FinancialTransaction(
    @SerialName("id")          val id: String,
    @SerialName("user_id")     val userId: String,
    @SerialName("gym_id")      val gymId: String? = null,
    @SerialName("amount")      val amount: Double,
    /** membership_payment | class_payment | pt_payment | refund */
    @SerialName("type")        val type: String,
    @SerialName("description") val description: String? = null,
    /** completed | pending | failed | refunded */
    @SerialName("status")      val status: String,
    @SerialName("created_at")  val createdAt: String = ""
)

// ---------------------------------------------------------------------------
// PaymentPlan — maps to "payment_plans" table
// ---------------------------------------------------------------------------
@Serializable
data class PaymentPlan(
    @SerialName("id")                val id: String,
    @SerialName("user_id")           val userId: String,
    @SerialName("gym_id")            val gymId: String? = null,
    @SerialName("total_amount")      val totalAmount: Double,
    @SerialName("installment_count") val installmentCount: Int,
    /** active | completed | cancelled */
    @SerialName("status")            val status: String,
    @SerialName("created_at")        val createdAt: String = ""
)

// ---------------------------------------------------------------------------
// PaymentInstallment — maps to "payment_installments" table
// ---------------------------------------------------------------------------
@Serializable
data class PaymentInstallment(
    @SerialName("id")                  val id: String,
    @SerialName("plan_id")             val planId: String,
    @SerialName("installment_number")  val installmentNumber: Int,
    @SerialName("amount")              val amount: Double,
    @SerialName("due_date")            val dueDate: String,
    @SerialName("paid_date")           val paidDate: String? = null,
    /** pending | paid | overdue */
    @SerialName("status")              val status: String
)

// ---------------------------------------------------------------------------
// Survey — maps to "surveys" table
// questions is a JSON array stored as List<JsonElement>
// ---------------------------------------------------------------------------
@Serializable
data class Survey(
    @SerialName("id")          val id: String,
    @SerialName("gym_id")      val gymId: String? = null,
    @SerialName("title")       val title: String,
    @SerialName("description") val description: String? = null,
    /** Legacy multi-question JSON array (may be empty when admin uses [question]). */
    @SerialName("questions")   val questions: List<JsonElement> = emptyList(),
    /** Single-question text used by the admin panel when [questions] is empty. */
    @SerialName("question")    val question: String? = null,
    /** satisfaction | service_quality | vote | poll | … */
    @SerialName("category")    val category: String? = null,
    @SerialName("start_date")  val startDate: String,
    @SerialName("end_date")    val endDate: String,
    /** active | closed | draft */
    @SerialName("status")      val status: String,
    @SerialName("created_at")  val createdAt: String = ""
)

// ---------------------------------------------------------------------------
// SurveyResponse — maps to "survey_responses" table
// answers is a free-form JSON object stored as Map<String, JsonElement>
// ---------------------------------------------------------------------------
@Serializable
data class SurveyResponse(
    @SerialName("id")         val id: String,
    @SerialName("survey_id")  val surveyId: String,
    @SerialName("user_id")    val userId: String,
    @SerialName("gym_id")     val gymId: String? = null,
    @SerialName("answers")    val answers: Map<String, JsonElement> = emptyMap(),
    @SerialName("created_at") val createdAt: String = ""
)

// ---------------------------------------------------------------------------
// GymArea — maps to "gym_areas" table
// ---------------------------------------------------------------------------
@Serializable
data class GymArea(
    @SerialName("id")          val id: String,
    @SerialName("gym_id")      val gymId: String? = null,
    @SerialName("name")        val name: String,
    /** studio | pool | sauna | court */
    @SerialName("area_type")   val areaType: String,
    @SerialName("capacity")    val capacity: Int,
    @SerialName("description") val description: String? = null,
    @SerialName("is_active")   val isActive: Boolean = true,
    @SerialName("amenities")   val amenities: List<String>? = null,
    @SerialName("created_at")  val createdAt: String = ""
)

// ---------------------------------------------------------------------------
// AreaReservation — maps to "area_reservations" table
// ---------------------------------------------------------------------------
@Serializable
data class AreaReservation(
    @SerialName("id")               val id: String,
    @SerialName("area_id")          val areaId: String,
    @SerialName("user_id")          val userId: String,
    @SerialName("gym_id")           val gymId: String? = null,
    @SerialName("reservation_date") val reservationDate: String,
    @SerialName("start_time")       val startTime: String,
    @SerialName("end_time")         val endTime: String,
    /** pending | confirmed | cancelled */
    @SerialName("status")           val status: String,
    @SerialName("notes")            val notes: String? = null,
    @SerialName("created_at")       val createdAt: String = "",
    @SerialName("gym_areas")         val area: GymArea? = null
)

// ---------------------------------------------------------------------------
// ProgramExercise — maps to "program_exercises" table
// Supabase join returns the exercise row under the "exercises" key.
// ---------------------------------------------------------------------------
@Serializable
data class ProgramExercise(
    @SerialName("id")                val id: String,
    @SerialName("program_id")        val programId: String,
    @SerialName("exercise_id")       val exerciseId: String,
    @SerialName("sets")              val sets: Int = 3,
    @SerialName("reps")              val reps: Int = 10,
    @SerialName("weight_suggestion") val weightSuggestion: Double? = null,
    @SerialName("rest_seconds")      val restSeconds: Int = 60,
    @SerialName("order_index")       val orderIndex: Int = 0,
    @SerialName("notes")             val notes: String? = null,
    @SerialName("exercises")         val exercise: Exercise? = null,
    @SerialName("created_at")        val createdAt: String = ""
)

// ---------------------------------------------------------------------------
// WorkoutSet — maps to "workout_sets" table
// ---------------------------------------------------------------------------
@Serializable
data class WorkoutSet(
    @SerialName("id")           val id: String,
    @SerialName("session_id")   val sessionId: String,
    @SerialName("exercise_id")  val exerciseId: String,
    @SerialName("user_id")      val userId: String,
    @SerialName("set_number")   val setNumber: Int = 1,
    @SerialName("reps")         val reps: Int = 0,
    @SerialName("weight")       val weight: Double? = null,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("rest_seconds") val restSeconds: Int? = null,
    @SerialName("notes")        val notes: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at")   val createdAt: String = ""
)

// ---------------------------------------------------------------------------
// Utility / derived data classes (not persisted directly)
// ---------------------------------------------------------------------------

data class SessionStats(
    val total: Int,
    val completed: Int,
    val thisWeek: Int
)

data class ProgressDataPoint(
    val date: String,
    val weight: Double
)

data class OneRmPercentage(
    val percentage: Int,
    val weight: Double
)

@Serializable
data class UserEvent(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val title: String = "",
    val note: String? = null,
    @SerialName("event_date") val eventDate: String = "",
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    val icon: String = "calendar",
    val color: String = "blue",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class UserEventInsert(
    @SerialName("user_id") val userId: String,
    val title: String,
    val note: String? = null,
    @SerialName("event_date") val eventDate: String,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    val icon: String = "calendar",
    val color: String = "blue"
)

@Serializable
data class UserEventUpdate(
    val title: String,
    val note: String? = null,
    @SerialName("event_date") val eventDate: String,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    val icon: String = "calendar",
    val color: String = "blue"
)

@Serializable
data class TrainingProgramIdRow(
    val id: String
)

@Serializable
data class GymEvent(
    val id: String,
    @SerialName("gym_id") val gymId: String,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    @SerialName("event_date") val eventDate: String,
    @SerialName("start_time") val startTime: String? = null,
    val capacity: Int? = null,
    val status: String = "active"
) {
    val formattedStartTime: String?
        get() = startTime?.take(5)
}

@Serializable
data class EventParticipant(
    val id: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    val status: String
)

@Serializable
data class EventParticipantInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("event_id") val eventId: String,
    val status: String = "registered"
)
