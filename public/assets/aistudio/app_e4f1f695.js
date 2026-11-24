const $ = (s, root=document) => root.querySelector(s);
const $$ = (s, root=document) => [...root.querySelectorAll(s)];
let me = null;
let schedule = [], subjects = [], announcements = [], resources = [], calendarEvents = [];
let calendarCursor = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
let selectedCalendarDate = new Date().toISOString().slice(0,10);
let rolePending = null;
let cameraStream = null;
let editingCalendarId = null;

const esc = value => String(value ?? '').replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
const roleName = role => ({admin:'Administrator',professor:'Professor',classrep:'Class Representative',moderator:'Moderator',student:'Student'}[role] || role);
const toast = message => { const t=$('#toast'); t.textContent=message; t.classList.add('show'); clearTimeout(window.__toast); window.__toast=setTimeout(()=>t.classList.remove('show'),3000); };

/* ---------- Form input limits (mirrors the server rules in api/index.js) ---------- */
const allow=(cls,max,hint,extra={})=>({max,hint,strip:new RegExp(`[^${cls}]`,'gu'),...extra});
const FREE_BAD=/[<>{}\\|^~`\u0000-\u0008\u000B\u000C\u000E-\u001F]/g;
const free=max=>({max,hint:'Not allowed: < > { } \\ | ^ ~ `',strip:FREE_BAD,counter:true});
const NAME_CLS="\\p{L} .'’\\-", EMAIL_CLS="A-Za-z0-9@._%+\\-", TITLE_CLS="\\p{L}\\p{N} .,:;!?'’\"()\\-_/&#@+%", ANSWER_CLS="\\p{L}\\p{N} .'’\\-";
const PASSWORD_RULE=allow('A-Za-z0-9!@#$%^&*_.?\\-',20,'8 to 20 characters. Letters, numbers and ! @ # $ % ^ & * _ - . ? only',{min:8});
const RULES={
  firstName:allow(NAME_CLS,50,"Letters, spaces and . ' - only"),
  middleName:allow(NAME_CLS,50,"Letters, spaces and . ' - only"),
  lastName:allow(NAME_CLS,50,"Letters, spaces and . ' - only"),
  studentId:allow('0-9',12,'Numbers only (5 to 12 digits)',{min:5}),
  campusEmail:allow(EMAIL_CLS,100,'Letters, numbers and @ . _ % + - only'),
  guardianGmail:allow(EMAIL_CLS,100,'Letters, numbers and @ . _ % + - only'),
  identifier:allow(EMAIL_CLS,100,''),
  username:allow('a-z0-9._\\-',30,'Lowercase letters, numbers and . _ - only',{lower:true,min:3}),
  password:PASSWORD_RULE,
  newPassword:PASSWORD_RULE,
  currentPassword:{max:72,hint:''},
  confirmPassword:{...PASSWORD_RULE,hint:''},
  enrolledSubjects:allow('A-Za-z0-9, ',100,'Subject codes separated by commas, e.g. COSC75, COSC80'),
  answer:allow(ANSWER_CLS,60,"Letters, numbers, spaces and . ' - only",{min:2}),
  title:allow(TITLE_CLS,100,"Letters, numbers, spaces and . , : ; ! ? ' \" ( ) - _ / & # @ + % only"),
  room:allow('A-Za-z0-9 \\-',20,'Letters, numbers, spaces and - only'),
  description:free(500), body:free(3000), reason:free(2000), message:free(1000), note:free(500),
  reviewNote:{...free(500),hint:''}, qrInput:{max:200,hint:''}
};
function ruleFor(el){
  if(el.closest('#loginForm')) return null; // sign-in must accept older passwords as they were saved
  if(el.hasAttribute('data-review-note')) return RULES.reviewNote;
  const key=el.name||el.id; if(!key) return null;
  if(/^(recoveryAnswer|answer)\d+$/.test(key)) return RULES.answer;
  return Object.prototype.hasOwnProperty.call(RULES,key)?RULES[key]:null;
}
function applyRule(el){
  if(el.dataset.ruled||el.type==='file'||el.type==='hidden') return;
  const rule=ruleFor(el); if(!rule) return;
  el.dataset.ruled='1'; if(!rule.strip) el.maxLength=rule.max; // fields that strip characters are trimmed in clean(), so pasted text is cleaned before it is cut to the limit
  let hintBox=null;
  const showHint=(rule.hint||rule.counter)&&!el.closest('#loginForm');
  if(showHint){
    hintBox=document.createElement('small'); hintBox.className='field-hint';
    hintBox.innerHTML=`<span class="fh-text"></span>${rule.counter?'<span class="fh-count"></span>':''}`;
    $('.fh-text',hintBox).textContent=rule.hint||'';
    (el.parentElement&&el.parentElement.tagName==='LABEL'?el.parentElement:el.parentNode).insertBefore(hintBox,el.parentElement&&el.parentElement.tagName==='LABEL'?null:el.nextSibling);
  }
  const count=()=>{const c=hintBox&&$('.fh-count',hintBox); if(c) c.textContent=`${el.value.length}/${rule.max}`};
  const clean=()=>{
    const before=el.value; let v=before;
    if(rule.lower) v=v.toLowerCase();
    if(rule.strip) v=v.replace(rule.strip,'');
    if(v.length>rule.max) v=v.slice(0,rule.max);
    if(v!==before){
      let caret=null; try{caret=el.selectionStart}catch{}
      el.value=v;
      try{ if(caret!==null){const pos=Math.max(0,caret-(before.length-v.length)); el.setSelectionRange(pos,pos)} }catch{}
      if(hintBox){hintBox.classList.add('flash');clearTimeout(hintBox._t);hintBox._t=setTimeout(()=>hintBox.classList.remove('flash'),700)}
    }
    el.setCustomValidity(v&&rule.min&&v.length<rule.min?`Please enter at least ${rule.min} characters.`:'');
    count();
  };
  el.addEventListener('input',clean); clean();
}
function enhanceForms(){ document.querySelectorAll('input,textarea').forEach(applyRule); }
new MutationObserver(enhanceForms).observe(document.body,{childList:true,subtree:true});
enhanceForms();

async function api(url, opt={}) {
  const options = {...opt, credentials:'same-origin'};
  options.headers = {'Content-Type':'application/json', ...(opt.headers||{})};
  let response;
  try { response = await fetch(url, options); }
  catch { throw Error('Can’t reach the local server. Make sure start.bat is running.'); }
  const data = await response.json().catch(()=>({}));
  if (!response.ok) throw Error(data.error || 'Something went wrong.');
  return data;
}

function updateClock(){
  const clock=$('#clock');
  if(clock) clock.textContent=new Date().toLocaleString([], {weekday:'short', month:'short', day:'numeric', hour:'2-digit', minute:'2-digit'});
}
setInterval(updateClock,1000); updateClock();

function initials(user){ return ((user?.firstName||'U')[0]+(user?.lastName||'S')[0]).toUpperCase(); }
function canStaff(){ return ['professor','admin','classrep','moderator'].includes(me?.activeRole); }
function canManage(){ return ['professor','admin'].includes(me?.activeRole); }
function canAdmin(){ return me?.activeRole==='admin'; }
function canScanQr(){ return me?.activeRole==='professor'; }
function canReview(){ return ['professor','admin','classrep','moderator'].includes(me?.activeRole); }
function subjectOptions(selected='') { return subjects.map(s=>`<option value="${esc(s.code)}" ${s.code===selected?'selected':''}>${esc(s.code)} — ${esc(s.name)}</option>`).join(''); }

function fileToDataUrl(file){
  return new Promise((resolve,reject)=>{
    if(file.size>8*1024*1024) return reject(new Error(`${file.name} is bigger than 8 MB.`));
    const reader=new FileReader(); reader.onload=()=>resolve({name:file.name,type:file.type,size:file.size,dataUrl:reader.result}); reader.onerror=()=>reject(new Error(`Couldn’t read ${file.name}.`)); reader.readAsDataURL(file);
  });
}
async function readFiles(input, max=5){
  const files=[...input.files].slice(0,max); const out=[];
  for(const file of files) out.push(await fileToDataUrl(file));
  return out;
}

function showRoleChoice(options){
  rolePending=options;
  $('#roleChoice').innerHTML=options.map(role=>`<button type="button" data-role-choice="${esc(role)}"><b>${esc(roleName(role))}</b><small>${role==='student'?'Open the student view':`Open the ${roleName(role).toLowerCase()} view`}</small></button>`).join('');
  $('#roleChoiceModal').classList.remove('hidden');
  $$('#roleChoice [data-role-choice]').forEach(btn=>btn.onclick=()=>finishLogin(btn.dataset.roleChoice));
}
async function finishLogin(roleChoice){
  try{
    const payload={identifier:$('#identifier').value.trim(),password:$('#password').value,roleChoice};
    const data=await api('/api/login',{method:'POST',body:JSON.stringify(payload)});
    if(data.requiresRoleChoice){ showRoleChoice(data.options); return; }
    $('#roleChoiceModal').classList.add('hidden'); rolePending=null; me=data.user; await showApp();
  }catch(error){toast(error.message)}
}

$('#closeRoleChoice').onclick=()=>$('#roleChoiceModal').classList.add('hidden');
$('#closeLightbox').onclick=closeLightbox;
$('#imgLightbox').onclick=e=>{if(e.target.id==='imgLightbox')closeLightbox()};
$('#irregular').onchange=e=>$('#irregSubjects').classList.toggle('hidden',e.target.value!=='1');
const RECOVERY_POOL=['first pet','favorite food','mom name','dad name','first school name'];
function shuffle(a){return [...a].sort(()=>Math.random()-.5)}
function renderRecoveryQuestions(){
  const picked=shuffle(RECOVERY_POOL).slice(0,3);
  $('#recoveryQuestions').innerHTML=picked.map((q,i)=>`<label>${esc(q.replace(/\b\w/g,c=>c.toUpperCase()))}<input name=\"recoveryAnswer${i}\" data-recovery-question=\"${esc(q)}\" required placeholder=\"Your answer\"></label>`).join('');
}
renderRecoveryQuestions();
$('#loginForm').onsubmit=async e=>{e.preventDefault();await finishLogin(null)};
$('#registerForm').onsubmit=async e=>{
  e.preventDefault(); const form=new FormData(e.target); const data=Object.fromEntries(form);
  data.isIrregular=data.isIrregular==='1'; data.enrolledSubjects=(data.enrolledSubjects||'').split(',').map(x=>x.trim()).filter(Boolean); data.recoveryQuestions=$$('[data-recovery-question]',e.target).map(input=>({question:input.dataset.recoveryQuestion,answer:input.value.trim()}));
  try{const result=await api('/api/register',{method:'POST',body:JSON.stringify(data)}); toast(result.message); e.target.reset(); renderRecoveryQuestions(); $('#irregSubjects').classList.add('hidden'); document.querySelector('[data-auth="login"]').click(); $('#identifier').value=data.campusEmail; $('#password').focus();}
  catch(error){toast(error.message)}
};
$$('[data-demo]').forEach(btn=>btn.onclick=async()=>{try{const result=await api('/api/demo-login',{method:'POST',body:JSON.stringify({role:btn.dataset.demo})});me=result.user;await showApp()}catch(error){toast(error.message)}});
const logoutModal=$('#logoutModal');
const closeLogoutModal=()=>logoutModal.classList.add('hidden');
$('#logout').onclick=()=>{logoutModal.classList.remove('hidden');$('#logoutCancel').focus()};
$('#logoutCancel').onclick=closeLogoutModal;
logoutModal.onclick=e=>{if(e.target===logoutModal)closeLogoutModal()};
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!logoutModal.classList.contains('hidden'))closeLogoutModal()});
$('#logoutConfirm').onclick=async()=>{$('#logoutConfirm').disabled=true;try{await api('/api/logout',{method:'POST'})}catch{}finally{location.reload()}};
$('#themeBtn').onclick=()=>{document.body.classList.toggle('night');localStorage.setItem('cstracker-theme',document.body.classList.contains('night')?'night':'day');$('#themeBtn').textContent=document.body.classList.contains('night')?'Light':'Dark'};
if(localStorage.getItem('cstracker-theme')==='night'){document.body.classList.add('night');setTimeout(()=>$('#themeBtn').textContent='Light',0)}

