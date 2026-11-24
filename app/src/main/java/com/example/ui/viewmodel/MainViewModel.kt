package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.CstrackerRepository
import com.example.data.firebase.unwrapFirestoreMessage
import com.example.data.model.Announcement
import com.example.data.model.AttendanceRecord
import com.example.data.model.CalendarEvent
import com.example.data.model.ClassResource
import com.example.data.model.DefaultCurriculum
import com.example.data.model.DirectMessage
import com.example.data.model.DirectoryEntry
import com.example.data.model.ExcuseLetter
import com.example.data.model.RoleInvite
import com.example.data.model.ScheduleSlot
import com.example.data.model.Subject
import com.example.data.model.UiState
import com.example.data.model.User
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WarningRecord
import com.example.util.InputValidators
import com.example.util.PickedAttachment
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: CstrackerRepository,
    val currentUserId: String,
) : ViewModel() {

    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessages = _toastMessages.asSharedFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _selectedActiveRole = MutableStateFlow<UserRole?>(null)

    private val _authenticatedUserState = MutableStateFlow<UiState<User>>(UiState.Loading)
    val authenticatedUserState: StateFlow<UiState<User>> = _authenticatedUserState.asStateFlow()

    private val _ownRoleInvite = MutableStateFlow<RoleInvite?>(null)
    val ownRoleInvite: StateFlow<RoleInvite?> = _ownRoleInvite.asStateFlow()

    init {
        refreshAuthenticatedUserRole()
    }

    fun refreshAuthenticatedUserRole() {
        viewModelScope.launch {
            _authenticatedUserState.value = UiState.Loading
            _ownRoleInvite.value = repository.fetchOwnRoleInvite()
            repository.fetchUserRoleOnAuth(currentUserId)
                .onSuccess { fetchedUser ->
                    _authenticatedUserState.value = UiState.Success(fetchedUser)
                }
                .onFailure { error ->
                    _authenticatedUserState.value = UiState.Error(
                        unwrapFirestoreMessage(error, "Failed to fetch your assigned role.")
                    )
                }
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(
        fallbackMessage: String,
        initial: UiState<T> = UiState.Loading,
    ): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(UiState.Error(unwrapFirestoreMessage(error, fallbackMessage)))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = initial,
            )

    val profileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .toUiStateFlow("Failed to load your profile.")

    val activeRole: StateFlow<UserRole> = combine(
        profileState,
        authenticatedUserState,
        _selectedActiveRole,
    ) { state, authUserState, override ->
        val profile = (state as? UiState.Success)?.data
        val fetchedUser = (authUserState as? UiState.Success)?.data
        val available = profile?.availableRoles() ?: listOfNotNull(fetchedUser?.resolvedRole, UserRole.STUDENT)
        when {
            override != null && override in available -> override
            profile != null -> UserRole.fromKey(profile.role)
            fetchedUser != null -> fetchedUser.resolvedRole
            else -> UserRole.STUDENT
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UserRole.STUDENT,
    )

    val subjectsState: StateFlow<UiState<List<Subject>>> =
        repository.observeSubjects()
            .map { list -> list.ifEmpty { DefaultCurriculum.defaultSubjects } }
            .toUiStateFlow("Failed to load subjects.")

    val schedulesState: StateFlow<UiState<List<ScheduleSlot>>> =
        repository.observeSchedules()
            .map { list -> list.ifEmpty { DefaultCurriculum.defaultSchedules } }
            .toUiStateFlow("Failed to load class schedule.")

    val calendarEventsState: StateFlow<UiState<List<CalendarEvent>>> =
        repository.observeCalendarEvents()
            .toUiStateFlow("Failed to load calendar events.")

    val announcementsState: StateFlow<UiState<List<Announcement>>> =
        repository.observeAnnouncements()
            .toUiStateFlow("Failed to load announcements.")

    val resourcesState: StateFlow<UiState<List<ClassResource>>> =
        repository.observeResources()
            .toUiStateFlow("Failed to load class files.")

    private val staffRoleFlow: Flow<Boolean> =
        combine(profileState, activeRole) { state, role ->
            val profile = (state as? UiState.Success)?.data ?: return@combine null
            val hasStaffAccess = UserRole.fromKey(profile.role) != UserRole.STUDENT ||
                !profile.subrole.isNullOrBlank()
            hasStaffAccess && role != UserRole.STUDENT
        }.distinctUntilChanged().flatMapLatest { value ->
            if (value == null) flowOf(false) else flowOf(value)
        }

    val attendanceState: StateFlow<UiState<List<AttendanceRecord>>> =
        combine(profileState, activeRole) { state, role ->
            val profile = (state as? UiState.Success)?.data
            if (profile == null) null else role.canReviewExcusesAndWarn
        }.distinctUntilChanged().flatMapLatest { isReviewer ->
            if (isReviewer == null) flowOf(emptyList())
            else repository.observeAttendance(isReviewer, currentUserId)
        }.toUiStateFlow("Failed to load attendance records.")

    val excuseLettersState: StateFlow<UiState<List<ExcuseLetter>>> =
        combine(profileState, activeRole) { state, role ->
            val profile = (state as? UiState.Success)?.data
            if (profile == null) null else role.canReviewExcusesAndWarn
        }.distinctUntilChanged().flatMapLatest { isReviewer ->
            if (isReviewer == null) flowOf(emptyList())
            else repository.observeExcuseLetters(isReviewer, currentUserId)
        }.toUiStateFlow("Failed to load excuse letters.")

    val warningsState: StateFlow<UiState<List<WarningRecord>>> =
        combine(profileState, activeRole) { state, role ->
            val profile = (state as? UiState.Success)?.data
            if (profile == null) null else role.canReviewExcusesAndWarn
        }.distinctUntilChanged().flatMapLatest { isReviewer ->
            if (isReviewer == null) flowOf(emptyList())
            else repository.observeWarnings(isReviewer, currentUserId)
        }.toUiStateFlow("Failed to load warnings.")

    val messagesState: StateFlow<UiState<List<DirectMessage>>> =
        profileState.flatMapLatest { state ->
            val profile = (state as? UiState.Success)?.data
            if (profile == null) flowOf(emptyList())
            else repository.observeMessages(currentUserId)
        }.toUiStateFlow("Failed to load messages.")

    val directoryState: StateFlow<UiState<List<DirectoryEntry>>> =
        combine(profileState, activeRole) { state, role ->
            val profile = (state as? UiState.Success)?.data
            if (profile == null) null else role.canPostAnnouncementsAndEvents
        }.distinctUntilChanged().flatMapLatest { isStaff ->
            if (isStaff == null) flowOf(emptyList())
            else repository.observeDirectory(isStaff, currentUserId)
        }.toUiStateFlow("Failed to load directory.")

    val adminUsersState: StateFlow<UiState<List<UserProfile>>> =
        combine(profileState, activeRole) { state, role ->
            val profile = (state as? UiState.Success)?.data
            profile != null && UserRole.fromKey(profile.role) == UserRole.ADMIN && role.canEditScheduleAndManageUsers
        }.distinctUntilChanged().flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf(emptyList())
            else repository.observeAllUsersForAdmin(currentUserId)
        }.toUiStateFlow("Failed to load user accounts.")

    val roleInvitesState: StateFlow<UiState<List<RoleInvite>>> =
        combine(profileState, activeRole) { state, role ->
            val profile = (state as? UiState.Success)?.data
            profile != null && UserRole.fromKey(profile.role) == UserRole.ADMIN && role.canEditScheduleAndManageUsers
        }.distinctUntilChanged().flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf(emptyList())
            else repository.observeRoleInvitesForAdmin(currentUserId)
        }.toUiStateFlow("Failed to load pre-approved role invites.")

    fun switchActiveRole(role: UserRole) {
        _selectedActiveRole.value = role
        emitToast("Switched to ${role.label} view.")
    }

    fun emitToast(message: String) {
        _toastMessages.tryEmit(message)
    }

    private inline fun runAction(
        successMessage: String? = null,
        crossinline block: suspend () -> Result<Unit>,
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val result = block()
                result.onSuccess {
                    if (successMessage != null) emitToast(successMessage)
                }.onFailure { err ->
                    emitToast(unwrapFirestoreMessage(err, "Action failed."))
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun completeInitialProfile(
        portalRole: UserRole,
        studentId: String,
        firstName: String,
        middleName: String,
        lastName: String,
        campusEmail: String,
        guardianGmail: String,
        irregular: Boolean,
        subjectsRaw: String,
    ) {
        val isStaffPortal = portalRole in listOf(UserRole.ADMIN, UserRole.PROFESSOR, UserRole.MODERATOR)
        val idErr = if (portalRole in listOf(UserRole.ADMIN, UserRole.PROFESSOR)) {
            InputValidators.validateStaffId(studentId)
        } else {
            InputValidators.validateStudentId(studentId)
        }
        val emailErr = if (isStaffPortal) {
            InputValidators.validateEmail("Institutional or Google email", campusEmail)
        } else {
            InputValidators.validateCampusEmail(campusEmail)
        }
        val err = idErr
            ?: InputValidators.validateName("First name", firstName)
            ?: InputValidators.validateName("Middle name", middleName, optional = true)
            ?: InputValidators.validateName("Last name", lastName)
            ?: emailErr
            ?: InputValidators.validateOptionalEmail("Guardian email", guardianGmail)
        if (err != null) {
            emitToast(err)
            return
        }
        val (subjects, subjErr) = if (irregular) InputValidators.parseSubjects(subjectsRaw) else emptyList<String>() to null
        if (subjErr != null) {
            emitToast(subjErr)
            return
        }
        runAction("Welcome to CSTRACKER!") {
            repository.saveInitialProfile(
                studentId = studentId,
                firstName = firstName,
                middleName = middleName,
                lastName = lastName,
                campusEmail = campusEmail,
                guardianGmail = guardianGmail,
                irregular = irregular,
                enrolledSubjects = subjects,
                requestedPortalRole = portalRole,
            ).also { res ->
                if (res.isSuccess) refreshAuthenticatedUserRole()
            }
        }
    }

    fun updateProfile(
        current: UserProfile,
        firstName: String,
        middleName: String,
        lastName: String,
        studentId: String,
        campusEmail: String,
        guardianGmail: String,
        irregular: Boolean,
        subjectsRaw: String,
    ) {
        val err = InputValidators.validateStudentId(studentId)
            ?: InputValidators.validateName("First name", firstName)
            ?: InputValidators.validateName("Middle name", middleName, optional = true)
            ?: InputValidators.validateName("Last name", lastName)
            ?: InputValidators.validateCampusEmail(campusEmail)
            ?: InputValidators.validateOptionalEmail("Guardian email", guardianGmail)
        if (err != null) {
            emitToast(err)
            return
        }
        val (subjects, subjErr) = if (irregular) InputValidators.parseSubjects(subjectsRaw) else emptyList<String>() to null
        if (subjErr != null) {
            emitToast(subjErr)
            return
        }
        runAction("Profile updated.") {
            repository.updateOwnProfile(
                current = current,
                firstName = firstName,
                middleName = middleName,
                lastName = lastName,
                studentId = studentId,
                campusEmail = campusEmail,
                guardianGmail = guardianGmail,
                irregular = irregular,
                enrolledSubjects = subjects,
            )
        }
    }

    fun updateProfilePhoto(current: UserProfile, photoBase64: String?) {
        runAction(if (photoBase64 == null) "Profile picture removed." else "Profile picture updated.") {
            repository.updateOwnProfile(
                current = current,
                firstName = current.firstName,
                middleName = current.middleName.orEmpty(),
                lastName = current.lastName,
                studentId = current.studentId,
                campusEmail = current.campusEmail,
                guardianGmail = current.guardianGmail.orEmpty(),
                irregular = current.irregular,
                enrolledSubjects = current.enrolledSubjects,
                profilePhotoBase64 = photoBase64,
            )
        }
    }

    fun addScheduleSlot(day: String, subjectCode: String, startTime: String, endTime: String, room: String) {
        val err = InputValidators.validateTimeRange(startTime, endTime)
            ?: InputValidators.validateRoom(room)
        if (err != null) {
            emitToast(err)
            return
        }
        runAction("Class slot added.") {
            repository.addScheduleSlot(day, startTime, endTime, subjectCode, room)
        }
    }

    fun deleteScheduleSlot(scheduleId: String) {
        runAction("Class slot removed.") {
            repository.deleteScheduleSlot(scheduleId)
        }
    }

    fun seedDefaultCurriculum() {
        runAction("Default CS subjects and schedule synced.") {
            repository.seedDefaultCurriculumIfEmpty()
        }
    }

    fun saveCalendarEvent(existing: CalendarEvent?, date: String, type: String, title: String, description: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty() || cleanTitle.length > 100) {
            emitToast("Title must be 1 to 100 characters.")
            return
        }
        val dateErr = InputValidators.validateDate(date)
        if (dateErr != null) {
            emitToast(dateErr)
            return
        }
        runAction(if (existing == null) "Event added." else "Event updated.") {
            repository.saveCalendarEvent(existing, date, type, cleanTitle, description)
        }
    }

    fun deleteCalendarEvent(eventId: String) {
        runAction("Event removed.") {
            repository.deleteCalendarEvent(eventId)
        }
    }

    fun postAnnouncement(
        title: String,
        body: String,
        subject: Subject?,
        authorName: String,
        attachment: PickedAttachment?,
    ) {
        if (title.trim().isEmpty() || title.trim().length > 100) {
            emitToast("Title must be 1 to 100 characters.")
            return
        }
        if (body.trim().isEmpty() || body.trim().length > 3000) {
            emitToast("Message must be 1 to 3000 characters.")
            return
        }
        runAction("Announcement posted.") {
            repository.postAnnouncement(
                title = title,
                body = body,
                subjectCode = subject?.code,
                subjectName = subject?.name,
                authorName = authorName,
                attachment = attachment,
            )
        }
    }

    fun deleteAnnouncement(announcementId: String) {
        runAction("Announcement removed.") {
            repository.deleteAnnouncement(announcementId)
        }
    }

    fun scanQrAttendance(
        rawQr: String,
        subject: Subject,
        classDate: String,
        status: String,
        verifierName: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> },
    ) {
        val dateErr = InputValidators.validateDate(classDate)
        if (dateErr != null) {
            emitToast(dateErr)
            onResult(false, dateErr)
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val res = repository.scanAndMarkAttendance(
                    rawQr = rawQr,
                    subjectCode = subject.code,
                    subjectName = subject.name,
                    classDate = classDate,
                    status = status,
                    verifierName = verifierName,
                )
                res.onSuccess { msg ->
                    emitToast(msg)
                    onResult(true, msg)
                }.onFailure { err ->
                    val msg = unwrapFirestoreMessage(err, "Could not mark attendance.")
                    emitToast(msg)
                    onResult(false, msg)
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun recordStudentQrAttendance(
        profile: UserProfile,
        rawQr: String,
        fallbackSubject: Subject?,
        classDate: String,
        validSubjects: List<Subject>,
        onResult: (Boolean, String) -> Unit = { _, _ -> },
    ) {
        val dateErr = InputValidators.validateDate(classDate)
        if (dateErr != null) {
            emitToast(dateErr)
            onResult(false, dateErr)
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val res = repository.recordStudentQrAttendance(
                    profile = profile,
                    rawQr = rawQr,
                    fallbackSubject = fallbackSubject,
                    defaultDate = classDate,
                    validSubjects = validSubjects,
                )
                res.onSuccess { msg ->
                    emitToast(msg)
                    onResult(true, msg)
                }.onFailure { err ->
                    val msg = unwrapFirestoreMessage(err, "Could not save QR check-in.")
                    emitToast(msg)
                    onResult(false, msg)
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun uploadClassResource(
        title: String,
        description: String,
        subject: Subject?,
        authorName: String,
        attachment: PickedAttachment?,
    ) {
        if (title.trim().isEmpty() || title.trim().length > 100) {
            emitToast("Title must be 1 to 100 characters.")
            return
        }
        if (attachment == null) {
            emitToast("Please choose a PDF file.")
            return
        }
        runAction("Class file uploaded.") {
            repository.uploadClassResource(
                title = title,
                description = description,
                subjectCode = subject?.code,
                subjectName = subject?.name,
                authorName = authorName,
                attachment = attachment,
            )
        }
    }

    fun deleteResource(resourceId: String) {
        runAction("File removed.") {
            repository.deleteResource(resourceId)
        }
    }

    fun submitExcuseLetter(
        profile: UserProfile,
        subject: Subject,
        reason: String,
        attachment: PickedAttachment?,
    ) {
        if (reason.trim().isEmpty() || reason.trim().length > 2000) {
            emitToast("Reason must be 1 to 2000 characters.")
            return
        }
        runAction("Excuse letter submitted.") {
            repository.submitExcuseLetter(
                profile = profile,
                subjectCode = subject.code,
                subjectName = subject.name,
                reason = reason,
                attachment = attachment,
            )
        }
    }

    fun reviewExcuseLetter(letterId: String, status: String, reviewNote: String, reviewerName: String) {
        runAction("Excuse letter ${status.lowercase()}.") {
            repository.reviewExcuseLetter(letterId, status, reviewNote, reviewerName)
        }
    }

    fun issueWarning(studentUid: String, studentName: String, note: String, issuerName: String) {
        if (note.trim().isEmpty() || note.trim().length > 500) {
            emitToast("Warning note must be 1 to 500 characters.")
            return
        }
        runAction("Warning saved.") {
            repository.issueWarning(studentUid, studentName, note, issuerName)
        }
    }

    fun sendMessage(senderName: String, receiver: DirectoryEntry, message: String) {
        if (message.trim().isEmpty() || message.trim().length > 1000) {
            emitToast("Message must be 1 to 1000 characters.")
            return
        }
        runAction("Message sent.") {
            repository.sendMessage(
                senderName = senderName,
                receiverUid = receiver.userId,
                receiverName = receiver.displayName,
                message = message,
            )
        }
    }

    fun markMessageRead(messageId: String) {
        viewModelScope.launch {
            repository.markMessageRead(messageId)
        }
    }

    fun updateUserRole(targetUid: String, newRole: String, newSubrole: String?) {
        runAction("User role updated.") {
            repository.updateUserRoleAsAdmin(targetUid, newRole, newSubrole)
        }
    }

    fun declineRoleRequest(targetUid: String) {
        runAction("Role registration request declined.") {
            repository.declineRoleRequestAsAdmin(targetUid)
        }
    }

    fun saveRoleInvite(email: String, role: String, note: String) {
        val emailErr = InputValidators.validateEmail("Invite email", email)
        if (emailErr != null) {
            emitToast(emailErr)
            return
        }
        runAction("Pre-approved ${UserRole.fromKey(role).label} invite saved.") {
            repository.saveRoleInvite(email, role, note)
        }
    }

    fun deleteRoleInvite(email: String) {
        runAction("Role invite removed.") {
            repository.deleteRoleInvite(email)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
