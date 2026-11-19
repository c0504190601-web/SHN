import pg from 'pg';
import dotenv from 'dotenv';
dotenv.config();

// Neon מוסיף לפעמים channel_binding=require — מסירים כדי למנוע תקלות חיבור בדרייבר.
const url = (process.env.DATABASE_URL || '').trim()
  .replace(/^['"]|['"]$/g, '')
  .replace(/([?&])channel_binding=[^&]*&?/, '$1').replace(/[?&]$/, '');
if (!url) console.error('DATABASE_URL לא הוגדר — האתר יעלה אבל לא יוכל לעבוד עם מסד הנתונים.');
// ספקים מנוהלים (Neon/Render) דורשים SSL; מקומי בדרך כלל לא.
const needSsl = /sslmode=require|neon\.tech|render\.com|supabase/.test(url);

export const pool = new pg.Pool({
  connectionString: url,
  ssl: needSsl ? { rejectUnauthorized: false } : false,
});

export const q = (text, params) => pool.query(text, params);
