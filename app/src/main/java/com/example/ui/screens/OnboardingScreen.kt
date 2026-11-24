package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.firebase.CstrackerRepository
import com.example.data.model.RoleInvite
import com.example.data.model.UserRole
import com.example.ui.components.CstrackerCard
import com.example.ui.components.PageHeader
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    initialPortalRole: UserRole,
    ownRoleInvite: RoleInvite?,
    isBusy: Boolean,
    onSubmit: (
        portalRole: UserRole,
        studentId: String,
        firstName: String,
        middleName: String,
        lastName: String,
        campusEmail: String,
        guardianGmail: String,
        irregular: Boolean,
        subjectsRaw: String,
    ) -> Unit,
    onSignOut: () -> Unit,
) {
    val authUser = Firebase.auth.currentUser
    val isBootstrappedAdmin = authUser?.isEmailVerified == true &&
        authUser.email.equals(CstrackerRepository.BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true)

    val invitedRole = ownRoleInvite?.role?.let { UserRole.fromKey(it) }
    val defaultRole = when {
        isBootstrappedAdmin -> UserRole.ADMIN
        invitedRole != null && initialPortalRole == UserRole.STUDENT -> invitedRole
        else -> initialPortalRole
    }

    var selectedRoleKey by rememberSaveable { mutableStateOf(defaultRole.key) }
    val portalRole = UserRole.fromKey(selectedRoleKey)

    val nameParts = (authUser?.displayName ?: "").trim().split(" ").filter { it.isNotBlank() }
    val defaultFirst = nameParts.firstOrNull().orEmpty()
    val defaultLast = if (nameParts.size > 1) nameParts.last() else ""
    val isStaffPortal = portalRole in listOf(UserRole.ADMIN, UserRole.PROFESSOR, UserRole.MODERATOR)
    val defaultEmail = when {
        authUser?.email?.endsWith("@cvsu.edu.ph", ignoreCase = true) == true -> authUser.email.orEmpty()
        isStaffPortal -> authUser?.email.orEmpty()
        else -> ""
    }

    var studentId by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf(defaultFirst) }
    var middleName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf(defaultLast) }
    var campusEmail by rememberSaveable(selectedRoleKey) { mutableStateOf(defaultEmail) }
    var guardianGmail by rememberSaveable { mutableStateOf("") }
    var irregular by rememberSaveable { mutableStateOf(false) }
    var enrolledSubjects by rememberSaveable { mutableStateOf("") }

    val hasPreApproval = isBootstrappedAdmin ||
        portalRole == UserRole.STUDENT ||
        (invitedRole != null && invitedRole == portalRole)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageHeader(
                title = "${portalRole.label} Registration",
                subtitle = when (portalRole) {
                    UserRole.ADMIN -> "Set up your administrator profile to manage accounts and class schedules."
                    UserRole.PROFESSOR -> "Set up your faculty profile for QR attendance scanning and class files."
                    UserRole.MODERATOR -> "Set up your moderator profile for excuse reviews, warnings, and updates."
                    UserRole.CLASS_REP -> "Set up your class representative profile for announcements and student tracking."
                    UserRole.STUDENT -> "Set up your student profile to generate your attendance QR pass."
                },
            )

            CstrackerCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Registration Role",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        UserRole.rankedEntries.forEach { roleOption ->
                            FilterChip(
                                selected = portalRole == roleOption,
                                onClick = { selectedRoleKey = roleOption.key },
                                label = { Text("${roleOption.label} (R${roleOption.rank})") },
                                modifier = Modifier.testTag("onboarding_role_${roleOption.key}"),
                            )
                        }
                    }

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (hasPreApproval) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = when {
                                isBootstrappedAdmin && portalRole == UserRole.ADMIN ->
                                    "Verified primary admin account: Admin access activates immediately."
                                invitedRole != null && invitedRole == portalRole ->
                                    "Pre-approved email invite found: ${portalRole.label} access activates immediately."
                                portalRole == UserRole.STUDENT ->
                                    "Student registration activates immediately upon saving."
                                else ->
                                    "Your profile starts with Student access and submits a ${portalRole.label} request for Admin approval."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            CstrackerCard {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val isFacultyOrAdmin = portalRole in listOf(UserRole.ADMIN, UserRole.PROFESSOR)
                    OutlinedTextField(
                        value = studentId,
                        onValueChange = { raw ->
                            studentId = if (isFacultyOrAdmin) {
                                raw.take(20)
                            } else {
                                raw.filter { ch -> ch.isDigit() }.take(12)
                            }
                        },
                        label = {
                            Text(
                                if (isFacultyOrAdmin) "Faculty / Staff ID (optional)"
                                else "Student ID (5-12 digits)"
                            )
                        },
                        placeholder = { Text(if (isFacultyOrAdmin) "FAC-1024" else "202413724") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (isFacultyOrAdmin) KeyboardType.Text else KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_student_id"),
                    )
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it.take(50) },
                        label = { Text("First name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_first_name"),
                    )
                    OutlinedTextField(
                        value = middleName,
                        onValueChange = { middleName = it.take(50) },
                        label = { Text("Middle name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it.take(50) },
                        label = { Text("Last name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_last_name"),
                    )
                    OutlinedTextField(
                        value = campusEmail,
                        onValueChange = { campusEmail = it.take(100) },
                        label = {
                            Text(
                                if (isStaffPortal) "Institutional or Google email"
                                else "Campus email (@cvsu.edu.ph)"
                            )
                        },
                        placeholder = {
                            Text(
                                if (isStaffPortal) "name@cvsu.edu.ph or name@gmail.com"
                                else "yourname@cvsu.edu.ph"
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_campus_email"),
                    )

                    if (!isFacultyOrAdmin) {
                        OutlinedTextField(
                            value = guardianGmail,
                            onValueChange = { guardianGmail = it.take(100) },
                            label = { Text("Guardian email (optional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Irregular student", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Specify custom subject codes for this term",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = irregular,
                                onCheckedChange = { irregular = it },
                                modifier = Modifier.testTag("onboarding_irregular_switch"),
                            )
                        }
                        if (irregular) {
                            OutlinedTextField(
                                value = enrolledSubjects,
                                onValueChange = { enrolledSubjects = it.take(100) },
                                label = { Text("Enrolled subjects (comma-separated)") },
                                placeholder = { Text("COSC75, COSC80") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            onSubmit(
                                portalRole,
                                studentId,
                                firstName,
                                middleName,
                                lastName,
                                campusEmail,
                                if (isFacultyOrAdmin) "" else guardianGmail,
                                if (isFacultyOrAdmin) false else irregular,
                                if (isFacultyOrAdmin) "" else enrolledSubjects,
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("onboarding_submit_button"),
                    ) {
                        Text(
                            if (isBusy) "Saving..."
                            else "Register as ${portalRole.label}"
                        )
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Sign out")
                    }
                }
            }
        }
    }
}
