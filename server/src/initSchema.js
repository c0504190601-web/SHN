import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import bcrypt from 'bcryptjs';
import { pool, q } from './db.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * יוצר את הסכמה (אידמפוטנטי) ומוודא שקיים מנהל ראשוני.
 * נקרא אוטומטית בעליית השרת — כך הפריסה אינה דורשת שלב ידני.
 */
export async function ensureSchema() {
  const schema = fs.readFileSync(path.join(__dirname, '..', 'schema.sql'), 'utf8');
  await pool.query(schema);

  const username = process.env.ADMIN_USERNAME || 'admin';
  const password = process.env.ADMIN_PASSWORD;
  if (!password) {
    console.warn('ADMIN_PASSWORD לא הוגדר — לא נוצר מנהל. הגדר אותו ואתחל מחדש.');
    return;
  }
  // מספור מכשירים מ-100 (מתקן מסד שנוצר עם רצף ישן, כל עוד אין בו מכשירים)
  const cnt = await q('SELECT count(*)::int AS n FROM devices');
  if (cnt.rows[0].n === 0) {
    await q('ALTER SEQUENCE device_number_seq MINVALUE 100 RESTART WITH 100');
  }

  // מנהל יחיד: שם המשתמש והסיסמה נקבעים לפי ADMIN_USERNAME / ADMIN_PASSWORD ב-Render.
  // שינוי שלהם שם ופריסה מחדש מעדכנים את המנהל הקיים (והמכשירים נשארים משויכים אליו).
  const first = await q('SELECT id, username, password FROM admins ORDER BY id LIMIT 1');
  if (first.rowCount === 0) {
    const hash = await bcrypt.hash(password, 12);
    await q('INSERT INTO admins (username, password) VALUES ($1,$2)', [username, hash]);
    console.log(`נוצר מנהל ראשוני: ${username}`);
  } else {
    const a = first.rows[0];
    const samePass = await bcrypt.compare(password, a.password);
    if (a.username !== username || !samePass) {
      const hash = samePass ? a.password : await bcrypt.hash(password, 12);
      await q('UPDATE admins SET username=$2, password=$3 WHERE id=$1', [a.id, username, hash]);
      console.log(`פרטי המנהל עודכנו: ${username}`);
    }
  }
  // כל מכשיר שעוד לא משויך — שייך למנהל (יש מנהל אחד)
  await q('UPDATE devices SET admin_id=(SELECT id FROM admins ORDER BY id LIMIT 1) WHERE admin_id IS NULL');
}