$('#nav').onclick=e=>{const btn=e.target.closest('button[data-page]');if(!btn)return; $$('#nav button').forEach(x=>x.classList.remove('active'));btn.classList.add('active');stopCamera();stopScanner();render(btn.dataset.page)};
$('#profileShortcut').onclick=()=>$('#nav [data-page=\"profile\"]')?.click();

async function showApp(){
  $('#authView').classList.add('hidden'); $('#appView').classList.remove('hidden');
  $('#userName').textContent=[me.firstName,me.lastName].filter(Boolean).join(' ')||'User';
  $('#userRole').textContent=roleName(me.activeRole);
  $('#avatar').textContent=initials(me);
  $$('.staff-nav').forEach(el=>el.classList.toggle('hidden',!canStaff()));
  $$('.manage-nav').forEach(el=>el.classList.toggle('hidden',!canManage()));
  $$('.prof-nav').forEach(el=>el.classList.toggle('hidden',me.activeRole!=='professor'));
  $$('.admin-nav').forEach(el=>el.classList.toggle('hidden',!canAdmin()));
  schedule=await api('/api/schedule'); subjects=await api('/api/subjects'); announcements=await api('/api/announcements'); resources=await api('/api/resources'); calendarEvents=await api('/api/calendar');
  render('dashboard');
  handlePendingStudentQr();
}

function render(page){
  const pages={dashboard:dashboard,schedule:schedulePage,calendar:calendarPage,announcements:announcementsPage,attendance:attendancePage,qr:qrPage,resources:resourcesPage,inbox:inboxPage,excuse:excusePage,warnings:warningsPage,admin:adminPage,profile:profilePage,scanner:scannerPage};
  Promise.resolve((pages[page]||dashboard)()).catch(error=>toast(error.message || 'Could not open this page.'));
}
function head(title,sub=''){return `<div class="page-head"><div><div class="eyebrow">CSTRACKER</div><h1>${esc(title)}</h1><p>${esc(sub)}</p></div></div>`}
function empty(text){return `<div class="empty"><b>${esc(text)}</b><span>Nothing here yet.</span></div>`}
function formatBytes(n){const v=Number(n)||0;if(v<1024)return `${v} B`;if(v<1024*1024)return `${Math.round(v/1024)} KB`;return `${(v/1024/1024).toFixed(1)} MB`}
function fileChips(files=[]){return files.map(f=>`<button class="file-chip" type="button" data-file-json="${encodeURIComponent(JSON.stringify(f))}">↗ ${esc(f.name)}</button>`).join('')}
function announcementMedia(files=[]){
  if(!files.length) return '';
  const images=files.filter(f=>(f.type||'').startsWith('image/'));
  const others=files.filter(f=>!(f.type||'').startsWith('image/'));
  const gallery=images.length?`<div class="announcement-media">${images.map(f=>`<img class="announcement-img" src="${f.dataUrl}" alt="${esc(f.name)}" data-lightbox-src="${encodeURIComponent(f.dataUrl)}" data-lightbox-alt="${encodeURIComponent(f.name)}">`).join('')}</div>`:'';
  return gallery+fileChips(others);
}
function bindMediaImages(){
  $$('.announcement-img').forEach(img=>img.onclick=()=>openLightbox(decodeURIComponent(img.dataset.lightboxSrc),decodeURIComponent(img.dataset.lightboxAlt)));
}
function openLightbox(src,alt){const box=$('#imgLightbox');if(!box)return;$('#lightboxImg').src=src;$('#lightboxImg').alt=alt||'';box.classList.remove('hidden')}
function closeLightbox(){const box=$('#imgLightbox');if(box)box.classList.add('hidden');const img=$('#lightboxImg');if(img)img.src=''}

