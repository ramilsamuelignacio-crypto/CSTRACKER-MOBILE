package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.Announcement
import com.example.data.model.AttendanceRecord
import com.example.data.model.CalendarEvent
import com.example.data.model.ClassResource
import com.example.data.model.DefaultCurriculum
import com.example.data.model.DirectMessage
import com.example.data.model.DirectoryEntry
import com.example.data.model.ExcuseLetter
import com.example.data.model.QrTokenEntry
import com.example.data.model.RoleInvite
import com.example.data.model.ScheduleSlot
import com.example.data.model.Subject
import com.example.data.model.User
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WarningRecord
import com.example.util.ParsedQrContent
import com.example.util.PickedAttachment
import com.example.util.QrUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CstrackerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val CLASS_GROUP = "CS-MAIN"
        const val BOOTSTRAP_ADMIN_EMAIL = "ramilsamuelignacio@gmail.com"
    }

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google.")

    private fun isBootstrappedAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.isEmailVerified && user.email.equals(BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true)
    }

    private fun <T> Flow<T>.logAndRethrow(op: OperationType, path: String, uid: String): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val toLog = firestoreError ?: (error as? Exception)
            if (toLog != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(toLog, op, path)
            }
            throw error
        }

    fun observeUserProfile(userId: String): Flow<UserProfile?> =
        db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .logAndRethrow(OperationType.GET, "users/$userId", userId)

    /**
     * Fetches the authenticated user's [User] data model and resolved [UserRole] from Firestore
     * immediately upon Firebase authentication. Checks `users/{uid}`, `staff_roles/{uid}`, and
     * `admins/{uid}` / bootstrapped admin status.
     */
    suspend fun fetchUserRoleOnAuth(userId: String = requireUserId()): Result<User> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("User must be signed in with Google."))
        val uid = currentUser.uid
        if (uid != userId) {
            return Result.failure(SecurityException("Cannot fetch role for another user."))
        }
        return try {
            val userSnap = db.collection("users").document(uid).get().await()
            val userFromDoc = if (userSnap.exists()) {
                userSnap.toObject(User::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            val profileFallback = if (userSnap.exists()) {
                userSnap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }

            val staffSnap = try {
                db.collection("staff_roles").document(uid).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            val staffRoleRaw = staffSnap?.takeIf { it.exists() }?.getString("role")
            val staffSubroleRaw = staffSnap?.takeIf { it.exists() }?.getString("subrole")

            val hasAdminDoc = try {
                db.collection("admins").document(uid).get().await().exists()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }

            val resolvedRole = when {
                !staffRoleRaw.isNullOrBlank() -> UserRole.fromKey(staffRoleRaw)
                userFromDoc != null && userFromDoc.role.isNotBlank() -> UserRole.fromKey(userFromDoc.role)
                isBootstrappedAdmin() || hasAdminDoc -> UserRole.ADMIN
                else -> UserRole.STUDENT
            }
            val resolvedSubrole = (staffSubroleRaw ?: userFromDoc?.subrole)
                ?.takeIf { it.isNotBlank() }
                ?.let { UserRole.fromKey(it).key }

            val baseUser = userFromDoc ?: profileFallback?.toUser() ?: User(
                id = uid,
                userId = uid,
                email = currentUser.email.orEmpty(),
                displayName = currentUser.displayName.orEmpty(),
            )
            Result.success(
                baseUser.copy(
                    id = uid,
                    userId = uid,
                    email = baseUser.email.ifBlank { profileFallback?.campusEmail.orEmpty() }
                        .ifBlank { currentUser.email.orEmpty() },
                    displayName = baseUser.displayName.ifBlank { profileFallback?.fullName.orEmpty() }
                        .ifBlank { currentUser.displayName.orEmpty() },
                    role = resolvedRole.key,
                    subrole = resolvedSubrole,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun fetchUserRole(userId: String = requireUserId()): Result<UserRole> =
        fetchUserRoleOnAuth(userId).map { it.resolvedRole }

    fun observeSubjects(): Flow<List<Subject>> = flow {
        val uid = requireUserId()
        db.collection("subjects")
            .whereEqualTo("classGroup", CLASS_GROUP)
            .snapshots()
            .map { snap ->
                snap.toObjects(Subject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.code }
            }
            .logAndRethrow(OperationType.LIST, "subjects", uid)
            .collect { emit(it) }
    }

    fun observeSchedules(): Flow<List<ScheduleSlot>> = flow {
        val uid = requireUserId()
        db.collection("schedules")
            .whereEqualTo("classGroup", CLASS_GROUP)
            .snapshots()
            .map { snap ->
                snap.toObjects(ScheduleSlot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareBy({ DefaultCurriculum.weekDays.indexOf(it.day) }, { it.startTime }))
            }
            .logAndRethrow(OperationType.LIST, "schedules", uid)
            .collect { emit(it) }
    }

    fun observeCalendarEvents(): Flow<List<CalendarEvent>> = flow {
        val uid = requireUserId()
        db.collection("calendar_events")
            .whereEqualTo("classGroup", CLASS_GROUP)
            .snapshots()
            .map { snap ->
                snap.toObjects(CalendarEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.date }
            }
            .logAndRethrow(OperationType.LIST, "calendar_events", uid)
            .collect { emit(it) }
    }

    fun observeAnnouncements(): Flow<List<Announcement>> = flow {
        val uid = requireUserId()
        db.collection("announcements")
            .whereEqualTo("classGroup", CLASS_GROUP)
            .snapshots()
            .map { snap ->
                snap.toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .logAndRethrow(OperationType.LIST, "announcements", uid)
            .collect { emit(it) }
    }

    fun observeResources(): Flow<List<ClassResource>> = flow {
        val uid = requireUserId()
        db.collection("resources")
            .whereEqualTo("classGroup", CLASS_GROUP)
            .snapshots()
            .map { snap ->
                snap.toObjects(ClassResource::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .logAndRethrow(OperationType.LIST, "resources", uid)
            .collect { emit(it) }
    }

    fun observeAttendance(isStaffView: Boolean, userId: String): Flow<List<AttendanceRecord>> {
        val query = if (isStaffView) {
            db.collection("attendance")
        } else {
            db.collection("attendance").whereEqualTo("studentUid", userId)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(AttendanceRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.classDate }
            }
            .logAndRethrow(OperationType.LIST, "attendance", userId)
    }

    fun observeExcuseLetters(isStaffView: Boolean, userId: String): Flow<List<ExcuseLetter>> {
        val query = if (isStaffView) {
            db.collection("excuse_letters")
        } else {
            db.collection("excuse_letters").whereEqualTo("studentUid", userId)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(ExcuseLetter::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .logAndRethrow(OperationType.LIST, "excuse_letters", userId)
    }

    fun observeWarnings(isStaffView: Boolean, userId: String): Flow<List<WarningRecord>> {
        val query = if (isStaffView) {
            db.collection("warnings")
        } else {
            db.collection("warnings").whereEqualTo("studentUid", userId)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(WarningRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .logAndRethrow(OperationType.LIST, "warnings", userId)
    }

    fun observeMessages(userId: String): Flow<List<DirectMessage>> =
        db.collection("messages")
            .whereArrayContains("participantUids", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(DirectMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .logAndRethrow(OperationType.LIST, "messages", userId)

    fun observeDirectory(isStaffView: Boolean, userId: String): Flow<List<DirectoryEntry>> {
        val query = if (isStaffView) {
            db.collection("directory")
        } else {
            db.collection("directory").whereEqualTo("staffVisible", true)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(DirectoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.displayName.lowercase() }
            }
            .logAndRethrow(OperationType.LIST, "directory", userId)
    }

    fun observeAllUsersForAdmin(userId: String): Flow<List<UserProfile>> =
        db.collection("users")
            .snapshots()
            .map { snap ->
                snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .logAndRethrow(OperationType.LIST, "users", userId)

    fun observeRoleInvitesForAdmin(userId: String): Flow<List<RoleInvite>> =
        db.collection("role_invites")
            .snapshots()
            .map { snap ->
                snap.toObjects(RoleInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.email }
            }
            .logAndRethrow(OperationType.LIST, "role_invites", userId)

    suspend fun fetchOwnRoleInvite(): RoleInvite? {
        val currentUser = auth.currentUser ?: return null
        val email = currentUser.email?.trim()?.lowercase()?.takeIf { currentUser.isEmailVerified && it.isNotEmpty() }
            ?: return null
        return try {
            val snap = db.collection("role_invites").document(email).get().await()
            if (snap.exists()) {
                snap.toObject(RoleInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveInitialProfile(
        studentId: String,
        firstName: String,
        middleName: String,
        lastName: String,
        campusEmail: String,
        guardianGmail: String,
        irregular: Boolean,
        enrolledSubjects: List<String>,
        requestedPortalRole: UserRole = UserRole.STUDENT,
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in with Google."))
        val path = "users/$uid"
        return try {
            val isAdminBoot = isBootstrappedAdmin()
            val invite = if (!isAdminBoot && requestedPortalRole != UserRole.STUDENT) {
                fetchOwnRoleInvite()
            } else {
                null
            }
            val matchedInviteRole = invite?.role?.takeIf { UserRole.fromKey(it) == requestedPortalRole }

            val assignedRole = when {
                isAdminBoot -> UserRole.ADMIN.key
                matchedInviteRole != null -> matchedInviteRole
                else -> UserRole.STUDENT.key
            }
            val pendingRequestedRole = when {
                isAdminBoot || matchedInviteRole != null -> null
                requestedPortalRole != UserRole.STUDENT -> requestedPortalRole.key
                else -> null
            }
            val staffVisible = assignedRole != UserRole.STUDENT.key
            val qrHash = QrUtils.generateStudentTokenHash(uid)
            val cleanMiddle = middleName.trim().takeIf { it.isNotEmpty() }
            val cleanGuardian = guardianGmail.trim().lowercase().takeIf { it.isNotEmpty() }
            val fullName = listOfNotNull(
                firstName.trim().takeIf { it.isNotEmpty() },
                cleanMiddle,
                lastName.trim().takeIf { it.isNotEmpty() }
            ).joinToString(" ")

            val userPayload = mapOf(
                "userId" to uid,
                "role" to assignedRole,
                "requestedRole" to pendingRequestedRole,
                "studentId" to studentId.trim(),
                "firstName" to firstName.trim(),
                "middleName" to cleanMiddle,
                "lastName" to lastName.trim(),
                "campusEmail" to campusEmail.trim().lowercase(),
                "guardianGmail" to cleanGuardian,
                "irregular" to irregular,
                "enrolledSubjects" to enrolledSubjects.take(12),
                "qrTokenHash" to qrHash,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }

            val dirPayload = mapOf(
                "userId" to uid,
                "displayName" to fullName,
                "studentIdLabel" to studentId.trim().takeIf { it.isNotEmpty() },
                "role" to assignedRole,
                "staffVisible" to staffVisible,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }

            val qrPayload = mapOf(
                "userId" to uid,
                "displayName" to fullName,
                "studentId" to studentId.trim(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )

            db.collection("users").document(uid).set(userPayload).await()
            db.collection("directory").document(uid).set(dirPayload).await()
            db.collection("qr_tokens").document(qrHash).set(qrPayload).await()

            if (assignedRole != UserRole.STUDENT.key) {
                db.collection("staff_roles").document(uid).set(
                    mapOf(
                        "userId" to uid,
                        "role" to assignedRole,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }

            if (assignedRole == UserRole.ADMIN.key) {
                db.collection("admins").document(uid).set(
                    mapOf(
                        "userId" to uid,
                        "grantedBy" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                seedDefaultCurriculumIfEmpty()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateOwnProfile(
        current: UserProfile,
        firstName: String,
        middleName: String,
        lastName: String,
        studentId: String,
        campusEmail: String,
        guardianGmail: String,
        irregular: Boolean,
        enrolledSubjects: List<String>,
        profilePhotoBase64: String? = current.profilePhotoBase64,
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in."))
        val path = "users/$uid"
        return try {
            val cleanMiddle = middleName.trim().takeIf { it.isNotEmpty() }
            val cleanGuardian = guardianGmail.trim().lowercase().takeIf { it.isNotEmpty() }
            val fullName = listOfNotNull(
                firstName.trim().takeIf { it.isNotEmpty() },
                cleanMiddle,
                lastName.trim().takeIf { it.isNotEmpty() }
            ).joinToString(" ")

            db.collection("users").document(uid).update(
                mapOf(
                    "firstName" to firstName.trim(),
                    "middleName" to cleanMiddle,
                    "lastName" to lastName.trim(),
                    "studentId" to studentId.trim(),
                    "campusEmail" to campusEmail.trim().lowercase(),
                    "guardianGmail" to cleanGuardian,
                    "irregular" to irregular,
                    "enrolledSubjects" to enrolledSubjects.take(12),
                    "profilePhotoBase64" to profilePhotoBase64,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            db.collection("directory").document(uid).update(
                mapOf(
                    "displayName" to fullName,
                    "studentIdLabel" to studentId.trim().takeIf { it.isNotEmpty() },
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            if (current.qrTokenHash.length == 64) {
                db.collection("qr_tokens").document(current.qrTokenHash).update(
                    mapOf(
                        "displayName" to fullName,
                        "studentId" to studentId.trim(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun seedDefaultCurriculumIfEmpty(): Result<Unit> {
        return try {
            requireUserId()
            val existingSubjects = db.collection("subjects")
                .whereEqualTo("classGroup", CLASS_GROUP)
                .limit(1)
                .get()
                .await()
            if (existingSubjects.isEmpty) {
                val batch = db.batch()
                DefaultCurriculum.defaultSubjects.forEach { subj ->
                    val ref = db.collection("subjects").document(subj.code)
                    batch.set(
                        ref,
                        mapOf(
                            "classGroup" to CLASS_GROUP,
                            "code" to subj.code,
                            "name" to subj.name,
                            "units" to subj.units,
                            "professor" to subj.professor,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                }
                batch.commit().await()
            }

            val existingSchedules = db.collection("schedules")
                .whereEqualTo("classGroup", CLASS_GROUP)
                .limit(1)
                .get()
                .await()
            if (existingSchedules.isEmpty) {
                val batch = db.batch()
                DefaultCurriculum.defaultSchedules.forEach { slot ->
                    val ref = db.collection("schedules").document(slot.id)
                    batch.set(
                        ref,
                        mapOf(
                            "classGroup" to CLASS_GROUP,
                            "day" to slot.day,
                            "startTime" to slot.startTime,
                            "endTime" to slot.endTime,
                            "subjectCode" to slot.subjectCode,
                            "room" to slot.room,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ).filterValues { it != null }
                    )
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "subjects_schedules")
            Result.failure(e)
        }
    }

    suspend fun addScheduleSlot(
        day: String,
        startTime: String,
        endTime: String,
        subjectCode: String,
        room: String,
    ): Result<Unit> {
        val docId = "sched_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        return try {
            requireUserId()
            val payload = mapOf(
                "classGroup" to CLASS_GROUP,
                "day" to day,
                "startTime" to startTime.trim(),
                "endTime" to endTime.trim(),
                "subjectCode" to subjectCode.trim(),
                "room" to room.trim().takeIf { it.isNotEmpty() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }
            db.collection("schedules").document(docId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "schedules/$docId")
            Result.failure(e)
        }
    }

    suspend fun deleteScheduleSlot(scheduleId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("schedules").document(scheduleId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "schedules/$scheduleId")
            Result.failure(e)
        }
    }

    suspend fun saveCalendarEvent(
        existing: CalendarEvent?,
        date: String,
        type: String,
        title: String,
        description: String,
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in."))
        val docId = existing?.id?.takeIf { it.isNotBlank() }
            ?: "evt_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        return try {
            val cleanDesc = description.trim().takeIf { it.isNotEmpty() }
            if (existing == null) {
                val payload = mapOf(
                    "classGroup" to CLASS_GROUP,
                    "date" to date.trim(),
                    "type" to type,
                    "title" to title.trim(),
                    "description" to cleanDesc,
                    "authorUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                db.collection("calendar_events").document(docId).set(payload).await()
            } else {
                db.collection("calendar_events").document(docId).update(
                    mapOf(
                        "date" to date.trim(),
                        "type" to type,
                        "title" to title.trim(),
                        "description" to cleanDesc,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "calendar_events/$docId")
            Result.failure(e)
        }
    }

    suspend fun deleteCalendarEvent(eventId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("calendar_events").document(eventId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "calendar_events/$eventId")
            Result.failure(e)
        }
    }

    suspend fun postAnnouncement(
        title: String,
        body: String,
        subjectCode: String?,
        subjectName: String?,
        authorName: String,
        attachment: PickedAttachment?,
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in."))
        val docId = "ann_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        return try {
            val payload = mapOf(
                "classGroup" to CLASS_GROUP,
                "title" to title.trim(),
                "body" to body.trim(),
                "subjectCode" to subjectCode?.takeIf { it.isNotBlank() },
                "subjectName" to subjectName?.takeIf { it.isNotBlank() },
                "authorUid" to uid,
                "authorName" to authorName.trim(),
                "attachmentName" to attachment?.name,
                "attachmentType" to attachment?.mimeType,
                "attachmentBase64" to attachment?.base64,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }
            db.collection("announcements").document(docId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "announcements/$docId")
            Result.failure(e)
        }
    }

    suspend fun deleteAnnouncement(announcementId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("announcements").document(announcementId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "announcements/$announcementId")
            Result.failure(e)
        }
    }

    suspend fun scanAndMarkAttendance(
        rawQr: String,
        subjectCode: String,
        subjectName: String?,
        classDate: String,
        status: String,
        verifierName: String,
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in."))
        val tokenHash = QrUtils.extractTokenHash(rawQr)
            ?: return Result.failure(IllegalArgumentException("Invalid QR code format."))
        return try {
            val qrSnap = db.collection("qr_tokens").document(tokenHash).get().await()
            if (!qrSnap.exists()) {
                return Result.failure(IllegalArgumentException("Student QR code not recognized."))
            }
            val qrEntry = qrSnap.toObject(QrTokenEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Could not read student QR record."))
            val recordId = "${qrEntry.userId}_${subjectCode}_$classDate"
            val docRef = db.collection("attendance").document(recordId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "status" to status,
                        "verifiedBy" to uid,
                        "verifiedByName" to verifierName,
                        "checkInMethod" to "professor_qr_scan",
                        "qrPayload" to rawQr.trim().take(250),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                val payload = mapOf(
                    "studentUid" to qrEntry.userId,
                    "studentId" to qrEntry.studentId,
                    "studentName" to qrEntry.displayName,
                    "subjectCode" to subjectCode,
                    "subjectName" to subjectName,
                    "classDate" to classDate,
                    "status" to status,
                    "verifiedBy" to uid,
                    "verifiedByName" to verifierName,
                    "checkInMethod" to "professor_qr_scan",
                    "qrPayload" to rawQr.trim().take(250),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success("${qrEntry.displayName} marked $status.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "attendance")
            Result.failure(e)
        }
    }

    suspend fun recordStudentQrAttendance(
        profile: UserProfile,
        rawQr: String,
        fallbackSubject: Subject?,
        defaultDate: String,
        validSubjects: List<Subject>,
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in."))
        val validCodes = validSubjects.map { it.code.uppercase() }.toSet()
        val parsed = QrUtils.parseQrString(rawQr, validCodes)
            ?: return Result.failure(
                IllegalArgumentException("Could not parse QR code. Scan a valid class attendance QR code.")
            )

        if (parsed is ParsedQrContent.StudentPass) {
            return Result.failure(
                IllegalArgumentException("That is a personal student pass QR. Scan a class attendance QR code instead.")
            )
        }

        val checkIn = parsed as ParsedQrContent.ClassCheckIn
        val subjectCode = checkIn.subjectCode
        val matchedSubject = validSubjects.firstOrNull { it.code.equals(subjectCode, ignoreCase = true) }
            ?: fallbackSubject?.takeIf { it.code.equals(subjectCode, ignoreCase = true) }
        val subjectName = matchedSubject?.name
        val classDate = checkIn.classDate ?: defaultDate
        val status = checkIn.status ?: "Present"

        if (!Regex("^[A-Z0-9]{2,20}$").matches(subjectCode)) {
            return Result.failure(IllegalArgumentException("Invalid subject code in QR: $subjectCode"))
        }
        if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(classDate)) {
            return Result.failure(IllegalArgumentException("Invalid date in QR: $classDate"))
        }

        val recordId = "${uid}_${subjectCode}_$classDate"
        val docRef = db.collection("attendance").document(recordId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val existingStatus = existing.getString("status") ?: status
                return Result.success("Already checked in for $subjectCode on $classDate ($existingStatus).")
            }
            val payload = mapOf(
                "studentUid" to uid,
                "studentId" to profile.studentId,
                "studentName" to profile.fullName,
                "subjectCode" to subjectCode,
                "subjectName" to subjectName,
                "classDate" to classDate,
                "status" to status,
                "verifiedBy" to uid,
                "verifiedByName" to "${profile.fullName} (QR Self Check-In)",
                "checkInMethod" to "camera_qr_self",
                "qrPayload" to rawQr.trim().take(250),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success("Checked in ($status) for $subjectCode on $classDate.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "attendance/$recordId")
            Result.failure(e)
        }
    }

    suspend fun uploadClassResource(
        title: String,
        description: String,
        subjectCode: String?,
        subjectName: String?,
        authorName: String,
        attachment: PickedAttachment,
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in."))
        val docId = "res_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        return try {
            val payload = mapOf(
                "classGroup" to CLASS_GROUP,
                "title" to title.trim(),
                "description" to description.trim().takeIf { it.isNotEmpty() },
                "subjectCode" to subjectCode?.takeIf { it.isNotBlank() },
                "subjectName" to subjectName?.takeIf { it.isNotBlank() },
                "authorUid" to uid,
                "authorName" to authorName.trim(),
                "fileName" to attachment.name,
                "fileSizeBytes" to attachment.sizeBytes,
                "fileBase64" to attachment.base64,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }
            db.collection("resources").document(docId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "resources/$docId")
            Result.failure(e)
        }
    }

    suspend fun deleteResource(resourceId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("resources").document(resourceId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "resources/$resourceId")
            Result.failure(e)
        }
    }

    suspend fun submitExcuseLetter(
        profile: UserProfile,
        subjectCode: String,
        subjectName: String?,
        reason: String,
        attachment: PickedAttachment?,
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in."))
        val docId = "exc_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        return try {
            val payload = mapOf(
                "studentUid" to uid,
                "studentId" to profile.studentId,
                "studentName" to profile.fullName,
                "subjectCode" to subjectCode,
                "subjectName" to subjectName,
                "reason" to reason.trim(),
                "attachmentName" to attachment?.name,
                "attachmentType" to attachment?.mimeType,
                "attachmentBase64" to attachment?.base64,
                "status" to "Pending",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }
            db.collection("excuse_letters").document(docId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "excuse_letters/$docId")
            Result.failure(e)
        }
    }

    suspend fun reviewExcuseLetter(
        letterId: String,
        status: String,
        reviewNote: String,
        reviewerName: String,
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in."))
        return try {
            db.collection("excuse_letters").document(letterId).update(
                mapOf(
                    "status" to status,
                    "reviewedBy" to uid,
                    "reviewerName" to reviewerName,
                    "reviewNote" to reviewNote.trim().takeIf { it.isNotEmpty() },
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "excuse_letters/$letterId")
            Result.failure(e)
        }
    }

    suspend fun issueWarning(
        studentUid: String,
        studentName: String,
        note: String,
        issuerName: String,
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in."))
        val docId = "wrn_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        return try {
            val payload = mapOf(
                "studentUid" to studentUid,
                "studentName" to studentName,
                "note" to note.trim(),
                "createdBy" to uid,
                "createdByName" to issuerName,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            db.collection("warnings").document(docId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "warnings/$docId")
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        senderName: String,
        receiverUid: String,
        receiverName: String,
        message: String,
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in."))
        val docId = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        return try {
            val payload = mapOf(
                "senderUid" to uid,
                "senderName" to senderName,
                "receiverUid" to receiverUid,
                "receiverName" to receiverName,
                "participantUids" to listOf(uid, receiverUid),
                "message" to message.trim(),
                "read" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            db.collection("messages").document(docId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "messages/$docId")
            Result.failure(e)
        }
    }

    suspend fun markMessageRead(messageId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("messages").document(messageId).update(
                mapOf(
                    "read" to true,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "messages/$messageId")
            Result.failure(e)
        }
    }

    suspend fun updateUserRoleAsAdmin(
        targetUid: String,
        newRole: String,
        newSubrole: String?,
    ): Result<Unit> {
        val adminUid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in."))
        return try {
            val effectiveSubrole = if (newRole == "student") newSubrole else null
            val staffVisible = newRole != "student" || effectiveSubrole != null

            db.collection("users").document(targetUid).update(
                mapOf(
                    "role" to newRole,
                    "subrole" to effectiveSubrole,
                    "requestedRole" to null,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            db.collection("directory").document(targetUid).update(
                mapOf(
                    "role" to newRole,
                    "subrole" to effectiveSubrole,
                    "staffVisible" to staffVisible,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            val staffRef = db.collection("staff_roles").document(targetUid)
            if (staffVisible) {
                val staffDoc = staffRef.get().await()
                if (staffDoc.exists()) {
                    staffRef.update(
                        mapOf(
                            "role" to newRole,
                            "subrole" to effectiveSubrole,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                } else {
                    staffRef.set(
                        mapOf(
                            "userId" to targetUid,
                            "role" to newRole,
                            "subrole" to effectiveSubrole,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ).filterValues { it != null }
                    ).await()
                }
            } else {
                staffRef.delete().await()
            }

            val adminRef = db.collection("admins").document(targetUid)
            if (newRole == "admin" || effectiveSubrole == "admin") {
                val existingAdmin = adminRef.get().await()
                if (!existingAdmin.exists()) {
                    adminRef.set(
                        mapOf(
                            "userId" to targetUid,
                            "grantedBy" to adminUid,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                }
            } else if (targetUid != adminUid) {
                adminRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$targetUid")
            Result.failure(e)
        }
    }

    suspend fun declineRoleRequestAsAdmin(targetUid: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("users").document(targetUid).update(
                mapOf(
                    "requestedRole" to null,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$targetUid")
            Result.failure(e)
        }
    }

    suspend fun saveRoleInvite(
        email: String,
        role: String,
        note: String,
    ): Result<Unit> {
        val adminUid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in."))
        val cleanEmail = email.trim().lowercase()
        val cleanNote = note.trim().takeIf { it.isNotEmpty() }
        return try {
            val docRef = db.collection("role_invites").document(cleanEmail)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "role" to role,
                        "invitedBy" to adminUid,
                        "note" to cleanNote,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                val payload = mapOf(
                    "email" to cleanEmail,
                    "role" to role,
                    "invitedBy" to adminUid,
                    "note" to cleanNote,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "role_invites/$cleanEmail")
            Result.failure(e)
        }
    }

    suspend fun deleteRoleInvite(email: String): Result<Unit> {
        val cleanEmail = email.trim().lowercase()
        return try {
            requireUserId()
            db.collection("role_invites").document(cleanEmail).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "role_invites/$cleanEmail")
            Result.failure(e)
        }
    }
}
