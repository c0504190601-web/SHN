import pg from 'pg';
import dotenv from 'dotenv';
dotenv.config();

const url = process.env.DATABASE_URL || '';
// ספקים מנוהלים (Neon/Render) דורשים SSL; מקומי בדרך כלל לא.
const needSsl = /sslmode=require|neon\.tech|render\.com|supabase/.test(url);

export const pool = new pg.Pool({
  connectionString: url,
  ssl: needSsl ? { rejectUnauthorized: false } : false,
});

export const q = (text, params) => pool.query(text, params);
