package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.core.view.WindowCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.CstrackerRepository
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.signOut
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.preferences.ThemePreferences
import com.example.ui.components.CstrackerCard
import com.example.ui.components.ErrorBanner
import com.example.ui.screens.AttendanceQrScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ConnectMoreScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PortalAuthMode
import com.example.ui.screens.ScheduleCalendarScreen
import com.example.ui.screens.UpdatesScreen
import com.example.ui.theme.CstrackerTheme
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

private enum class AppDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    SCHEDULE("Schedule", Icons.Rounded.Schedule, Icons.Outlined.Schedule, "nav_schedule"),
    CALENDAR("Calendar", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_calendar"),
    ATTENDANCE("Attendance", Icons.Rounded.QrCode2, Icons.Outlined.QrCode2, "nav_attendance"),
    PROFILE("Profile", Icons.Rounded.Person, Icons.Outlined.Person, "nav_profile"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val themePrefs = remember(context) { ThemePreferences(context.applicationContext) }
            val darkPref by themePrefs.isDarkTheme.collectAsStateWithLifecycle(initialValue = null)
            val isDarkTheme = darkPref ?: isSystemInDarkTheme()
            val scope = rememberCoroutineScope()

            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                )
            }

            CstrackerTheme(darkTheme = isDarkTheme) {
                val authUser by Firebase.auth.authStateFlow()
                    .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

                var selectedPortalRoleKey by rememberSaveable { mutableStateOf(UserRole.STUDENT.key) }
                var selectedAuthModeName by rememberSaveable { mutableStateOf(PortalAuthMode.LOGIN.name) }
                var explicitPortalSignIn by rememberSaveable { mutableStateOf(false) }

                val selectedPortalRole = UserRole.fromKey(selectedPortalRoleKey)
                val selectedAuthMode = PortalAuthMode.entries.firstOrNull { it.name == selectedAuthModeName }
                    ?: PortalAuthMode.LOGIN

                val user = authUser
                if (user == null) {
                    AuthScreen(
                        initialPortalRole = selectedPortalRole,
                        initialAuthMode = selectedAuthMode,
                        onPortalSelected = { role, mode, explicitTap ->
                            selectedPortalRoleKey = role.key
                            selectedAuthModeName = mode.name
                            explicitPortalSignIn = explicitTap
                        },
                        onAuthSuccess = {},
                    )
                } else {
                    key(user.uid) {
                        val vm: MainViewModel = viewModel(
                            key = user.uid,
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY])
                                    MainViewModel(CstrackerRepository(app), user.uid)
                                }
                            },
                        )
                        AuthenticatedRoot(
                            viewModel = vm,
                            selectedPortalRole = selectedPortalRole,
                            selectedAuthMode = selectedAuthMode,
                            explicitPortalSignIn = explicitPortalSignIn,
                            onAcknowledgePortal = { explicitPortalSignIn = false },
                            isDarkTheme = isDarkTheme,
                            onToggleDarkTheme = { enabled ->
                                scope.launch { themePrefs.setDarkTheme(enabled) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedRoot(
    viewModel: MainViewModel,
    selectedPortalRole: UserRole,
    selectedAuthMode: PortalAuthMode,
    explicitPortalSignIn: Boolean,
    onAcknowledgePortal: () -> Unit,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val ownRoleInvite by viewModel.ownRoleInvite.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.toastMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun triggerSignOut() {
        onAcknowledgePortal()
        signOut(credentialManager = credentialManager, scope = scope)
    }

    when (val state = profileState) {
        is UiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ErrorBanner(message = state.message)
                    Button(onClick = { triggerSignOut() }) {
                        Text("Sign out")
                    }
                }
            }
        }

        is UiState.Success -> {
            val profile = state.data
            if (profile == null) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.navigationBars.union(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                    ),
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        OnboardingScreen(
                            initialPortalRole = selectedPortalRole,
                            ownRoleInvite = ownRoleInvite,
                            isBusy = isBusy,
                            onSubmit = { portalRole, studentId, first, middle, last, campusEmail, guardian, irregular, subjectsRaw ->
                                onAcknowledgePortal()
                                viewModel.completeInitialProfile(
                                    portalRole = portalRole,
                                    studentId = studentId,
                                    firstName = first,
                                    middleName = middle,
                                    lastName = last,
                                    campusEmail = campusEmail,
                                    guardianGmail = guardian,
                                    irregular = irregular,
                                    subjectsRaw = subjectsRaw,
                                )
                            },
                            onSignOut = { triggerSignOut() },
                        )
                    }
                }
            } else {
                val allowedRoles = profile.availableRoles()
                LaunchedEffect(profile.userId, selectedPortalRole, explicitPortalSignIn) {
                    if (explicitPortalSignIn && selectedPortalRole in allowedRoles) {
                        viewModel.switchActiveRole(selectedPortalRole)
                        onAcknowledgePortal()
                    }
                }

                if (explicitPortalSignIn && selectedAuthMode == PortalAuthMode.LOGIN && selectedPortalRole !in allowedRoles) {
                    val actualRole = UserRole.fromKey(profile.role)
                    val pendingRole = profile.requestedRole?.takeIf { it.isNotBlank() }?.let { UserRole.fromKey(it) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CstrackerCard(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "${selectedPortalRole.label} Portal Access Required",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    text = "Your Google account (${profile.campusEmail}) is currently assigned as ${actualRole.label} (Rank ${actualRole.rank}), not ${selectedPortalRole.label}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (pendingRole != null) {
                                    Text(
                                        text = "Your ${pendingRole.label} registration request is still awaiting Admin approval.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Button(
                                    onClick = { onAcknowledgePortal() },
                                    modifier = Modifier.fillMaxWidth().testTag("continue_assigned_role_btn"),
                                ) {
                                    Text("Continue as ${actualRole.label}")
                                }
                                TextButton(
                                    onClick = { triggerSignOut() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Sign out & switch Google account")
                                }
                            }
                        }
                    }
                } else {
                    CstrackerShell(
                        profile = profile,
                        viewModel = viewModel,
                        isDarkTheme = isDarkTheme,
                        isBusy = isBusy,
                        snackbarHostState = snackbarHostState,
                        onToggleDarkTheme = onToggleDarkTheme,
                        onSignOut = { triggerSignOut() },
                    )
                }
            }
        }
    }
}

