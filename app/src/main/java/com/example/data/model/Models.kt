package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class UserRole(val key: String, val label: String, val rank: Int) {
    ADMIN("admin", "Admin", 1),
    PROFESSOR("professor", "Professor", 2),
    MODERATOR("moderator", "Moderator", 3),
    CLASS_REP("class_rep", "Class rep", 4),
    STUDENT("student", "Student", 5);

    val canViewCore: Boolean
        get() = true

    val canScanQrAttendance: Boolean
        get() = this == PROFESSOR

    val canPostAnnouncementsAndEvents: Boolean
        get() = this in setOf(ADMIN, PROFESSOR, MODERATOR, CLASS_REP)

    val canUploadClassFiles: Boolean
        get() = this in setOf(ADMIN, PROFESSOR)

    val canReviewExcusesAndWarn: Boolean
        get() = this in setOf(ADMIN, PROFESSOR, MODERATOR)

    val canEditScheduleAndManageUsers: Boolean
        get() = this == ADMIN

    companion object {
        fun fromKey(raw: String?): UserRole = when (raw?.trim()?.lowercase()) {
            "admin" -> ADMIN
            "professor" -> PROFESSOR
            "moderator" -> MODERATOR
            "class_rep", "classrep", "class rep" -> CLASS_REP
            "student" -> STUDENT
            else -> STUDENT
        }

        val rankedEntries: List<UserRole>
            get() = entries.sortedBy { it.rank }
    }
}

@Keep
@IgnoreExtraProperties
data class User(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = UserRole.STUDENT.key,
    val subrole: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val resolvedRole: UserRole
        get() = UserRole.fromKey(role)
}

enum class AttendanceStatus(val label: String) {
    PRESENT("Present"),
    LATE("Late"),
    ABSENT("Absent");

    companion object {
        fun fromLabel(raw: String?): AttendanceStatus =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: PRESENT
    }
}

enum class EventType(val label: String) {
    QUIZ("Quiz"),
    EXAM("Exam"),
    HOLIDAY("Holiday"),
    DEADLINE("Deadline"),
    CLASS("Class"),
    REMINDER("Reminder");

    companion object {
        fun fromLabel(raw: String?): EventType =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: CLASS
    }
}

enum class ExcuseStatus(val label: String) {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected");

