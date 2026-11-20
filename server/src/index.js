import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';
import { adminRouter } from './routes/admin.js';
import { deviceRouter } from './routes/device.js';
import { ensureSchema } from './initSchema.js';
import { pool } from './db.js';

dotenv.config();
const __dirname = path.dirname(fileURLToPath(import.meta.url));

const app = express();
app.set('trust proxy', 1);
app.use(helmet({ contentSecurityPolicy: false }));
app.use(cors({ origin: process.env.CORS_ORIGIN || '*' }));
app.use(express.json({ limit: '8mb' })); // רשימת אפליקציות עם אייקונים

// בדיקת תקינות: גם השרת וגם החיבור למסד הנתונים
app.get('/api/health', async (_req, res) => {
  let db = false;
  try { await pool.query('SELECT 1'); db = true; } catch {}
  res.json({ ok: true, db, adminConfigured: !!process.env.ADMIN_PASSWORD });
});

app.use('/api', adminRouter);
app.use('/api/device', deviceRouter);

// אתר הניהול (סטטי)
app.use('/', express.static(path.join(__dirname, '..', 'public')));

app.use((err, _req, res, _next) => {
  console.error(err);
  res.status(500).json({ error: 'שגיאת שרת' });
});

// לא מפילים את השרת על שגיאה שלא נתפסה — רק רושמים ביומן
process.on('unhandledRejection', (e) => console.error('unhandledRejection:', e));

const port = process.env.PORT || 8080;

// יצירת סכמה ומנהל ראשוני בעלייה (אידמפוטנטי), ואז הרצת השרת.
ensureSchema()
  .catch((e) => console.error('אתחול סכמה נכשל:', e.message))
  .finally(() => app.listen(port, () => console.log(`נסיעה כשרה server על פורט ${port}`)));
