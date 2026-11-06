import { Router } from 'express';
import bcrypt from 'bcryptjs';
import rateLimit from 'express-rate-limit';
import { q } from '../db.js';
import { signAdmin, requireAdmin } from '../auth.js';

export const adminRouter = Router();

const loginLimiter = rateLimit({ windowMs: 15 * 60 * 1000, max: 20 });

/** כניסת מנהל. */
adminRouter.post('/auth/login', loginLimiter, async (req, res) => {
  const { username, password } = req.body || {};
  if (!username || !password) return res.status(400).json({ error: 'חסרים פרטים' });
  const r = await q('SELECT * FROM admins WHERE username=$1', [username]);
  if (r.rowCount === 0) return res.status(401).json({ error: 'שם משתמש או סיסמה שגויים' });
  const ok = await bcrypt.compare(password, r.rows[0].password);
  if (!ok) return res.status(401).json({ error: 'שם משתמש או סיסמה שגויים' });
  res.json({ token: signAdmin(r.rows[0]), username: r.rows[0].username });
});

/** קישור מכשיר לחשבון לפי קוד חד-פעמי. */
adminRouter.post('/devices/link', requireAdmin, async (req, res) => {
  const { code, name } = req.body || {};
  const r = await q('SELECT id FROM devices WHERE link_code=$1', [code]);
  if (r.rowCount === 0) return res.status(404).json({ error: 'קוד קישור לא נמצא' });
  await q('UPDATE devices SET admin_id=$1, name=$2, link_code=NULL WHERE id=$3',
    [req.admin.id, name || null, r.rows[0].id]);
  res.json({ ok: true, deviceId: r.rows[0].id });
});

/** רשימת מכשירי המנהל. */
adminRouter.get('/devices', requireAdmin, async (req, res) => {
  const r = await q(
    `SELECT id, name, model, android, device_owner, firewall_on, policy_version,
            applied_version, last_seen,
            (now() - last_seen < interval '2 minutes') AS online
     FROM devices WHERE admin_id=$1 ORDER BY id`,
    [req.admin.id]
  );
  res.json({ devices: r.rows });
});

async function ownDevice(adminId, id) {
  const r = await q('SELECT * FROM devices WHERE id=$1 AND admin_id=$2', [id, adminId]);
  return r.rowCount ? r.rows[0] : null;
}

/** פרטי מכשיר ומדיניות. */
adminRouter.get('/devices/:id', requireAdmin, async (req, res) => {
  const d = await ownDevice(req.admin.id, req.params.id);
  if (!d) return res.status(404).json({ error: 'מכשיר לא נמצא' });
  res.json({ device: d });
});

/** עדכון מדיניות מהאתר. */
adminRouter.put('/devices/:id/policy', requireAdmin, async (req, res) => {
  const d = await ownDevice(req.admin.id, req.params.id);
  if (!d) return res.status(404).json({ error: 'מכשיר לא נמצא' });
  const policy = req.body?.policy ?? {};
  const summary = (req.body?.summary || 'עדכון מדיניות מהאתר').slice(0, 200);
  const r = await q(
    'UPDATE devices SET policy=$2, policy_version=policy_version+1 WHERE id=$1 RETURNING policy_version',
    [d.id, policy]
  );
  const v = r.rows[0].policy_version;
  await q(`INSERT INTO change_log (device_id, actor, summary, version) VALUES ($1,$2,$3,$4)`,
    [d.id, `web:${req.admin.username}`, summary, v]);
  res.json({ version: v });
});

/** יומן אירועים. */
adminRouter.get('/devices/:id/events', requireAdmin, async (req, res) => {
  const d = await ownDevice(req.admin.id, req.params.id);
  if (!d) return res.status(404).json({ error: 'מכשיר לא נמצא' });
  const r = await q('SELECT level, message, source, created_at FROM events WHERE device_id=$1 ORDER BY id DESC LIMIT 200', [d.id]);
  res.json({ events: r.rows });
});

/** יומן שינויי מדיניות. */
adminRouter.get('/devices/:id/changes', requireAdmin, async (req, res) => {
  const d = await ownDevice(req.admin.id, req.params.id);
  if (!d) return res.status(404).json({ error: 'מכשיר לא נמצא' });
  const r = await q('SELECT actor, summary, version, created_at FROM change_log WHERE device_id=$1 ORDER BY id DESC LIMIT 200', [d.id]);
  res.json({ changes: r.rows });
});
