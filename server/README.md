# K-Droid Filter — שרת ואתר ניהול

שרת Node.js + PostgreSQL ואתר ניהול בעברית. מנהל יחיד, שליטה מרחוק על המכשירים.

## דרישות
- Node.js 18 ומעלה
- PostgreSQL 13 ומעלה
- שרת Linux (מומלץ VPS, למשל Hetzner CX22). לא שירות שנרדם.

## התקנה
```bash
cd server
cp .env.example .env     # ערוך ומלא ערכים אמיתיים (DATABASE_URL, JWT_SECRET, סיסמת מנהל)
npm install
npm run init-db          # יוצר טבלאות ומנהל ראשוני
npm start                # מריץ על הפורט שב-.env (ברירת מחדל 8080)
```

## הפעלה קבועה (מומלץ)
הרץ מאחורי Nginx עם TLS (Let's Encrypt), והשתמש ב-systemd או pm2:
```bash
npm i -g pm2
pm2 start src/index.js --name kdroid
pm2 save && pm2 startup
```

### Nginx + TLS (דוגמה)
הפנה דומיין לשרת, ואז:
```nginx
server {
  server_name kdroid.example.com;
  location / { proxy_pass http://127.0.0.1:8080; proxy_set_header Host $host; proxy_set_header X-Forwarded-For $remote_addr; }
}
```
ואז `certbot --nginx -d kdroid.example.com` לקבלת תעודת TLS.

## אבטחה
- כל התקשורת חייבת לרוץ מעל HTTPS (TLS). אל תפתח את השרת ללא TLS.
- החלף את `JWT_SECRET` למחרוזת אקראית ארוכה.
- סיסמת המנהל נשמרת כגיבוב bcrypt. בחר סיסמה חזקה.
- יש הגבלת קצב על כניסה ועל רישום מכשירים.

## גיבוי ושחזור
- גיבוי יומי של מסד הנתונים:
  `pg_dump "$DATABASE_URL" > backup-$(date +%F).sql`
- שחזור:
  `psql "$DATABASE_URL" < backup-YYYY-MM-DD.sql`

## זרימת רישום מכשיר
1. האפליקציה במכשיר נרשמת אוטומטית ומקבלת מספר (מ-10001) וקוד קישור.
2. באתר, בכרטיס "קישור מכשיר חדש", הזן את קוד הקישור ושם למכשיר.
3. מכאן המכשיר מופיע ברשימה וניתן לשלוט בו.

## סנכרון
- שינוי באתר מעלה את גרסת המדיניות; המכשיר מושך ומחיל אותה בסנכרון הבא.
- שינוי במכשיר נדחף לשרת.
- במקרה של התנגשות — השינוי האחרון (לפי זמן השרת) גובר, ונרשם ביומן השינויים.

## נקודות קצה עיקריות (API)
- `POST /api/auth/login` — כניסת מנהל
- `POST /api/devices/link` — קישור מכשיר לפי קוד
- `GET /api/devices` — רשימת מכשירים
- `GET /api/devices/:id` — פרטי מכשיר ומדיניות
- `PUT /api/devices/:id/policy` — עדכון מדיניות
- `GET /api/devices/:id/events` — יומן
- `POST /api/device/enroll` — רישום מכשיר (מהאפליקציה)
- `GET /api/device/sync` — משיכת מדיניות (מהאפליקציה)
- `PUT /api/device/policy` — דחיפת שינוי מהמכשיר

הערה: חיבור אפליקציית האנדרואיד לשרת (רישום וסנכרון) נבנה בשלב 5ב.
