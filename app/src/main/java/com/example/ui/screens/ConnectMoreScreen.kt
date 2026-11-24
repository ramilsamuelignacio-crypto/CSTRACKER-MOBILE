package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DirectMessage
import com.example.data.model.DirectoryEntry
import com.example.data.model.ExcuseLetter
import com.example.data.model.RoleInvite
import com.example.data.model.Subject
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.CstrackerCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ImageLightboxDialog
import com.example.ui.components.PageHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.UserAvatar
import com.example.util.AttachmentUtils
import com.example.util.PickedAttachment
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectMoreScreen(
    profile: UserProfile,
    activeRole: UserRole,
    subjects: List<Subject>,
    excuseLetters: List<ExcuseLetter>,
    messages: List<DirectMessage>,
    directory: List<DirectoryEntry>,
    adminUsers: List<UserProfile>,
    roleInvites: List<RoleInvite>,
    isDarkTheme: Boolean,
    isBusy: Boolean,
    initialTab: Int = 0,
    onSubmitExcuse: (subject: Subject, reason: String, attachment: PickedAttachment?) -> Unit,
    onReviewExcuse: (letterId: String, status: String, reviewNote: String) -> Unit,
    onSendMessage: (receiver: DirectoryEntry, message: String) -> Unit,
    onMarkMessageRead: (messageId: String) -> Unit,
    onUpdateProfile: (
        firstName: String,
        middleName: String,
        lastName: String,
        studentId: String,
        campusEmail: String,
        guardianGmail: String,
        irregular: Boolean,
        subjectsRaw: String,
    ) -> Unit,
    onUpdateProfilePhoto: (photoBase64: String?) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onSwitchRole: (UserRole) -> Unit,
    onSignOut: () -> Unit,
    onUpdateUserRole: (targetUid: String, newRole: String, newSubrole: String?) -> Unit,
    onDeclineRoleRequest: (targetUid: String) -> Unit,
    onSaveRoleInvite: (email: String, role: String, note: String) -> Unit,
    onDeleteRoleInvite: (email: String) -> Unit,
    onSeedCurriculum: () -> Unit,
    onToast: (String) -> Unit,
) {
    val isAdmin = activeRole == UserRole.ADMIN
    val maxTabIndex = if (isAdmin) 3 else 2
    var selectedTab by rememberSaveable(initialTab, isAdmin) {
        mutableIntStateOf(initialTab.coerceIn(0, maxTabIndex))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Profile") },
                modifier = Modifier.testTag("tab_profile"),
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Excuses") },
                modifier = Modifier.testTag("tab_excuses"),
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Inbox") },
                modifier = Modifier.testTag("tab_messages"),
            )
            if (isAdmin) {
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Admin") },
                    modifier = Modifier.testTag("tab_admin"),
                )
            }
        }

        when (selectedTab) {
            0 -> ProfileTab(
                profile = profile,
                activeRole = activeRole,
                isDarkTheme = isDarkTheme,
                isBusy = isBusy,
                onUpdateProfile = onUpdateProfile,
                onUpdateProfilePhoto = onUpdateProfilePhoto,
                onToggleDarkTheme = onToggleDarkTheme,
                onSwitchRole = onSwitchRole,
                onSignOut = onSignOut,
                onToast = onToast,
            )
            1 -> ExcusesTab(
                activeRole = activeRole,
                subjects = subjects,
                excuseLetters = excuseLetters,
                isBusy = isBusy,
                onSubmitExcuse = onSubmitExcuse,
                onReviewExcuse = onReviewExcuse,
                onToast = onToast,
            )
            2 -> MessagesTab(
                profile = profile,
                messages = messages,
                directory = directory,
                isBusy = isBusy,
                onSendMessage = onSendMessage,
                onMarkMessageRead = onMarkMessageRead,
            )
            3 -> if (isAdmin) {
                AdminTab(
                    currentUid = profile.userId,
                    adminUsers = adminUsers,
                    roleInvites = roleInvites,
                    isBusy = isBusy,
                    onUpdateUserRole = onUpdateUserRole,
                    onDeclineRoleRequest = onDeclineRoleRequest,
                    onSaveRoleInvite = onSaveRoleInvite,
                    onDeleteRoleInvite = onDeleteRoleInvite,
                    onSeedCurriculum = onSeedCurriculum,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExcusesTab(
    activeRole: UserRole,
    subjects: List<Subject>,
    excuseLetters: List<ExcuseLetter>,
    isBusy: Boolean,
    onSubmitExcuse: (subject: Subject, reason: String, attachment: PickedAttachment?) -> Unit,
    onReviewExcuse: (letterId: String, status: String, reviewNote: String) -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val canReviewExcuses = activeRole.canReviewExcusesAndWarn
    var selectedSubject by remember(subjects) { mutableStateOf(subjects.firstOrNull()) }
    var subjectExpanded by remember { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }
    var pickedAttachment by remember { mutableStateOf<PickedAttachment?>(null) }
    var lightboxData by remember { mutableStateOf<Pair<Bitmap, String>?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val img = AttachmentUtils.compressImageToBase64(context, uri)
            if (img != null) pickedAttachment = img
            else onToast("Could not process selected image.")
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            AttachmentUtils.readPdfToBase64(context, uri, maxBase64Length = 340_000)
                .onSuccess { pickedAttachment = it }
                .onFailure { onToast(it.message ?: "Could not read PDF.") }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                title = "Excuse letters",
                subtitle = if (canReviewExcuses) "Review student absence requests or submit your own."
                else "Submit absence documentation and check review status.",
            )
        }

        if (subjects.isNotEmpty()) {
            item {
                CstrackerCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Submit excuse letter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        ExposedDropdownMenuBox(
                            expanded = subjectExpanded,
                            onExpandedChange = { subjectExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = selectedSubject?.let { "${it.code} — ${it.name}" } ?: "Select subject",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Subject") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(subjectExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(
                                expanded = subjectExpanded,
                                onDismissRequest = { subjectExpanded = false },
                            ) {
                                subjects.forEach { subj ->
                                    DropdownMenuItem(
                                        text = { Text("${subj.code} — ${subj.name}") },
                                        onClick = {
                                            selectedSubject = subj
                                            subjectExpanded = false
                                        },
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it.take(2000) },
                            label = { Text("Reason for absence") },
                            minLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("excuse_reason_input"),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("excuse_pick_image_btn"),
                            ) {
                                Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Attach photo", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("excuse_pick_pdf_btn"),
                            ) {
                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Attach PDF", maxLines = 1)
                            }
                        }

                        if (pickedAttachment != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Attached: ${pickedAttachment!!.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { pickedAttachment = null }) {
                                    Text("Clear")
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val subj = selectedSubject ?: return@Button
                                onSubmitExcuse(subj, reason, pickedAttachment)
                                reason = ""
                                pickedAttachment = null
                            },
                            enabled = !isBusy && selectedSubject != null && reason.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("submit_excuse_btn"),
                        ) {
                            Text("Submit excuse letter")
                        }
                    }
                }
            }
        }

        if (excuseLetters.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No excuse letters yet",
                    subtitle = "Submitted excuse letters and review updates appear here.",
                    icon = Icons.Rounded.Description,
                )
            }
        } else {
            items(excuseLetters, key = { it.id }) { letter ->
                ExcuseLetterCard(
                    letter = letter,
                    canReview = canReviewExcuses && (letter.status == "Pending" || activeRole == UserRole.ADMIN),
                    isBusy = isBusy,
                    onReview = { status, note -> onReviewExcuse(letter.id, status, note) },
                    onPreviewImage = { bmp, title -> lightboxData = bmp to title },
                    onOpenPdf = { name, mime, b64 ->
                        AttachmentUtils.openBase64File(context, name, mime, b64)
                            .onFailure { onToast("Install a PDF viewer app to open $name.") }
                    },
                )
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
}

@Composable
private fun ExcuseLetterCard(
    letter: ExcuseLetter,
    canReview: Boolean,
    isBusy: Boolean,
    onReview: (status: String, reviewNote: String) -> Unit,
    onPreviewImage: (Bitmap, String) -> Unit,
    onOpenPdf: (name: String, mime: String, base64: String) -> Unit,
) {
    var reviewNote by remember(letter.id) { mutableStateOf(letter.reviewNote.orEmpty()) }
    val dateStr = letter.createdAt?.toDate()?.let {
        SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(it)
    } ?: "Just now"
    val imgBitmap = remember(letter.attachmentBase64, letter.attachmentType) {
        if (letter.attachmentType?.startsWith("image/") == true) {
            AttachmentUtils.decodeBase64ToBitmap(letter.attachmentBase64)
        } else null
    }

    CstrackerCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${letter.studentName} (${letter.studentId.ifBlank { "—" }})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${letter.subjectCode}${letter.subjectName?.let { " · $it" } ?: ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusPill(letter.status)
            }

            Text(letter.reason, style = MaterialTheme.typography.bodyMedium)
            Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (imgBitmap != null) {
                Image(
                    bitmap = imgBitmap.asImageBitmap(),
                    contentDescription = letter.attachmentName ?: "Excuse attachment",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPreviewImage(imgBitmap, letter.attachmentName ?: "Excuse Attachment") },
                )
            } else if (!letter.attachmentName.isNullOrBlank() && !letter.attachmentBase64.isNullOrBlank()) {
                OutlinedButton(
                    onClick = {
                        onOpenPdf(
                            letter.attachmentName,
                            letter.attachmentType ?: "application/pdf",
                            letter.attachmentBase64,
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(letter.attachmentName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (!letter.reviewerName.isNullOrBlank() || !letter.reviewNote.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (!letter.reviewerName.isNullOrBlank()) {
                            Text(
                                "Reviewed by ${letter.reviewerName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (!letter.reviewNote.isNullOrBlank()) {
                            Text(letter.reviewNote, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (canReview) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OutlinedTextField(
                    value = reviewNote,
                    onValueChange = { reviewNote = it.take(500) },
                    label = { Text("Reviewer note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onReview("Approved", reviewNote) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("approve_excuse_btn_${letter.id}"),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Approve")
                    }
                    OutlinedButton(
                        onClick = { onReview("Rejected", reviewNote) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("reject_excuse_btn_${letter.id}"),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(4.dp))
                        Text("Reject", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagesTab(
    profile: UserProfile,
    messages: List<DirectMessage>,
    directory: List<DirectoryEntry>,
    isBusy: Boolean,
    onSendMessage: (receiver: DirectoryEntry, message: String) -> Unit,
    onMarkMessageRead: (messageId: String) -> Unit,
) {
    val recipients = remember(directory, profile.userId) {
        directory.filter { it.userId != profile.userId }
    }
    var selectedRecipient by remember(recipients) { mutableStateOf(recipients.firstOrNull()) }
    var recipientExpanded by remember { mutableStateOf(false) }
    var messageText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(messages, profile.userId) {
        messages.filter { it.receiverUid == profile.userId && !it.read }
            .forEach { onMarkMessageRead(it.id) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                title = "Direct messages",
                subtitle = "Message class officers, professors, or classmates.",
            )
        }

        item {
            CstrackerCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("New message", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (recipients.isEmpty()) {
                        Text(
                            "No recipients available yet. Staff accounts and classmates appear here once enrolled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = recipientExpanded,
                            onExpandedChange = { recipientExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = selectedRecipient?.let {
                                    "${it.displayName} (${UserRole.fromKey(it.role).label})"
                                } ?: "Select recipient",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("To") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(recipientExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(
                                expanded = recipientExpanded,
                                onDismissRequest = { recipientExpanded = false },
                            ) {
                                recipients.forEach { entry ->
                                    DropdownMenuItem(
                                        text = {
                                            Text("${entry.displayName} (${UserRole.fromKey(entry.role).label})")
                                        },
                                        onClick = {
                                            selectedRecipient = entry
                                            recipientExpanded = false
                                        },
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it.take(1000) },
                            label = { Text("Message") },
                            minLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("message_compose_input"),
                        )

                        Button(
                            onClick = {
                                val target = selectedRecipient ?: return@Button
                                onSendMessage(target, messageText)
                                messageText = ""
                            },
                            enabled = !isBusy && selectedRecipient != null && messageText.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("send_message_btn"),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Send message")
                        }
                    }
                }
            }
        }

        if (messages.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No messages yet",
                    subtitle = "Your direct message conversations will show up here.",
                    icon = Icons.Rounded.MailOutline,
                )
            }
        } else {
            items(messages, key = { it.id }) { msg ->
                val isSentByMe = msg.senderUid == profile.userId
                val dateStr = msg.createdAt?.toDate()?.let {
                    SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(it)
                } ?: "Just now"

                CstrackerCard {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isSentByMe) "To: ${msg.receiverName}" else "From: ${msg.senderName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (!isSentByMe && !msg.read) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(
                                            "New",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(msg.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileTab(
    profile: UserProfile,
    activeRole: UserRole,
    isDarkTheme: Boolean,
    isBusy: Boolean,
    onUpdateProfile: (
        firstName: String,
        middleName: String,
        lastName: String,
        studentId: String,
        campusEmail: String,
        guardianGmail: String,
        irregular: Boolean,
        subjectsRaw: String,
    ) -> Unit,
    onUpdateProfilePhoto: (photoBase64: String?) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onSwitchRole: (UserRole) -> Unit,
    onSignOut: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    var firstName by remember(profile.userId, profile.firstName) { mutableStateOf(profile.firstName) }
    var middleName by remember(profile.userId, profile.middleName) { mutableStateOf(profile.middleName.orEmpty()) }
    var lastName by remember(profile.userId, profile.lastName) { mutableStateOf(profile.lastName) }
    var studentId by remember(profile.userId, profile.studentId) { mutableStateOf(profile.studentId) }
    var campusEmail by remember(profile.userId, profile.campusEmail) { mutableStateOf(profile.campusEmail) }
    var guardianGmail by remember(profile.userId, profile.guardianGmail) { mutableStateOf(profile.guardianGmail.orEmpty()) }
    var irregular by remember(profile.userId, profile.irregular) { mutableStateOf(profile.irregular) }
    var subjectsRaw by remember(profile.userId, profile.enrolledSubjects) {
        mutableStateOf(profile.enrolledSubjects.joinToString(", "))
    }
    var showSignOutDialog by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val picked = AttachmentUtils.compressImageToBase64(context, uri, maxDimension = 512, maxBase64Length = 180_000)
            if (picked != null) onUpdateProfilePhoto(picked.base64)
            else onToast("Could not process profile photo.")
        }
    }

    val availableRoles = profile.availableRoles()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                title = "My profile",
                subtitle = "Manage your campus details, theme, and account access.",
            )
        }

        item {
            CstrackerCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        UserAvatar(
                            initials = profile.initials,
                            photoBase64 = profile.profilePhotoBase64,
                            size = 68.dp,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(profile.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${activeRole.label} (Rank ${activeRole.rank})${profile.subrole?.let { " · ${UserRole.fromKey(it).label}" } ?: ""}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            val pendingRole = profile.requestedRole?.takeIf { it.isNotBlank() }?.let { UserRole.fromKey(it) }
                            if (pendingRole != null && pendingRole != UserRole.fromKey(profile.role)) {
                                Text(
                                    "Pending ${pendingRole.label} registration (awaiting Admin approval)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("change_photo_btn"),
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Change photo")
                        }
                        if (!profile.profilePhotoBase64.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { onUpdateProfilePhoto(null) },
                                enabled = !isBusy,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        if (availableRoles.size > 1) {
            item {
                CstrackerCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Active role view", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            availableRoles.forEach { role ->
                                AssistChip(
                                    onClick = { onSwitchRole(role) },
                                    label = {
                                        Text(
                                            text = if (role == activeRole) "${role.label} (Active)" else role.label,
                                            fontWeight = if (role == activeRole) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            RolePermissionsCard(activeRole = activeRole)
        }

        item {
            CstrackerCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Campus information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = studentId,
                        onValueChange = { studentId = it.filter { ch -> ch.isDigit() }.take(12) },
                        label = { Text("Student ID (5-12 digits)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("profile_student_id"),
                    )
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it.take(50) },
                        label = { Text("First name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("profile_first_name"),
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
                        modifier = Modifier.fillMaxWidth().testTag("profile_last_name"),
                    )
                    OutlinedTextField(
                        value = campusEmail,
                        onValueChange = { campusEmail = it.take(100) },
                        label = { Text("Campus email (@cvsu.edu.ph)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = guardianGmail,
                        onValueChange = { guardianGmail = it.take(100) },
                        label = { Text("Guardian email (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Irregular student", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Specify enrolled subjects manually",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = irregular, onCheckedChange = { irregular = it })
                    }
                    if (irregular) {
                        OutlinedTextField(
                            value = subjectsRaw,
                            onValueChange = { subjectsRaw = it.take(200) },
                            label = { Text("Enrolled subjects (comma-separated)") },
                            placeholder = { Text("COSC75, MATH3, COSC80") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Button(
                        onClick = {
                            onUpdateProfile(
                                firstName,
                                middleName,
                                lastName,
                                studentId,
                                campusEmail,
                                guardianGmail,
                                irregular,
                                subjectsRaw,
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("save_profile_btn"),
                    ) {
                        Text("Save changes")
                    }
                }
            }
        }

        item {
            CstrackerCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Dark mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Switch between light and dark themes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = onToggleDarkTheme,
                            modifier = Modifier.testTag("dark_mode_switch"),
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    OutlinedButton(
                        onClick = { showSignOutDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("sign_out_btn"),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign out")
                    }
                }
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out of CSTRACKER?") },
            text = { Text("You will need to sign back in with Google to access your class tracker.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Sign out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            },
        )
    }
}

private data class RolePreset(
    val label: String,
    val role: String,
    val subrole: String?,
)

private val adminRolePresets = listOf(
    RolePreset("Admin (Rank 1)", "admin", null),
    RolePreset("Professor (Rank 2)", "professor", null),
    RolePreset("Moderator (Rank 3)", "moderator", null),
    RolePreset("Class rep (Rank 4)", "class_rep", null),
    RolePreset("Student + Moderator", "student", "moderator"),
    RolePreset("Student + Class rep", "student", "class_rep"),
    RolePreset("Student (Rank 5)", "student", null),
)

@Composable
private fun RolePermissionsCard(activeRole: UserRole) {
    val capabilities = listOf(
        "View schedule, calendar, announcements" to { r: UserRole -> r.canViewCore },
        "Scan QR attendance" to { r: UserRole -> r.canScanQrAttendance },
        "Post announcements and calendar events" to { r: UserRole -> r.canPostAnnouncementsAndEvents },
        "Upload class files" to { r: UserRole -> r.canUploadClassFiles },
        "Review excuse letters and issue warnings" to { r: UserRole -> r.canReviewExcusesAndWarn },
        "Edit schedule and manage user accounts" to { r: UserRole -> r.canEditScheduleAndManageUsers },
    )
    var showMatrix by rememberSaveable { mutableStateOf(false) }

    CstrackerCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Role-based access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${activeRole.label} · Access rank ${activeRole.rank} (1 = highest)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(onClick = { showMatrix = !showMatrix }) {
                    Text(if (showMatrix) "Hide all" else "Compare all")
                }
            }

            capabilities.forEach { (label, check) ->
                val allowed = check(activeRole)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (allowed) "✓" else "–",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (showMatrix) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("All roles by rank", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                UserRole.rankedEntries.forEach { role ->
                    val summary = buildList {
                        add("View")
                        if (role.canScanQrAttendance) add("Scan QR")
                        if (role.canPostAnnouncementsAndEvents) add("Post Updates & Events")
                        if (role.canUploadClassFiles) add("Upload Files")
                        if (role.canReviewExcusesAndWarn) add("Review Excuses & Warn")
                        if (role.canEditScheduleAndManageUsers) add("Edit Schedule & Users")
                    }.joinToString(" · ")
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (role == activeRole) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Rank ${role.rank} · ${role.label}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (role == activeRole) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminTab(
    currentUid: String,
    adminUsers: List<UserProfile>,
    roleInvites: List<RoleInvite>,
    isBusy: Boolean,
    onUpdateUserRole: (targetUid: String, newRole: String, newSubrole: String?) -> Unit,
    onDeclineRoleRequest: (targetUid: String) -> Unit,
    onSaveRoleInvite: (email: String, role: String, note: String) -> Unit,
    onDeleteRoleInvite: (email: String) -> Unit,
    onSeedCurriculum: () -> Unit,
) {
    val totalCount = adminUsers.size
    val profCount = adminUsers.count { it.role == "professor" }
    val modCount = adminUsers.count { it.role == "moderator" || it.subrole == "moderator" }
    val repCount = adminUsers.count {
        UserRole.fromKey(it.role) == UserRole.CLASS_REP ||
            it.subrole?.let { sub -> UserRole.fromKey(sub) == UserRole.CLASS_REP } == true
    }
    val pendingRequests = adminUsers.filter { user ->
        val req = user.requestedRole?.takeIf { it.isNotBlank() }?.let { UserRole.fromKey(it) }
        req != null && req != UserRole.fromKey(user.role)
    }

    var inviteEmail by rememberSaveable { mutableStateOf("") }
    var inviteNote by rememberSaveable { mutableStateOf("") }
    var inviteRoleKey by rememberSaveable { mutableStateOf(UserRole.PROFESSOR.key) }
    var inviteExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                title = "Administration",
                subtitle = "Manage account roles (Ranks 1–5), role invites, approvals, and curriculum.",
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminMetricTile("Accounts", totalCount, Modifier.weight(1f))
                AdminMetricTile("Professors", profCount, Modifier.weight(1f))
                AdminMetricTile("Moderators", modCount, Modifier.weight(1f))
                AdminMetricTile("Class reps", repCount, Modifier.weight(1f))
            }
        }

        if (pendingRequests.isNotEmpty()) {
            item {
                Text(
                    "Pending role registrations (${pendingRequests.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(pendingRequests, key = { "req_${it.userId}" }) { reqUser ->
                val targetRole = UserRole.fromKey(reqUser.requestedRole)
                CstrackerCard {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            UserAvatar(initials = reqUser.initials, photoBase64 = reqUser.profilePhotoBase64, size = 40.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(reqUser.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Requested: ${targetRole.label} (Rank ${targetRole.rank}) · ${reqUser.campusEmail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onUpdateUserRole(reqUser.userId, targetRole.key, null) },
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Approve ${targetRole.label}")
                            }
                            OutlinedButton(
                                onClick = { onDeclineRoleRequest(reqUser.userId) },
                                enabled = !isBusy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Decline")
                            }
                        }
                    }
                }
            }
        }

        item {
            CstrackerCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pre-approve role by email", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Invited Google emails receive their assigned role automatically when they register through that portal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = inviteEmail,
                        onValueChange = { inviteEmail = it.take(100) },
                        label = { Text("Google account email") },
                        placeholder = { Text("professor@gmail.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth().testTag("invite_email_input"),
                    )
                    ExposedDropdownMenuBox(
                        expanded = inviteExpanded,
                        onExpandedChange = { inviteExpanded = it },
                    ) {
                        val selectedRole = UserRole.fromKey(inviteRoleKey)
                        OutlinedTextField(
                            value = "${selectedRole.label} (Rank ${selectedRole.rank})",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Invited role") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(inviteExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = inviteExpanded,
                            onDismissRequest = { inviteExpanded = false },
                        ) {
                            UserRole.rankedEntries.forEach { roleOption ->
                                DropdownMenuItem(
                                    text = { Text("${roleOption.label} (Rank ${roleOption.rank})") },
                                    onClick = {
                                        inviteRoleKey = roleOption.key
                                        inviteExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = inviteNote,
                        onValueChange = { inviteNote = it.take(200) },
                        label = { Text("Note (optional)") },
                        placeholder = { Text("CS Faculty / Section Officer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            val targetEmail = inviteEmail
                            val targetNote = inviteNote
                            onSaveRoleInvite(targetEmail, inviteRoleKey, targetNote)
                            inviteEmail = ""
                            inviteNote = ""
                        },
                        enabled = !isBusy && inviteEmail.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_invite_btn"),
                    ) {
                        Text("Save pre-approved invite")
                    }

                    if (roleInvites.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text("Active email invites (${roleInvites.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        roleInvites.forEach { invite ->
                            val r = UserRole.fromKey(invite.role)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(invite.email, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${r.label} (Rank ${r.rank})${invite.note?.let { " · $it" } ?: ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { onDeleteRoleInvite(invite.email) }, enabled = !isBusy) {
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            CstrackerCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Class curriculum", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Populate Firestore with the 7 core CS subjects and 12 weekly class slots if empty.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onSeedCurriculum,
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("sync_curriculum_btn"),
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sync default CS schedule")
                    }
                }
            }
        }

        items(adminUsers, key = { it.userId }) { user ->
            AdminUserRoleCard(
                user = user,
                isSelf = user.userId == currentUid,
                isBusy = isBusy,
                onSaveRole = { role, subrole -> onUpdateUserRole(user.userId, role, subrole) },
            )
        }
    }
}

@Composable
private fun AdminMetricTile(label: String, count: Int, modifier: Modifier = Modifier) {
    CstrackerCard(modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminUserRoleCard(
    user: UserProfile,
    isSelf: Boolean,
    isBusy: Boolean,
    onSaveRole: (role: String, subrole: String?) -> Unit,
) {
    val currentPreset = remember(user.role, user.subrole) {
        val normalizedRole = UserRole.fromKey(user.role).key
        val normalizedSubrole = user.subrole?.let { UserRole.fromKey(it).key }
        adminRolePresets.firstOrNull { it.role == normalizedRole && it.subrole == normalizedSubrole }
            ?: adminRolePresets.firstOrNull { it.role == normalizedRole && it.subrole == null }
            ?: adminRolePresets.first()
    }
    var selectedPreset by remember(user.userId, user.role, user.subrole) { mutableStateOf(currentPreset) }
    var expanded by remember { mutableStateOf(false) }
    val hasChanges = selectedPreset.role != UserRole.fromKey(user.role).key ||
        selectedPreset.subrole != user.subrole?.let { UserRole.fromKey(it).key }

    CstrackerCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                UserAvatar(initials = user.initials, photoBase64 = user.profilePhotoBase64, size = 44.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSelf) "${user.fullName} (You)" else user.fullName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "ID: ${user.studentId.ifBlank { "—" }} · ${user.campusEmail}",
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (!isSelf) expanded = it },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = selectedPreset.label,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isSelf,
                        label = { Text("Assigned role") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        adminRolePresets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label) },
                                onClick = {
                                    selectedPreset = preset
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                Button(
                    onClick = { onSaveRole(selectedPreset.role, selectedPreset.subrole) },
                    enabled = !isSelf && !isBusy && hasChanges,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Save")
                }
            }
        }
    }
}