async function dashboard(){
  try{
    const [attendance,warnings,ex] = await Promise.all([api('/api/attendance'),api('/api/warnings'),api('/api/excuse')]);
    const mine=me.activeRole==='student' ? attendance : attendance;
    const abs=mine.filter(x=>x.status==='Absent').length, late=mine.filter(x=>x.status==='Late').length;
    const pending=ex.filter(x=>x.status==='Pending').length;
    const fullName=[me.firstName,me.middleName,me.lastName].filter(Boolean).join(' ')||'—';
    $('#main').innerHTML=head(`Hi, ${me.firstName||'there'}`, 'Here’s what’s happening with your class.')+
      `<section class="dash-group" aria-labelledby="dashAcademic">
        <div class="dash-group-head"><span class="dash-group-dot"></span><h2 id="dashAcademic">Academic</h2></div>
        <div class="cards">
          <div class="card stat"><span>Absences</span><b>${abs}</b><small>${abs>=3?'Please check your attendance.':'Looking good'}</small></div>
          <div class="card stat"><span>Lates</span><b>${late}</b><small>Keep an eye on your time</small></div>
          <div class="card stat"><span>Announcements</span><b>${announcements.length}</b><small>Latest class updates</small></div>
          <div class="card stat"><span>Excuse letters</span><b>${pending}</b><small>Waiting for review</small></div>
        </div>
        <div class="grid2">
          <section class="card"><div class="section-title"><h2>Next classes</h2><button class="text-btn" data-go="schedule">View schedule</button></div>${schedule.slice(0,5).map(s=>`<div class="list-row"><div><b>${esc(s.subject_code)}</b><span>${esc(s.name)}</span></div><aside>${esc(s.day)}<br>${esc(s.start_time)}–${esc(s.end_time)} · Rm ${esc(s.room)}</aside></div>`).join('')||empty('No schedule')}</section>
          <section class="card"><div class="section-title"><h2>Latest updates</h2><button class="text-btn" data-go="announcements">See all</button></div>${announcements.slice(0,4).map(a=>`<div class="notice"><b>${esc(a.title)}</b><span>${esc(a.subject_name||'General')} · ${esc(a.author_name||'Staff')}</span></div>`).join('')||empty('No announcements')}</section>
        </div>
        ${warnings.length?`<section class="card warning-card"><h2>Recent reminders</h2>${warnings.slice(0,3).map(w=>`<div class="notice"><b>${esc(w.note)}</b><span>${new Date(w.created_at).toLocaleString()}</span></div>`).join('')}</section>`:''}
      </section>
      <section class="dash-group" aria-labelledby="dashPersonal">
        <div class="dash-group-head"><span class="dash-group-dot"></span><h2 id="dashPersonal">Personal details</h2></div>
        <section class="card personal-card">
          <div class="personal-avatar">${me.profilePicture?`<img src="${esc(me.profilePicture)}" alt="">`:`<span>${esc(initials(me))}</span>`}</div>
          <dl class="personal-list">
            <div><dt>Name</dt><dd>${esc(fullName)}</dd></div>
            <div><dt>Student ID</dt><dd>${esc(me.studentId||'—')}</dd></div>
            <div><dt>Campus email</dt><dd>${esc(me.campusEmail||me.username||'—')}</dd></div>
            <div><dt>Role</dt><dd>${esc(roleName(me.activeRole))}</dd></div>
          </dl>
          <div class="personal-actions"><button class="secondary" data-go="profile">My Profile</button><button class="secondary" data-go="qr">My QR</button><button class="secondary" data-go="inbox">Messages</button></div>
        </section>
      </section>`;
    $$('[data-go]').forEach(b=>b.onclick=()=>{const target=b.dataset.go; $(`#nav [data-page="${target}"]`)?.click()});
  }catch(error){toast(error.message)}
}

function localISO(date){
  const d=new Date(date); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
}
function timeValue(value){
  const m=String(value||'').match(/(\d{1,2}):(\d{2})/); return m ? Number(m[1])*60+Number(m[2]) : 9999;
}
function scheduleForDay(day){
  return schedule.filter(s=>s.day===day).sort((a,b)=>timeValue(a.start_time)-timeValue(b.start_time));
}
function eventClass(type){ return String(type||'class').toLowerCase().replace(/\s+/g,'-'); }
function eventTypeLabel(type){ return ({quiz:'Quiz',exam:'Exam',holiday:'Holiday',deadline:'Deadline',class:'Class',reminder:'Reminder'}[eventClass(type)]||type||'Event'); }

function schedulePage(){
  const days=['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'];
  const todayName=new Date().toLocaleDateString('en-US',{weekday:'long'});
  const total=schedule.length;
  $('#main').innerHTML=head('Schedule',`${total} class${total===1?'':'es'} each week. Everything is grouped by day so it is easier to scan.`)+
    `<div class="schedule-summary card">
      <div><span class="schedule-kicker">This week</span><b>${total}</b><small>scheduled class${total===1?'':'es'}</small></div>
      <div><span class="schedule-kicker">Today</span><b>${scheduleForDay(todayName).length}</b><small>${esc(todayName)}</small></div>
      <div class="schedule-tip"><span>Quick view</span><small>Time → subject → room</small></div>
    </div>
    <section class="schedule-board">${days.map(day=>{
      const classes=scheduleForDay(day), isToday=day===todayName;
      return `<article class="schedule-day ${isToday?'is-today':''}">
        <div class="schedule-day-head"><div><span>${esc(day.slice(0,3).toUpperCase())}</span><h2>${esc(day)}</h2></div>${isToday?'<em>Today</em>':''}</div>
        <div class="schedule-day-list">${classes.length?classes.map(s=>`<div class="class-card">
          <div class="class-time">${esc(s.start_time)}<span>to ${esc(s.end_time)}</span></div>
          <div class="class-main"><strong>${esc(s.subject_code)}</strong><h3>${esc(s.name)}</h3><div class="class-meta"><span>Room ${esc(s.room||'—')}</span><span>${esc(s.professor||'No professor')}</span></div></div>
        </div>`).join(''):'<div class="no-class"><span>Free day</span><small>No classes scheduled.</small></div>'}</div>
      </article>`;
    }).join('')}</section>
    <section class="card schedule-list-card"><div class="section-title"><div><h2>All classes</h2><span class="muted">Sorted by day and start time.</span></div></div><div class="table-scroll"><table><thead><tr><th>Day</th><th>Time</th><th>Subject</th><th>Room</th><th>Professor</th></tr></thead><tbody>${days.flatMap(d=>scheduleForDay(d)).map(s=>`<tr><td><b>${esc(s.day)}</b></td><td><b>${esc(s.start_time)}</b><br><small>${esc(s.end_time)}</small></td><td><b>${esc(s.subject_code)}</b><br><small>${esc(s.name)}</small></td><td>${esc(s.room||'—')}</td><td>${esc(s.professor||'—')}</td></tr>`).join('')||`<tr><td colspan="5">No classes have been added yet.</td></tr>`}</tbody></table></div></section>`;
}

