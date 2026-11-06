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
  const existing = await q('SELECT id FROM admins WHERE username=$1', [username]);
  if (existing.rowCount === 0) {
    const hash = await bcrypt.hash(password, 12);
    await q('INSERT INTO admins (username, password) VALUES ($1,$2)', [username, hash]);
    console.log(`נוצר מנהל ראשוני: ${username}`);
  }
}
