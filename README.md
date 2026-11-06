# K-Droid Filter

מערכת סינון וניהול מכשירים (MDM) ל-Android 8+, בעברית RTL.
אפליקציית Kotlin + Jetpack Compose, שרת Node.js ואתר ניהול.

## רכיבים
- **אפליקציה** (`app/`) — Device Owner, קוד מנהל, ניהול אפליקציות, חומת אש (VPNService, ללא Root),
  סינון DNS, ~35 הגבלות מערכת, קיוסק, סנכרון לשרת.
- **שרת ואתר** (`server/`) — Node.js + PostgreSQL, אתר ניהול בעברית, רישום וסנכרון מכשירים.

## בנייה
- אפליקציה: GitHub Actions בונה APK אוטומטית (לשונית Actions ← Artifacts). לבנייה מקומית: `gradle assembleDebug`.
- שרת: ראה `server/README.md`.

## תיעוד
- [התקנה והפעלה](docs/INSTALL.md)
- [הרשאות](docs/PERMISSIONS.md)
- [מצב תכונות (מושלם/חלקי/לא נתמך)](docs/FEATURES.md)
- [בדיקות קבלה](docs/TESTING.md)
- [בניית גרסת שחרור](docs/RELEASE.md)

## עקרון מנחה
אין "הצלחה מדומה". כל יכולת מדווחת מצב אמיתי מה-API: פועל / נכשל / נדרש Device Owner / נדרש Root / לא נתמך.
יכולות רבות תלויות יצרן וגרסת Android — יש לאמת על מכשיר אמיתי לפני הכרזה על "מוכן".
