const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const QRCode = require('qrcode');
const db = require('../database');

const SESSION_SECRET = process.env.SESSION_SECRET || 'cstracker-offline-local-session-secret-change-this';
const DB_KEY_HEX = process.env.DB_KEY || '';
const DB_KEY = /^[0-9a-fA-F]{64}$/.test(DB_KEY_HEX)
  ? Buffer.from(DB_KEY_HEX, 'hex')
  : crypto.createHash('sha256').update(DB_KEY_HEX || 'cstracker-offline-local-key').digest();

const MAX_BODY = 12 * 1024 * 1024;
const b64 = b => Buffer.from(b).toString('base64url');
const unb64 = s => Buffer.from(s, 'base64url');
const sign = value => crypto.createHmac('sha256', SESSION_SECRET).update(value).digest('base64url');
const sha = value => crypto.createHash('sha256').update(String(value)).digest('hex');
const now = () => new Date().toISOString();

function setSession(res, userId, activeRole) {
  const payload = b64(JSON.stringify({ userId, activeRole, exp: Date.now() + 7 * 864e5 }));
  res.setHeader('Set-Cookie', `cstracker_session=${payload}.${sign(payload)}; Path=/; HttpOnly; SameSite=Lax; Max-Age=604800`);
}
function clearSession(res) {
  res.setHeader('Set-Cookie', 'cstracker_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0');
}
function getSession(req) {
  const cookies = String(req.headers.cookie || '').split(';').map(x => x.trim());
  const raw = cookies.find(x => x.startsWith('cstracker_session='))?.slice('cstracker_session='.length);
  if (!raw) return null;
  const [payload, sig] = raw.split('.');
  if (!payload || !sig || sign(payload) !== sig) return null;
  try {
    const data = JSON.parse(unb64(payload).toString());
    return data.exp > Date.now() ? data : null;
  } catch { return null; }
}
function encrypt(value) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', DB_KEY, iv);
  const data = Buffer.concat([cipher.update(String(value ?? ''), 'utf8'), cipher.final()]);
  return `${b64(iv)}.${b64(cipher.getAuthTag())}.${b64(data)}`;
}
function decrypt(value) {
  if (!value) return '';
  try {
    const [iv, tag, data] = value.split('.').map(unb64);
    const decipher = crypto.createDecipheriv('aes-256-gcm', DB_KEY, iv);
    decipher.setAuthTag(tag);
    return Buffer.concat([decipher.update(data), decipher.final()]).toString('utf8');
  } catch { return ''; }
}
function parseArray(value) {
  try { return JSON.parse(decrypt(value) || '[]'); } catch { return []; }
}
function safeUser(u, activeRole) {
  if (!u) return null;
  return {
    id: u.id,
    role: u.role,
    subrole: u.subrole || null,
    studentId: decrypt(u.student_id_enc),
    firstName: decrypt(u.first_name_enc),
    middleName: decrypt(u.middle_name_enc),
    lastName: decrypt(u.last_name_enc),
    campusEmail: decrypt(u.campus_email_enc),
    username: decrypt(u.username_enc),
    guardianGmail: decrypt(u.guardian_gmail_enc),
    isIrregular: !!u.is_irregular,
    enrolledSubjects: parseArray(u.enrolled_subjects_enc),
    emailVerified: !!u.email_verified,
    activeRole: activeRole || u.role,
    profilePicture: u.profile_picture_enc ? decrypt(u.profile_picture_enc) : ''
  };
}
async function readBody(req) {
  if (req.body && typeof req.body === 'object') return req.body;
  let body = '';
  for await (const chunk of req) {
    body += chunk;
    if (Buffer.byteLength(body) > MAX_BODY) throw new Error('Request is too large. Keep files under 8 MB each.');
  }
  try { return body ? JSON.parse(body) : {}; } catch { throw new Error('Invalid request data.'); }
}
function send(res, status, data) { res.status(status); return res.json(data); }
function allowedRole(activeRole, roles) { return roles.includes(activeRole); }
function findUserByIdentifier(identifier) {
  const wanted = String(identifier || '').trim().toLowerCase();
  if (!wanted) return null;
  return db.users.find(u =>
    decrypt(u.campus_email_enc).toLowerCase() === wanted ||
    decrypt(u.username_enc).toLowerCase() === wanted
  );
}
function getUser(req) {
  const session = getSession(req);
  if (!session) return null;
  const row = db.users.find(u => u.id === session.userId);
  if (!row) return null;
  return { row, session, user: safeUser(row, session.activeRole) };
}
function nextId(collection) { return collection.reduce((m, x) => Math.max(m, Number(x.id) || 0), 0) + 1; }
function save() { db.save(); }
function cleanText(v, max = 5000) { return String(v ?? '').trim().slice(0, max); }
function timeValue(value) { const m=String(value||'').match(/^(\d{1,2}):(\d{2})$/); return m ? Number(m[1])*60+Number(m[2]) : 9999; }
function normalizeRole(role) {
  return ['student', 'professor', 'admin', 'classrep', 'moderator'].includes(role) ? role : 'student';
}
function roleOptions(user) {
  const options = new Set([user.role]);
  if (user.role === 'student' && user.subrole) options.add(user.subrole);
  if (['admin', 'classrep', 'moderator'].includes(user.role)) options.add('student');
  return [...options];
}
function isStaff(role) { return ['professor', 'admin', 'classrep', 'moderator'].includes(role); }
const EVENT_TYPES = ['Quiz','Exam','Holiday','Deadline','Class','Reminder'];
function calendarError(b) {
  return firstError(
    dateError('Date', b.date),
    (b.type && !EVENT_TYPES.includes(b.type)) ? 'Choose a valid event type.' : null,
    fieldError('Title', b.title, RULE.title),
    fieldError('Description', b.description, RULE.text, { optional: true, max: 500 })
  );
}
function canEditSchedule(role) { return role === 'admin'; }
function canScanQr(role) { return role === 'professor'; }

