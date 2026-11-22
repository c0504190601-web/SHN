'use strict';
let TOKEN = localStorage.getItem('kosherride_token') || null;
let CUR = null; // מכשיר נוכחי + policy

// רשימת הגבלות — חייבת להתאים למזהים באפליקציה (RestrictionsManager)
const RESTRICTIONS = [
  ['מכשיר ורשת', [
    ['wifi','שינוי הגדרות Wi-Fi'],['bt_cfg','שינוי Bluetooth'],['bt','חסימת Bluetooth'],
    ['hotspot','נקודה חמה'],['roaming','נדידה'],['mobile','נתונים סלולריים'],['airplane','מצב טיסה'],
    ['net_reset','איפוס רשת'],['private_dns','Private DNS'],['vpn','הגדרות VPN (נעילת חומת אש)'],
    ['cell_broadcast','Cell Broadcast'],['nfc_beam','NFC']]],
  ['אבטחה ומערכת', [
    ['factory_reset','איפוס יצרן'],['safe_boot','מצב בטוח'],['dev_options','אפשרויות מפתח'],
    ['unknown_sources','מקורות לא מוכרים'],['install_apps','התקנת אפליקציות'],['uninstall_apps','הסרת אפליקציות'],
    ['apps_control','ניהול אפליקציות'],['credentials','תעודות אבטחה'],['physical_media','כרטיס SD'],
    ['usb','USB'],['printing','הדפסה']]],
  ['חשבונות ומשתמשים', [
    ['add_user','הוספת משתמשים'],['user_switch','החלפת משתמשים'],['accounts','חשבונות'],['managed_profile','פרופיל עבודה']]],
  ['תקשורת ומדיה', [
    ['calls','שיחות'],['sms','SMS'],['location','מיקום'],['camera','מצלמה'],['microphone','מיקרופון']]],
  ['ממשק', [
    ['screenshot','צילומי מסך'],['statusbar','שורת מצב'],['volume','עוצמת קול'],
    ['wallpaper','טפט'],['autofill','Autofill'],['keyguard','מסך נעילה']]],
];

async function api(path, opts = {}) {
  opts.headers = Object.assign({ 'Content-Type': 'application/json' }, opts.headers || {});
  if (TOKEN) opts.headers.Authorization = 'Bearer ' + TOKEN;
  const r = await fetch('/api' + path, opts);
  const data = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(data.error || 'שגיאה');
  return data;
}

function show(id) {
  ['view-login', 'view-devices', 'view-device'].forEach(v =>
    document.getElementById(v).classList.toggle('hidden', v !== id));
}

async function doLogin() {
  const u = document.getElementById('u').value, p = document.getElementById('p').value;
  try {
    const d = await api('/auth/login', { method: 'POST', body: JSON.stringify({ username: u, password: p }) });
    TOKEN = d.token; localStorage.setItem('kosherride_token', TOKEN);
    document.getElementById('who').textContent = d.username;
    showDevices();
  } catch (e) { document.getElementById('loginErr').textContent = e.message; }
}

let DEVICES = [];

async function showDevices() {
  show('view-devices');
  try {
    const d = await api('/devices');
    DEVICES = d.devices;
    renderDeviceList();
  } catch (e) {
    if (String(e.message).includes('אימות') || String(e.message).includes('אסימון')) logout();
  }
}

function openByNumber() {
  const n = document.getElementById('devNum').value.trim();
  const err = document.getElementById('numErr');
  err.textContent = '';
  if (!/^\d+$/.test(n)) { err.textContent = 'הזן מספר מכשיר'; return; }
  openDevice(n).catch(() => { show('view-devices'); err.textContent = 'מכשיר ' + n + ' לא נמצא'; });
}