async function calendarPage(){
  const y=calendarCursor.getFullYear(), m=calendarCursor.getMonth();
  const first=new Date(y,m,1).getDay(), days=new Date(y,m+1,0).getDate();
  const monthLabel=calendarCursor.toLocaleString([],{month:'long',year:'numeric'});
  const today=localISO(new Date());
  const visibleDays=Array.from({length:42},(_,i)=>i-first+1);
  const selectedEvents=calendarEvents.filter(e=>e.date===selectedCalendarDate).sort((a,b)=>String(a.type).localeCompare(String(b.type)));
  const selectedDateObj=new Date(`${selectedCalendarDate}T12:00:00`);
  const selectedDayName=selectedDateObj.toLocaleDateString('en-US',{weekday:'long'});
  const selectedClasses=scheduleForDay(selectedDayName);
  const selectedLabel=selectedDateObj.toLocaleDateString([],{weekday:'long',month:'long',day:'numeric',year:'numeric'});

  $('#main').innerHTML=head('Calendar','A clear month view for exams, quizzes, deadlines and class reminders.')+
    `<div class="calendar-toolbar">
      <button id="prevMonth" aria-label="Previous month">‹</button>
      <button id="todayMonth" class="today-btn">Today</button>
      <div class="calendar-month"><span>Calendar</span><h2>${esc(monthLabel)}</h2></div>
      <button id="nextMonth" aria-label="Next month">›</button>
    </div>
    <div class="calendar-layout">
      <section class="calendar-box card">
        <div class="calendar-head">${['Sun','Mon','Tue','Wed','Thu','Fri','Sat'].map(x=>`<b>${x}</b>`).join('')}</div>
        <div class="calendar">${visibleDays.map(dayNum=>{
          if(dayNum<1||dayNum>days) return '<div class="day blank"></div>';
          const dateObj=new Date(y,m,dayNum), iso=localISO(dateObj), events=calendarEvents.filter(e=>e.date===iso), classes=scheduleForDay(dateObj.toLocaleDateString('en-US',{weekday:'long'}));
          const isToday=iso===today, isSelected=iso===selectedCalendarDate;
          return `<button type="button" class="day ${isToday?'today':''} ${isSelected?'selected':''}" data-calendar-date="${iso}">
            <span class="day-number">${dayNum}</span>${isToday?'<span class="day-today">Today</span>':''}
            <div class="day-items">${events.slice(0,3).map(e=>`<span class="event event-${eventClass(e.type)}"><i></i><b>${esc(eventTypeLabel(e.type))}</b><span>${esc(e.title)}</span></span>`).join('')}
            ${events.length>3?`<span class="more-events">+${events.length-3} more</span>`:''}
            ${classes.slice(0,2).map(s=>`<span class="mini-class"><i></i>${esc(s.subject_code)} · ${esc(s.start_time)}</span>`).join('')}
            </div>
          </button>`;
        }).join('')}</div>
        <div class="calendar-legend"><span><i class="legend-dot quiz"></i>Quiz</span><span><i class="legend-dot exam"></i>Exam</span><span><i class="legend-dot deadline"></i>Deadline</span><span><i class="legend-dot holiday"></i>Holiday</span><span><i class="legend-dot class"></i>Class</span></div>
      </section>
      <aside class="calendar-details card">
        <div class="selected-date-head"><div><span>Selected day</span><h2>${esc(selectedLabel)}</h2></div><button id="selectToday" class="text-btn">Jump to today</button></div>
        <div class="detail-block"><div class="detail-title"><b>Events</b><span>${selectedEvents.length}</span></div>${selectedEvents.length?selectedEvents.map(e=>`<div class="calendar-event-detail"><div class="event-detail-icon event-${eventClass(e.type)}">${esc(eventTypeLabel(e.type).slice(0,1))}</div><div><strong>${esc(e.title)}</strong><span>${esc(eventTypeLabel(e.type))}</span>${e.description?`<p>${esc(e.description)}</p>`:''}${canStaff()?`<div class="event-actions"><button class="text-btn" data-edit-event="${e.id}">Edit</button><button class="text-btn danger" data-delete-event="${e.id}">Remove</button></div>`:''}</div></div>`).join(''):'<div class="detail-empty">No events on this day.</div>'}</div>
        <div class="detail-block"><div class="detail-title"><b>Classes</b><span>${selectedClasses.length}</span></div>${selectedClasses.length?selectedClasses.map(s=>`<div class="calendar-class-detail"><strong>${esc(s.subject_code)}</strong><span>${esc(s.start_time)}–${esc(s.end_time)} · Room ${esc(s.room||'—')}</span><small>${esc(s.name)}${s.professor?` · ${esc(s.professor)}`:''}</small></div>`).join(''):'<div class="detail-empty">No classes scheduled.</div>'}</div>
      </aside>
    </div>
    ${canStaff()?`<section class="card form-card calendar-form"><div class="section-title"><div><h2 id="eventFormTitle">Add calendar event</h2><span class="muted">Keep events short and easy to scan.</span></div><button id="cancelEventEdit" type="button" class="text-btn hidden">Cancel edit</button></div><form id="eventForm" class="form-grid"><input id="eventId" type="hidden" name="id"><label>Date<input id="eventDate" type="date" name="date" value="${esc(selectedCalendarDate)}" required></label><label>Type<select name="type"><option>Quiz</option><option>Exam</option><option>Holiday</option><option>Deadline</option><option>Class</option><option>Reminder</option></select></label><label>Title<input name="title" placeholder="e.g. OS Quiz" required></label><label>Description<input name="description" placeholder="Optional note"></label><button id="eventSubmit" class="primary">Add event</button></form></section>`:''}`;

  $('#prevMonth').onclick=()=>{calendarCursor=new Date(y,m-1,1);calendarPage()};
  $('#nextMonth').onclick=()=>{calendarCursor=new Date(y,m+1,1);calendarPage()};
  const jumpToday=()=>{const now=new Date();calendarCursor=new Date(now.getFullYear(),now.getMonth(),1);selectedCalendarDate=localISO(now);calendarPage()};
  $('#todayMonth').onclick=jumpToday; $('#selectToday').onclick=jumpToday;
  $$('[data-calendar-date]').forEach(btn=>btn.onclick=()=>{selectedCalendarDate=btn.dataset.calendarDate;calendarPage()});
  $$('[data-delete-event]').forEach(btn=>btn.onclick=async()=>{if(!confirm('Remove this calendar event?'))return;try{await api(`/api/calendar/${btn.dataset.deleteEvent}`,{method:'DELETE'});calendarEvents=await api('/api/calendar');toast('Event removed');calendarPage()}catch(error){toast(error.message)}});
  $$('[data-edit-event]').forEach(btn=>btn.onclick=()=>{const event=calendarEvents.find(x=>String(x.id)===String(btn.dataset.editEvent));if(!event)return;editingCalendarId=event.id;$('#eventId').value=event.id;$('#eventDate').value=event.date;$('[name=type]',$('#eventForm')).value=event.type;$('[name=title]',$('#eventForm')).value=event.title;$('[name=description]',$('#eventForm')).value=event.description||'';$('#eventFormTitle').textContent='Edit calendar event';$('#eventSubmit').textContent='Save changes';$('#cancelEventEdit').classList.remove('hidden');$('#eventForm').scrollIntoView({behavior:'smooth',block:'center'});});
  $('#cancelEventEdit')?.addEventListener('click',()=>{editingCalendarId=null;calendarPage()});
  $('#eventForm')?.addEventListener('submit',async e=>{e.preventDefault();try{const data=Object.fromEntries(new FormData(e.target));const id=data.id;delete data.id;if(editingCalendarId){await api(`/api/calendar/${id}`,{method:'PATCH',body:JSON.stringify(data)});toast('Event updated')}else{await api('/api/calendar',{method:'POST',body:JSON.stringify(data)});toast('Event added')}calendarEvents=await api('/api/calendar');selectedCalendarDate=data.date;const d=new Date(`${data.date}T12:00:00`);calendarCursor=new Date(d.getFullYear(),d.getMonth(),1);editingCalendarId=null;calendarPage()}catch(error){toast(error.message)}});
}

