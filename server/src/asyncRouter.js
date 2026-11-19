import { Router } from 'express';

/**
 * Router שמעביר שגיאות של פונקציות async למטפל השגיאות של Express.
 * בלי זה, שגיאת מסד נתונים בתוך נתיב async מפילה את כל השרת (Express 4 + Node 18).
 */
export function asyncRouter() {
  const r = Router();
  for (const m of ['get', 'post', 'put', 'delete', 'patch', 'use']) {
    const orig = r[m].bind(r);
    r[m] = (...args) => orig(...args.map(wrap));
  }
  return r;
}

function wrap(h) {
  if (typeof h !== 'function' || h.length === 4) return h; // לא פונקציה, או מטפל שגיאות
  return function (req, res, next) {
    try {
      const p = h(req, res, next);
      if (p && typeof p.catch === 'function') p.catch(next);
    } catch (e) { next(e); }
  };
}
