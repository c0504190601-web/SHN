# בניית גרסת שחרור חתומה — K-Droid Filter

ה-CI בונה כברירת מחדל APK מסוג **debug** (חתום במפתח debug, לבדיקות).
לשחרור אמיתי נדרש APK חתום במפתח שלך.

## 1. יצירת מפתח חתימה (פעם אחת)
```
keytool -genkeypair -v -keystore kdroid-release.jks \
  -alias kdroid -keyalg RSA -keysize 2048 -validity 10000
```
שמור את הקובץ ואת הסיסמאות במקום בטוח. **אובדן המפתח = אי אפשר לעדכן את האפליקציה.**

## 2. בנייה מקומית
```
export KEYSTORE_PATH=/path/to/kdroid-release.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=kdroid
export KEY_PASSWORD=...
gradle assembleRelease
```
הפלט: `app/build/outputs/apk/release/app-release.apk`

## 3. בנייה ב-GitHub Actions (אופציונלי)
הוסף Secrets למאגר: `KEYSTORE_BASE64` (הקובץ ב-base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
הוסף ל-workflow שלב שמפענח את ה-keystore ומגדיר `KEYSTORE_PATH` לפני `gradle assembleRelease`.
כל עוד אין keystore, build.gradle בונה release לא חתום — לכן ה-CI הנוכחי בונה debug.

## 4. מעקב גרסה ו-commit
לכל APK תעד:
- `versionName` / `versionCode` (מ-`app/build.gradle`)
- ה-commit המדויק שממנו נבנה (`git rev-parse HEAD`)

כך לכל קובץ שמסופק יש התאמה מדויקת לקוד.

## הקשחת אבטחה שכבר קיימת
- `allowBackup=false`
- `usesCleartextTraffic=false` — סנכרון מעל HTTPS בלבד
- קוד מנהל: PBKDF2 + Salt, הגבלת ניסיונות
- אסימוני שרת מאובטחים, TLS, הגבלת קצב
