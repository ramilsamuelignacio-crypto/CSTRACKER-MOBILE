package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Announcement
import com.example.data.model.ClassResource
import com.example.data.model.DirectoryEntry
import com.example.data.model.Subject
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WarningRecord
import com.example.ui.components.CstrackerCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ImageLightboxDialog
import com.example.ui.components.PageHeader
import com.example.util.AttachmentUtils
import com.example.util.PickedAttachment
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    profile: UserProfile,
    activeRole: UserRole,
    subjects: List<Subject>,
    announcements: List<Announcement>,
    resources: List<ClassResource>,
    warnings: List<WarningRecord>,
    directory: List<DirectoryEntry>,
    initialTab: Int = 0,
    onPostAnnouncement: (title: String, body: String, subject: Subject?, attachment: PickedAttachment?) -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
    onUploadResource: (title: String, description: String, subject: Subject?, attachment: PickedAttachment?) -> Unit,
    onDeleteResource: (String) -> Unit,
    onIssueWarning: (studentUid: String, studentName: String, note: String) -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }
    var showAnnouncementDialog by remember { mutableStateOf(false) }
    var showResourceDialog by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var lightboxData by remember { mutableStateOf<Pair<Bitmap, String>?>(null) }

    val isStaff = activeRole.canPostAnnouncementsAndEvents
    val canManageFiles = activeRole.canUploadClassFiles
    val canWarn = activeRole.canReviewExcusesAndWarn

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Updates") },
                    modifier = Modifier.testTag("tab_announcements"),
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Class Files") },
                    modifier = Modifier.testTag("tab_resources"),
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Warnings") },
                    modifier = Modifier.testTag("tab_warnings"),
                )
            }

            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            PageHeader(
                                title = "Announcements",
                                subtitle = "Class updates, reminders, and quick notices.",
                            )
                        }
                        if (announcements.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No announcements yet",
                                    subtitle = "Staff posts and class updates will show up here.",
                                    icon = Icons.Rounded.Campaign,
                                )
                            }
                        } else {
                            items(announcements, key = { it.id }) { ann ->
                                val dateStr = ann.createdAt?.toDate()?.let {
                                    SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(it)
                                } ?: "Just now"
                                val imgBitmap = remember(ann.attachmentBase64, ann.attachmentType) {
                                    if (ann.attachmentType?.startsWith("image/") == true) {
                                        AttachmentUtils.decodeBase64ToBitmap(ann.attachmentBase64)
                                    } else null
                                }
                                CstrackerCard {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                            ) {
                                                Text(
                                                    text = ann.subjectName ?: "General",
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(ann.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(ann.body, style = MaterialTheme.typography.bodyMedium)
                                        Text("Posted by ${ann.authorName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        if (imgBitmap != null) {
                                            Image(
                                                bitmap = imgBitmap.asImageBitmap(),
                                                contentDescription = ann.attachmentName ?: ann.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(180.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { lightboxData = imgBitmap to (ann.attachmentName ?: ann.title) },
                                            )
                                        } else if (!ann.attachmentName.isNullOrBlank() && !ann.attachmentBase64.isNullOrBlank()) {
                                            OutlinedButton(
                                                onClick = {
                                                    AttachmentUtils.openBase64File(
                                                        context,
                                                        ann.attachmentName,
                                                        ann.attachmentType ?: "application/pdf",
                                                        ann.attachmentBase64,
                                                    ).onFailure { onToast("No app found to open this file.") }
                                                },
                                                modifier = Modifier.heightIn(min = 48.dp),
                                            ) {
                                                Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(ann.attachmentName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }

                                        if (isStaff) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                TextButton(onClick = { onDeleteAnnouncement(ann.id) }) {
                                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            PageHeader(
                                title = "Class files",
                                subtitle = "PDF handouts and reviewers shared by professors.",
                            )
                        }
                        if (resources.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No class files yet",
                                    subtitle = "Uploaded PDFs and handouts will appear here.",
                                    icon = Icons.Rounded.Description,
                                )
                            }
                        } else {
                            items(resources, key = { it.id }) { res ->
                                CstrackerCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 48.dp, height = 58.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFFFFF0EF)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = "PDF", tint = Color(0xFFB42318))
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                            ) {
                                                Text(
                                                    res.subjectName ?: "General",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Text(res.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            if (!res.description.isNullOrBlank()) {
                                                Text(res.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text(
                                                "By ${res.authorName} · ${AttachmentUtils.formatBytes(res.fileSizeBytes)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Button(
                                                    onClick = {
                                                        val b64 = res.fileBase64
                                                        if (b64.isNullOrBlank()) {
                                                            onToast("File data unavailable.")
                                                        } else {
                                                            AttachmentUtils.openBase64File(context, res.fileName, "application/pdf", b64)
                                                                .onFailure { onToast("Install a PDF viewer app to open ${res.fileName}.") }
                                                        }
                                                    },
                                                    modifier = Modifier.heightIn(min = 48.dp),
                                                ) {
                                                    Text("Open PDF")
                                                }
                                                if (canManageFiles) {
                                                    IconButton(onClick = { onDeleteResource(res.id) }) {
                                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete file", tint = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            PageHeader(
                                title = "Warnings",
                                subtitle = "Attendance and academic reminders.",
                            )
                        }
                        if (warnings.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No warnings",
                                    subtitle = "Great job keeping up with class requirements.",
                                    icon = Icons.Rounded.WarningAmber,
                                )
                            }
                        } else {
                            items(warnings, key = { it.id }) { warn ->
                                val dateStr = warn.createdAt?.toDate()?.let {
                                    SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(it)
                                } ?: "Just now"
                                CstrackerCard {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(warn.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(warn.note, style = MaterialTheme.typography.bodyMedium)
                                        Text("Issued by ${warn.createdByName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedTab == 0 && isStaff) {
            FloatingActionButton(
                onClick = { showAnnouncementDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("fab_post_announcement"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Post announcement")
            }
        } else if (selectedTab == 1 && canManageFiles) {
            FloatingActionButton(
                onClick = { showResourceDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("fab_upload_pdf"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Upload PDF")
            }
        } else if (selectedTab == 2 && canWarn) {
            FloatingActionButton(
                onClick = { showWarningDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("fab_add_warning"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add warning")
            }
        }
    }

    if (lightboxData != null) {
        ImageLightboxDialog(
            bitmap = lightboxData!!.first,
            title = lightboxData!!.second,
            onDismiss = { lightboxData = null },
        )
    }

    if (showAnnouncementDialog) {
        PostAnnouncementDialog(
            subjects = subjects,
            onDismiss = { showAnnouncementDialog = false },
            onToast = onToast,
            onConfirm = { title, body, subject, attachment ->
                onPostAnnouncement(title, body, subject, attachment)
                showAnnouncementDialog = false
            },
        )
    }

    if (showResourceDialog) {
        UploadResourceDialog(
            subjects = subjects,
            onDismiss = { showResourceDialog = false },
            onToast = onToast,
            onConfirm = { title, desc, subject, attachment ->
                onUploadResource(title, desc, subject, attachment)
                showResourceDialog = false
            },
        )
    }

    if (showWarningDialog) {
        IssueWarningDialog(
            directory = directory,
            fallbackProfile = profile,
            onDismiss = { showWarningDialog = false },
            onConfirm = { uid, name, note ->
                onIssueWarning(uid, name, note)
                showWarningDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostAnnouncementDialog(
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
    onConfirm: (title: String, body: String, subject: Subject?, attachment: PickedAttachment?) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf<Subject?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var pickedAttachment by remember { mutableStateOf<PickedAttachment?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val img = AttachmentUtils.compressImageToBase64(context, uri)
            if (img != null) pickedAttachment = img
            else onToast("Could not process selected image.")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Post announcement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(100) },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedSubject?.let { "${it.code} — ${it.name}" } ?: "General",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Subject") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("General") }, onClick = { selectedSubject = null; expanded = false })
                        subjects.forEach { s ->
                            DropdownMenuItem(text = { Text("${s.code} — ${s.name}") }, onClick = { selectedSubject = s; expanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it.take(3000) },
                    label = { Text("Message") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(pickedAttachment?.name ?: "Attach image (optional)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(title, body, selectedSubject, pickedAttachment) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Post")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadResourceDialog(
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
    onConfirm: (title: String, description: String, subject: Subject?, attachment: PickedAttachment?) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf(subjects.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    var pickedPdf by remember { mutableStateOf<PickedAttachment?>(null) }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            AttachmentUtils.readPdfToBase64(context, uri)
                .onSuccess { pickedPdf = it }
                .onFailure { onToast(it.message ?: "Could not read PDF.") }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload class PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(100) },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedSubject?.let { "${it.code} — ${it.name}" } ?: "General",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Subject") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("General") }, onClick = { selectedSubject = null; expanded = false })
                        subjects.forEach { s ->
                            DropdownMenuItem(text = { Text("${s.code} — ${s.name}") }, onClick = { selectedSubject = s; expanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(500) },
                    label = { Text("Description (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(pickedPdf?.name ?: "Choose PDF (up to 320 KB)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(title, description, selectedSubject, pickedPdf) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Upload")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueWarningDialog(
    directory: List<DirectoryEntry>,
    fallbackProfile: UserProfile,
    onDismiss: () -> Unit,
    onConfirm: (studentUid: String, studentName: String, note: String) -> Unit,
) {
    val recipients = remember(directory, fallbackProfile) {
        directory.ifEmpty {
            listOf(
                DirectoryEntry(
                    userId = fallbackProfile.userId,
                    displayName = fallbackProfile.fullName,
                    studentIdLabel = fallbackProfile.studentId,
                    role = fallbackProfile.role,
                )
            )
        }
    }
    var selected by remember { mutableStateOf(recipients.first()) }
    var expanded by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add warning") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = "${selected.displayName}${selected.studentIdLabel?.let { " · $it" } ?: ""}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Student") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        recipients.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text("${entry.displayName}${entry.studentIdLabel?.let { " · $it" } ?: ""}") },
                                onClick = { selected = entry; expanded = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text("Reminder or warning note") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected.userId, selected.displayName, note) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Save warning")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
