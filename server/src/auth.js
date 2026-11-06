import jwt from 'jsonwebtoken';
import { q } from './db.js';

const SECRET = process.env.JWT_SECRET || 'dev-secret';

export function signAdmin(admin) {
  return jwt.sign({ id: admin.id, username: admin.username }, SECRET, { expiresIn: '30d' });
}

/** מידלוור לאימות מנהל (אתר). */
export function requireAdmin(req, res, next) {
  const h = req.headers.authorization || '';
  const token = h.startsWith('Bearer ') ? h.slice(7) : null;
  if (!token) return res.status(401).json({ error: 'נדרש אימות' });
  try {
    req.admin = jwt.verify(token, SECRET);
    next();
  } catch {
    res.status(401).json({ error: 'אסימון לא תקף' });
  }
}

/** מידלוור לאימות מכשיר לפי אסימון המכשיר. */
export async function requireDevice(req, res, next) {
  const h = req.headers.authorization || '';
  const token = h.startsWith('Bearer ') ? h.slice(7) : null;
  if (!token) return res.status(401).json({ error: 'נדרש אסימון מכשיר' });
  const r = await q('SELECT * FROM devices WHERE token=$1', [token]);
  if (r.rowCount === 0) return res.status(401).json({ error: 'מכשיר לא מוכר' });
  req.device = r.rows[0];
  next();
}