function renderDeviceList() {
  const f = (document.getElementById('devFilter')?.value || '').trim().toLowerCase();
  const list = DEVICES.filter(dev => !f || String(dev.id).includes(f) || (dev.name || '').toLowerCase().includes(f));
  {
    const el = document.getElementById('deviceList');
    el.innerHTML = list.length ? '' : '<div class="muted">' + (DEVICES.length ? 'לא נמצאו מכשירים.' : 'אין מכשירים עדיין. מכשיר נרשם לבד ברגע שמפעילים בו הרשאת ניהול.') + '</div>';
    list.forEach(dev => {
      const div = document.createElement('div');
      div.className = 'devitem';
      div.onclick = () => openDevice(dev.id);
      div.innerHTML = `<div><b>#${dev.id}</b> ${dev.name ? '· ' + dev.name : ''}
        <div class="muted">${dev.model || ''} ${dev.android ? '· Android ' + dev.android : ''}</div></div>
        <span class="chip ${dev.online ? 'on' : 'off'}">${dev.online ? 'מחובר' : 'לא מחובר'}</span>`;
      el.appendChild(div);
    });
  }
}


// ===================== דף מכשיר — זהה לאפליקציה =====================
const TABS = ['לוח בקרה', 'אפליקציות', 'חומת אש', 'סינון', 'אבחון', 'הגבלות', 'יומן'];
let TAB = 0, POL = null, REP = {}, CAT = [], EVENTS = [];
let APPQ = '', APPF = 'ALL', APPSYS = false, OPEN_APP = null, SAVE_T = null;