function announcementsPage(){
  $('#main').innerHTML=head('Announcements','Class updates, reminders and quick notices.')+
    `<div class="stack">${announcements.map(a=>`<article class="card announcement"><div class="announcement-meta"><span class="tag">${esc(a.subject_name||'General')}</span><span>${new Date(a.created_at).toLocaleString()}</span></div><h2>${esc(a.title)}</h2><p>${esc(a.body)}</p>${announcementMedia(a.attachments)}${canStaff()?`<div><button class="text-btn danger" data-delete-announcement="${a.id}">Remove</button></div>`:''}</article>`).join('')||empty('No announcements yet.')}</div>`+
    (canStaff()?`<section class="card form-card"><h2>Post an update</h2><form id="annForm"><div class="form-grid"><label>Title<input name="title" required placeholder="Short and clear"></label><label>Subject<select name="subjectCode"><option value="">General</option>${subjectOptions()}</select></label></div><label>Message<textarea name="body" rows="5" required placeholder="What should the class know?"></textarea></label><label>Attach files <input id="annFiles" type="file" multiple accept="image/*,application/pdf"></label><small class="muted">Up to 5 files, 8 MB each.</small><button class="primary">Post announcement</button></form></section>`:'');
  bindFileButtons();
  bindMediaImages();
  $$('[data-delete-announcement]').forEach(btn=>btn.onclick=async()=>{if(!confirm('Remove this announcement?'))return;try{await api(`/api/announcements/${btn.dataset.deleteAnnouncement}`,{method:'DELETE'});announcements=await api('/api/announcements');toast('Announcement removed');announcementsPage()}catch(error){toast(error.message)}});
  $('#annForm')?.addEventListener('submit',async e=>{e.preventDefault();try{const o=Object.fromEntries(new FormData(e.target));o.attachments=await readFiles($('#annFiles'));await api('/api/announcements',{method:'POST',body:JSON.stringify(o)});announcements=await api('/api/announcements');toast('Posted');announcementsPage()}catch(error){toast(error.message)}});
}

async function attendancePage(){
  const rows=await api('/api/attendance');
  $('#main').innerHTML=head('Attendance',canScanQr()?'Check records or mark a student with the QR tracker.':canStaff()?'Check attendance records.':'Your attendance record.')+
    `<section class="card"><div class="section-title"><h2>Records</h2><span class="muted">${rows.length} record${rows.length===1?'':'s'}</span></div><div class="table-scroll"><table><thead><tr>${canStaff()?'<th>Student</th>':''}<th>Subject</th><th>Date</th><th>Status</th><th>Verified</th></tr></thead><tbody>${rows.map(x=>`<tr>${canStaff()?`<td><b>${esc(x.studentId||'—')}</b><br><small>${esc(x.studentName)}</small></td>`:''}<td>${esc(x.subject_code)}<br><small>${esc(x.subjectName||'')}</small></td><td>${esc(x.class_date)}</td><td><span class="status ${esc(x.status)}">${esc(x.status)}</span></td><td>${x.verified_at?new Date(x.verified_at).toLocaleString():'—'}</td></tr>`).join('')||`<tr><td colspan="5">No attendance records yet.</td></tr>`}</tbody></table></div></section>`+
    (canScanQr()?`<section class="card scanner-card"><div class="section-title"><div><h2>Mark attendance</h2><p>Scan the student QR or paste the QR data.</p></div><button id="cameraBtn" class="secondary">Use camera</button></div><video id="qrVideo" class="qr-video hidden" autoplay playsinline></video><div class="scanner-grid"><textarea id="qrInput" placeholder='Paste QR data here if you are not using the camera'></textarea><div><label>Subject<select id="scanSubject">${subjectOptions()}</select></label><label>Status<select id="scanStatus"><option>Present</option><option>Late</option><option>Absent</option></select></label><label>Date<input id="scanDate" type="date" value="${new Date().toISOString().slice(0,10)}"></label><button class="primary wide" id="scanBtn">Save attendance</button></div></div></section>`:'');
  if(canScanQr()){
    $('#scanBtn').onclick=async()=>{try{await api('/api/attendance/scan',{method:'POST',body:JSON.stringify({qrPayload:$('#qrInput').value,subjectCode:$('#scanSubject').value,status:$('#scanStatus').value,classDate:$('#scanDate').value})});toast('Attendance saved');stopCamera();attendancePage()}catch(error){toast(error.message)}};
    $('#cameraBtn').onclick=toggleCamera;
  }
}
async function toggleCamera(){
  if(!canScanQr()){toast('Only professors can use the QR tracker.');return}
  const video=$('#qrVideo');
  if(cameraStream){stopCamera();return}
  if(!('BarcodeDetector' in window)){toast('This browser does not support QR camera scanning. Paste the QR data instead.');return}
  try{cameraStream=await navigator.mediaDevices.getUserMedia({video:{facingMode:'environment'}});video.srcObject=cameraStream;video.classList.remove('hidden');$('#cameraBtn').textContent='Stop camera';const detector=new BarcodeDetector({formats:['qr_code']});scanLoop(detector,video)}catch(error){toast('Camera access was blocked. You can still paste the QR data.')}
}
async function scanLoop(detector,video){
  if(!cameraStream)return;
  try{const codes=await detector.detect(video);if(codes[0]?.rawValue){$('#qrInput').value=codes[0].rawValue;toast('QR captured. Choose the subject and save.');stopCamera();return;}}catch{}
  requestAnimationFrame(()=>scanLoop(detector,video));
}
function stopCamera(){if(cameraStream){cameraStream.getTracks().forEach(t=>t.stop());cameraStream=null;}const video=$('#qrVideo');if(video){video.srcObject=null;video.classList.add('hidden')}const btn=$('#cameraBtn');if(btn)btn.textContent='Use camera'}

async function qrPage(){const data=await api('/api/qr');$('#main').innerHTML=head('My QR code','Keep this handy for attendance. It belongs to your account.')+`<section class="card qr-card"><img src="${data.dataUrl}" alt="Student QR code"><div class="qr-actions"><button class="primary" id="printQr">Print</button><button class="secondary" id="openQr">Open image</button></div><p class="muted">Don’t post this QR publicly.</p></section>`;$('#printQr').onclick=()=>window.print();$('#openQr').onclick=()=>window.open(data.dataUrl,'_blank','noopener')}

async function resourcesPage(){
  resources=await api('/api/resources');
  $('#main').innerHTML=head('Class files','PDFs and materials shared by your professors.')+
    `<div class="resource-grid">${resources.map(r=>`<article class="card resource"><div class="pdf-icon">PDF</div><div class="resource-main"><div class="announcement-meta"><span class="tag">${esc(r.subjectName||'General')}</span><span>${new Date(r.created_at).toLocaleDateString()}</span></div><h2>${esc(r.title)}</h2><p>${esc(r.description||'No description.')}</p><small>Uploaded by ${esc(r.authorName||'Professor')} · ${formatBytes(r.attachment?.size||0)}</small><div class="resource-actions"><button class="primary" data-resource="${r.id}">Open PDF</button>${canManage()?`<button class="text-btn danger" data-delete-resource="${r.id}">Remove</button>`:''}</div></div></article>`).join('')||empty('No class files yet.')}</div>`+
    (canManage()?`<section class="card form-card"><h2>Upload a PDF</h2><p class="muted">Share handouts, reviewers, instructions and other class files.</p><form id="resourceForm"><div class="form-grid"><label>Title<input name="title" required placeholder="e.g. OS Reviewer"></label><label>Subject<select name="subjectCode">${subjectOptions()}</select></label></div><label>Description<textarea name="description" rows="3" placeholder="Optional"></textarea></label><label>PDF file<input id="resourceFile" type="file" accept="application/pdf" required></label><button class="primary">Upload PDF</button></form></section>`:'');
  $$('[data-resource]').forEach(btn=>btn.onclick=async()=>{try{const r=await api(`/api/resources/${btn.dataset.resource}`);const a=document.createElement('a');a.href=r.dataUrl;a.target='_blank';a.rel='noopener';a.click()}catch(error){toast(error.message)}});
  $$('[data-delete-resource]').forEach(btn=>btn.onclick=async()=>{if(!confirm('Remove this file?'))return;try{await api(`/api/resources/${btn.dataset.deleteResource}`,{method:'DELETE'});toast('File removed');resourcesPage()}catch(error){toast(error.message)}});
  $('#resourceForm')?.addEventListener('submit',async e=>{e.preventDefault();try{const f=await readFiles($('#resourceFile'),1);if(!f[0]||f[0].type!=='application/pdf')throw Error('Choose a PDF file.');const o=Object.fromEntries(new FormData(e.target));o.attachment=f[0];await api('/api/resources',{method:'POST',body:JSON.stringify(o)});toast('PDF uploaded');resourcesPage()}catch(error){toast(error.message)}});
}

