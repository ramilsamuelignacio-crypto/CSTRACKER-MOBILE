package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.Subject
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.CstrackerCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PageHeader
import com.example.ui.components.StatusPill
import com.example.util.ParsedQrContent
import com.example.util.QrUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceQrScreen(
    profile: UserProfile,
    activeRole: UserRole,
    subjects: List<Subject>,
    attendance: List<AttendanceRecord>,
    isBusy: Boolean,
    initialTab: Int = 0,
    onScanQr: (rawQr: String, subject: Subject, classDate: String, status: String, onDone: (Boolean, String) -> Unit) -> Unit,
    onStudentScanQr: (rawQr: String, fallbackSubject: Subject?, classDate: String, onDone: (Boolean, String) -> Unit) -> Unit,
    onToast: (String) -> Unit,
) {
    val canScan = activeRole.canScanQrAttendance
    var selectedTab by rememberSaveable(initialTab) {
        mutableIntStateOf(initialTab.coerceIn(0, 2))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Attendance") },
                modifier = Modifier.testTag("tab_attendance"),
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("My QR") },
                modifier = Modifier.testTag("tab_my_qr"),
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(if (canScan) "QR Scanner" else "Scan QR") },
                modifier = Modifier.testTag("tab_qr_scanner"),
            )
        }

        when (selectedTab) {
            0 -> AttendanceListTab(
                isStaff = activeRole.canReviewExcusesAndWarn,
                attendance = attendance,
            )
            1 -> StudentQrTab(
                profile = profile,
                onToast = onToast,
            )
            2 -> if (canScan) {
                ProfessorScannerTab(
                    profile = profile,
                    subjects = subjects,
                    isBusy = isBusy,
                    onScanQr = onScanQr,
                    onToast = onToast,
                )
            } else {
                StudentCheckInScannerTab(
                    profile = profile,
                    subjects = subjects,
                    isBusy = isBusy,
                    onStudentScanQr = onStudentScanQr,
                    onToast = onToast,
                )
            }
        }
    }
}

