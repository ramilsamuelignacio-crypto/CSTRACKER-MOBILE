package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CalendarEvent
import com.example.data.model.DefaultCurriculum
import com.example.data.model.EventType
import com.example.data.model.ScheduleSlot
import com.example.data.model.Subject
import com.example.data.model.UserRole
import com.example.ui.components.CstrackerCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PageHeader
import com.example.ui.components.eventColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleCalendarScreen(
    activeRole: UserRole,
    subjects: List<Subject>,
    schedules: List<ScheduleSlot>,
    calendarEvents: List<CalendarEvent>,
    onAddScheduleSlot: (day: String, subjectCode: String, start: String, end: String, room: String) -> Unit,
    onDeleteScheduleSlot: (String) -> Unit,
    onSaveCalendarEvent: (existing: CalendarEvent?, date: String, type: String, title: String, description: String) -> Unit,
    onDeleteCalendarEvent: (String) -> Unit,
    fixedTab: Int? = null,
    scheduleQuickActions: (@Composable () -> Unit)? = null,
) {
    var localTab by rememberSaveable { mutableIntStateOf(fixedTab ?: 0) }
    val selectedTab = fixedTab ?: localTab
    var showAddSchedule by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var showEventDialog by remember { mutableStateOf(false) }

    val todayIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time) }
    var selectedDateIso by rememberSaveable { mutableStateOf(todayIso) }

    val isAdmin = activeRole.canEditScheduleAndManageUsers
    val isStaff = activeRole.canPostAnnouncementsAndEvents

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (fixedTab == null) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { localTab = 0 },
                        text = { Text("Schedule") },
                        modifier = Modifier.testTag("tab_schedule"),
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { localTab = 1 },
                        text = { Text("Calendar") },
                        modifier = Modifier.testTag("tab_calendar"),
                    )
                }
            }

            if (selectedTab == 0) {
                ScheduleTabContent(
                    subjects = subjects,
                    schedules = schedules,
                    canEdit = isAdmin,
                    onDeleteSlot = onDeleteScheduleSlot,
                    quickActions = scheduleQuickActions,
                )
            } else {
                CalendarTabContent(
                    subjects = subjects,
                    schedules = schedules,
                    events = calendarEvents,
                    todayIso = todayIso,
                    selectedDateIso = selectedDateIso,
                    onSelectDate = { selectedDateIso = it },
                    canEdit = isStaff,
                    onEditEvent = { evt ->
                        editingEvent = evt
                        showEventDialog = true
                    },
                    onDeleteEvent = onDeleteCalendarEvent,
                )
            }
        }

        if (selectedTab == 0 && isAdmin) {
            FloatingActionButton(
                onClick = { showAddSchedule = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("fab_add_schedule"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add class slot")
            }
        } else if (selectedTab == 1 && isStaff) {
            FloatingActionButton(
                onClick = {
                    editingEvent = null
                    showEventDialog = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("fab_add_event"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add calendar event")
            }
        }
    }

    if (showAddSchedule) {
        AddScheduleDialog(
            subjects = subjects,
            onDismiss = { showAddSchedule = false },
            onConfirm = { day, code, start, end, room ->
                onAddScheduleSlot(day, code, start, end, room)
                showAddSchedule = false
            },
        )
    }

    if (showEventDialog) {
        EventEditorDialog(
            existing = editingEvent,
            defaultDate = selectedDateIso,
            onDismiss = { showEventDialog = false },
            onConfirm = { date, type, title, desc ->
                onSaveCalendarEvent(editingEvent, date, type, title, desc)
                showEventDialog = false
            },
        )
    }
}

@Composable
private fun ScheduleTabContent(
    subjects: List<Subject>,
    schedules: List<ScheduleSlot>,
    canEdit: Boolean,
    onDeleteSlot: (String) -> Unit,
    quickActions: (@Composable () -> Unit)? = null,
) {
    val subjectMap = subjects.associateBy { it.code }
    val todayName = remember {
        SimpleDateFormat("EEEE", Locale.US).format(Calendar.getInstance().time)
    }
    val orderedDays = remember(todayName) {
        val rest = DefaultCurriculum.weekDays.filter { it != todayName }
        listOf(todayName) + rest
    }
    val todayCount = schedules.count { it.day == todayName }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                title = "Schedule",
                subtitle = "${schedules.size} weekly classes grouped by day.",
            )
        }

        if (quickActions != null) {
            item { quickActions() }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CstrackerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("THIS WEEK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${schedules.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("scheduled classes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                CstrackerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("TODAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text("$todayCount", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(todayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        items(orderedDays) { day ->
            val daySlots = schedules.filter { it.day == day }.sortedBy { it.startTime }
            val isToday = day == todayName
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (isToday) 1.5.dp else 1.dp,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                day.take(3).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(day, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (isToday) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    "Today",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    if (daySlots.isEmpty()) {
                        Text(
                            "Free day — no classes scheduled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        daySlots.forEach { slot ->
                            val subj = subjectMap[slot.subjectCode]
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.width(64.dp)) {
                                        Text(
                                            slot.startTime,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "to ${slot.endTime}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(slot.subjectCode, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        Text(
                                            subj?.name ?: slot.subjectCode,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            buildString {
                                                append("Rm ${slot.room ?: "—"}")
                                                if (!subj?.professor.isNullOrBlank()) {
                                                    append(" · ${subj?.professor}")
                                                }
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (canEdit) {
                                        IconButton(onClick = { onDeleteSlot(slot.id) }) {
                                            Icon(
                                                Icons.Rounded.DeleteOutline,
                                                contentDescription = "Remove class",
                                                tint = MaterialTheme.colorScheme.error,
                                            )
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
}

@Composable
private fun CalendarTabContent(
    subjects: List<Subject>,
    schedules: List<ScheduleSlot>,
    events: List<CalendarEvent>,
    todayIso: String,
    selectedDateIso: String,
    onSelectDate: (String) -> Unit,
    canEdit: Boolean,
    onEditEvent: (CalendarEvent) -> Unit,
    onDeleteEvent: (String) -> Unit,
) {
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    val cursor = remember(monthOffset) {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthOffset)
        }
    }
    val year = cursor.get(Calendar.YEAR)
    val month = cursor.get(Calendar.MONTH)
    val firstDayOfWeek = cursor.get(Calendar.DAY_OF_WEEK) - 1
    val daysInMonth = cursor.getActualMaximum(Calendar.DAY_OF_MONTH)
    val monthLabel = remember(year, month) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cursor.time)
    }

    val selectedEvents = events.filter { it.date == selectedDateIso }.sortedBy { it.type }
    val selectedDayName = remember(selectedDateIso) {
        runCatching {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedDateIso)
            SimpleDateFormat("EEEE", Locale.US).format(d!!)
        }.getOrDefault("")
    }
    val selectedLabel = remember(selectedDateIso) {
        runCatching {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedDateIso)
            SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(d!!)
        }.getOrDefault(selectedDateIso)
    }
    val dayClasses = schedules.filter { it.day == selectedDayName }.sortedBy { it.startTime }
    val subjectMap = subjects.associateBy { it.code }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                title = "Calendar",
                subtitle = "Exams, quizzes, deadlines, holidays, and class sessions.",
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { monthOffset-- }, modifier = Modifier.testTag("cal_prev_month")) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(monthLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        monthOffset = 0
                        onSelectDate(todayIso)
                    }) {
                        Text("Today")
                    }
                }
                IconButton(onClick = { monthOffset++ }, modifier = Modifier.testTag("cal_next_month")) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
        }

        item {
            CstrackerCard {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { d ->
                            Text(
                                text = d,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    val totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7
                    for (rowStart in 0 until totalCells step 7) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (col in 0 until 7) {
                                val cellIndex = rowStart + col
                                val dayNum = cellIndex - firstDayOfWeek + 1
                                if (dayNum < 1 || dayNum > daysInMonth) {
                                    Spacer(Modifier.weight(1f).aspectRatio(1f))
                                } else {
                                    val iso = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayNum)
                                    val dayEvents = events.filter { it.date == iso }
                                    val isToday = iso == todayIso
                                    val isSelected = iso == selectedDateIso
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                    isToday -> MaterialTheme.colorScheme.surfaceVariant
                                                    else -> MaterialTheme.colorScheme.surface
                                                }
                                            )
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(10.dp),
                                            )
                                            .clickable { onSelectDate(iso) }
                                            .padding(4.dp),
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.align(Alignment.TopStart),
                                        )
                                        if (dayEvents.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            ) {
                                                dayEvents.take(3).forEach { evt ->
                                                    val (_, _, dotColor) = eventColors(evt.type)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(dotColor)
                                                    )
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
        }

        item {
            CstrackerCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(selectedLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("EVENTS (${selectedEvents.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (selectedEvents.isEmpty()) {
                        EmptyStateCard("No events on this day", "Select another date or add a class reminder.", icon = Icons.Rounded.Event)
                    } else {
                        selectedEvents.forEach { evt ->
                            val (bg, fg, _) = eventColors(evt.type)
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = bg,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(evt.type.uppercase(), style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Bold)
                                        Text(evt.title, style = MaterialTheme.typography.titleSmall, color = fg, fontWeight = FontWeight.Bold)
                                        if (!evt.description.isNullOrBlank()) {
                                            Text(evt.description, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.85f))
                                        }
                                    }
                                    if (canEdit) {
                                        Row {
                                            IconButton(onClick = { onEditEvent(evt) }) {
                                                Icon(Icons.Rounded.Edit, contentDescription = "Edit event", tint = fg)
                                            }
                                            IconButton(onClick = { onDeleteEvent(evt.id) }) {
                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete event", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text("CLASSES (${dayClasses.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (dayClasses.isEmpty()) {
                        Text("No classes scheduled for $selectedDayName.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        dayClasses.forEach { slot ->
                            val subj = subjectMap[slot.subjectCode]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("${slot.subjectCode} · ${subj?.name ?: ""}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.width(8.dp))
                                Text("${slot.startTime}–${slot.endTime} (Rm ${slot.room ?: "—"})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddScheduleDialog(
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onConfirm: (day: String, subjectCode: String, start: String, end: String, room: String) -> Unit,
) {
    var selectedDay by remember { mutableStateOf("Tuesday") }
    var dayExpanded by remember { mutableStateOf(false) }
    var selectedSubject by remember { mutableStateOf(subjects.firstOrNull()?.code ?: "COSC75") }
    var subjExpanded by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("11:00") }
    var room by remember { mutableStateOf("107") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add class slot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(expanded = dayExpanded, onExpandedChange = { dayExpanded = it }) {
                    OutlinedTextField(
                        value = selectedDay,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Day") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dayExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false }) {
                        DefaultCurriculum.weekDays.forEach { d ->
                            DropdownMenuItem(text = { Text(d) }, onClick = { selectedDay = d; dayExpanded = false })
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = subjExpanded, onExpandedChange = { subjExpanded = it }) {
                    OutlinedTextField(
                        value = selectedSubject,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Subject") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(subjExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = subjExpanded, onDismissRequest = { subjExpanded = false }) {
                        subjects.forEach { s ->
                            DropdownMenuItem(text = { Text("${s.code} — ${s.name}") }, onClick = { selectedSubject = s.code; subjExpanded = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it.take(5) },
                        label = { Text("Start (HH:MM)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it.take(5) },
                        label = { Text("End (HH:MM)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it.take(20) },
                    label = { Text("Room") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedDay, selectedSubject, startTime, endTime, room) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Add class")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditorDialog(
    existing: CalendarEvent?,
    defaultDate: String,
    onDismiss: () -> Unit,
    onConfirm: (date: String, type: String, title: String, description: String) -> Unit,
) {
    var date by remember { mutableStateOf(existing?.date ?: defaultDate) }
    var type by remember { mutableStateOf(existing?.type ?: "Quiz") }
    var typeExpanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add calendar event" else "Edit calendar event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.take(10) },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        EventType.entries.forEach { t ->
                            DropdownMenuItem(text = { Text(t.label) }, onClick = { type = t.label; typeExpanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(100) },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(500) },
                    label = { Text("Description (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(date, type, title, description) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(if (existing == null) "Add event" else "Save changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