async function excusePage(){
  const rows=await api('/api/excuse');
  $('#main').innerHTML=head('Excuse letters',canReview()?'Review submitted letters and their attachments.':'Send an excuse letter with supporting files.')+
    (canReview()?`<section class="stack">${rows.map(x=>`<article class="card excuse"><div class="announcement-meta"><span class="tag ${x.status.toLowerCase()}">${esc(x.status)}</span><span>${new Date(x.created_at).toLocaleString()}</span></div><h2>${esc(x.studentName||x.studentId||'Student')}</h2><b>${esc(x.subject_code)} — ${esc(x.subjectName||'')}</b><p>${esc(x.reason)}</p>${fileChips(x.attachments)}<div class="review-box"><textarea data-review-note="${x.id}" placeholder="Optional note">${esc(x.review_note||'')}</textarea><button class="primary" data-review="${x.id}" data-status="Approved">Approve</button><button class="secondary" data-review="${x.id}" data-status="Rejected">Reject</button></div></article>`).join('')||empty('No excuse letters yet.')}</section>`:
    `<section class="card form-card"><form id="exForm"><label>Subject<select name="subjectCode">${subjectOptions()}</select></label><label>Reason<textarea name="reason" rows="7" required placeholder="Tell your professor what happened."></textarea></label><label>Supporting files<input id="excuseFiles" type="file" multiple accept="image/*,application/pdf"></label><small class="muted">You can attach photos of notes, medical/official documents, or a PDF. Up to 5 files, 8 MB each.</small><div id="filePreview" class="file-preview"></div><button class="primary">Submit excuse letter</button></form></section><section class="stack"><h2>My submissions</h2>${rows.map(x=>`<article class="card excuse"><div class="announcement-meta"><span class="tag ${x.status.toLowerCase()}">${esc(x.status)}</span><span>${new Date(x.created_at).toLocaleString()}</span></div><b>${esc(x.subject_code)} — ${esc(x.subjectName||'')}</b><p>${esc(x.reason)}</p>${fileChips(x.attachments)}${x.review_note?`<div class="review-note"><b>Staff note:</b> ${esc(x.review_note)}</div>`:''}</article>`).join('')||empty('You have not submitted an excuse letter.')}</section>`);
  bindFileButtons();
  $('#excuseFiles')?.addEventListener('change',()=>{const files=[...$('#excuseFiles').files];$('#filePreview').innerHTML=files.map(f=>`<span class="file-preview-item"><b>${esc(f.type.startsWith('image/')?'IMG':'PDF')}</b>${esc(f.name)}<small>${formatBytes(f.size)}</small></span>`).join('')});
  $('#exForm')?.addEventListener('submit',async e=>{e.preventDefault();try{const o=Object.fromEntries(new FormData(e.target));o.attachments=await readFiles($('#excuseFiles'));await api('/api/excuse',{method:'POST',body:JSON.stringify(o)});toast('Excuse letter submitted');excusePage()}catch(error){toast(error.message)}});
  $$('[data-review]').forEach(btn=>btn.onclick=async()=>{const id=btn.dataset.review;try{await api(`/api/excuse/${id}`,{method:'PATCH',body:JSON.stringify({status:btn.dataset.status,reviewNote:$(`[data-review-note="${id}"]`).value})});toast('Updated');excusePage()}catch(error){toast(error.message)}});
}

function bindFileButtons(){
  $$('.file-chip').forEach(btn=>btn.onclick=()=>{try{const file=JSON.parse(decodeURIComponent(btn.dataset.fileJson));const a=document.createElement('a');a.href=file.dataUrl;a.target='_blank';a.rel='noopener';a.download=file.name;a.click()}catch{toast('Could not open that file.')}});
}

async function profilePage(){
  const picture=me.profilePicture||'';
  $('#main').innerHTML=head('Profile','Manage your profile picture and password. Your name and student ID cannot be changed here.')+
  `<div class=\"grid2\"><section class=\"card form-card profile-card\"><div class=\"profile-large\">${picture?`<img src=\"${picture}\" alt=\"Profile picture\">`:`<span>${esc(initials(me))}</span>`}</div><h2>${esc([me.firstName,me.middleName,me.lastName].filter(Boolean).join(' '))}</h2><p class=\"muted\">${esc(me.studentId)} · ${esc(me.campusEmail)}</p><form id=\"pictureForm\"><label>Profile picture<input id=\"profilePicture\" type=\"file\" accept=\"image/png,image/jpeg,image/webp\"></label><button class=\"primary\">Save picture</button><button type=\"button\" class=\"secondary\" id=\"removePicture\">Remove picture</button></form></section>
  <section class=\"card form-card\"><h2>Change password</h2><form id=\"passwordForm\"><label>Current password<input name=\"currentPassword\" type=\"password\" required></label><label>New password<input name=\"newPassword\" type=\"password\" minlength=\"8\" required></label><label>Confirm new password<input name=\"confirmPassword\" type=\"password\" minlength=\"8\" required></label><button class=\"primary\">Change password</button></form></section></div>`;
  $('#pictureForm').onsubmit=async e=>{e.preventDefault();try{const f=$('#profilePicture').files[0];let profilePicture='';if(f){if(f.size>2*1024*1024)throw Error('Profile picture must be 2 MB or smaller.');profilePicture=(await fileToDataUrl(f)).dataUrl;}const data=await api('/api/profile',{method:'PATCH',body:JSON.stringify({profilePicture})});me=data.user;$('#avatar').innerHTML=me.profilePicture?`<img src=\"${me.profilePicture}\" alt=\"\">`:esc(initials(me));toast('Profile picture updated');profilePage()}catch(error){toast(error.message)}};
  $('#removePicture').onclick=async()=>{try{const data=await api('/api/profile',{method:'PATCH',body:JSON.stringify({profilePicture:''})});me=data.user;toast('Profile picture removed');profilePage()}catch(error){toast(error.message)}};
  $('#passwordForm').onsubmit=async e=>{e.preventDefault();const o=Object.fromEntries(new FormData(e.target));if(o.newPassword!==o.confirmPassword)return toast('New passwords do not match.');try{await api('/api/profile',{method:'PATCH',body:JSON.stringify(o)});toast('Password changed successfully');e.target.reset()}catch(error){toast(error.message)}};
}

