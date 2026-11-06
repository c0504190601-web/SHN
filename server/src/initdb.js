import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import bcrypt from 'bcryptjs';
import dotenv from 'dotenv';
import { pool, q } from './db.js';

dotenv.config();
const __dirname = path.dirname(fileURLToPath(import.meta.url));

async function main() {
  const schema = fs.readFileSync(path.join(__dirname, '..', 'schema.sql'), 'utf8');
  await pool.query(schema);
  console.log('הסכמה הוחלה.');

  const username = process.env.ADMIN_USERNAME || 'admin';
  const password = process.env.ADMIN_PASSWORD || 'admin';
  const existing = await q('SELECT id FROM admins WHERE username=$1', [username]);
  if (existing.rowCount === 0) {
    const hash = await bcrypt.hash(password, 12);
    await q('INSERT INTO admins (username, password) VALUES ($1,$2)', [username, hash]);
    console.log(`נוצר מנהל ראשוני: ${username}`);
  } else {
    console.log('מנהל כבר קיים — לא שונה.');
  }
  await pool.end();
}

main().catch((e) => { console.error(e); process.exit(1); });
