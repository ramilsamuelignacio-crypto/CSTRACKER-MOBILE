package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Announcement
import com.example.data.model.AttendanceRecord
import com.example.data.model.ExcuseLetter
import com.example.data.model.ScheduleSlot
import com.example.data.model.Subject
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WarningRecord
import com.example.ui.components.CstrackerCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PageHeader
import com.example.ui.components.UserAvatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    profile: UserProfile,
    activeRole: UserRole,
    subjects: List<Subject>,
    schedules: List<ScheduleSlot>,
    announcements: List<Announcement>,
    attendance: List<AttendanceRecord>,
    excuseLetters: List<ExcuseLetter>,
    warnings: List<WarningRecord>,
    onGoSchedule: () -> Unit,
    onGoAnnouncements: () -> Unit,
    onGoProfile: () -> Unit,
    onGoQr: () -> Unit,
    onGoMessages: () -> Unit,
    onSwitchRole: (UserRole) -> Unit,
) {
    val absCount = attendance.count { it.status == "Absent" }
    val lateCount = attendance.count { it.status == "Late" }
    val pendingExcuses = excuseLetters.count { it.status == "Pending" }
    val subjectMap = subjects.associateBy { it.code }
    val availableRoles = profile.availableRoles()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeader(
                title = "Hi, ${profile.firstName.ifBlank { "there" }}",
                subtitle = "Here's what's happening with your class.",
                trailing = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = activeRole.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            )
        }

        if (availableRoles.size > 1) {
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    availableRoles.forEach { role ->
                        AssistChip(
                            onClick = { onSwitchRole(role) },
                            leadingIcon = if (role == activeRole) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.SwapHoriz,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else null,
                            label = {
                                Text(
                                    text = if (role == activeRole) "${role.label} (Active)" else role.label,
                                    fontWeight = if (role == activeRole) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            modifier = Modifier.testTag("switch_role_${role.key}"),
                        )
                    }
                }
            }
        }

        item {
            SectionDotHeader("Academic")
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(
                        label = "Absences",
                        value = absCount.toString(),
                        hint = if (absCount >= 3) "Check your attendance" else "Looking good",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Lates",
                        value = lateCount.toString(),
                        hint = "Keep an eye on time",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(
                        label = "Announcements",
                        value = announcements.size.toString(),
                        hint = "Latest class updates",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Excuse letters",
                        value = pendingExcuses.toString(),
                        hint = "Waiting for review",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            CstrackerCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Next classes", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = onGoSchedule, modifier = Modifier.testTag("dash_view_schedule_btn")) {
                            Text("View schedule")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (schedules.isEmpty()) {
                        EmptyStateCard("No schedule", "No classes added yet.")
                    } else {
                        schedules.take(5).forEachIndexed { idx, slot ->
                            val subj = subjectMap[slot.subjectCode]
                            if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(slot.subjectCode, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        subj?.name ?: "CS Course",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(slot.day, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "${slot.startTime}–${slot.endTime}${slot.room?.let { " · Rm $it" } ?: ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            CstrackerCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Latest updates", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = onGoAnnouncements, modifier = Modifier.testTag("dash_see_announcements_btn")) {
                            Text("See all")
                        }
                    }
                    if (announcements.isEmpty()) {
                        EmptyStateCard("No announcements", "Class updates will appear here.")
                    } else {
                        announcements.take(4).forEachIndexed { idx, ann ->
                            if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                                Text(ann.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${ann.subjectName ?: "General"} · ${ann.authorName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (warnings.isNotEmpty()) {
            item {
                CstrackerCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text("Recent reminders", style = MaterialTheme.typography.titleMedium)
                        }
                        warnings.take(3).forEach { w ->
                            val dateStr = w.createdAt?.toDate()?.let {
                                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(it)
                            } ?: "Just now"
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(w.note, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionDotHeader("Personal details")
        }

        item {
            CstrackerCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        UserAvatar(initials = profile.initials, photoBase64 = profile.profilePhotoBase64, size = 60.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "ID: ${profile.studentId.ifBlank { "—" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                profile.campusEmail.ifBlank { "—" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onGoProfile,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dash_profile_btn"),
                        ) {
                            Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Profile", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onGoQr,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dash_qr_btn"),
                        ) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("My QR", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onGoMessages,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dash_messages_btn"),
                        ) {
                            Icon(Icons.Rounded.MailOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Inbox", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionDotHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            border = BorderStroke(0.dp, MaterialTheme.colorScheme.primary),
            content = {},
        )
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    CstrackerCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