/* ---------- Input rules (mirrored in public/app.js) ---------- */
const RULE = {
  name:     { re: /^[\p{L}][\p{L} .'’\-]*$/u,                     min: 1, max: 50,  hint: 'letters, spaces, apostrophe, period and hyphen only' },
  studentId:{ re: /^[0-9]+$/,                                       min: 5, max: 12,  hint: 'numbers only (5 to 12 digits)' },
  username: { re: /^[a-z0-9._\-]+$/,                               min: 3, max: 30,  hint: 'lowercase letters, numbers, dot, underscore and hyphen only' },
  title:    { re: /^[\p{L}\p{N} .,:;!?'’"()\-_\/&#@+%]+$/u,       min: 1, max: 100, hint: 'letters, numbers, spaces and . , : ; ! ? \' " ( ) - _ / & # @ + % only' },
  room:     { re: /^[A-Za-z0-9 \-]+$/,                             min: 1, max: 20,  hint: 'letters, numbers, spaces and hyphen only' },
  answer:   { re: /^[\p{L}\p{N} .'’\-]+$/u,                        min: 2, max: 60,  hint: 'letters, numbers, spaces, apostrophe, period and hyphen only' },
  subjects: { re: /^[A-Za-z0-9, ]+$/,                               min: 2, max: 100, hint: 'subject codes separated by commas (letters and numbers only)' },
  text:     { re: /^[^<>{}\\|^~`\u0000-\u0008\u000B\u000C\u000E-\u001F]+$/u, min: 1, max: 1000, hint: 'the characters < > { } \\ | ^ ~ ` are not allowed' }
};
function fieldError(label, value, rule, { optional = false, max } = {}) {
  const v = String(value ?? '').trim();
  const limit = max || rule.max;
  if (!v) return optional ? null : `${label} is required.`;
  if (v.length < rule.min) return `${label} is too short (minimum ${rule.min} characters).`;
  if (v.length > limit) return `${label} is too long (maximum ${limit} characters).`;
  if (!rule.re.test(v)) return `${label}: ${rule.hint}.`;
  return null;
}
const PASSWORD_SPECIALS = '! @ # $ % ^ & * _ - . ?';
const PASSWORD_RE = /^[A-Za-z0-9!@#$%^&*_.?\-]+$/;
function passwordError(value, label = 'Password') {
  const v = String(value ?? '');
  if (v.length < 8) return `${label} must be at least 8 characters.`;
  if (v.length > 20) return `${label} is too long (maximum 20 characters).`;
  if (!PASSWORD_RE.test(v)) return `${label} may only contain letters, numbers and these special characters: ${PASSWORD_SPECIALS}`;
  return null;
}
const EMAIL_RE = /^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$/;
const CAMPUS_EMAIL_RE = /^[A-Za-z0-9._%+\-]+@cvsu\.edu\.ph$/i;
function emailError(label, value, { campus = false } = {}) {
  const v = String(value ?? '').trim();
  if (!v) return `${label} is required.`;
  if (v.length > 100) return `${label} is too long (maximum 100 characters).`;
  if (!(campus ? CAMPUS_EMAIL_RE : EMAIL_RE).test(v)) return campus ? `${label} must end with @cvsu.edu.ph and use only letters, numbers and . _ % + -` : `${label} is not a valid email address.`;
  return null;
}
function dateError(label, value) {
  const v = String(value ?? '');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(v)) return `${label} must be a valid date.`;
  const d = new Date(v + 'T00:00:00Z');
  if (Number.isNaN(d.getTime()) || d.toISOString().slice(0, 10) !== v) return `${label} must be a valid date.`;
  return null;
}
function firstError(...errs) { return errs.find(Boolean) || null; }

/* ---------- Security questions (3 recovery questions) ---------- */
const RECOVERY_POOL = ['first pet', 'favorite food', 'mom name', 'dad name', 'first school name'];
const normAnswer = a => String(a ?? '').normalize('NFKC').trim().toLowerCase().replace(/\s+/g, ' ');
function hashAnswer(answer) {
  const salt = crypto.randomBytes(16);
  const hash = crypto.scryptSync(normAnswer(answer), salt, 32, { N: 16384, r: 8, p: 1 });
  return `s1$${b64(salt)}$${b64(hash)}`;
}
function verifyAnswer(answer, stored) {
  stored = String(stored || '');
  if (stored.startsWith('s1$')) {
    const [, saltB64, hashB64] = stored.split('$');
    try {
      const expected = unb64(hashB64);
      const actual = crypto.scryptSync(normAnswer(answer), unb64(saltB64), expected.length, { N: 16384, r: 8, p: 1 });
      return crypto.timingSafeEqual(actual, expected);
    } catch { return false; }
  }
  // legacy unsalted SHA-256 (upgraded to scrypt after the next successful recovery)
  const legacy = Buffer.from(sha(cleanText(answer, 300).toLowerCase()));
  const target = Buffer.from(stored);
  return legacy.length === target.length && crypto.timingSafeEqual(legacy, target);
}
// Lock recovery attempts: 5 wrong tries per email -> 15 minute lock
const recoveryFails = new Map();
function recoveryLocked(email) {
  const r = recoveryFails.get(email);
  if (!r) return 0;
  if (r.until && r.until > Date.now()) return Math.ceil((r.until - Date.now()) / 60000);
  if (r.until && r.until <= Date.now()) recoveryFails.delete(email);
  return 0;
}
function recoveryFailed(email) {
  const r = recoveryFails.get(email) || { count: 0 };
  r.count += 1;
  if (r.count >= 5) { r.until = Date.now() + 15 * 60000; r.count = 0; }
  recoveryFails.set(email, r);
}
function canUploadResources(role) { return ['professor', 'admin'].includes(role); }
function canReviewExcuse(role) { return ['professor', 'admin', 'moderator'].includes(role); }
function subjectFor(code) { return db.subjects.find(s => s.code === code); }
function userDisplay(id) {
  const u = db.users.find(x => x.id === id);
  return u ? `${decrypt(u.first_name_enc)} ${decrypt(u.last_name_enc)}`.trim() : 'Unknown user';
}
function publicAttachment(file) {
  return {
    name: cleanText(file.name, 180),
    type: cleanText(file.type, 100),
    size: Number(file.size) || 0,
    dataUrl: String(file.dataUrl || '')
  };
}
function validateAttachments(files, { imagesOnly = false, pdfOnly = false } = {}) {
  if (!Array.isArray(files)) return [];
  const allowed = [];
  let total = 0;
  for (const file of files.slice(0, 5)) {
    const name = cleanText(file?.name, 180);
    const type = cleanText(file?.type, 100).toLowerCase();
    const dataUrl = String(file?.dataUrl || '');
    const size = Number(file?.size) || 0;
    if (!name || !dataUrl.startsWith('data:') || size <= 0 || size > 8 * 1024 * 1024) continue;
    if (imagesOnly && !type.startsWith('image/')) continue;
    if (pdfOnly && type !== 'application/pdf') continue;
    if (!imagesOnly && !pdfOnly && !(type.startsWith('image/') || type === 'application/pdf' || type.startsWith('text/'))) continue;
    if (total + size > 20 * 1024 * 1024) continue;
    total += size;
    allowed.push(publicAttachment({ name, type, size, dataUrl }));
  }
  return allowed;
}

const SUBJECTS = [
  ['COSC75','SOFTWARE ENGINEERING II',3,'ARMIN ARAGONCILLO'],
  ['MATH3','LINEAR ALGEBRA',3,'MA. CHRISTINE JOYCE RELLOSA'],
  ['COSC80','OPERATING SYSTEMS',3,'STEVEN GOHAN MATRO'],
  ['COSC85','NETWORKS AND COMMUNICATION',3,'MICHAEL MADSEN VENTURA'],
  ['COSC101','CS ELECTIVE 1',3,'CHRISTOPHER HERNANDEZ'],
  ['DCIT26','APPLICATIONS DEVELOPMENT AND EMERGING TECHNOLOGIES',3,'JAMES RAPHAEL TOLEDO'],
  ['DCIT65','SOCIAL AND PROFESSIONAL ISSUES',3,'EMILIO DEL ROSARIO III']
];
const SCHEDULES = [
  ['Tuesday','07:00','09:00','DCIT65','119'],['Tuesday','09:00','11:00','COSC75','107'],
  ['Tuesday','13:00','15:00','COSC75','109'],['Tuesday','15:00','17:00','MATH3','206'],
  ['Tuesday','17:00','19:00','DCIT26','107'],['Wednesday','11:00','13:00','COSC85','207'],
  ['Wednesday','13:00','15:00','COSC80','107'],['Thursday','09:00','11:00','COSC85','109'],
  ['Thursday','11:00','13:00','COSC80','217'],['Thursday','15:00','17:00','DCIT26','217'],
  ['Friday','09:00','11:00','COSC101','107'],['Friday','11:00','13:00','COSC101','107']
];
function ensureSeedData() {
  if (!Array.isArray(db.subjects) || !db.subjects.length) db.subjects = SUBJECTS.map(([code,name,units,professor]) => ({code,name,units,professor}));
  if (!Array.isArray(db.schedules) || !db.schedules.length) db.schedules = SCHEDULES.map(([day,start_time,end_time,subject_code,room], i) => ({id:i+1,day,start_time,end_time,subject_code,room}));
  for (const key of ['announcements','attendance','excuse_letters','warnings','resources','calendar_events','messages']) if (!Array.isArray(db[key])) db[key] = [];
  save();
}
function makeDemoUser(role) {
  const email = `${role}.demo@cvsu.edu.ph`;
  let user = findUserByIdentifier(email);
  if (user) return user;
  user = {
    id: nextId(db.users), role, subrole: role === 'admin' ? null : null,
    campus_email_enc: encrypt(email), username_enc: encrypt(`${role}.demo`), password_hash: null,
    student_id_enc: encrypt(`CSTRACKER-${role.toUpperCase()}-DEMO`), first_name_enc: encrypt('Demo'), middle_name_enc: encrypt(''),
    last_name_enc: encrypt(role.charAt(0).toUpperCase()+role.slice(1)), guardian_gmail_enc: encrypt('guardian@example.com'),
    recovery_questions_enc: encrypt(JSON.stringify([{question:'What was your first pet?',answer_hash:hashAnswer('demo')},{question:'What is your favorite food?',answer_hash:hashAnswer('pizza')},{question:'What was your first school name?',answer_hash:hashAnswer('demo school')}])) , profile_picture_enc:'',
    is_irregular:false, enrolled_subjects_enc:encrypt('[]'), email_verified:true,
    verification_token_hash:null, qr_token_hash:sha(`CSTRACKER-${role}-DEMO-QR`), created_at:now()
  };
  return user;
}


// ---- Helpers ----
const manilaDate = () => new Date(Date.now() + 8 * 3600 * 1000).toISOString().slice(0, 10);
function publicBase(req) {
  if (process.env.PUBLIC_URL) return process.env.PUBLIC_URL.replace(/\/+$/, '');
  const proto = String(req.headers['x-forwarded-proto'] || 'http').split(',')[0].trim();
  const host = String(req.headers['x-forwarded-host'] || req.headers.host || 'localhost').split(',')[0].trim();
  return `${proto}://${host}`;
}

module.exports = async (req, res) => {
  try {
    ensureSeedData();
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const path = url.pathname.replace(/\/+$/, '') || '/';
    const body = await readBody(req);

    if (req.method === 'POST' && path === '/api/register') {
      const {studentId,firstName,middleName,lastName,campusEmail,password,guardianGmail,isIrregular,enrolledSubjects=[],recoveryQuestions=[]} = body;
      if (!studentId || !firstName || !lastName || !campusEmail || !password || !guardianGmail) return send(res,400,{error:'Please fill in all required fields.'});
      if ([studentId,firstName,middleName,lastName,campusEmail,password,guardianGmail].some(v => v !== undefined && v !== null && typeof v !== 'string' && typeof v !== 'number')) return send(res,400,{error:'Invalid form data.'});
      const regError = firstError(
        fieldError('Student ID', studentId, RULE.studentId),
        fieldError('First name', firstName, RULE.name),
        fieldError('Middle name', middleName, RULE.name, { optional: true }),
        fieldError('Last name', lastName, RULE.name),
        emailError('Campus email', campusEmail, { campus: true }),
        emailError('Guardian email', guardianGmail),
        passwordError(password)
      );
      if (regError) return send(res,400,{error:regError});
      const subjectList = (Array.isArray(enrolledSubjects) ? enrolledSubjects : []).map(x => String(x).trim().toUpperCase()).filter(Boolean);
      if (subjectList.length > 12 || subjectList.some(x => !/^[A-Z0-9]{2,12}$/.test(x))) return send(res,400,{error:'Subjects: use up to 12 subject codes with letters and numbers only (example: COSC75, COSC80).'});
      const recovery = Array.isArray(recoveryQuestions) ? recoveryQuestions.slice(0,3) : [];
      if (recovery.length !== 3 || recovery.some(x => !x || !cleanText(x.question,200) || !cleanText(x.answer,300))) return send(res,400,{error:'Please answer all 3 recovery questions.'});
      if (recovery.some(x => !RECOVERY_POOL.includes(cleanText(x.question,200)))) return send(res,400,{error:'Choose recovery questions from the list provided.'});
      if (new Set(recovery.map(x=>cleanText(x.question,200))).size !== 3) return send(res,400,{error:'Recovery questions must be different.'});
      const ansError = firstError(...recovery.map((x,i) => fieldError(`Recovery answer ${i+1}`, x.answer, RULE.answer)));
      if (ansError) return send(res,400,{error:ansError});
      if (findUserByIdentifier(String(campusEmail).trim())) return send(res,409,{error:'That campus email is already registered.'});
      if (db.users.some(x => decrypt(x.student_id_enc).toLowerCase() === String(studentId).trim().toLowerCase())) return send(res,409,{error:'That student ID is already registered.'});
      const qrToken = crypto.randomBytes(24).toString('hex');
      const user = {
        id: nextId(db.users), role:'student', subrole:null,
        campus_email_enc:encrypt(String(campusEmail).trim().toLowerCase()), username_enc:null, password_hash:await bcrypt.hash(password,12),
        student_id_enc:encrypt(String(studentId).trim()), first_name_enc:encrypt(String(firstName).trim()), middle_name_enc:encrypt(String(middleName || '').trim()),
        last_name_enc:encrypt(String(lastName).trim()), guardian_gmail_enc:encrypt(String(guardianGmail).trim().toLowerCase()), is_irregular:!!isIrregular,
        enrolled_subjects_enc:encrypt(JSON.stringify(subjectList)), email_verified:true,
        recovery_questions_enc:encrypt(JSON.stringify(recovery.map(x=>({question:cleanText(x.question,200),answer_hash:hashAnswer(x.answer)})))),
        profile_picture_enc:'', verification_token_hash:null, qr_token_hash:sha(qrToken), created_at:now()
      };
      db.users.push(user); save();
      return send(res,200,{ok:true,message:'Account created. You can sign in now.',userId:user.id,offline:true});
    }
    if (req.method === 'POST' && path === '/api/forgot/start') {
      const email = cleanText(body.campusEmail,100).toLowerCase();
      if (!CAMPUS_EMAIL_RE.test(email)) return send(res,400,{error:'Enter a valid @cvsu.edu.ph campus email.'});
      const lockedFor = recoveryLocked(email); if (lockedFor) return send(res,429,{error:`Too many wrong attempts. Try again in ${lockedFor} minute${lockedFor===1?'':'s'}.`});
      const user = findUserByIdentifier(email);
      if (!user || decrypt(user.campus_email_enc).toLowerCase() !== email) return send(res,404,{error:'No account was found for that campus email.'});
      let recovery=[]; try { recovery=JSON.parse(decrypt(user.recovery_questions_enc)||'[]'); } catch { recovery=[]; }
      if (recovery.length !== 3) return send(res,400,{error:'This account does not have password recovery questions set up.'});
      return send(res,200,{ok:true,questions:recovery.map(x=>x.question)});
    }
    if (req.method === 'POST' && path === '/api/forgot/reset') {
      const email = cleanText(body.campusEmail,100).toLowerCase();
      if (!CAMPUS_EMAIL_RE.test(email)) return send(res,400,{error:'Enter a valid @cvsu.edu.ph campus email.'});
      const lockedFor = recoveryLocked(email); if (lockedFor) return send(res,429,{error:`Too many wrong attempts. Try again in ${lockedFor} minute${lockedFor===1?'':'s'}.`});
      const user = findUserByIdentifier(email);
      if (!user || decrypt(user.campus_email_enc).toLowerCase() !== email) return send(res,404,{error:'No account was found for that campus email.'});
      const pwError = passwordError(body.newPassword,'New password'); if (pwError) return send(res,400,{error:pwError});
      let recovery=[]; try { recovery=JSON.parse(decrypt(user.recovery_questions_enc)||'[]'); } catch { recovery=[]; }
      const answers=Array.isArray(body.answers)?body.answers:[];
      if (recovery.length!==3 || answers.length!==3) return send(res,400,{error:'Answer all 3 recovery questions.'});
      const valid=recovery.map((x,i)=>verifyAnswer(answers[i],x.answer_hash)).every(Boolean);
      if (!valid) { recoveryFailed(email); return send(res,401,{error:'One or more recovery answers are incorrect.'}); }
      recoveryFails.delete(email);
      user.recovery_questions_enc=encrypt(JSON.stringify(recovery.map((x,i)=>({question:x.question,answer_hash:hashAnswer(answers[i])}))));
      user.password_hash=await bcrypt.hash(String(body.newPassword),12); save();
      return send(res,200,{ok:true,message:'Password changed. You can sign in now.'});
    }

    if (req.method === 'GET' && path === '/api/verify') return res.redirect('/verify.html?status=offline');
    if (req.method === 'POST' && path === '/api/resend-verification') return send(res,200,{ok:true,message:'Offline mode does not use email verification.'});

    if (req.method === 'POST' && path === '/api/login') {
      const {identifier,password,roleChoice} = body;
      if (String(identifier||'').length > 100 || String(password||'').length > 200) return send(res,400,{error:'Email/username or password is incorrect.'});
      const user = findUserByIdentifier(identifier);
      if (!user || !(await bcrypt.compare(String(password || ''),user.password_hash || ''))) return send(res,401,{error:'Email/username or password is incorrect.'});
      const options = roleOptions(user);
      if (options.length > 1 && !roleChoice) return send(res,200,{requiresRoleChoice:true,options});
      const activeRole = options.includes(roleChoice) ? roleChoice : user.role;
      setSession(res,user.id,activeRole);
      return send(res,200,{ok:true,user:safeUser(user,activeRole)});
    }
    if (req.method === 'POST' && path === '/api/demo-login') {
      if (process.env.ENABLE_DEMO === 'false') return send(res,403,{error:'Demo login is disabled.'});
      const role = normalizeRole(body.role);
      let user = makeDemoUser(role);
      if (!user.password_hash) user.password_hash = await bcrypt.hash('Demo1234!',12);
      db.users = db.users || [];
      if (!db.users.find(x=>x.id===user.id)) db.users.push(user);
      save(); setSession(res,user.id,role); return send(res,200,{ok:true,user:safeUser(user,role)});
    }
    if (req.method === 'POST' && path === '/api/logout') { clearSession(res); return send(res,200,{ok:true}); }
    if (req.method === 'GET' && path === '/api/me') { const u=getUser(req); if(!u)return send(res,401,{error:'You are not signed in.'}); return send(res,200,u.user); }

    if (req.method === 'PATCH' && path === '/api/profile') {
      const u=getUser(req); if(!u)return send(res,401,{error:'Please sign in first.'});
      if (body.profilePicture !== undefined) {
        const picture=String(body.profilePicture||'');
        if (picture && !/^data:image\/(png|jpeg|jpg|webp);base64,/i.test(picture)) return send(res,400,{error:'Profile picture must be PNG, JPG, JPEG, or WEBP.'});
        if (picture.length > 3*1024*1024) return send(res,400,{error:'Profile picture is too large. Keep it under 2 MB.'});
        u.row.profile_picture_enc=picture ? encrypt(picture) : '';
      }
      if (body.newPassword !== undefined) {
        const pwError = passwordError(body.newPassword,'New password'); if (pwError) return send(res,400,{error:pwError});
        if (!(await bcrypt.compare(String(body.currentPassword||''),u.row.password_hash||''))) return send(res,401,{error:'Current password is incorrect.'});
        u.row.password_hash=await bcrypt.hash(String(body.newPassword),12);
      }
      save(); return send(res,200,{ok:true,user:safeUser(u.row,u.session.activeRole),message:'Profile updated.'});
    }

    if (req.method === 'GET' && path === '/api/schedule') {
      const out=db.schedules.map(x=>{const s=subjectFor(x.subject_code)||{};return {...x,name:s.name,professor:s.professor};}).sort((a,b)=>`${a.day}${a.start_time}`.localeCompare(`${b.day}${b.start_time}`));
      return send(res,200,out);
    }
    if (req.method === 'POST' && path === '/api/schedule') {
      const u=getUser(req); if(!u || !canEditSchedule(u.user.activeRole))return send(res,403,{error:'Only admins can change the class schedule.'});
      const day=cleanText(body.day,20), start=cleanText(body.start_time,5), end=cleanText(body.end_time,5), subjectCode=cleanText(body.subject_code,30), room=cleanText(body.room,40);
      if(!['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'].includes(day)||!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(start)||!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(end)||!subjectFor(subjectCode)) return send(res,400,{error:'Enter a valid day, time and subject.'});
      if(timeValue(start)>=timeValue(end)) return send(res,400,{error:'End time must be later than start time.'});
      const roomError = fieldError('Room', room, RULE.room, { optional: true }); if (roomError) return send(res,400,{error:roomError});
      if(db.schedules.some(x=>x.day===day&&x.start_time===start&&x.subject_code===subjectCode)) return send(res,409,{error:'That class is already on the schedule.'});
      db.schedules.push({id:nextId(db.schedules),day,start_time:start,end_time:end,subject_code:subjectCode,room}); save(); return send(res,200,{ok:true,message:'Schedule added.'});
    }
    if (req.method === 'DELETE' && path.startsWith('/api/schedule/')) {
      const u=getUser(req); if(!u || !canEditSchedule(u.user.activeRole))return send(res,403,{error:'Only admins can change the class schedule.'});
      const id=Number(path.split('/').pop()); const i=db.schedules.findIndex(x=>x.id===id); if(i<0)return send(res,404,{error:'Schedule entry not found.'}); db.schedules.splice(i,1); save(); return send(res,200,{ok:true,message:'Schedule removed.'});
    }
    if (req.method === 'GET' && path === '/api/subjects') return send(res,200,[...db.subjects].sort((a,b)=>a.code.localeCompare(b.code)));

    if (req.method === 'GET' && path === '/api/announcements') {
      return send(res,200,[...db.announcements].sort((a,b)=>new Date(b.created_at)-new Date(a.created_at)).map(x=>({...x,subject_name:subjectFor(x.subject_code)?.name,author_name:userDisplay(x.author_id)})));
    }
    if (req.method === 'POST' && path === '/api/announcements') {
      const u=getUser(req); if(!u)return send(res,401,{error:'Please sign in first.'});
      if(!isStaff(u.user.activeRole))return send(res,403,{error:'Your role cannot post announcements.'});
      const {title,body:message,subjectCode,attachments=[]}=body;
      const annError = firstError(fieldError('Title', title, RULE.title), fieldError('Message', message, RULE.text, { max: 3000 }));
      if (annError) return send(res,400,{error:annError});
      if (subjectCode && !subjectFor(subjectCode)) return send(res,400,{error:'Choose a valid subject.'});
      const files=validateAttachments(attachments);
      db.announcements.push({id:nextId(db.announcements),title:cleanText(title,100),body:cleanText(message,3000),subject_code:subjectCode||null,author_id:u.row.id,attachments:files,created_at:now()});
      save(); return send(res,200,{ok:true,message:'Announcement posted.'});
    }

    if (req.method === 'DELETE' && path.startsWith('/api/announcements/')) {
      const u=getUser(req); if(!u || !isStaff(u.user.activeRole))return send(res,403,{error:'Only class staff can remove announcements.'});
      const id=Number(path.split('/').pop()); const i=db.announcements.findIndex(x=>x.id===id); if(i<0)return send(res,404,{error:'Announcement not found.'}); db.announcements.splice(i,1); save(); return send(res,200,{ok:true,message:'Announcement removed.'});
    }

    if (req.method === 'GET' && path === '/api/attendance') {
      const u=getUser(req); if(!u)return send(res,401,{error:'Please sign in first.'});
      let rows=db.attendance;
      if(!isStaff(u.user.activeRole)) rows=rows.filter(a=>a.student_id===u.row.id);
      return send(res,200,[...rows].sort((a,b)=>String(b.class_date).localeCompare(String(a.class_date))).map(a=>({...a,studentId:decrypt(db.users.find(x=>x.id===a.student_id)?.student_id_enc),studentName:userDisplay(a.student_id),subjectName:subjectFor(a.subject_code)?.name})));
    }
    if (req.method === 'POST' && path === '/api/attendance/scan') {
      const u=getUser(req); if(!u)return send(res,401,{error:'Please sign in first.'});
      if(!canScanQr(u.user.activeRole))return send(res,403,{error:'Only professors can use the QR attendance tracker.'});
      const {qrPayload,subjectCode,status='Present',classDate}=body; let parsed;
      if(typeof qrPayload!=='string' || qrPayload.length>200) return send(res,400,{error:'That QR data is not valid.'});
      try{
        if(/^[0-9a-f]{64}$/i.test(qrPayload.trim())){ parsed={studentIdHash:qrPayload.trim().toLowerCase()}; }
        else if(/^https?:\/\//i.test(qrPayload.trim())){ parsed={studentIdHash:new URL(qrPayload.trim()).searchParams.get('student')}; }
        else parsed=JSON.parse(qrPayload);
      }catch{return send(res,400,{error:'That QR data is not valid.'});}
      if(!parsed || typeof parsed.studentIdHash!=='string' || !/^[0-9a-f]{64}$/.test(parsed.studentIdHash)) return send(res,400,{error:'That QR data is not valid.'});
      const target=db.users.find(x=>x.qr_token_hash===parsed.studentIdHash); if(!target)return send(res,404,{error:'Student QR not recognized.'});
      if(!subjectFor(subjectCode)) return send(res,400,{error:'Choose a valid subject.'});
      if(classDate){ const dErr=dateError('Date',classDate); if(dErr) return send(res,400,{error:dErr}); }
      const date=classDate||manilaDate();
      const existing=db.attendance.find(a=>a.student_id===target.id&&a.subject_code===subjectCode&&a.class_date===date);
      const row={id:existing?.id||nextId(db.attendance),student_id:target.id,subject_code:subjectCode,class_date:date,status:['Present','Late','Absent'].includes(status)?status:'Present',verified_by:u.row.id,verified_at:now(),created_at:existing?.created_at||now()};
      if(existing)Object.assign(existing,row);else db.attendance.push(row); save(); return send(res,200,{ok:true,message:`${userDisplay(target.id)} marked ${row.status}.`});
    }


    if (req.method === 'GET' && path === '/api/qr') {
      const u=getUser(req); if(!u)return send(res,401,{error:'Please sign in first.'});
      const dataUrl=await QRCode.toDataURL(`${publicBase(req)}/?student=${u.row.qr_token_hash}`,{width:320,margin:2});
      return send(res,200,{dataUrl});
    }

    if (req.method === 'GET' && path === '/api/excuse') {
      const u=getUser(req); if(!u)return send(res,401,{error:'Please sign in first.'});
      let rows=isStaff(u.user.activeRole)?db.excuse_letters:db.excuse_letters.filter(x=>x.student_id===u.row.id);
      return send(res,200,[...rows].sort((a,b)=>new Date(b.created_at)-new Date(a.created_at)).map(x=>({...x,studentId:decrypt(db.users.find(z=>z.id===x.student_id)?.student_id_enc),studentName:userDisplay(x.student_id),subjectName:subjectFor(x.subject_code)?.name,reviewerName:x.reviewed_by?userDisplay(x.reviewed_by):null,attachments:x.attachments||[]})));
    }
    if (req.method === 'POST' && path === '/api/excuse') {
      const u=getUser(req); if(!u)return send(res,401,{error:'Please sign in first.'});
      const {subjectCode,reason,attachments=[]}=body; if(!subjectCode||!subjectFor(subjectCode))return send(res,400,{error:'Choose a valid subject.'});
      const reasonError=fieldError('Reason', reason, RULE.text, { max: 2000 }); if(reasonError)return send(res,400,{error:reasonError});
      const files=validateAttachments(attachments,{imagesOnly:false});
      db.excuse_letters.push({id:nextId(db.excuse_letters),student_id:u.row.id,subject_code:subjectCode,reason:cleanText(reason,2000),attachments:files,status:'Pending',reviewed_by:null,review_note:'',created_at:now()});
      save(); return send(res,200,{ok:true,message:'Excuse letter submitted.'});
    }
    if (req.method === 'PATCH' && path.startsWith('/api/excuse/')) {
      const u=getUser(req); if(!u || !canReviewExcuse(u.user.activeRole))return send(res,403,{error:'Only professors, admins and moderators can review excuse letters.'});
      const id=Number(path.split('/').pop()); const row=db.excuse_letters.find(x=>x.id===id); if(!row)return send(res,404,{error:'Excuse letter not found.'});
      const noteError=fieldError('Note', body.reviewNote, RULE.text, { optional: true, max: 500 }); if(noteError)return send(res,400,{error:noteError});
      const status=['Approved','Rejected','Pending'].includes(body.status)?body.status:'Pending'; row.status=status; row.reviewed_by=u.row.id; row.review_note=cleanText(body.reviewNote,500); row.reviewed_at=now(); save(); return send(res,200,{ok:true,message:`Excuse letter ${status.toLowerCase()}.`});
    }

    if (req.method === 'GET' && path === '/api/resources') {
      const u=getUser(req); if(!u)return send(res,401,{error:'Please sign in first.'});
      return send(res,200,[...db.resources].sort((a,b)=>new Date(b.created_at)-new Date(a.created_at)).map(x=>({...x,subjectName:subjectFor(x.subject_code)?.name,authorName:userDisplay(x.author_id),attachment:x.attachment?{name:x.attachment.name,type:x.attachment.type,size:x.attachment.size}:null})));
    }
    if (req.method === 'POST' && path === '/api/resources') {
      const u=getUser(req); if(!u || !canUploadResources(u.user.activeRole))return send(res,403,{error:'Only professors and admins can upload class files.'});
      const {title,description,subjectCode,attachment}=body;
      const resError=firstError(fieldError('Title', title, RULE.title), fieldError('Description', description, RULE.text, { optional: true, max: 500 })); if(resError)return send(res,400,{error:resError});
      if(!attachment)return send(res,400,{error:'Title and PDF file are required.'});
      if(subjectCode && !subjectFor(subjectCode)) return send(res,400,{error:'Choose a valid subject.'});
      const files=validateAttachments([attachment],{pdfOnly:true}); if(!files.length)return send(res,400,{error:'Please upload a valid PDF under 8 MB.'});
      db.resources.push({id:nextId(db.resources),title:cleanText(title,100),description:cleanText(description,500),subject_code:subjectCode||null,author_id:u.row.id,attachment:files[0],created_at:now()}); save(); return send(res,200,{ok:true,message:'PDF uploaded.'});
    }
    if (req.method === 'GET' && path.startsWith('/api/resources/')) {
      const id=Number(path.split('/').pop()); const row=db.resources.find(x=>x.id===id); if(!row?.attachment)return send(res,404,{error:'File not found.'});
      return send(res,200,{name:row.attachment.name,type:row.attachment.type,dataUrl:row.attachment.dataUrl});
    }
    if (req.method === 'DELETE' && path.startsWith('/api/resources/')) {
      const u=getUser(req); if(!u || !canUploadResources(u.user.activeRole))return send(res,403,{error:'Only professors and admins can remove class files.'});
      const id=Number(path.split('/').pop()); const i=db.resources.findIndex(x=>x.id===id); if(i<0)return send(res,404,{error:'File not found.'}); db.resources.splice(i,1); save(); return send(res,200,{ok:true,message:'File removed.'});
    }

    if (req.method === 'GET' && path === '/api/calendar') return send(res,200,[...db.calendar_events].sort((a,b)=>String(a.date).localeCompare(String(b.date))));
    if (req.method === 'POST' && path === '/api/calendar') {
      const u=getUser(req); if(!u || !isStaff(u.user.activeRole))return send(res,403,{error:'Only class staff can add calendar events.'});
      const calError=calendarError(body); if(calError)return send(res,400,{error:calError});
      db.calendar_events.push({id:nextId(db.calendar_events),date:String(body.date),type:body.type||'Class',title:cleanText(body.title,100),description:cleanText(body.description,500),author_id:u.row.id,created_at:now()}); save(); return send(res,200,{ok:true,message:'Calendar event added.'});
    }
    if (path.startsWith('/api/calendar/')) {
      const u=getUser(req); if(!u || !isStaff(u.user.activeRole))return send(res,403,{error:'Only class staff can manage events.'});
      const id=Number(path.split('/').pop()); const row=db.calendar_events.find(x=>x.id===id); if(!row)return send(res,404,{error:'Event not found.'});
      if(req.method==='PATCH'){
        const calError=calendarError(body); if(calError)return send(res,400,{error:calError});
        row.date=String(body.date); row.type=body.type||'Class'; row.title=cleanText(body.title,100); row.description=cleanText(body.description,500); row.updated_at=now(); save(); return send(res,200,{ok:true,message:'Event updated.'});
      }
      if(req.method==='DELETE'){ db.calendar_events.splice(db.calendar_events.indexOf(row),1); save(); return send(res,200,{ok:true,message:'Event removed.'}); }
    }

    if (req.method === 'GET' && path === '/api/messages') {
      const u=getUser(req); if(!u)return send(res,401,{error:'Please sign in first.'});
      return send(res,200,[...db.messages].filter(x=>x.sender_id===u.row.id||x.receiver_id===u.row.id).sort((a,b)=>new Date(b.created_at)-new Date(a.created_at)).map(x=>({...x,senderName:userDisplay(x.sender_id),receiverName:userDisplay(x.receiver_id)})));
    }
    if (req.method === 'POST' && path === '/api/messages') {
      const u=getUser(req); if(!u)return send(res,401,{error:'Please sign in first.'});
      const receiverId=Number(body.receiverId); if(receiverId===u.row.id)return send(res,400,{error:'You cannot message yourself.'}); const receiver=db.users.find(x=>x.id===receiverId); if(!receiver)return send(res,404,{error:'Recipient not found.'});
      const msgError=fieldError('Message', body.message, RULE.text, { max: 1000 }); if(msgError)return send(res,400,{error:msgError});
      db.messages.push({id:nextId(db.messages),sender_id:u.row.id,receiver_id:receiverId,message:cleanText(body.message,1000),created_at:now(),read:false}); save(); return send(res,200,{ok:true,message:'Message sent.'});
    }
    if (req.method === 'GET' && path === '/api/directory') {
      const u=getUser(req); if(!u)return send(res,401,{error:'Please sign in first.'});
      const rows=db.users.filter(x=>x.id!==u.row.id).filter(x=>isStaff(u.user.activeRole) || isStaff(x.role) || ['moderator','classrep'].includes(x.subrole)).map(x=>({id:x.id,name:userDisplay(x.id),role:x.role,subrole:x.subrole,studentId:decrypt(x.student_id_enc)}));
      return send(res,200,rows);
    }

    if (req.method === 'GET' && path === '/api/warnings') {
      const u=getUser(req); if(!u)return send(res,401,{error:'Please sign in first.'});
      let rows=isStaff(u.user.activeRole)?db.warnings:db.warnings.filter(x=>x.student_id===u.row.id);
      return send(res,200,[...rows].sort((a,b)=>new Date(b.created_at)-new Date(a.created_at)).map(x=>({...x,studentName:userDisplay(x.student_id)})));
    }
    if (req.method === 'POST' && path === '/api/warnings') {
      const u=getUser(req); if(!u || !['professor','admin','moderator'].includes(u.user.activeRole))return send(res,403,{error:'Only professors, admins and moderators can add warnings.'});
      const studentId=Number(body.studentId); if(!db.users.find(x=>x.id===studentId))return send(res,404,{error:'Student not found.'}); const noteErr=fieldError('Warning note', body.note, RULE.text, { max: 500 }); if(noteErr)return send(res,400,{error:noteErr});
      db.warnings.push({id:nextId(db.warnings),student_id:studentId,note:cleanText(body.note,500),created_by:u.row.id,created_at:now()}); save(); return send(res,200,{ok:true,message:'Warning saved.'});
    }

    if (req.method === 'GET' && path === '/api/admin/users') {
      const u=getUser(req); if(!u)return send(res,401,{error:'Please sign in first.'}); if(u.user.activeRole!=='admin')return send(res,403,{error:'Admin access only.'});
      return send(res,200,[...db.users].sort((a,b)=>new Date(b.created_at)-new Date(a.created_at)).map(x=>({id:x.id,role:x.role,subrole:x.subrole,studentId:decrypt(x.student_id_enc),name:userDisplay(x.id),username:decrypt(x.username_enc),campusEmail:decrypt(x.campus_email_enc),is_irregular:x.is_irregular,email_verified:x.email_verified,created_at:x.created_at})));
    }
    if (req.method === 'PATCH' && path.startsWith('/api/admin/users/')) {
      const u=getUser(req); if(!u || u.user.activeRole!=='admin')return send(res,403,{error:'Admin access only.'});
      const id=Number(path.split('/').pop()); const target=db.users.find(x=>x.id===id); if(!target)return send(res,404,{error:'User not found.'});
      const role=normalizeRole(body.role); const subrole=body.subrole&&['admin','classrep','moderator'].includes(body.subrole)?body.subrole:null;
      if(target.id===u.row.id && role!=='admin')return send(res,400,{error:'Keep at least one admin account.'});
      target.role=role; target.subrole=role==='student'?subrole:null; if(role!=='student') target.subrole=null; save(); return send(res,200,{ok:true,message:'User role updated.'});
    }
    if (req.method === 'POST' && path === '/api/admin/users') {
      const u=getUser(req); if(!u || u.user.activeRole!=='admin')return send(res,403,{error:'Admin access only.'});
      const role=normalizeRole(body.role); const username=cleanText(body.username,80).toLowerCase(); const password=String(body.password||''); const firstName=cleanText(body.firstName,50); const lastName=cleanText(body.lastName,50);
      const staffError=firstError(fieldError('Username', username, RULE.username), passwordError(password,'Temporary password'), fieldError('First name', firstName, RULE.name), fieldError('Last name', lastName, RULE.name));
      if(staffError)return send(res,400,{error:staffError});
      const extraError=firstError(fieldError('Student ID', body.studentId, RULE.studentId, { optional: true }), fieldError('Middle name', body.middleName, RULE.name, { optional: true }), body.guardianGmail ? emailError('Guardian email', body.guardianGmail) : null);
      if(extraError)return send(res,400,{error:extraError});
      if(findUserByIdentifier(username))return send(res,409,{error:'That username is already in use.'});
      const user={id:nextId(db.users),role,subrole:role==='student'?null:null,campus_email_enc:encrypt(''),username_enc:encrypt(username),password_hash:await bcrypt.hash(password,12),student_id_enc:encrypt(body.studentId||''),first_name_enc:encrypt(firstName),middle_name_enc:encrypt(body.middleName||''),last_name_enc:encrypt(lastName),guardian_gmail_enc:encrypt(body.guardianGmail||''),is_irregular:false,enrolled_subjects_enc:encrypt('[]'),email_verified:true,verification_token_hash:null,qr_token_hash:sha(crypto.randomBytes(24).toString('hex')),created_at:now()};
      db.users.push(user); save(); return send(res,200,{ok:true,message:'Account created.',id:user.id});
    }

    return send(res,404,{error:'That feature is not available.'});
  } catch (err) {
    console.error(err);
    return send(res,500,{error:'Offline server error: '+err.message});
  }
};
