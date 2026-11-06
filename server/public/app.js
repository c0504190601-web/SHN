'use strict';
let TOKEN = localStorage.getItem('kdroid_token') || null;
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
    TOKEN = d.token; localStorage.setItem('kdroid_token', TOKEN);
    document.getElementById('who').textContent = d.username;
    showDevices();
  } catch (e) { document.getElementById('loginErr').textContent = e.message; }
}

async function showDevices() {
  show('view-devices');
  try {
    const d = await api('/devices');
    const el = document.getElementById('deviceList');
    el.innerHTML = d.devices.length ? '' : '<div class="muted">אין מכשירים מקושרים עדיין.</div>';
    d.devices.forEach(dev => {
      const div = document.createElement('div');
      div.className = 'devitem';
      div.onclick = () => openDevice(dev.id);
      div.innerHTML = `<div><b>${dev.name || 'מכשיר'} · #${dev.id}</b>
        <div class="muted">${dev.model || ''} ${dev.android ? '· Android ' + dev.android : ''}</div></div>
        <span class="chip ${dev.online ? 'on' : 'off'}">${dev.online ? 'מחובר' : 'לא מחובר'}</span>`;
      el.appendChild(div);
    });
  } catch (e) {
    if (String(e.message).includes('אימות') || String(e.message).includes('אסימון')) logout();
  }
}

async function linkDevice() {
  const code = document.getElementById('linkCode').value.trim();
  const name = document.getElementById('linkName').value.trim();
  try {
    await api('/devices/link', { method: 'POST', body: JSON.stringify({ code, name }) });
    document.getElementById('linkCode').value = ''; document.getElementById('linkName').value = '';
    showDevices();
  } catch (e) { document.getElementById('linkErr').textContent = e.message; }
}

function defPolicy(p) {
  p = p || {};
  p.restrictions = p.restrictions || {};
  p.firewall = p.firewall || { enabled: false, blockWholeDevice: false, filterMode: false };
  p.kiosk = p.kiosk || { enabled: false };
  p.domains = p.domains || { mode: 'BLOCK', blocked: [], allowed: [] };
  return p;
}

async function openDevice(id) {
  show('view-device');
  const d = await api('/devices/' + id);
  CUR = d.device; CUR.policy = defPolicy(CUR.policy);
  renderDevice();
  loadEvents(id);
}

function sw(on, onclick) {
  return `<span class="sw ${on ? 'swon' : ''}" onclick="${onclick}"><span class="k"></span></span>`;
}

