package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.onGoogleSignInClicked
import com.example.data.model.UserRole
import com.example.ui.components.ErrorBanner
import com.example.ui.theme.DeepPine
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.MintGlow

enum class PortalAuthMode(val label: String) {
    LOGIN("Sign In"),
    REGISTER("Register"),
}

private data class RolePortalMeta(
    val role: UserRole,
    val icon: ImageVector,
    val tagline: String,
    val registrationNote: String,
)

private val rolePortals = listOf(
    RolePortalMeta(
        role = UserRole.STUDENT,
        icon = Icons.Rounded.School,
        tagline = "View schedule, calendar, announcements, and personal QR attendance pass.",
        registrationNote = "Instant access with your student ID and @cvsu.edu.ph email.",
    ),
    RolePortalMeta(
        role = UserRole.CLASS_REP,
        icon = Icons.Rounded.Campaign,
        tagline = "Post class announcements and calendar events alongside student access.",
        registrationNote = "Instant if pre-approved by an Admin; otherwise queued for Admin verification.",
    ),
    RolePortalMeta(
        role = UserRole.MODERATOR,
        icon = Icons.Rounded.Gavel,
        tagline = "Review excuse letters, issue attendance warnings, and post updates.",
        registrationNote = "Instant if pre-approved by an Admin; otherwise queued for Admin verification.",
    ),
    RolePortalMeta(
        role = UserRole.PROFESSOR,
        icon = Icons.Rounded.QrCodeScanner,
        tagline = "Exclusive QR attendance scanner, upload class files, review excuses & warn.",
        registrationNote = "Requires Admin invite or Admin approval after registration.",
    ),
    RolePortalMeta(
        role = UserRole.ADMIN,
        icon = Icons.Rounded.AdminPanelSettings,
        tagline = "Manage user accounts, role invites, schedules, files, and approvals.",
        registrationNote = "For designated program admins or pre-approved admin emails.",
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
    initialPortalRole: UserRole = UserRole.STUDENT,
    initialAuthMode: PortalAuthMode = PortalAuthMode.LOGIN,
    onPortalSelected: (role: UserRole, mode: PortalAuthMode, explicitTap: Boolean) -> Unit,
    onAuthSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var selectedRoleKey by rememberSaveable { mutableStateOf(initialPortalRole.key) }
    var authModeName by rememberSaveable { mutableStateOf(initialAuthMode.name) }
    var isLoading by remember { mutableStateOf(false) }
    var isAutoChecking by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val selectedRole = UserRole.fromKey(selectedRoleKey)
    val authMode = PortalAuthMode.entries.firstOrNull { it.name == authModeName } ?: PortalAuthMode.LOGIN
    val selectedMeta = rolePortals.firstOrNull { it.role == selectedRole } ?: rolePortals.first()

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isAutoChecking = false
                onPortalSelected(selectedRole, authMode, false)
                onAuthSuccess()
            },
            onUnauthenticated = {
                isAutoChecking = false
            },
            scope = scope,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
            .safeDrawingPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                Brush.linearGradient(listOf(ForestGreen, DeepPine)),
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = selectedMeta.icon,
                            contentDescription = "${selectedRole.label} portal icon",
                            tint = MintGlow,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "CSTRACKER",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${selectedRole.label} ${authMode.label} Portal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    PortalAuthMode.entries.forEachIndexed { idx, mode ->
                        SegmentedButton(
                            selected = authMode == mode,
                            onClick = {
                                authModeName = mode.name
                                errorMessage = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = idx,
                                count = PortalAuthMode.entries.size,
                            ),
                            modifier = Modifier.testTag("auth_mode_${mode.name.lowercase()}"),
                        ) {
                            Text(mode.label, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Text(
                    text = if (authMode == PortalAuthMode.LOGIN) {
                        "Choose your role portal to sign in with Google."
                    } else {
                        "Select the role you are registering for."
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rolePortals.forEach { portal ->
                        val isSelected = portal.role == selectedRole
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    selectedRoleKey = portal.role.key
                                    errorMessage = null
                                }
                                .testTag("portal_role_${portal.role.key}"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = portal.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(22.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = portal.role.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            },
                                        ) {
                                            Text(
                                                text = "Rank ${portal.role.rank}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        text = portal.tagline,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (authMode == PortalAuthMode.LOGIN) {
                                "${selectedRole.label} Sign-In Verification"
                            } else {
                                "${selectedRole.label} Registration"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (authMode == PortalAuthMode.LOGIN) {
                                "Your Google account's assigned role will be fetched from Firestore to verify ${selectedRole.label} access."
                            } else {
                                selectedMeta.registrationNote
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PortalCapabilityChip("View Core")
                            if (selectedRole.canScanQrAttendance) PortalCapabilityChip("Scan QR")
                            if (selectedRole.canPostAnnouncementsAndEvents) PortalCapabilityChip("Post Events & Updates")
                            if (selectedRole.canUploadClassFiles) PortalCapabilityChip("Upload Files")
                            if (selectedRole.canReviewExcusesAndWarn) PortalCapabilityChip("Review & Warn")
                            if (selectedRole.canEditScheduleAndManageUsers) PortalCapabilityChip("Schedule & Users")
                        }
                    }
                }

                if (errorMessage != null) {
                    ErrorBanner(message = errorMessage!!)
                }

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        onPortalSelected(selectedRole, authMode, true)
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            },
                        )
                    },
                    enabled = !isLoading && !isAutoChecking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("google_sign_in_button"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (isLoading || isAutoChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(if (isAutoChecking) "Checking session..." else "Signing in...")
                    } else {
                        Text(
                            text = if (authMode == PortalAuthMode.LOGIN) {
                                "Sign in as ${selectedRole.label} with Google"
                            } else {
                                "Register as ${selectedRole.label} with Google"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortalCapabilityChip(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}
