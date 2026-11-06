import { pool } from './db.js';
import { ensureSchema } from './initSchema.js';

// הרצה ידנית של אתחול הסכמה (השרת עושה זאת גם לבד בעלייה).
ensureSchema()
  .then(() => console.log('הסכמה מוכנה.'))
  .catch((e) => { console.error(e); process.exitCode = 1; })
  .finally(() => pool.end());