function renderDevice() {
  const d = CUR, pol = d.policy;
  let h = `<div class="card"><h3>${d.name || 'מכשיר'} · #${d.id}</h3>
    <div class="muted">${d.model || ''} ${d.android ? '· Android ' + d.android : ''}</div>
    <div class="row"><span>Device Owner</span><span class="chip ${d.device_owner ? 'on' : 'off'}">${d.device_owner ? 'פעיל' : 'לא פעיל'}</span></div>
    <div class="row"><span>חומת אש</span><span class="chip ${d.firewall_on ? 'on' : 'off'}">${d.firewall_on ? 'פעילה' : 'כבויה'}</span></div>
    <div class="row"><span>גרסת מדיניות</span><span class="muted">${d.policy_version} (הוחל: ${d.applied_version})</span></div>
    <div class="row"><span>נראה לאחרונה</span><span class="muted">${d.last_seen ? new Date(d.last_seen).toLocaleString('he-IL') : '—'}</span></div>
  </div>`;

  // חומת אש
  h += `<div class="card"><h3>חומת אש</h3>
    <div class="row"><span>מצב סינון דומיינים (אחרת: חסימה לפי אפליקציה)</span>${sw(pol.firewall.filterMode, "tgl('firewall.filterMode')")}</div>
    <div class="row"><span>חסימת אינטרנט לכל המכשיר</span>${sw(pol.firewall.blockWholeDevice, "tgl('firewall.blockWholeDevice')")}</div>
  </div>`;

  // סינון דומיינים
  h += `<div class="card"><h3>סינון דומיינים</h3>
    <div class="row"><span>מצב רשימה</span>
      <span><button class="ghost" onclick="setMode('BLOCK')" ${pol.domains.mode==='BLOCK'?'style="border-color:var(--cyan)"':''}>שחורה</button>
      <button class="ghost" onclick="setMode('ALLOW')" ${pol.domains.mode==='ALLOW'?'style="border-color:var(--cyan)"':''}>לבנה</button></span></div>
    <h4>דומיינים חסומים</h4><div class="grid2"><input type="text" id="addBlocked" placeholder="example.com"><button onclick="addDom('blocked')">הוסף</button></div>
    <div id="blockedTags">${tags('blocked')}</div>
    <h4>דומיינים מותרים</h4><div class="grid2"><input type="text" id="addAllowed" placeholder="example.com"><button onclick="addDom('allowed')">הוסף</button></div>
    <div id="allowedTags">${tags('allowed')}</div>
  </div>`;

  // קיוסק
  h += `<div class="card"><h3>קיוסק</h3>
    <div class="row"><span>מצב קיוסק</span>${sw(pol.kiosk.enabled, "tgl('kiosk.enabled')")}</div>
    <div class="muted">בחירת אפליקציות למסך הבית מתבצעת במכשיר ומסתנכרנת.</div>
  </div>`;

  // הגבלות
  h += '<div class="card"><h3>הגבלות מערכת</h3>';
  RESTRICTIONS.forEach(([group, items]) => {
    h += `<h4>${group}</h4>`;
    items.forEach(([key, title]) => {
      h += `<div class="row"><span>${title}</span>${sw(!!pol.restrictions[key], "tgl('restrictions." + key + "')")}</div>`;
    });
  });
  h += `<div class="save-bar"><button onclick="savePolicy()">שמור שינויים</button>
    <span class="muted" id="saveMsg"></span></div></div>`;

  h += '<div class="card"><h3>יומן אירועים</h3><div id="eventsBox" class="muted">טוען…</div></div>';

  document.getElementById('deviceBody').innerHTML = h;
}

function tags(which) {
  return (CUR.policy.domains[which] || []).map((d, i) =>
    `<span class="tag"><b onclick="rmDom('${which}',${i})">✕</b>${d}</span>`).join('') ||
    '<span class="muted">אין</span>';
}

function tgl(pathStr) {
  const parts = pathStr.split('.');
  let o = CUR.policy;
  for (let i = 0; i < parts.length - 1; i++) o = o[parts[i]];
  const last = parts[parts.length - 1];
  o[last] = !o[last];
  renderDevice();
}

function setMode(m) { CUR.policy.domains.mode = m; renderDevice(); }
function addDom(which) {
  const inp = document.getElementById(which === 'blocked' ? 'addBlocked' : 'addAllowed');
  const v = inp.value.trim().toLowerCase().replace(/^www\./, '');
  if (v) { CUR.policy.domains[which] = CUR.policy.domains[which] || []; CUR.policy.domains[which].push(v); renderDevice(); }
}
function rmDom(which, i) { CUR.policy.domains[which].splice(i, 1); renderDevice(); }

async function savePolicy() {
  try {
    const r = await api('/devices/' + CUR.id + '/policy', {
      method: 'PUT', body: JSON.stringify({ policy: CUR.policy, summary: 'עדכון מהאתר' })
    });
    CUR.policy_version = r.version;
    document.getElementById('saveMsg').textContent = 'נשמר (גרסה ' + r.version + '). יוחל במכשיר בסנכרון הבא.';
  } catch (e) { document.getElementById('saveMsg').textContent = e.message; }
}

async function loadEvents(id) {
  try {
    const d = await api('/devices/' + id + '/events');
    const box = document.getElementById('eventsBox');
    if (!box) return;
    box.innerHTML = d.events.length ? d.events.map(e =>
      `<div class="log">${new Date(e.created_at).toLocaleString('he-IL')} [${e.level}] ${e.message}</div>`).join('')
      : 'אין אירועים.';
  } catch {}
}

function logout() { TOKEN = null; localStorage.removeItem('kdroid_token'); show('view-login'); }

// אתחול
if (TOKEN) showDevices(); else show('view-login');