async function openForgot(){
  const modal=$('#forgotModal'), box=$('#forgotContent');modal.classList.remove('hidden');
  box.innerHTML=`<form id=\"forgotEmailForm\" class=\"form-card\"><p class=\"muted\">Enter your CVSU campus email first.</p><label>Campus email<input name=\"campusEmail\" type=\"email\" required pattern=\"^[^@\\s]+@cvsu\\.edu\\.ph$\" placeholder=\"yourname@cvsu.edu.ph\"></label><button class=\"primary\">Continue</button></form>`;
  $('#forgotEmailForm').onsubmit=async e=>{e.preventDefault();const email=new FormData(e.target).get('campusEmail');try{const data=await api('/api/forgot/start',{method:'POST',body:JSON.stringify({campusEmail:email})});box.innerHTML=`<form id=\"forgotAnswersForm\" class=\"form-card\"><p class=\"muted\">Answer all 3 recovery questions exactly as you entered them during registration.</p>${data.questions.map((q,i)=>`<label>${esc(q)}<input name=\"answer${i}\" required></label>`).join('')}<label>New password<input name=\"newPassword\" type=\"password\" minlength=\"8\" required></label><label>Confirm new password<input name=\"confirmPassword\" type=\"password\" minlength=\"8\" required></label><button class=\"primary\">Change password</button></form>`;$('#forgotAnswersForm').onsubmit=async ev=>{ev.preventDefault();const f=new FormData(ev.target);const np=f.get('newPassword');if(np!==f.get('confirmPassword'))return toast('New passwords do not match.');try{await api('/api/forgot/reset',{method:'POST',body:JSON.stringify({campusEmail:email,answers:[f.get('answer0'),f.get('answer1'),f.get('answer2')],newPassword:np})});toast('Password changed. You can sign in now.');modal.classList.add('hidden');}catch(error){toast(error.message)}}}catch(error){toast(error.message)}};
}
$('#forgotPasswordBtn').onclick=openForgot; $('#closeForgot').onclick=()=>$('#forgotModal').classList.add('hidden');

async function inboxPage(){
  const [messages,directory]=await Promise.all([api('/api/messages'),api('/api/directory')]);
  $('#main').innerHTML=head('Messages','A simple way to reach your professor or class staff.')+
    `<section class="card form-card"><h2>New message</h2><form id="msgForm"><label>Send to<select name="receiverId">${directory.map(x=>`<option value="${x.id}">${esc(x.name)} · ${esc(roleName(x.role))}</option>`).join('')}</select></label><label>Message<textarea name="message" rows="4" required placeholder="Type your message..."></textarea></label><button class="primary">Send</button></form></section>
     <section class="stack"><h2>Recent messages</h2>${messages.map(m=>`<article class="card message"><div><b>${esc(m.sender_id===me.id?'You':m.senderName)}</b> <span class="muted">to ${esc(m.receiver_id===me.id?'You':m.receiverName)}</span><small>${new Date(m.created_at).toLocaleString()}</small></div><p>${esc(m.message)}</p></article>`).join('')||empty('No messages yet.')}</section>`;
  $('#msgForm')?.addEventListener('submit',async e=>{e.preventDefault();try{await api('/api/messages',{method:'POST',body:JSON.stringify(Object.fromEntries(new FormData(e.target)))});toast('Message sent');inboxPage()}catch(error){toast(error.message)}});
}

async function warningsPage(){
  const rows=await api('/api/warnings');
  const directory=canReview()?await api('/api/directory'):[];
  $('#main').innerHTML=head('Warnings','Keep track of attendance and class reminders.')+
    `<section class="stack">${rows.map(w=>`<article class="card warning-card"><div class="announcement-meta"><span class="tag">${esc(w.studentName)}</span><span>${new Date(w.created_at).toLocaleString()}</span></div><p>${esc(w.note)}</p></article>`).join('')||empty('No warnings yet.')}</section>`+
    (['professor','admin','moderator'].includes(me.activeRole)?`<section class="card form-card"><h2>Add a warning</h2><form id="warnForm"><label>Student<select name="studentId">${directory.filter(x=>x.role==='student'||x.subrole).map(x=>`<option value="${x.id}">${esc(x.name)} · ${esc(x.studentId||'')}</option>`).join('')}</select></label><label>Note<textarea name="note" rows="4" required placeholder="Keep it factual and short."></textarea></label><button class="primary">Save warning</button></form></section>`:'');
  $('#warnForm')?.addEventListener('submit',async e=>{e.preventDefault();try{await api('/api/warnings',{method:'POST',body:JSON.stringify(Object.fromEntries(new FormData(e.target)))});toast('Warning saved');warningsPage()}catch(error){toast(error.message)}});
}

async function adminPage(){
  const [users,currentSchedule]=await Promise.all([api('/api/admin/users'),api('/api/schedule')]);
  const roleOptions=[['student','Student'],['student|moderator','Student + Moderator'],['student|classrep','Student + Class Representative'],['professor','Professor'],['moderator','Moderator'],['classrep','Class Representative'],['admin','Administrator']];
  $('#main').innerHTML=head('Administration','Accounts and class setup.')+
    `<div class="cards"><div class="card stat"><span>Accounts</span><b>${users.length}</b></div><div class="card stat"><span>Students</span><b>${users.filter(x=>x.role==='student').length}</b></div><div class="card stat"><span>Professors</span><b>${users.filter(x=>x.role==='professor').length}</b></div><div class="card stat"><span>Moderators</span><b>${users.filter(x=>x.role==='moderator'||x.subrole==='moderator').length}</b></div></div>
    <section class="card form-card"><h2>Create staff account</h2><form id="staffForm"><div class="form-grid"><label>First name<input name="firstName" required></label><label>Last name<input name="lastName" required></label><label>Username<input name="username" required></label><label>Temporary password<input name="password" required minlength="8"></label><label>Role<select name="role"><option value="professor">Professor</option><option value="moderator">Moderator</option><option value="classrep">Class Representative</option><option value="admin">Administrator</option></select></label></div><button class="primary">Create account</button></form></section>
    <section class="card"><div class="section-title"><div><h2>Accounts</h2><span class="muted">You can give a student a secondary class role.</span></div></div><div class="table-scroll"><table><thead><tr><th>Name</th><th>Username / Email</th><th>Role</th><th>Created</th><th></th></tr></thead><tbody>${users.map(u=>{const current=u.role==='student'&&u.subrole?`student|${u.subrole}`:u.role;return `<tr><td><b>${esc(u.name)}</b><br><small>${esc(u.studentId||'')}</small></td><td>${esc(u.username||u.campusEmail||'—')}</td><td><select data-user-role="${u.id}">${roleOptions.map(([v,label])=>`<option value="${v}" ${current===v?'selected':''}>${esc(label)}</option>`).join('')}</select></td><td>${new Date(u.created_at).toLocaleDateString()}</td><td><button class="text-btn" data-save-role="${u.id}">Save</button></td></tr>`}).join('')}</tbody></table></div></section>
    <section class="card form-card"><div class="section-title"><div><h2>Manage schedule</h2><span class="muted">Add or remove class slots. Changes are saved locally.</span></div></div><form id="scheduleForm" class="form-grid"><label>Day<select name="day"><option>Monday</option><option>Tuesday</option><option>Wednesday</option><option>Thursday</option><option>Friday</option><option>Saturday</option><option>Sunday</option></select></label><label>Subject<select name="subject_code">${subjectOptions()}</select></label><label>Start<input name="start_time" type="time" required></label><label>End<input name="end_time" type="time" required></label><label>Room<input name="room" placeholder="e.g. 107"></label><button class="primary">Add class</button></form><div class="table-scroll schedule-admin-table"><table><thead><tr><th>Day</th><th>Time</th><th>Subject</th><th>Room</th><th></th></tr></thead><tbody>${['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'].flatMap(day=>currentSchedule.filter(x=>x.day===day)).map(s=>`<tr><td>${esc(s.day)}</td><td>${esc(s.start_time)}–${esc(s.end_time)}</td><td><b>${esc(s.subject_code)}</b><br><small>${esc(s.name||'')}</small></td><td>${esc(s.room||'—')}</td><td><button class="text-btn danger" data-delete-schedule="${s.id}">Remove</button></td></tr>`).join('')||`<tr><td colspan="5">No schedule entries.</td></tr>`}</tbody></table></div></section>`;
  $('#staffForm')?.addEventListener('submit',async e=>{e.preventDefault();try{await api('/api/admin/users',{method:'POST',body:JSON.stringify(Object.fromEntries(new FormData(e.target)))});toast('Account created');adminPage()}catch(error){toast(error.message)}});
  $$('[data-save-role]').forEach(btn=>btn.onclick=async()=>{const id=btn.dataset.saveRole;const raw=$(`[data-user-role="${id}"]`).value;const [role,subrole='']=raw.split('|');try{await api(`/api/admin/users/${id}`,{method:'PATCH',body:JSON.stringify({role,subrole:subrole||null})});toast('Role updated');adminPage()}catch(error){toast(error.message)}});
  $('#scheduleForm')?.addEventListener('submit',async e=>{e.preventDefault();try{await api('/api/schedule',{method:'POST',body:JSON.stringify(Object.fromEntries(new FormData(e.target)))});toast('Class added');adminPage();schedule=await api('/api/schedule')}catch(error){toast(error.message)}});
  $$('[data-delete-schedule]').forEach(btn=>btn.onclick=async()=>{if(!confirm('Remove this class from the schedule?'))return;try{await api(`/api/schedule/${btn.dataset.deleteSchedule}`,{method:'DELETE'});toast('Class removed');schedule=await api('/api/schedule');adminPage()}catch(error){toast(error.message)}});
}

async function restoreSession(){
  try{const user=await api('/api/me');me=user;await showApp();}
  catch{ if(sessionStorage.getItem('pendingStudentQr')) toast('Sign in as a professor to mark this student.'); }
}
(function(){const st=new URLSearchParams(location.search).get('student');
  if(st&&/^[0-9a-f]{64}$/.test(st)){try{sessionStorage.setItem('pendingStudentQr',st)}catch{}history.replaceState(null,'',location.pathname)}})();
restoreSession();

if('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(()=>{});
if(new URLSearchParams(location.search).get('verified')==='1') toast('You can sign in now.');

async function handlePendingStudentQr(){
  let h=null; try{h=sessionStorage.getItem('pendingStudentQr')}catch{}
  if(!h) return; try{sessionStorage.removeItem('pendingStudentQr')}catch{}
  if(!canScanQr()){toast('Only professors can mark attendance with a student QR.');return}
  $$('#nav button').forEach(x=>x.classList.toggle('active',x.dataset.page==='attendance'));
  try{await attendancePage();const box=$('#qrInput');if(box){box.value=JSON.stringify({studentIdHash:h});toast('Student QR captured. Choose the subject and save.')}}catch(error){toast(error.message)}
}

/* ---------- Professor QR scanner (scan student QR codes one after another) ---------- */
function localDate(){return new Date(Date.now()-new Date().getTimezoneOffset()*60000).toISOString().slice(0,10)}
let scanStream=null,scanTimer=null,lastCode='',lastAt=0,jsQRLoading=null;
function loadJsQR(){
  if(window.jsQR) return Promise.resolve();
  if(!jsQRLoading) jsQRLoading=new Promise((res,rej)=>{const s=document.createElement('script');s.src='https://cdn.jsdelivr.net/npm/jsqr@1.4.0/dist/jsQR.min.js';s.onload=res;s.onerror=()=>{jsQRLoading=null;rej(Error('Could not load the QR reader. Check your internet connection.'))};document.head.appendChild(s)});
  return jsQRLoading;
}
function stopScanner(){
  if(scanTimer){clearInterval(scanTimer);scanTimer=null}
  if(scanStream){scanStream.getTracks().forEach(t=>t.stop());scanStream=null}
  const v=$('#sVideo'); if(v){v.srcObject=null;v.classList.add('hidden')}
  const b=$('#sStart'); if(b) b.textContent='Start camera';
  const h=$('#sHint'); if(h) h.textContent='Camera is off.';
}
function addScanLog(ok,text){
  const log=$('#sLog'); if(!log) return;
  if(log.classList.contains('muted')){log.classList.remove('muted');log.innerHTML=''}
  log.insertAdjacentHTML('afterbegin',`<div class="scan-row ${ok?'ok':'bad'}">${ok?'✓':'✕'} ${esc(text)} <small>${new Date().toLocaleTimeString([], {hour:'2-digit',minute:'2-digit',second:'2-digit'})}</small></div>`);
  while(log.children.length>15) log.lastChild.remove();
}
async function submitScan(raw){
  try{
    const r=await api('/api/attendance/scan',{method:'POST',body:JSON.stringify({qrPayload:String(raw).trim().slice(0,200),subjectCode:$('#sSubject').value,status:$('#sStatus').value,classDate:$('#sDate').value})});
    addScanLog(true,r.message); if(navigator.vibrate) navigator.vibrate(80);
  }catch(error){addScanLog(false,error.message)}
}
async function startScanner(){
  stopScanner();
  const video=$('#sVideo');
  if(!navigator.mediaDevices?.getUserMedia){toast('The camera needs a secure (https) connection and a supported browser. You can paste the QR data instead.');return}
  try{scanStream=await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:'environment'}}})}
  catch{toast('Camera permission was blocked. Allow the camera, or paste the QR data instead.');return}
  video.srcObject=scanStream; video.classList.remove('hidden'); try{await video.play()}catch{}
  $('#sStart').textContent='Stop camera'; $('#sHint').textContent='Point the camera at a student QR. It saves automatically.';
  let detector=null;
  if('BarcodeDetector' in window){try{detector=new BarcodeDetector({formats:['qr_code']})}catch{}}
  if(!detector){try{await loadJsQR()}catch(error){toast(error.message);stopScanner();return}}
  const canvas=$('#sCanvas'),ctx=canvas.getContext('2d',{willReadFrequently:true});
  scanTimer=setInterval(async()=>{
    if(!video.videoWidth) return; let raw=null;
    try{
      if(detector){const found=await detector.detect(video);raw=found[0]?.rawValue||null}
      else{canvas.width=video.videoWidth;canvas.height=video.videoHeight;ctx.drawImage(video,0,0);const img=ctx.getImageData(0,0,canvas.width,canvas.height);const r=window.jsQR(img.data,img.width,img.height,{inversionAttempts:'dontInvert'});raw=r?r.data:null}
    }catch{}
    if(raw&&(raw!==lastCode||Date.now()-lastAt>4000)){lastCode=raw;lastAt=Date.now();submitScan(raw)}
  },250);
}
async function scannerPage(){
  if(!canScanQr()){toast('Only professors can open the scanner.');return}
  $('#main').innerHTML=head('QR Scanner','Pick the class, then scan each student’s QR. Attendance saves automatically.')+
  `<section class="card scanner-card"><div class="scanner-grid"><div><label>Subject<select id="sSubject">${subjectOptions()}</select></label><label>Status<select id="sStatus"><option>Present</option><option>Late</option></select></label><label>Date<input id="sDate" type="date" value="${localDate()}"></label><button class="primary wide" id="sStart">Start camera</button><label>Or paste QR data<input id="sPaste" placeholder="Paste the QR data, then press Enter"></label></div><div><video id="sVideo" class="qr-video hidden" autoplay playsinline muted></video><canvas id="sCanvas"></canvas><p class="muted" id="sHint">Camera is off.</p></div></div></section>
  <section class="card"><div class="section-title"><h2>Scanned</h2></div><div id="sLog" class="muted">Nothing scanned yet.</div></section>`;
  $('#sStart').onclick=()=>scanStream?stopScanner():startScanner();
  $('#sPaste').onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();const v=e.target.value.trim();if(v){submitScan(v);e.target.value=''}}};
}
