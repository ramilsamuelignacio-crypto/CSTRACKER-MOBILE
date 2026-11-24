package com.example.util

object InputValidators {
    private val STUDENT_ID_RE = Regex("^[0-9]{5,12}$")
    private val NAME_RE = Regex("^[\\p{L}][\\p{L} .'’\\-]{0,49}$")
    private val EMAIL_RE = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    private val CAMPUS_EMAIL_RE = Regex("^[A-Za-z0-9._%+\\-]+@cvsu\\.edu\\.ph$", RegexOption.IGNORE_CASE)
    private val ROOM_RE = Regex("^[A-Za-z0-9 \\-]{1,20}$")
    private val TIME_RE = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")
    private val DATE_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val SUBJECT_CODE_RE = Regex("^[A-Z0-9]{2,12}$")

    fun validateStudentId(value: String, optional: Boolean = false): String? {
        val v = value.trim()
        if (v.isEmpty()) return if (optional) null else "Student ID is required."
        if (!STUDENT_ID_RE.matches(v)) return "Student ID must be 5 to 12 digits."
        return null
    }

    fun validateStaffId(value: String): String? {
        val v = value.trim()
        if (v.isEmpty()) return null
        if (v.length > 20 || !ROOM_RE.matches(v)) {
            return "Faculty/Staff ID may only use letters, numbers, spaces, and hyphens (max 20)."
        }
        return null
    }

    fun validateName(label: String, value: String, optional: Boolean = false): String? {
        val v = value.trim()
        if (v.isEmpty()) return if (optional) null else "$label is required."
        if (v.length > 50) return "$label is too long (max 50 characters)."
        if (!NAME_RE.matches(v)) return "$label may only contain letters, spaces, apostrophes, periods, and hyphens."
        return null
    }

    fun validateCampusEmail(value: String): String? {
        val v = value.trim()
        if (v.isEmpty()) return "Campus email is required."
        if (v.length > 100) return "Campus email is too long."
        if (!CAMPUS_EMAIL_RE.matches(v)) return "Campus email must end with @cvsu.edu.ph."
        return null
    }

    fun validateEmail(label: String, value: String): String? {
        val v = value.trim()
        if (v.isEmpty()) return "$label is required."
        if (v.length > 100) return "$label is too long."
        if (!EMAIL_RE.matches(v)) return "$label is not a valid email address."
        return null
    }

    fun validateOptionalEmail(label: String, value: String): String? {
        val v = value.trim()
        if (v.isEmpty()) return null
        if (v.length > 100) return "$label is too long."
        if (!EMAIL_RE.matches(v)) return "$label is not a valid email address."
        return null
    }

    fun parseSubjects(raw: String): Pair<List<String>, String?> {
        val list = raw.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        if (list.size > 12) return emptyList<String>() to "Use up to 12 subject codes."
        if (list.any { !SUBJECT_CODE_RE.matches(it) }) {
            return emptyList<String>() to "Subject codes must be 2-12 letters/numbers (e.g. COSC75, COSC80)."
        }
        return list to null
    }

    fun validateRoom(value: String): String? {
        val v = value.trim()
        if (v.isEmpty()) return null
        if (!ROOM_RE.matches(v)) return "Room: letters, numbers, spaces, and hyphen only (max 20)."
        return null
    }

    fun validateTimeRange(start: String, end: String): String? {
        if (!TIME_RE.matches(start) || !TIME_RE.matches(end)) return "Use 24-hour HH:MM format (e.g. 09:00)."
        val startMin = start.substringBefore(":").toInt() * 60 + start.substringAfter(":").toInt()
        val endMin = end.substringBefore(":").toInt() * 60 + end.substringAfter(":").toInt()
        if (startMin >= endMin) return "End time must be later than start time."
        return null
    }

    fun validateDate(value: String): String? {
        if (!DATE_RE.matches(value)) return "Date must use YYYY-MM-DD format."
        return null
    }
}
