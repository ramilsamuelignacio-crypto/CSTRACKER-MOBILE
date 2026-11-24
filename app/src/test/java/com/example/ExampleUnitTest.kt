package com.example

import com.example.data.model.User
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.util.ParsedQrContent
import com.example.util.QrUtils
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun userRole_parsesAllFiveRolesIncludingClassRep() {
        assertEquals(UserRole.ADMIN, UserRole.fromKey("admin"))
        assertEquals(UserRole.PROFESSOR, UserRole.fromKey("professor"))
        assertEquals(UserRole.MODERATOR, UserRole.fromKey("moderator"))
        assertEquals(UserRole.CLASS_REP, UserRole.fromKey("class_rep"))
        assertEquals(UserRole.CLASS_REP, UserRole.fromKey("classrep"))
        assertEquals(UserRole.STUDENT, UserRole.fromKey("student"))
        assertEquals(UserRole.STUDENT, UserRole.fromKey(null))
    }

    @Test
    fun userModel_defaultsAndProfileConversion() {
        val defaultUser = User(userId = "u1", email = "u1@cvsu.edu.ph", displayName = "Test User")
        assertEquals("student", defaultUser.role)
        assertEquals(UserRole.STUDENT, defaultUser.resolvedRole)

        val repUser = User(userId = "u2", role = "class_rep")
        assertEquals(UserRole.CLASS_REP, repUser.resolvedRole)
        assertTrue(repUser.resolvedRole.canPostAnnouncementsAndEvents)
        assertFalse(repUser.resolvedRole.canReviewExcusesAndWarn)

        val profile = UserProfile(
            id = "u3",
            userId = "u3",
            role = "classrep",
            firstName = "Maria",
            lastName = "Santos",
            campusEmail = "maria@cvsu.edu.ph",
        )
        val mapped = profile.toUser()
        assertEquals("class_rep", mapped.role)
        assertEquals(UserRole.CLASS_REP, mapped.resolvedRole)
        assertEquals("Maria Santos", mapped.displayName)
        assertEquals("maria@cvsu.edu.ph", mapped.email)
    }

    @Test
    fun qrUtils_parsesCheckInFormatsAndStudentPass() {
        val builtUri = QrUtils.buildCheckInQrPayload(
            subjectCode = "cosc75",
            classDate = "2026-11-23",
            status = "Present",
            sessionId = "secA",
        )
        val parsedUri = QrUtils.parseQrString(builtUri)
        assertTrue(parsedUri is ParsedQrContent.ClassCheckIn)
        parsedUri as ParsedQrContent.ClassCheckIn
        assertEquals("COSC75", parsedUri.subjectCode)
        assertEquals("2026-11-23", parsedUri.classDate)
        assertEquals("Present", parsedUri.status)
        assertEquals("secA", parsedUri.sessionId)

        val parsedJson = QrUtils.parseQrString("""{"subjectCode":"cosc80","classDate":"2026-11-24","status":"late"}""")
        assertTrue(parsedJson is ParsedQrContent.ClassCheckIn)
        parsedJson as ParsedQrContent.ClassCheckIn
        assertEquals("COSC80", parsedJson.subjectCode)
        assertEquals("2026-11-24", parsedJson.classDate)
        assertEquals("Late", parsedJson.status)

        val parsedDelimited = QrUtils.parseQrString("CSTRACKER:COSC101:2026-11-25:Late")
        assertTrue(parsedDelimited is ParsedQrContent.ClassCheckIn)
        parsedDelimited as ParsedQrContent.ClassCheckIn
        assertEquals("COSC101", parsedDelimited.subjectCode)
        assertEquals("2026-11-25", parsedDelimited.classDate)
        assertEquals("Late", parsedDelimited.status)

        val parsedPlain = QrUtils.parseQrString("COSC75", setOf("COSC75", "COSC80"))
        assertTrue(parsedPlain is ParsedQrContent.ClassCheckIn)
        parsedPlain as ParsedQrContent.ClassCheckIn
        assertEquals("COSC75", parsedPlain.subjectCode)
        assertNull(parsedPlain.classDate)
        assertNull(parsedPlain.status)

        assertNull(QrUtils.parseQrString("UNKNOWN", setOf("COSC75")))

        val token = QrUtils.generateStudentTokenHash("uid_123")
        val parsedPass = QrUtils.parseQrString(QrUtils.buildQrPayload(token))
        assertTrue(parsedPass is ParsedQrContent.StudentPass)
        assertEquals(token, (parsedPass as ParsedQrContent.StudentPass).tokenHash)
    }
}