@Composable
private fun CstrackerShell(
    profile: UserProfile,
    viewModel: MainViewModel,
    isDarkTheme: Boolean,
    isBusy: Boolean,
    snackbarHostState: SnackbarHostState,
    onToggleDarkTheme: (Boolean) -> Unit,
    onSignOut: () -> Unit,
) {
    val activeRole by viewModel.activeRole.collectAsStateWithLifecycle()
    val subjectsState by viewModel.subjectsState.collectAsStateWithLifecycle()
    val schedulesState by viewModel.schedulesState.collectAsStateWithLifecycle()
    val eventsState by viewModel.calendarEventsState.collectAsStateWithLifecycle()
    val announcementsState by viewModel.announcementsState.collectAsStateWithLifecycle()
    val resourcesState by viewModel.resourcesState.collectAsStateWithLifecycle()
    val attendanceState by viewModel.attendanceState.collectAsStateWithLifecycle()
    val excusesState by viewModel.excuseLettersState.collectAsStateWithLifecycle()
    val warningsState by viewModel.warningsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val directoryState by viewModel.directoryState.collectAsStateWithLifecycle()
    val adminUsersState by viewModel.adminUsersState.collectAsStateWithLifecycle()
    val roleInvitesState by viewModel.roleInvitesState.collectAsStateWithLifecycle()

    val subjects = (subjectsState as? UiState.Success)?.data.orEmpty()
    val schedules = (schedulesState as? UiState.Success)?.data.orEmpty()
    val events = (eventsState as? UiState.Success)?.data.orEmpty()
    val announcements = (announcementsState as? UiState.Success)?.data.orEmpty()
    val resources = (resourcesState as? UiState.Success)?.data.orEmpty()
    val attendance = (attendanceState as? UiState.Success)?.data.orEmpty()
    val excuses = (excusesState as? UiState.Success)?.data.orEmpty()
    val warnings = (warningsState as? UiState.Success)?.data.orEmpty()
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    val directory = (directoryState as? UiState.Success)?.data.orEmpty()
    val adminUsers = (adminUsersState as? UiState.Success)?.data.orEmpty()
    val roleInvites = (roleInvitesState as? UiState.Success)?.data.orEmpty()

    var currentTab by rememberSaveable { mutableStateOf("Schedule") }
    var secondaryView by rememberSaveable { mutableStateOf<String?>(null) }
    var updatesInitialTab by rememberSaveable { mutableIntStateOf(0) }
    var attendanceInitialTab by rememberSaveable { mutableIntStateOf(0) }
    var profileInitialTab by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = secondaryView != null || currentTab != "Schedule") {
        if (secondaryView != null) {
            secondaryView = null
        } else {
            currentTab = "Schedule"
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.navigationBars.union(
                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
            ),
            bottomBar = {
                if (!useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        AppDestination.entries.forEach { dest ->
                            val isSelected = currentTab == dest.label && secondaryView == null
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    secondaryView = null
                                    currentTab = dest.label
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail(windowInsets = WindowInsets(0, 0, 0, 0)) {
                        AppDestination.entries.forEach { dest ->
                            val isSelected = currentTab == dest.label && secondaryView == null
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = {
                                    secondaryView = null
                                    currentTab = dest.label
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxSize(),
                    ) {
                        when {
                            secondaryView == "Overview" -> Column(modifier = Modifier.fillMaxSize()) {
                                TextButton(
                                    onClick = { secondaryView = null },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Back to Schedule")
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    DashboardScreen(
                                        profile = profile,
                                        activeRole = activeRole,
                                        subjects = subjects,
                                        schedules = schedules,
                                        announcements = announcements,
                                        attendance = attendance,
                                        excuseLetters = excuses,
                                        warnings = warnings,
                                        onGoSchedule = {
                                            secondaryView = null
                                            currentTab = "Schedule"
                                        },
                                        onGoAnnouncements = {
                                            updatesInitialTab = 0
                                            secondaryView = "Updates"
                                        },
                                        onGoProfile = {
                                            secondaryView = null
                                            profileInitialTab = 0
                                            currentTab = "Profile"
                                        },
                                        onGoQr = {
                                            secondaryView = null
                                            attendanceInitialTab = 1
                                            currentTab = "Attendance"
                                        },
                                        onGoMessages = {
                                            secondaryView = null
                                            profileInitialTab = 2
                                            currentTab = "Profile"
                                        },
                                        onSwitchRole = viewModel::switchActiveRole,
                                    )
                                }
                            }

                            secondaryView == "Updates" -> Column(modifier = Modifier.fillMaxSize()) {
                                TextButton(
                                    onClick = { secondaryView = null },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Back to Schedule")
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    UpdatesScreen(
                                        profile = profile,
                                        activeRole = activeRole,
                                        subjects = subjects,
                                        announcements = announcements,
                                        resources = resources,
                                        warnings = warnings,
                                        directory = directory,
                                        initialTab = updatesInitialTab,
                                        onPostAnnouncement = { title, body, subject, attachment ->
                                            viewModel.postAnnouncement(title, body, subject, profile.fullName, attachment)
                                        },
                                        onDeleteAnnouncement = viewModel::deleteAnnouncement,
                                        onUploadResource = { title, desc, subject, attachment ->
                                            viewModel.uploadClassResource(title, desc, subject, profile.fullName, attachment)
                                        },
                                        onDeleteResource = viewModel::deleteResource,
                                        onIssueWarning = { uid, name, note ->
                                            viewModel.issueWarning(uid, name, note, profile.fullName)
                                        },
                                        onToast = viewModel::emitToast,
                                    )
                                }
                            }

                            currentTab == "Schedule" -> ScheduleCalendarScreen(
                                activeRole = activeRole,
                                subjects = subjects,
                                schedules = schedules,
                                calendarEvents = events,
                                fixedTab = 0,
                                scheduleQuickActions = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        AssistChip(
                                            onClick = { secondaryView = "Overview" },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Rounded.Dashboard,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            },
                                            label = { Text("Overview") },
                                            modifier = Modifier.testTag("quick_overview_chip"),
                                        )
                                        AssistChip(
                                            onClick = {
                                                updatesInitialTab = 0
                                                secondaryView = "Updates"
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Rounded.Campaign,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            },
                                            label = {
                                                Text(
                                                    if (announcements.isNotEmpty()) "Updates (${announcements.size})"
                                                    else "Updates & Files"
                                                )
                                            },
                                            modifier = Modifier.testTag("quick_updates_chip"),
                                        )
                                    }
                                },
                                onAddScheduleSlot = viewModel::addScheduleSlot,
                                onDeleteScheduleSlot = viewModel::deleteScheduleSlot,
                                onSaveCalendarEvent = viewModel::saveCalendarEvent,
                                onDeleteCalendarEvent = viewModel::deleteCalendarEvent,
                            )

                            currentTab == "Calendar" -> ScheduleCalendarScreen(
                                activeRole = activeRole,
                                subjects = subjects,
                                schedules = schedules,
                                calendarEvents = events,
                                fixedTab = 1,
                                onAddScheduleSlot = viewModel::addScheduleSlot,
                                onDeleteScheduleSlot = viewModel::deleteScheduleSlot,
                                onSaveCalendarEvent = viewModel::saveCalendarEvent,
                                onDeleteCalendarEvent = viewModel::deleteCalendarEvent,
                            )

                            currentTab == "Attendance" -> AttendanceQrScreen(
                                profile = profile,
                                activeRole = activeRole,
                                subjects = subjects,
                                attendance = attendance,
                                isBusy = isBusy,
                                initialTab = attendanceInitialTab,
                                onScanQr = { rawQr, subject, classDate, status, onDone ->
                                    viewModel.scanQrAttendance(rawQr, subject, classDate, status, profile.fullName, onDone)
                                },
                                onStudentScanQr = { rawQr, fallbackSubject, classDate, onDone ->
                                    viewModel.recordStudentQrAttendance(
                                        profile = profile,
                                        rawQr = rawQr,
                                        fallbackSubject = fallbackSubject,
                                        classDate = classDate,
                                        validSubjects = subjects,
                                        onResult = onDone,
                                    )
                                },
                                onToast = viewModel::emitToast,
                            )

                            currentTab == "Profile" -> ConnectMoreScreen(
                                profile = profile,
                                activeRole = activeRole,
                                subjects = subjects,
                                excuseLetters = excuses,
                                messages = messages,
                                directory = directory,
                                adminUsers = adminUsers,
                                roleInvites = roleInvites,
                                isDarkTheme = isDarkTheme,
                                isBusy = isBusy,
                                initialTab = profileInitialTab,
                                onSubmitExcuse = { subject, reason, attachment ->
                                    viewModel.submitExcuseLetter(profile, subject, reason, attachment)
                                },
                                onReviewExcuse = { letterId, status, note ->
                                    viewModel.reviewExcuseLetter(letterId, status, note, profile.fullName)
                                },
                                onSendMessage = { receiver, msg ->
                                    viewModel.sendMessage(profile.fullName, receiver, msg)
                                },
                                onMarkMessageRead = viewModel::markMessageRead,
                                onUpdateProfile = { first, middle, last, studentId, campusEmail, guardian, irregular, subjectsRaw ->
                                    viewModel.updateProfile(
                                        current = profile,
                                        firstName = first,
                                        middleName = middle,
                                        lastName = last,
                                        studentId = studentId,
                                        campusEmail = campusEmail,
                                        guardianGmail = guardian,
                                        irregular = irregular,
                                        subjectsRaw = subjectsRaw,
                                    )
                                },
                                onUpdateProfilePhoto = { photo ->
                                    viewModel.updateProfilePhoto(profile, photo)
                                },
                                onToggleDarkTheme = onToggleDarkTheme,
                                onSwitchRole = viewModel::switchActiveRole,
                                onSignOut = onSignOut,
                                onUpdateUserRole = viewModel::updateUserRole,
                                onDeclineRoleRequest = viewModel::declineRoleRequest,
                                onSaveRoleInvite = viewModel::saveRoleInvite,
                                onDeleteRoleInvite = viewModel::deleteRoleInvite,
                                onSeedCurriculum = viewModel::seedDefaultCurriculum,
                                onToast = viewModel::emitToast,
                            )
                        }
                    }
                }
            }
        }
    }
}