    companion object {
        fun fromLabel(raw: String?): ExcuseStatus =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "student",
    val subrole: String? = null,
    val requestedRole: String? = null,
    val studentId: String = "",
    val firstName: String = "",
    val middleName: String? = null,
    val lastName: String = "",
    val campusEmail: String = "",
    val guardianGmail: String? = null,
    val irregular: Boolean = false,
    val enrolledSubjects: List<String> = emptyList(),
    val qrTokenHash: String = "",
    val profilePhotoBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val fullName: String
        get() = listOfNotNull(
            firstName.takeIf { it.isNotBlank() },
            middleName?.takeIf { it.isNotBlank() },
            lastName.takeIf { it.isNotBlank() }
        ).joinToString(" ").ifBlank { "Student" }

    val initials: String
        get() {
            val f = firstName.firstOrNull()?.uppercaseChar() ?: 'C'
            val l = lastName.firstOrNull()?.uppercaseChar() ?: 'S'
            return "$f$l"
        }

    fun availableRoles(): List<UserRole> {
        val primary = UserRole.fromKey(role)
        val set = linkedSetOf(primary)
        if (primary == UserRole.STUDENT && !subrole.isNullOrBlank()) {
            set.add(UserRole.fromKey(subrole))
        }
        if (primary in listOf(UserRole.ADMIN, UserRole.CLASS_REP, UserRole.MODERATOR)) {
            set.add(UserRole.STUDENT)
        }
        return set.toList()
    }

    fun toUser(): User = User(
        id = id,
        userId = userId,
        email = campusEmail,
        displayName = fullName,
        role = UserRole.fromKey(role).key,
        subrole = subrole?.let { UserRole.fromKey(it).key },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

@Keep
@IgnoreExtraProperties
data class RoleInvite(
    @DocumentId val id: String = "",
    val email: String = "",
    val role: String = UserRole.STUDENT.key,
    val subrole: String? = null,
    val invitedBy: String = "",
    val note: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class DirectoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val studentIdLabel: String? = null,
    val role: String = "student",
    val subrole: String? = null,
    val staffVisible: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class QrTokenEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val studentId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class Subject(
    @DocumentId val id: String = "",
    val classGroup: String = "CS-MAIN",
    val code: String = "",
    val name: String = "",
    val units: Long = 3L,
    val professor: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ScheduleSlot(
    @DocumentId val id: String = "",
    val classGroup: String = "CS-MAIN",
    val day: String = "Tuesday",
    val startTime: String = "07:00",
    val endTime: String = "09:00",
    val subjectCode: String = "",
    val room: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CalendarEvent(
    @DocumentId val id: String = "",
    val classGroup: String = "CS-MAIN",
    val date: String = "",
    val type: String = "Class",
    val title: String = "",
    val description: String? = null,
    val authorUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val classGroup: String = "CS-MAIN",
    val title: String = "",
    val body: String = "",
    val subjectCode: String? = null,
    val subjectName: String? = null,
    val authorUid: String = "",
    val authorName: String = "",
    val attachmentName: String? = null,
    val attachmentType: String? = null,
    val attachmentBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val studentUid: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val subjectCode: String = "",
    val subjectName: String? = null,
    val classDate: String = "",
    val status: String = "Present",
    val verifiedBy: String = "",
    val verifiedByName: String = "",
    val checkInMethod: String? = null,
    val qrPayload: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ClassResource(
    @DocumentId val id: String = "",
    val classGroup: String = "CS-MAIN",
    val title: String = "",
    val description: String? = null,
    val subjectCode: String? = null,
    val subjectName: String? = null,
    val authorUid: String = "",
    val authorName: String = "",
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val fileBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ExcuseLetter(
    @DocumentId val id: String = "",
    val studentUid: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val subjectCode: String = "",
    val subjectName: String? = null,
    val reason: String = "",
    val attachmentName: String? = null,
    val attachmentType: String? = null,
    val attachmentBase64: String? = null,
    val status: String = "Pending",
    val reviewedBy: String? = null,
    val reviewerName: String? = null,
    val reviewNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class WarningRecord(
    @DocumentId val id: String = "",
    val studentUid: String = "",
    val studentName: String = "",
    val note: String = "",
    val createdBy: String = "",
    val createdByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class DirectMessage(
    @DocumentId val id: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val receiverUid: String = "",
    val receiverName: String = "",
    val participantUids: List<String> = emptyList(),
    val message: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

object DefaultCurriculum {
    val weekDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    val defaultSubjects = listOf(
        Subject(id = "COSC75", code = "COSC75", name = "SOFTWARE ENGINEERING II", units = 3, professor = "ARMIN ARAGONCILLO"),
        Subject(id = "MATH3", code = "MATH3", name = "LINEAR ALGEBRA", units = 3, professor = "MA. CHRISTINE JOYCE RELLOSA"),
        Subject(id = "COSC80", code = "COSC80", name = "OPERATING SYSTEMS", units = 3, professor = "STEVEN GOHAN MATRO"),
        Subject(id = "COSC85", code = "COSC85", name = "NETWORKS AND COMMUNICATION", units = 3, professor = "MICHAEL MADSEN VENTURA"),
        Subject(id = "COSC101", code = "COSC101", name = "CS ELECTIVE 1", units = 3, professor = "CHRISTOPHER HERNANDEZ"),
        Subject(id = "DCIT26", code = "DCIT26", name = "APPLICATIONS DEVELOPMENT AND EMERGING TECHNOLOGIES", units = 3, professor = "JAMES RAPHAEL TOLEDO"),
        Subject(id = "DCIT65", code = "DCIT65", name = "SOCIAL AND PROFESSIONAL ISSUES", units = 3, professor = "EMILIO DEL ROSARIO III"),
    )

    val defaultSchedules = listOf(
        ScheduleSlot(id = "sched_01", day = "Tuesday", startTime = "07:00", endTime = "09:00", subjectCode = "DCIT65", room = "119"),
        ScheduleSlot(id = "sched_02", day = "Tuesday", startTime = "09:00", endTime = "11:00", subjectCode = "COSC75", room = "107"),
        ScheduleSlot(id = "sched_03", day = "Tuesday", startTime = "13:00", endTime = "15:00", subjectCode = "COSC75", room = "109"),
        ScheduleSlot(id = "sched_04", day = "Tuesday", startTime = "15:00", endTime = "17:00", subjectCode = "MATH3", room = "206"),
        ScheduleSlot(id = "sched_05", day = "Tuesday", startTime = "17:00", endTime = "19:00", subjectCode = "DCIT26", room = "107"),
        ScheduleSlot(id = "sched_06", day = "Wednesday", startTime = "11:00", endTime = "13:00", subjectCode = "COSC85", room = "207"),
        ScheduleSlot(id = "sched_07", day = "Wednesday", startTime = "13:00", endTime = "15:00", subjectCode = "COSC80", room = "107"),
        ScheduleSlot(id = "sched_08", day = "Thursday", startTime = "09:00", endTime = "11:00", subjectCode = "COSC85", room = "109"),
        ScheduleSlot(id = "sched_09", day = "Thursday", startTime = "11:00", endTime = "13:00", subjectCode = "COSC80", room = "217"),
        ScheduleSlot(id = "sched_10", day = "Thursday", startTime = "15:00", endTime = "17:00", subjectCode = "DCIT26", room = "217"),
        ScheduleSlot(id = "sched_11", day = "Friday", startTime = "09:00", endTime = "11:00", subjectCode = "COSC101", room = "107"),
        ScheduleSlot(id = "sched_12", day = "Friday", startTime = "11:00", endTime = "13:00", subjectCode = "COSC101", room = "107"),
    )
}