@Composable
private fun AttendanceListTab(
    isStaff: Boolean,
    attendance: List<AttendanceRecord>,
) {
    val presentCount = attendance.count { it.status == "Present" }
    val lateCount = attendance.count { it.status == "Late" }
    val absentCount = attendance.count { it.status == "Absent" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                title = "Attendance",
                subtitle = if (isStaff) "Class attendance records." else "Your recorded class attendance.",
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniCountCard("Present", presentCount, Modifier.weight(1f))
                MiniCountCard("Late", lateCount, Modifier.weight(1f))
                MiniCountCard("Absent", absentCount, Modifier.weight(1f))
            }
        }
        if (attendance.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No attendance records yet",
                    subtitle = "Scan a class QR code or have your professor scan your personal QR pass.",
                    icon = Icons.AutoMirrored.Rounded.FactCheck,
                )
            }
        } else {
            items(attendance, key = { it.id }) { row ->
                CstrackerCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (isStaff) {
                                Text(
                                    "${row.studentName} (${row.studentId.ifBlank { "—" }})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                "${row.subjectCode}${row.subjectName?.let { " · $it" } ?: ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val methodNote = if (row.checkInMethod == "camera_qr_self") "QR Check-In" else "Verified"
                            Text(
                                "${row.classDate} · $methodNote by ${row.verifiedByName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        StatusPill(row.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniCountCard(label: String, count: Int, modifier: Modifier = Modifier) {
    CstrackerCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StudentQrTab(
    profile: UserProfile,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val payload = remember(profile.qrTokenHash) { QrUtils.buildQrPayload(profile.qrTokenHash) }
    val qrBitmap = remember(payload) { QrUtils.generateQrBitmap(payload, sizePx = 640) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeader(
                title = "My QR code",
                subtitle = "Show this to your professor for fast attendance check-in.",
            )
        }
        item {
            CstrackerCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (qrBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White,
                            shadowElevation = 4.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Student attendance QR code",
                                modifier = Modifier.size(240.dp).padding(12.dp),
                            )
                        }
                    } else {
                        Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(120.dp))
                    }

                    Text(profile.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Student ID: ${profile.studentId.ifBlank { "—" }}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(payload))
                                onToast("QR code data copied.")
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("copy_qr_button"),
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy QR")
                        }
                        Button(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, payload)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share CSTRACKER QR"))
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("share_qr_button"),
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                    }

                    Text(
                        "Keep your personal QR pass private — do not post it publicly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentCheckInScannerTab(
    profile: UserProfile,
    subjects: List<Subject>,
    isBusy: Boolean,
    onStudentScanQr: (rawQr: String, fallbackSubject: Subject?, classDate: String, onDone: (Boolean, String) -> Unit) -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    var fallbackSubject by remember(subjects) { mutableStateOf(subjects.firstOrNull()) }
    var subjExpanded by remember { mutableStateOf(false) }
    var classDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }
    var manualPayload by remember { mutableStateOf("") }
    var cameraEnabled by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var lastScannedCode by remember { mutableStateOf("") }
    var lastScannedAt by remember { mutableLongStateOf(0L) }
    var lastParsedSummary by remember { mutableStateOf<String?>(null) }
    val scanLogs = remember { mutableStateListOf<Pair<Boolean, String>>() }
    val validCodes = remember(subjects) { subjects.map { it.code.uppercase() }.toSet() }

    fun submitStudentScan(raw: String) {
        val parsed = QrUtils.parseQrString(raw, validCodes)
        lastParsedSummary = when (parsed) {
            is ParsedQrContent.ClassCheckIn -> {
                val d = parsed.classDate ?: classDate
                val s = parsed.status ?: "Present"
                "Parsed: ${parsed.subjectCode} · $d · $s"
            }
            is ParsedQrContent.StudentPass -> "Parsed: Personal Student Pass (cannot self-check-in)"
            null -> "Unrecognized QR format: ${raw.take(48)}"
        }
        onStudentScanQr(raw, fallbackSubject, classDate) { ok, msg ->
            scanLogs.add(0, ok to msg)
            if (scanLogs.size > 12) scanLogs.removeAt(scanLogs.lastIndex)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (granted) {
            cameraEnabled = true
        } else {
            onToast("Camera permission denied. You can still pick a QR image or paste QR data.")
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bmp = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            }.getOrNull()
            val decoded = bmp?.let { QrUtils.decodeFromBitmap(it) }
            if (decoded != null) {
                submitStudentScan(decoded)
            } else {
                onToast("No QR code found in that image.")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                title = "Scan Class QR",
                subtitle = "Scan your class attendance QR with CameraX to save check-in to your student profile.",
            )
        }

        item {
            CstrackerCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Rounded.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                "Linked student: ${profile.fullName} (${profile.studentId.ifBlank { profile.userId }})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (cameraEnabled) {
                                    cameraEnabled = false
                                } else if (hasCameraPermission) {
                                    cameraEnabled = true
                                } else {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("toggle_student_camera_scanner_btn"),
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (cameraEnabled) "Stop camera" else "Start camera")
                        }
                        OutlinedButton(
                            onClick = {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pick QR image")
                        }
                    }

                    if (cameraEnabled && hasCameraPermission) {
                        CameraQrPreviewBox(
                            onQrDetected = { raw ->
                                val now = System.currentTimeMillis()
                                if (raw != lastScannedCode || now - lastScannedAt > 3500L) {
                                    lastScannedCode = raw
                                    lastScannedAt = now
                                    submitStudentScan(raw)
                                }
                            },
                        )
                    }

                    if (isBusy) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Saving attendance to Firestore...", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    lastParsedSummary?.let { summary ->
                        Text(
                            summary,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    if (subjects.isNotEmpty()) {
                        ExposedDropdownMenuBox(expanded = subjExpanded, onExpandedChange = { subjExpanded = it }) {
                            OutlinedTextField(
                                value = fallbackSubject?.let { "${it.code} — ${it.name}" } ?: "Select subject",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Default Subject (used if QR omits subject)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(subjExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(expanded = subjExpanded, onDismissRequest = { subjExpanded = false }) {
                                subjects.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text("${s.code} — ${s.name}") },
                                        onClick = { fallbackSubject = s; subjExpanded = false },
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = classDate,
                        onValueChange = { classDate = it.take(10) },
                        label = { Text("Default Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = manualPayload,
                        onValueChange = { manualPayload = it.take(250) },
                        label = { Text("Or paste class QR string") },
                        placeholder = { Text("e.g. cstracker://attendance?subject=COSC75&date=$classDate") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("student_manual_qr_input"),
                    )

                    Button(
                        onClick = {
                            if (manualPayload.isNotBlank()) {
                                submitStudentScan(manualPayload)
                                manualPayload = ""
                            }
                        },
                        enabled = !isBusy && manualPayload.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("student_save_qr_btn"),
                    ) {
                        Text("Parse & Save Check-In")
                    }
                }
            }
        }

        if (scanLogs.isNotEmpty()) {
            item {
                ScanLogListCard(scanLogs = scanLogs)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfessorScannerTab(
    profile: UserProfile,
    subjects: List<Subject>,
    isBusy: Boolean,
    onScanQr: (rawQr: String, subject: Subject, classDate: String, status: String, onDone: (Boolean, String) -> Unit) -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var selectedSubject by remember(subjects) { mutableStateOf(subjects.firstOrNull()) }
    var subjExpanded by remember { mutableStateOf(false) }
    var selectedStatus by remember { mutableStateOf(AttendanceStatus.PRESENT) }
    var classDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }
    var manualPayload by remember { mutableStateOf("") }
    var cameraEnabled by remember { mutableStateOf(false) }
    var showClassQr by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var lastScannedCode by remember { mutableStateOf("") }
    var lastScannedAt by remember { mutableLongStateOf(0L) }
    val scanLogs = remember { mutableStateListOf<Pair<Boolean, String>>() }

    fun submitScan(raw: String) {
        val subject = selectedSubject
        if (subject == null) {
            onToast("No subjects available yet.")
            return
        }
        onScanQr(raw, subject, classDate, selectedStatus.label) { ok, msg ->
            scanLogs.add(0, ok to msg)
            if (scanLogs.size > 12) scanLogs.removeAt(scanLogs.lastIndex)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (granted) {
            cameraEnabled = true
        } else {
            onToast("Camera permission denied. You can still paste QR data or pick a QR image.")
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bmp = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            }.getOrNull()
            val decoded = bmp?.let { QrUtils.decodeFromBitmap(it) }
            if (decoded != null) {
                submitScan(decoded)
            } else {
                onToast("No QR code found in that image.")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                title = "QR Scanner",
                subtitle = "Scan student QR passes or display a class check-in QR for students.",
            )
        }

        item {
            CstrackerCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExposedDropdownMenuBox(expanded = subjExpanded, onExpandedChange = { subjExpanded = it }) {
                        OutlinedTextField(
                            value = selectedSubject?.let { "${it.code} — ${it.name}" } ?: "No subjects configured",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Subject") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(subjExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = subjExpanded, onDismissRequest = { subjExpanded = false }) {
                            subjects.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s.code} — ${s.name}") },
                                    onClick = { selectedSubject = s; subjExpanded = false },
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AttendanceStatus.entries.forEach { st ->
                            FilterChip(
                                selected = selectedStatus == st,
                                onClick = { selectedStatus = st },
                                label = { Text(st.label) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = classDate,
                        onValueChange = { classDate = it.take(10) },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (cameraEnabled) {
                                    cameraEnabled = false
                                } else if (hasCameraPermission) {
                                    cameraEnabled = true
                                } else {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("toggle_camera_scanner_btn"),
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (cameraEnabled) "Stop camera" else "Start camera")
                        }
                        OutlinedButton(
                            onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pick QR image")
                        }
                    }

                    if (cameraEnabled && hasCameraPermission) {
                        CameraQrPreviewBox(
                            onQrDetected = { raw ->
                                val now = System.currentTimeMillis()
                                if (raw != lastScannedCode || now - lastScannedAt > 3500L) {
                                    lastScannedCode = raw
                                    lastScannedAt = now
                                    submitScan(raw)
                                }
                            },
                        )
                    }

                    OutlinedTextField(
                        value = manualPayload,
                        onValueChange = { manualPayload = it.take(200) },
                        label = { Text("Or paste QR data / token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("manual_qr_input"),
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (manualPayload.isNotBlank()) {
                                    submitScan(manualPayload)
                                    manualPayload = ""
                                }
                            },
                            enabled = !isBusy && manualPayload.isNotBlank() && selectedSubject != null,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save_manual_qr_btn"),
                        ) {
                            Text("Save attendance")
                        }
                        OutlinedButton(
                            onClick = {
                                if (profile.qrTokenHash.length == 64) {
                                    submitScan(QrUtils.buildQrPayload(profile.qrTokenHash))
                                }
                            },
                            enabled = !isBusy && profile.qrTokenHash.length == 64 && selectedSubject != null,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Test my QR")
                        }
                    }

                    OutlinedButton(
                        onClick = { showClassQr = !showClassQr },
                        enabled = selectedSubject != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("toggle_class_checkin_qr_btn"),
                    ) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (showClassQr) "Hide Class Check-In QR" else "Show Class Check-In QR for Students")
                    }
                }
            }
        }

        if (showClassQr && selectedSubject != null) {
            item {
                val subj = selectedSubject!!
                val checkInStatus = if (selectedStatus == AttendanceStatus.LATE) "Late" else "Present"
                val checkInPayload = remember(subj.code, classDate, checkInStatus) {
                    QrUtils.buildCheckInQrPayload(
                        subjectCode = subj.code,
                        classDate = classDate,
                        status = checkInStatus,
                    )
                }
                val checkInBitmap = remember(checkInPayload) { QrUtils.generateQrBitmap(checkInPayload, sizePx = 560) }
                CstrackerCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Class Check-In QR · ${subj.code}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "$classDate · Marks $checkInStatus when scanned by students",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (checkInBitmap != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Image(
                                    bitmap = checkInBitmap.asImageBitmap(),
                                    contentDescription = "Class attendance QR code for ${subj.code}",
                                    modifier = Modifier.size(210.dp).padding(10.dp),
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(checkInPayload))
                                onToast("Class QR string copied.")
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Class QR String")
                        }
                    }
                }
            }
        }

        if (scanLogs.isNotEmpty()) {
            item {
                ScanLogListCard(scanLogs = scanLogs)
            }
        }
    }
}

@Composable
private fun ScanLogListCard(scanLogs: List<Pair<Boolean, String>>) {
    CstrackerCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recent scans", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            scanLogs.forEach { (ok, msg) ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (ok) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(msg, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraQrPreviewBox(
    onQrDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var boundProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { boundProvider?.unbindAll() }
            executor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    boundProvider = provider
                    val preview = Preview.Builder().build().apply {
                        surfaceProvider = previewView.surfaceProvider
                    }
                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            )
                        )
                        .build()
                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(executor) { imageProxy ->
                        try {
                            val decoded = QrUtils.decodeFromImageProxy(imageProxy)
                            if (decoded != null) {
                                ContextCompat.getMainExecutor(context).execute {
                                    onQrDetected(decoded)
                                }
                            }
                        } finally {
                            imageProxy.close()
                        }
                    }

                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(160.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
        )
    }
}
