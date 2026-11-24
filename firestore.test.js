const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const PROF_UID = "prof_uid_789";
const MOD_UID = "mod_uid_321";
const REP_UID = "rep_uid_654";
const ADMIN_UID = "admin_uid_001";
const BOOTSTRAP_ADMIN_UID = "bootstrap_admin_000";
const BOOTSTRAP_ADMIN_EMAIL = "ramilsamuelignacio@gmail.com";
const TOKEN_ALICE = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function adminContext() {
  return testEnv.authenticatedContext(BOOTSTRAP_ADMIN_UID, {
    email: BOOTSTRAP_ADMIN_EMAIL,
    email_verified: true,
  });
}

async function seedRoles() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const t = new Date(Date.now() - 10000);
    await db.collection("admins").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      grantedBy: BOOTSTRAP_ADMIN_UID,
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("staff_roles").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      role: "admin",
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("staff_roles").doc(PROF_UID).set({
      userId: PROF_UID,
      role: "professor",
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("staff_roles").doc(MOD_UID).set({
      userId: MOD_UID,
      role: "moderator",
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("staff_roles").doc(REP_UID).set({
      userId: REP_UID,
      role: "class_rep",
      createdAt: t,
      updatedAt: t,
    });
  });
}

test("Unauthenticated users are denied everywhere", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertFails(db.collection("users").doc(ALICE_UID).get());
  await assertFails(db.collection("schedules").where("classGroup", "==", "CS-MAIN").get());
  await assertFails(db.collection("announcements").where("classGroup", "==", "CS-MAIN").get());
  await assertFails(db.collection("messages").where("participantUids", "array-contains", ALICE_UID).get());
});

test("Bootstrapped admin email spoof without email_verified is rejected", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: BOOTSTRAP_ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  const t = new Date(Date.now() - 1000);
  await assertFails(
    spoofDb.collection("subjects").doc("COSC75").set({
      classGroup: "CS-MAIN",
      code: "COSC75",
      name: "SOFTWARE ENGINEERING II",
      units: 3,
      professor: "ARMIN ARAGONCILLO",
      createdAt: t,
      updatedAt: t,
    })
  );
});

test("Student profile isolation, creation, partial update, and role escalation prevention", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const t = new Date(Date.now() - 5000);

  // Student cannot self-assign professor role
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      role: "professor",
      studentId: "202413724",
      firstName: "Alice",
      lastName: "Cruz",
      campusEmail: "alice@cvsu.edu.ph",
      irregular: false,
      enrolledSubjects: [],
      qrTokenHash: TOKEN_ALICE,
      createdAt: t,
      updatedAt: t,
    })
  );

  // Valid student registration succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      role: "student",
      studentId: "202413724",
      firstName: "Alice",
      lastName: "Cruz",
      campusEmail: "alice@cvsu.edu.ph",
      irregular: false,
      enrolledSubjects: ["COSC75"],
      qrTokenHash: TOKEN_ALICE,
      createdAt: t,
      updatedAt: t,
    })
  );

  // Partial profile update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      firstName: "Alicia",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Student cannot escalate own role on update or inject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot read Alice's private PII profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
});