const esc = (x) => String(x ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const clone = (o) => JSON.parse(JSON.stringify(o || {}));

function defPolicy(p) {
  p = p || {};
  p.restrictions = p.restrictions || {};
  p.firewall = Object.assign({ enabled: false, blockWholeDevice: false, filterMode: false, defaultAllow: true }, p.firewall || {});
  p.kiosk = Object.assign({ enabled: false }, p.kiosk || {});
  p.domains = Object.assign({ mode: 'BLOCK', blocked: [], allowed: [] }, p.domains || {});
  p.domains.blocked = p.domains.blocked || []; p.domains.allowed = p.domains.allowed || [];
  p.watermark = Object.assign({ enabled: false }, p.watermark || {});
  p.mapsFilter = Object.assign({ enabled: false, extra: [] }, p.mapsFilter || {});
  p.mapsFilter.extra = p.mapsFilter.extra || [];
  p.icon = Object.assign({ hidden: false }, p.icon || {});
  p.apps = p.apps || {};
  return p;
}

async function openDevice(id) {
  show('view-device');
  document.getElementById('deviceBody').innerHTML = '<div class="card muted">טוען…</div>';
  const d = await api('/devices/' + id);
  CUR = d.device;
  REP = CUR.reported || {};
  CAT = Array.isArray(CUR.apps) ? CUR.apps : [];
  // אם המכשיר כבר החיל את הגרסה האחרונה — מציגים את המצב שהוא דיווח (כולל שינויים שנעשו באפליקציה).
  // אחרת — מציגים את השינויים מהאתר שעדיין ממתינים לסנכרון.
  const synced = (CUR.applied_version || 0) >= (CUR.policy_version || 0) && REP.state;
  POL = defPolicy(clone(synced ? REP.state : CUR.policy));
  CUR.policy = POL;
  TAB = 0; OPEN_APP = null;
  renderDevice();
  loadEvents(id);
}

function sw(on, onclick) {
  return `<span class="sw ${on ? 'swon' : ''}" onclick="${onclick}"><span class="k"></span></span>`;
}
function toggleRow(title, sub, on, onclick) {
  // לחיצה על כל השורה מחליפה את המתג (כמו באפליקציה)
  return `<div class="row clickrow" onclick="${onclick}"><div><div>${title}</div>${sub ? `<div class="muted">${sub}</div>` : ''}</div>${sw(on, '')}</div>`;
}

function renderDevice() {
  const d = CUR;
  const seen = d.last_seen ? new Date(d.last_seen) : null;
  const online = seen && (Date.now() - seen.getTime() < 20 * 60000);
  let h = `<div class="dev-head">
      <div><span class="brand">נסיעה כשרה</span> <span class="muted">· ניהול</span></div>
      <div class="dev-num">מכשיר מס' ${d.id}</div>
    </div>
    <div class="savebar-top"><span class="chip ${online ? 'on' : 'off'}">${online ? 'מחובר' : 'לא מחובר'}</span>
      <span class="muted" id="saveMsg">${pendingText()}</span></div>
    <div class="tabs">${TABS.map((t, i) => `<span class="tab ${i === TAB ? 'sel' : ''}" onclick="setTab(${i})">${t}</span>`).join('')}</div>
    <div id="tabBody"></div>`;
  document.getElementById('deviceBody').innerHTML = h;
  renderTab();
}

function pendingText() {
  if ((CUR.policy_version || 0) > (CUR.applied_version || 0))
    return 'שינויים ממתינים — יוחלו במכשיר בסנכרון הבא (עד 15 דקות)';
  return REP.at ? 'מעודכן לפי דיווח המכשיר מ-' + new Date(REP.at).toLocaleString('he-IL') : 'המכשיר עוד לא דיווח מצב מלא';
}

function setTab(i) { TAB = i; renderDevice(); window.scrollTo(0, 0); }

function renderTab() {
  const el = document.getElementById('tabBody');
  if (!el) return;
  el.innerHTML = [tabDashboard, tabApps, tabFirewall, tabFiltering, tabDiag, tabRestrictions, tabLog][TAB]();
  if (TAB === 6) renderEvents();
}

// ---------- לוח בקרה ----------
function tabDashboard() {
  const d = CUR, pol = POL;
  let h = `<div class="card"><h3>מספר מכשיר ושליטה מרחוק</h3>
    <div class="big-num">${d.id}</div>
    <div class="muted">זה מספר המכשיר. כדי לשנות הגדרות מרחוק — נכנסים לאתר למכשיר לפי המספר הזה.</div>
    <div class="row"><span>סנכרון אחרון</span><span class="muted">${d.last_seen ? new Date(d.last_seen).toLocaleString('he-IL') : '—'}</span></div>
    <div class="row"><span>גרסת מדיניות</span><span class="muted">${d.policy_version} (הוחל במכשיר: ${d.applied_version})</span></div>
    <div class="grid2" style="margin-top:6px"><input type="text" id="devName" placeholder="שם למכשיר (לא חובה)" value="${esc(d.name || '')}">
      <button class="ghost" onclick="saveName()">שמור שם</button></div>
  </div>`;

  const perm = REP.overlayPermission;
  h += `<div class="card"><h3>סמל כשרות על המסך</h3>
    ${toggleRow('הצג סמל קבוע במרכז המסך',
      !pol.watermark.enabled ? 'כבוי' : (perm === false ? '<span style="color:var(--amber)">ממתין להרשאה במכשיר</span>' : '<span style="color:var(--green)">מוצג</span>'),
      pol.watermark.enabled, "tgl('watermark.enabled')")}
    <div class="muted">סמל מגן חצי-שקוף עם הכיתוב "נסיעה כשרה". לחיצות עוברות דרכו, כך שהוא לא מפריע לשימוש.</div>
    ${pol.watermark.enabled && perm === false ? '<div class="muted" style="color:var(--amber)">נדרשת במכשיר הרשאה חד-פעמית: "הצגה מעל אפליקציות אחרות".</div>' : ''}
  </div>`;

  h += `<div class="card"><h3>מצב הגנה</h3>
    <div style="color:${d.device_owner ? 'var(--green)' : 'var(--amber)'};font-weight:600">
      ${d.device_owner ? 'המכשיר מנוהל כ-Device Owner.' : 'המכשיר אינו Device Owner — חלק מההגבלות לא יחולו.'}</div>
    <div class="row"><span>חומת אש</span><span class="chip ${d.firewall_on ? 'on' : 'off'}">${d.firewall_on ? 'פעילה' : 'כבויה'}</span></div>
  </div>`;

  const info = Array.isArray(REP.info) ? REP.info : [];
  h += `<div class="card"><h3>פרטי מכשיר</h3>${info.length
    ? info.map(([k, v]) => `<div class="row"><span>${esc(k)}</span><span class="muted">${esc(v)}</span></div>`).join('')
    : `<div class="row"><span>דגם</span><span class="muted">${esc(d.model || '—')}</span></div>
       <div class="row"><span>גרסת Android</span><span class="muted">${esc(d.android || '—')}</span></div>`}</div>`;
  return h;
}

// ---------- אפליקציות ----------
function appList() {
  const byPkg = {};
  CAT.forEach(a => { byPkg[a.p] = { p: a.p, l: a.l || a.p, s: !!a.s, i: a.i || null }; });
  Object.keys(POL.apps).forEach(p => { if (!byPkg[p]) byPkg[p] = { p, l: p, s: false, i: null }; });
  return Object.values(byPkg).sort((a, b) => a.l.localeCompare(b.l, 'he'));
}
function appState(p) {
  return Object.assign({ net: !!POL.firewall.defaultAllow, browser: false, home: false, hidden: false, suspended: false, noUninstall: false, pending: false }, POL.apps[p] || {});
}
function isManaged(st) { return st.hidden || st.suspended || st.noUninstall || !st.net || st.browser; }

function tabApps() {
  const all = appList();
  const q = APPQ.trim().toLowerCase();
  const list = all.filter(a => {
    const st = appState(a.p);
    if (!APPSYS && a.s && APPF !== 'HOME' && APPF !== 'PENDING') return false;
    if (q && !a.l.toLowerCase().includes(q) && !a.p.toLowerCase().includes(q)) return false;
    if (APPF === 'MANAGED') return isManaged(st);
    if (APPF === 'HOME') return st.home;
    if (APPF === 'PENDING') return st.pending;
    return true;
  });
  const chip = (k, label) => `<span class="fchip ${APPF === k ? 'sel' : ''}" onclick="APPF='${k}';renderTab()">${label}</span>`;
  let h = `<input type="text" id="appQ" placeholder="חיפוש לפי שם או חבילה" value="${esc(APPQ)}" oninput="APPQ=this.value;renderAppRows()">
    <div class="fchips">${chip('ALL', 'הכל')}${chip('MANAGED', 'מנוהלות')}${chip('HOME', 'במסך הבית')}${chip('PENDING', 'ממתינות לאישור')}
      <span class="fchip ${APPSYS ? 'sel' : ''}" onclick="APPSYS=!APPSYS;renderTab()">${APPSYS ? 'כולל מערכת ✓' : 'אפליקציות מערכת'}</span></div>`;
  if (!CUR.device_owner) h += '<div class="muted" style="color:var(--amber)">שים לב: ללא Device Owner פעיל, פעולות הסתרה/השעיה/מניעת הסרה לא יחולו.</div>';
  if (!all.length) h += '<div class="card muted">רשימת האפליקציות תופיע אחרי הסנכרון הראשון של המכשיר עם הגרסה החדשה של האפליקציה.</div>';
  if (APPF === 'HOME' && !list.length && !APPQ.trim()) h += '<div class="muted" style="color:var(--amber)">עוד לא נבחרו אפליקציות למסך הבית. כדי להוסיף: עבור ל"הכל", לחץ על אפליקציה והפעל "הצג במסך הבית (קיוסק)".</div>';
  h += `<div class="muted" id="appCount">${list.length} אפליקציות · לחץ על שורה לפעולות</div><div id="appRows">${list.map(appRow).join('')}</div>`;
  return h;
}
function renderAppRows() {
  // חיפוש בלי לאבד את הפוקוס בשדה
  const all = appList(), q = APPQ.trim().toLowerCase();
  const list = all.filter(a => {
    const st = appState(a.p);
    if (!APPSYS && a.s && APPF !== 'HOME' && APPF !== 'PENDING') return false;
    if (q && !a.l.toLowerCase().includes(q) && !a.p.toLowerCase().includes(q)) return false;
    if (APPF === 'MANAGED') return isManaged(st);
    if (APPF === 'HOME') return st.home;
    if (APPF === 'PENDING') return st.pending;
    return true;
  });
  document.getElementById('appRows').innerHTML = list.map(appRow).join('');
  document.getElementById('appCount').textContent = list.length + ' אפליקציות · לחץ על שורה לפעולות';
}
function mini(text, cls) { return `<span class="mini ${cls}">${text}</span>`; }
function appRow(a) {
  const st = appState(a.p), open = OPEN_APP === a.p, P = esc(a.p).replace(/'/g, '');
  let chips = '';
  if (st.pending) chips += mini('ממתינה לאישור', 'm-amber');
  chips += mini(st.net ? 'רשת: מותר' : 'רשת: חסום', st.net ? 'm-green' : 'm-red');
  if (st.hidden) chips += mini('מוסתרת', 'm-amber');
  if (st.suspended) chips += mini('מושעית', 'm-amber');
  if (st.noUninstall) chips += mini('חסומה להסרה', 'm-cyan');
  if (st.browser) chips += mini('דפדפן פנימי חסום', 'm-purple');
  if (st.home) chips += mini('במסך הבית', 'm-cyan');
  const icon = a.i ? `<img class="app-ic" src="data:image/png;base64,${a.i}" alt="">` : '<span class="app-ic ph"></span>';
  let h = `<div class="app-row" onclick="OPEN_APP=OPEN_APP==='${P}'?null:'${P}';renderAppRows()">
    <div class="app-top">${icon}<div><div class="app-l">${esc(a.l)}</div><div class="app-p">${esc(a.p)}</div></div></div>
    <div class="minis">${chips}</div>`;
  if (open) {
    const t = (label, key) => `<div class="row clickrow" onclick="event.stopPropagation();appTgl('${P}','${key}')"><span>${label}</span>${sw(st[key], '')}</div>`;
    h += `<div class="app-acts">${t('אפשר אינטרנט', 'net')}${t('חסום דפדפן פנימי', 'browser')}${t('הצג במסך הבית (קיוסק)', 'home')}
      ${t('הסתר אפליקציה', 'hidden')}${t('השעה אפליקציה', 'suspended')}${t('מנע הסרה', 'noUninstall')}
      ${st.browser ? learnBox(a.p, st) : ''}
      ${st.pending ? '<div class="muted" style="color:var(--amber)">אפליקציה חדשה — חסומה לרשת עד אישור. הפעלת "אפשר אינטרנט" מאשרת אותה.</div>' : ''}</div>`;
  }
  return h + '</div>';
}
function learnBox(p, st) {
  const P = esc(p).replace(/'/g, '');
  const list = st.browserAllow || [];
  const learning = (st.learnUntil || 0) > Date.now();
  const requested = st.learnRequest && st.learnRequest > (st.learnUntil || 0) - 5 * 60000 && !learning;
  let status;
  if (learning) status = `<span style="color:var(--amber)">מצב למידה פעיל במכשיר עד ${new Date(st.learnUntil).toLocaleTimeString('he-IL', { hour: '2-digit', minute: '2-digit' })}</span>`;
  else if (requested) status = '<span style="color:var(--amber)">בקשת למידה נשלחה — תתחיל במכשיר בסנכרון הבא</span>';
  else if (!list.length) status = '<span style="color:var(--amber)">עוד לא נלמדו כתובות — הדפדפן הפנימי עדיין לא חסום</span>';
  else status = `נלמדו ${list.length} כתובות. כל אתר אחר שייפתח מתוך האפליקציה ייחסם.`;
  return `<div class="learn-box" onclick="event.stopPropagation()">
    <div>${status}</div>
    <div class="muted">מצב למידה: 5 דקות שבהן משתמשים באפליקציה כרגיל, וכל כתובת שהיא צריכה נרשמת כמותרת. פועל במצב "סינון דומיינים", אנדרואיד 10 ומעלה.</div>
    <div>${list.map((d, i) => `<span class="tag"><b onclick="rmLearned('${P}',${i})">✕</b>${esc(d)}</span>`).join('')}</div>
    <button class="ghost" style="margin-top:6px" onclick="startLearn('${P}')">${list.length ? 'למד עוד (5 דקות)' : 'התחל למידה (5 דקות)'}</button>
  </div>`;
}
function startLearn(p) { const st = appState(p); st.learnRequest = Date.now(); POL.apps[p] = st; changed(); renderAppRows(); }
function rmLearned(p, i) { const st = appState(p); st.browserAllow = (st.browserAllow || []).slice(); st.browserAllow.splice(i, 1); POL.apps[p] = st; changed(); renderAppRows(); }

function appTgl(p, key) {
  const st = appState(p);
  st[key] = !st[key];
  if (key === 'net' && st.net) st.pending = false;
  POL.apps[p] = st;
  changed();
  renderAppRows();
}

// ---------- חומת אש ----------
function tabFirewall() {
  const f = POL.firewall;
  const allowed = REP.allowedCount ?? Object.values(POL.apps).filter(a => a.net).length;
  const blocked = REP.blockedCount ?? '—';
  return `<div class="card"><h3>חומת אש</h3>
      ${toggleRow('הגנת רשת', f.enabled ? '<span style="color:var(--green)">פעילה</span>' : 'כבויה', f.enabled, "tgl('firewall.enabled')")}
      <div class="muted">חוסמת אינטרנט לכל אפליקציה שלא אושרה. ללא Root (VPN מקומי).</div></div>
    <div class="card"><h3>מצב פעולה</h3>
      ${toggleRow(f.filterMode ? 'סינון דומיינים (DNS)' : 'חסימה לפי אפליקציה',
        f.filterMode ? 'כל האפליקציות עוברות דרך סינון הדומיינים' : 'אפליקציות לא מאושרות מנותקות לגמרי', f.filterMode, "tgl('firewall.filterMode')")}</div>
    <div class="card"><h3>ברירת מחדל לאפליקציות</h3>
      ${toggleRow(f.defaultAllow ? 'הכול מותר, חוץ ממה שחסמתי' : 'הכול חסום, חוץ ממה שאישרתי',
        f.defaultAllow ? 'חוסמים אפליקציה בלשונית "אפליקציות" ← כיבוי "אפשר אינטרנט". אפליקציות חדשות ממתינות לאישור.' : 'רק אפליקציות שהופעל להן "אפשר אינטרנט" גולשות.',
        f.defaultAllow, "tgl('firewall.defaultAllow')")}</div>
    <div class="card"><h3>חסימת אינטרנט לכל המכשיר</h3>
      ${toggleRow('נתק אינטרנט לכל האפליקציות (מצב חסימה)', '', f.blockWholeDevice, "tgl('firewall.blockWholeDevice')")}</div>
    <div class="card"><h3>סיכום</h3>
      <div class="row"><span>אפליקציות מורשות לאינטרנט</span><span style="color:var(--green)">${allowed}</span></div>
      <div class="row"><span>אפליקציות חסומות</span><span style="color:var(--red)">${blocked}</span></div></div>`;
}

// ---------- סינון ----------
function tabFiltering() {
  const pol = POL;
  const mode = (m, label) => `<span class="fchip ${pol.domains.mode === m ? 'sel' : ''}" onclick="setMode('${m}')">${label}</span>`;
  const ready = pol.firewall.enabled && pol.firewall.filterMode;
  let h = `<div class="card"><h3>מצב סינון</h3>
      <div class="fchips">${mode('BLOCK', 'רשימה שחורה (הכול מותר חוץ מ-)')}${mode('ALLOW', 'רשימה לבנה (הכול חסום חוץ מ-)')}</div>
      ${REP.upstreamDns ? `<div class="muted">שרת DNS במעלה הזרם: ${esc(REP.upstreamDns)}</div>` : ''}</div>`;
  h += `<div class="card"><h3>סינון גוגל מפות</h3>
      ${toggleRow('חסימת תמונות וסרטונים',
        !pol.mapsFilter.enabled ? 'כבוי' : (ready ? '<span style="color:var(--green)">פעיל — תמונות, סרטונים ו-Street View חסומים</span>' : '<span style="color:var(--amber)">מופעל, אך ממתין למצב \'סינון דומיינים\'</span>'),
        pol.mapsFilter.enabled, "tgl('mapsFilter.enabled')")}
      <div class="muted">המפה, החיפוש והניווט ממשיכים לעבוד. ללא Root. באנדרואיד מתחת ל-10 החסימה חלה על כל המכשיר.</div>
      ${pol.mapsFilter.enabled && !ready ? `<div class="muted" style="color:var(--amber)">הסינון עובד דרך חומת האש במצב 'סינון דומיינים'.</div>
        <button class="ghost" style="margin-top:6px" onclick="POL.firewall.filterMode=true;POL.firewall.enabled=true;changed();renderTab()">עבור למצב סינון דומיינים והפעל</button>` : ''}
    </div>`;
  h += domEditor('גוגל מפות — כתובות נוספות לחסימה', 'maps', pol.mapsFilter.extra);
  h += domEditor('דומיינים חסומים', 'blocked', pol.domains.blocked);
  h += domEditor('דומיינים מותרים', 'allowed', pol.domains.allowed);
  return h;
}
function domEditor(title, which, items) {
  return `<div class="card"><h3>${title}</h3>
    <div class="grid2"><input type="text" id="add_${which}" placeholder="example.com" onkeydown="if(event.key==='Enter')addDom('${which}')"><button onclick="addDom('${which}')">הוסף</button></div>
    <div>${items.map((d, i) => `<span class="tag"><b onclick="rmDom('${which}',${i})">✕</b>${esc(d)}</span>`).join('') || '<span class="muted">אין</span>'}</div></div>`;
}
function domArr(which) { return which === 'maps' ? POL.mapsFilter.extra : POL.domains[which]; }
function addDom(which) {
  const inp = document.getElementById('add_' + which);
  const v = inp.value.trim().toLowerCase().replace(/^https?:\/\//, '').replace(/\/.*$/, '').replace(/^\*?\./, '').replace(/^www\./, '');
  if (!v) return;
  const arr = domArr(which);
  if (!arr.includes(v)) arr.push(v);
  changed(); renderTab();
}
function rmDom(which, i) { domArr(which).splice(i, 1); changed(); renderTab(); }
function setMode(m) { POL.domains.mode = m; changed(); renderTab(); }

// ---------- אבחון ----------
const DIAG_LABEL = { SUPPORTED: ['פועל', 'on'], FAILED: ['נכשל', 'm-red'], NEEDS_DEVICE_OWNER: ['דורש Device Owner', 'm-amber'],
  NEEDS_ROOT: ['דורש Root', 'm-amber'], NEEDS_PERMISSION: ['דורש הרשאה', 'm-amber'], UNSUPPORTED: ['לא נתמך', 'off'], UNKNOWN: ['לא ידוע', 'off'] };
function tabDiag() {
  const diag = Array.isArray(REP.diag) ? REP.diag : [];
  let h = `<div class="card"><h3>אבחון יכולות</h3>${diag.length ? diag.map(r => {
    const [label, cls] = DIAG_LABEL[r.status] || [r.status, 'off'];
    return `<div class="row"><div><div>${esc(r.title)}</div>${r.detail ? `<div class="muted">${esc(r.detail)}</div>` : ''}</div><span class="chip ${cls}">${label}</span></div>`;
  }).join('') : '<div class="muted">האבחון יופיע אחרי הסנכרון הבא של המכשיר.</div>'}</div>`;
  h += `<div class="card"><h3>אייקון האפליקציה</h3>
    ${toggleRow('הסתר את אייקון האפליקציה במכשיר', POL.icon.hidden ? 'מוסתר' : 'מוצג', POL.icon.hidden, "tgl('icon.hidden')")}
    <div class="muted">כשהאייקון מוסתר, נכנסים לאפליקציה דרך הגדרות המכשיר ← אפליקציות ← נסיעה כשרה, או בקוד החייגן *#*#5374#*#*.</div></div>`;
  return h;
}

// ---------- הגבלות ----------
function tabRestrictions() {
  let h = CUR.device_owner ? '' : '<div class="card muted" style="color:var(--amber)">ההגבלות דורשות Device Owner פעיל במכשיר.</div>';
  RESTRICTIONS.forEach(([group, items]) => {
    h += `<div class="card"><h3>${group}</h3>${items.map(([key, title]) =>
      `<div class="row clickrow" onclick="tgl('restrictions.${key}')"><span>${title}</span>${sw(!!POL.restrictions[key], '')}</div>`).join('')}</div>`;
  });
  h += `<div class="card"><h3>קיוסק ו-Launcher</h3>
    ${toggleRow('מצב קיוסק', 'רק האפליקציות שסומנו "במסך הבית" יופיעו וייפתחו', POL.kiosk.enabled, "tgl('kiosk.enabled')")}
    <div class="muted">בחירת האפליקציות למסך הבית — בלשונית "אפליקציות" ← "הצג במסך הבית (קיוסק)".</div></div>`;
  return h;
}

// ---------- יומן ----------
function tabLog() {
  const lines = Array.isArray(REP.log) ? REP.log : [];
  return `<div class="card"><h3>יומן אירועים ושגיאות</h3>
      ${lines.length ? lines.map(l => `<div class="log">${esc(l)}</div>`).join('') : '<div class="muted">אין רשומות עדיין.</div>'}</div>
    <div class="card"><h3>אירועי שרת</h3><div id="eventsBox" class="muted">טוען…</div></div>`;
}
function renderEvents() {
  const box = document.getElementById('eventsBox');
  if (!box) return;
  box.innerHTML = EVENTS.length ? EVENTS.map(e =>
    `<div class="log">${new Date(e.created_at).toLocaleString('he-IL')} [${esc(e.level)}] ${esc(e.message)}</div>`).join('') : 'אין אירועים.';
}

// ---------- שינוי ושמירה אוטומטית ----------
function tgl(pathStr) {
  const parts = pathStr.split('.');
  let o = POL;
  for (let i = 0; i < parts.length - 1; i++) o = o[parts[i]] = o[parts[i]] || {};
  const last = parts[parts.length - 1];
  o[last] = !o[last];
  changed();
  renderTab();
}

function changed() {
  const m = document.getElementById('saveMsg');
  if (m) m.textContent = 'שומר…';
  clearTimeout(SAVE_T);
  SAVE_T = setTimeout(savePolicy, 700);
}

async function savePolicy() {
  const m = document.getElementById('saveMsg');
  try {
    const r = await api('/devices/' + CUR.id + '/policy', {
      method: 'PUT', body: JSON.stringify({ policy: POL, summary: 'עדכון מהאתר' })
    });
    CUR.policy_version = r.version;
    if (m) m.textContent = 'נשמר · יוחל במכשיר בסנכרון הבא (עד 15 דקות)';
  } catch (e) { if (m) m.textContent = 'השמירה נכשלה: ' + e.message; }
}

async function saveName() {
  const name = document.getElementById('devName').value.trim();
  try { await api('/devices/' + CUR.id + '/name', { method: 'PUT', body: JSON.stringify({ name }) }); CUR.name = name; renderDevice(); }
  catch (e) { const m = document.getElementById('saveMsg'); if (m) m.textContent = e.message; }
}

async function loadEvents(id) {
  try { EVENTS = (await api('/devices/' + id + '/events')).events || []; } catch { EVENTS = []; }
  renderEvents();
}

function logout() { TOKEN = null; localStorage.removeItem('kosherride_token'); show('view-login'); }

// אתחול
if (TOKEN) showDevices(); else show('view-login');
