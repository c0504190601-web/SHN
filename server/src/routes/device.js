import { Router } from 'express';
import crypto from 'crypto';
import rateLimit from 'express-rate-limit';
import { q } from '../db.js';
import { requireDevice } from '../auth.js';

export const deviceRouter = Router();

const enrollLimiter = rateLimit({ windowMs: 60 * 1000, max: 10 });

function rnd(n) { return crypto.randomBytes(n).toString('hex'); }
function code6() { return String(Math.floor(100000 + Math.random() * 900000)); }

/** רישום מכשיר חדש — מקבל מזהה (מ-10001), אסימון וקוד קישור חד-פעמי. */
deviceRouter.post('/enroll', enrollLimiter, async (req, res) => {
  const token = rnd(24);
  const link = code6();
  const { model = null, android = null } = req.body || {};
  const r = await q(
    `INSERT INTO devices (token, link_code, model, android, last_seen)
     VALUES ($1,$2,$3,$4, now()) RETURNING id`,
    [token, link, model, android]
  );
  const id = r.rows[0].id;
  await q(`INSERT INTO events (device_id, source, level, message) VALUES ($1,'device','INFO','המכשיר נרשם')`, [id]);
  res.json({ deviceId: id, token, linkCode: link });
});

/** דיווח מצב מכשיר. */
deviceRouter.post('/status', requireDevice, async (req, res) => {
  const { model, android, deviceOwner, firewallOn } = req.body || {};
  await q(
    `UPDATE devices SET model=COALESCE($2,model), android=COALESCE($3,android),
       device_owner=COALESCE($4,device_owner), firewall_on=COALESCE($5,firewall_on), last_seen=now()
     WHERE id=$1`,
    [req.device.id, model ?? null, android ?? null, deviceOwner ?? null, firewallOn ?? null]
  );
  res.json({ ok: true });
});

/** סנכרון: המכשיר מושך מדיניות ומדווח איזו גרסה הוחלה. */
deviceRouter.get('/sync', requireDevice, async (req, res) => {
  const applied = parseInt(req.query.applied, 10);
  if (!Number.isNaN(applied)) {
    await q('UPDATE devices SET applied_version=$2 WHERE id=$1', [req.device.id, applied]);
  }
  await q('UPDATE devices SET last_seen=now() WHERE id=$1', [req.device.id]);
  const r = await q('SELECT policy, policy_version FROM devices WHERE id=$1', [req.device.id]);
  res.json({ version: r.rows[0].policy_version, policy: r.rows[0].policy });
});

/** המכשיר דוחף שינוי מקומי במדיניות. */
deviceRouter.put('/policy', requireDevice, async (req, res) => {
  const policy = req.body?.policy ?? {};
  const r = await q(
    `UPDATE devices SET policy=$2, policy_version=policy_version+1,
       applied_version=policy_version+1, last_seen=now()
     WHERE id=$1 RETURNING policy_version`,
    [req.device.id, policy]
  );
  const v = r.rows[0].policy_version;
  await q(`INSERT INTO change_log (device_id, actor, summary, version) VALUES ($1,'device','שינוי מקומי מהמכשיר',$2)`, [req.device.id, v]);
  res.json({ version: v });
});

/** המכשיר שולח אירועי יומן. */
deviceRouter.post('/events', requireDevice, async (req, res) => {
  const events = Array.isArray(req.body?.events) ? req.body.events : [];
  for (const e of events.slice(0, 200)) {
    await q(
      `INSERT INTO events (device_id, source, level, message) VALUES ($1,'device',$2,$3)`,
      [req.device.id, (e.level || 'INFO').slice(0, 8), String(e.message || '').slice(0, 500)]
    );
  }
  res.json({ ok: true, stored: events.length });
});