test("Directory query alignment & QR token access", async () => {
  await seedRoles();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const profDb = testEnv.authenticatedContext(PROF_UID).firestore();
  const t = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("directory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Cruz",
      studentIdLabel: "202413724",
      role: "student",
      staffVisible: false,
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(
    aliceDb.collection("qr_tokens").doc(TOKEN_ALICE).set({
      userId: ALICE_UID,
      displayName: "Alice Cruz",
      studentId: "202413724",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Student can query staffVisible == true, but unfiltered directory list fails for student
  await assertSucceeds(aliceDb.collection("directory").where("staffVisible", "==", true).get());
  await assertFails(aliceDb.collection("directory").get());

  // Professor can look up student QR token and list directory
  await assertSucceeds(profDb.collection("qr_tokens").doc(TOKEN_ALICE).get());
  await assertSucceeds(profDb.collection("directory").get());
  // Another student cannot read Alice's QR token
  await assertFails(bobDb.collection("qr_tokens").doc(TOKEN_ALICE).get());
});

test("Shared class schedules, calendar, announcements, resources", async () => {
  await seedRoles();
  const adminDb = adminContext().firestore();
  const profDb = testEnv.authenticatedContext(PROF_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const t = new Date(Date.now() - 5000);

  await assertSucceeds(
    adminDb.collection("schedules").doc("sched_1").set({
      classGroup: "CS-MAIN",
      day: "Tuesday",
      startTime: "09:00",
      endTime: "11:00",
      subjectCode: "COSC75",
      room: "107",
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertFails(
    aliceDb.collection("schedules").doc("sched_2").set({
      classGroup: "CS-MAIN",
      day: "Friday",
      startTime: "09:00",
      endTime: "11:00",
      subjectCode: "COSC101",
      createdAt: t,
      updatedAt: t,
    })
  );

  await assertSucceeds(aliceDb.collection("schedules").where("classGroup", "==", "CS-MAIN").get());
  await assertFails(aliceDb.collection("schedules").get());

  await assertSucceeds(
    profDb.collection("announcements").doc("ann_1").set({
      classGroup: "CS-MAIN",
      title: "Midterm Coverage",
      body: "Review Chapters 1 to 4.",
      authorUid: PROF_UID,
      authorName: "Prof. Cruz",
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(aliceDb.collection("announcements").where("classGroup", "==", "CS-MAIN").get());
});

test("Attendance, excuse letters (with terminal state lock), warnings, and messages", async () => {
  await seedRoles();
  const profDb = testEnv.authenticatedContext(PROF_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const t = new Date(Date.now() - 5000);

  // Professor marks attendance
  await assertSucceeds(
    profDb.collection("attendance").doc(`${ALICE_UID}_COSC75_2026-11-23`).set({
      studentUid: ALICE_UID,
      studentId: "202413724",
      studentName: "Alice Cruz",
      subjectCode: "COSC75",
      subjectName: "SOFTWARE ENGINEERING II",
      classDate: "2026-11-23",
      status: "Present",
      verifiedBy: PROF_UID,
      verifiedByName: "Prof. Cruz",
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(aliceDb.collection("attendance").where("studentUid", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("attendance").where("studentUid", "==", ALICE_UID).get());

  // Alice submits Pending excuse letter
  await assertSucceeds(
    aliceDb.collection("excuse_letters").doc("ex_1").set({
      studentUid: ALICE_UID,
      studentId: "202413724",
      studentName: "Alice Cruz",
      subjectCode: "COSC75",
      reason: "Had a fever",
      status: "Pending",
      createdAt: t,
      updatedAt: t,
    })
  );
  // Alice cannot self-approve
  await assertFails(
    aliceDb.collection("excuse_letters").doc("ex_1").update({
      status: "Approved",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // Professor approves
  await assertSucceeds(
    profDb.collection("excuse_letters").doc("ex_1").update({
      status: "Approved",
      reviewedBy: PROF_UID,
      reviewerName: "Prof. Cruz",
      reviewNote: "Get well soon",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // Terminal state lock prevents non-admin re-modification
  await assertFails(
    profDb.collection("excuse_letters").doc("ex_1").update({
      status: "Rejected",
      reviewedBy: PROF_UID,
      reviewerName: "Prof. Cruz",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Direct messaging isolation
  await assertSucceeds(
    aliceDb.collection("messages").doc("msg_1").set({
      senderUid: ALICE_UID,
      senderName: "Alice Cruz",
      receiverUid: PROF_UID,
      receiverName: "Prof. Cruz",
      participantUids: [ALICE_UID, PROF_UID],
      message: "Hello Professor!",
      read: false,
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(aliceDb.collection("messages").where("participantUids", "array-contains", ALICE_UID).get());
  await assertFails(bobDb.collection("messages").doc("msg_1").get());
  await assertSucceeds(
    profDb.collection("messages").doc("msg_1").update({
      read: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Role-based access matrix across Admin, Professor, Moderator, Class rep, and Student", async () => {
  await seedRoles();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const profDb = testEnv.authenticatedContext(PROF_UID).firestore();
  const modDb = testEnv.authenticatedContext(MOD_UID).firestore();
  const repDb = testEnv.authenticatedContext(REP_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const t = new Date(Date.now() - 5000);

  // Authenticated user can fetch their own staff_roles document on login, not another user's
  await assertSucceeds(repDb.collection("staff_roles").doc(REP_UID).get());
  await assertSucceeds(aliceDb.collection("staff_roles").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("staff_roles").doc(REP_UID).get());

  await assertSucceeds(
    aliceDb.collection("qr_tokens").doc(TOKEN_ALICE).set({
      userId: ALICE_UID,
      displayName: "Alice Cruz",
      studentId: "202413724",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Only Professor can scan QR attendance (read student QR token & create attendance)
  await assertSucceeds(profDb.collection("qr_tokens").doc(TOKEN_ALICE).get());
  await assertFails(adminDb.collection("qr_tokens").doc(TOKEN_ALICE).get());
  await assertFails(modDb.collection("qr_tokens").doc(TOKEN_ALICE).get());
  await assertFails(repDb.collection("qr_tokens").doc(TOKEN_ALICE).get());

  await assertFails(
    adminDb.collection("attendance").doc(`${ALICE_UID}_COSC75_2026-11-24`).set({
      studentUid: ALICE_UID,
      studentId: "202413724",
      studentName: "Alice Cruz",
      subjectCode: "COSC75",
      classDate: "2026-11-24",
      status: "Present",
      verifiedBy: ADMIN_UID,
      verifiedByName: "Admin User",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Class rep can post announcements and calendar events
  await assertSucceeds(
    repDb.collection("announcements").doc("ann_rep").set({
      classGroup: "CS-MAIN",
      title: "Room change",
      body: "Moved to Room 109.",
      authorUid: REP_UID,
      authorName: "Rep User",
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(
    repDb.collection("calendar_events").doc("evt_rep").set({
      classGroup: "CS-MAIN",
      date: "2026-11-25",
      type: "Reminder",
      title: "Bring lab manual",
      authorUid: REP_UID,
      createdAt: t,
      updatedAt: t,
    })
  );

  // Student cannot post announcements or calendar events
  await assertFails(
    aliceDb.collection("announcements").doc("ann_alice").set({
      classGroup: "CS-MAIN",
      title: "Test",
      body: "Not allowed",
      authorUid: ALICE_UID,
      authorName: "Alice",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Upload class files: Admin & Professor allowed; Moderator & Class rep denied
  await assertSucceeds(
    profDb.collection("resources").doc("res_prof").set({
      classGroup: "CS-MAIN",
      title: "Week 4 Slides",
      authorUid: PROF_UID,
      authorName: "Prof User",
      fileName: "week4.pdf",
      fileSizeBytes: 12000,
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertFails(
    modDb.collection("resources").doc("res_mod").set({
      classGroup: "CS-MAIN",
      title: "Notes",
      authorUid: MOD_UID,
      authorName: "Mod User",
      fileName: "notes.pdf",
      fileSizeBytes: 12000,
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertFails(
    repDb.collection("resources").doc("res_rep").set({
      classGroup: "CS-MAIN",
      title: "Notes",
      authorUid: REP_UID,
      authorName: "Rep User",
      fileName: "notes.pdf",
      fileSizeBytes: 12000,
      createdAt: t,
      updatedAt: t,
    })
  );

  // Review excuse letters & issue warnings: Moderator allowed; Class rep denied
  await assertSucceeds(
    aliceDb.collection("excuse_letters").doc("ex_mat").set({
      studentUid: ALICE_UID,
      studentId: "202413724",
      studentName: "Alice Cruz",
      subjectCode: "COSC75",
      reason: "Medical appointment",
      status: "Pending",
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertFails(repDb.collection("excuse_letters").get());
  await assertFails(
    repDb.collection("excuse_letters").doc("ex_mat").update({
      status: "Approved",
      reviewedBy: REP_UID,
      reviewerName: "Rep User",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    modDb.collection("excuse_letters").doc("ex_mat").update({
      status: "Approved",
      reviewedBy: MOD_UID,
      reviewerName: "Mod User",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    repDb.collection("warnings").doc("wrn_rep").set({
      studentUid: ALICE_UID,
      studentName: "Alice Cruz",
      note: "Warning",
      createdBy: REP_UID,
      createdByName: "Rep User",
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(
    modDb.collection("warnings").doc("wrn_mod").set({
      studentUid: ALICE_UID,
      studentName: "Alice Cruz",
      note: "Late twice this week",
      createdBy: MOD_UID,
      createdByName: "Mod User",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Edit schedule: Admin allowed; Professor & Moderator denied
  await assertSucceeds(
    adminDb.collection("schedules").doc("sched_adm").set({
      classGroup: "CS-MAIN",
      day: "Monday",
      startTime: "08:00",
      endTime: "10:00",
      subjectCode: "COSC75",
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertFails(
    profDb.collection("schedules").doc("sched_prof").set({
      classGroup: "CS-MAIN",
      day: "Monday",
      startTime: "10:00",
      endTime: "12:00",
      subjectCode: "COSC75",
      createdAt: t,
      updatedAt: t,
    })
  );
});

test("Separate role registration with pre-approved invites and pending role requests", async () => {
  await seedRoles();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice.rep@cvsu.edu.ph",
    email_verified: true,
  }).firestore();
  const invitedProfUid = "invited_prof_999";
  const invitedProfDb = testEnv.authenticatedContext(invitedProfUid, {
    email: "Prof.Invited@gmail.com",
    email_verified: true,
  }).firestore();
  const t = new Date(Date.now() - 5000);

  // Non-admin cannot create role invites
  await assertFails(
    aliceDb.collection("role_invites").doc("alice.rep@cvsu.edu.ph").set({
      email: "alice.rep@cvsu.edu.ph",
      role: "professor",
      invitedBy: ALICE_UID,
      createdAt: t,
      updatedAt: t,
    })
  );

  // Admin creates pre-approved invite for professor (lowercase email doc id)
  await assertSucceeds(
    adminDb.collection("role_invites").doc("prof.invited@gmail.com").set({
      email: "prof.invited@gmail.com",
      role: "professor",
      invitedBy: ADMIN_UID,
      note: "CS Faculty",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Invited user can read their own invite, Alice cannot read it
  await assertSucceeds(invitedProfDb.collection("role_invites").doc("prof.invited@gmail.com").get());
  await assertFails(aliceDb.collection("role_invites").doc("prof.invited@gmail.com").get());

  // Invited user registers immediately as professor (not admin)
  await assertFails(
    invitedProfDb.collection("users").doc(invitedProfUid).set({
      userId: invitedProfUid,
      role: "admin",
      studentId: "FAC-01",
      firstName: "Prof",
      lastName: "Invited",
      campusEmail: "prof.invited@gmail.com",
      irregular: false,
      enrolledSubjects: [],
      qrTokenHash: TOKEN_ALICE,
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(
    invitedProfDb.collection("users").doc(invitedProfUid).set({
      userId: invitedProfUid,
      role: "professor",
      studentId: "FAC-01",
      firstName: "Prof",
      lastName: "Invited",
      campusEmail: "prof.invited@gmail.com",
      irregular: false,
      enrolledSubjects: [],
      qrTokenHash: TOKEN_ALICE,
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(
    invitedProfDb.collection("staff_roles").doc(invitedProfUid).set({
      userId: invitedProfUid,
      role: "professor",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Uninvited user registering via Class rep portal creates student profile with requestedRole pending approval
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      role: "student",
      requestedRole: "class_rep",
      studentId: "202413724",
      firstName: "Alice",
      lastName: "Cruz",
      campusEmail: "alice.rep@cvsu.edu.ph",
      irregular: false,
      enrolledSubjects: [],
      qrTokenHash: TOKEN_ALICE,
      createdAt: t,
      updatedAt: t,
    })
  );
});

test("Student QR self check-in rules enforce identity, allowed statuses, and no student overwrite", async () => {
  await seedRoles();
  const profDb = testEnv.authenticatedContext(PROF_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const t = new Date(Date.now() - 5000);

  // Student Alice can save her own Present check-in linked to her UID with deterministic ID
  await assertSucceeds(
    aliceDb.collection("attendance").doc(`${ALICE_UID}_COSC75_2026-11-26`).set({
      studentUid: ALICE_UID,
      studentId: "202413724",
      studentName: "Alice Cruz",
      subjectCode: "COSC75",
      subjectName: "SOFTWARE ENGINEERING II",
      classDate: "2026-11-26",
      status: "Present",
      verifiedBy: ALICE_UID,
      verifiedByName: "Alice Cruz (QR Self Check-In)",
      checkInMethod: "camera_qr_self",
      qrPayload: "cstracker://attendance?subject=COSC75&date=2026-11-26&status=Present",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Student cannot self-check-in as Absent
  await assertFails(
    aliceDb.collection("attendance").doc(`${ALICE_UID}_COSC101_2026-11-26`).set({
      studentUid: ALICE_UID,
      studentId: "202413724",
      studentName: "Alice Cruz",
      subjectCode: "COSC101",
      classDate: "2026-11-26",
      status: "Absent",
      verifiedBy: ALICE_UID,
      verifiedByName: "Alice Cruz (QR Self Check-In)",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Student cannot create attendance for another student (Bob)
  await assertFails(
    aliceDb.collection("attendance").doc(`${BOB_UID}_COSC75_2026-11-26`).set({
      studentUid: BOB_UID,
      studentId: "202413799",
      studentName: "Bob Santos",
      subjectCode: "COSC75",
      classDate: "2026-11-26",
      status: "Present",
      verifiedBy: ALICE_UID,
      verifiedByName: "Alice Cruz (QR Self Check-In)",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Professor marks Alice Absent for 2026-11-27; Alice cannot overwrite/update that record
  await assertSucceeds(
    profDb.collection("attendance").doc(`${ALICE_UID}_COSC75_2026-11-27`).set({
      studentUid: ALICE_UID,
      studentId: "202413724",
      studentName: "Alice Cruz",
      subjectCode: "COSC75",
      classDate: "2026-11-27",
      status: "Absent",
      verifiedBy: PROF_UID,
      verifiedByName: "Prof. Cruz",
      checkInMethod: "professor_qr_scan",
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertFails(
    aliceDb.collection("attendance").doc(`${ALICE_UID}_COSC75_2026-11-27`).update({
      status: "Present",
      verifiedBy: ALICE_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("attendance").doc(`${ALICE_UID}_COSC75_2026-11-27`).set({
      studentUid: ALICE_UID,
      studentId: "202413724",
      studentName: "Alice Cruz",
      subjectCode: "COSC75",
      classDate: "2026-11-27",
      status: "Present",
      verifiedBy: ALICE_UID,
      verifiedByName: "Alice Cruz (QR Self Check-In)",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Professor can still update Alice's self-checked attendance record if needed
  await assertSucceeds(
    profDb.collection("attendance").doc(`${ALICE_UID}_COSC75_2026-11-26`).update({
      status: "Late",
      verifiedBy: PROF_UID,
      verifiedByName: "Prof. Cruz",
      checkInMethod: "professor_qr_scan",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Unrelated student Bob cannot read Alice's self check-in record
  await assertFails(bobDb.collection("attendance").doc(`${ALICE_UID}_COSC75_2026-11-26`).get());
});
