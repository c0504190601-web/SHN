-- סכמת מסד הנתונים של K-Droid Filter

CREATE TABLE IF NOT EXISTS admins (
    id          SERIAL PRIMARY KEY,
    username    TEXT UNIQUE NOT NULL,
    password    TEXT NOT NULL,          -- גיבוב bcrypt
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- רצף מזהי מכשירים החל מ-10001
CREATE SEQUENCE IF NOT EXISTS device_number_seq START 10001;

CREATE TABLE IF NOT EXISTS devices (
    id            INTEGER PRIMARY KEY DEFAULT nextval('device_number_seq'),
    name          TEXT,
    token         TEXT UNIQUE NOT NULL,     -- אסימון מכשיר (סודי)
    link_code     TEXT,                     -- קוד קישור חד-פעמי
    admin_id      INTEGER REFERENCES admins(id),
    model         TEXT,
    android       TEXT,
    device_owner  BOOLEAN DEFAULT false,
    firewall_on   BOOLEAN DEFAULT false,
    policy        JSONB DEFAULT '{}'::jsonb,
    policy_version INTEGER DEFAULT 1,
    applied_version INTEGER DEFAULT 0,
    last_seen     TIMESTAMPTZ,
    created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS events (
    id          BIGSERIAL PRIMARY KEY,
    device_id   INTEGER REFERENCES devices(id) ON DELETE CASCADE,
    source      TEXT,            -- 'device' | 'web'
    level       TEXT,            -- INFO | WARN | ERROR
    message     TEXT,
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- יומן שינויי מדיניות (מי שינה, מה, מתי)
CREATE TABLE IF NOT EXISTS change_log (
    id          BIGSERIAL PRIMARY KEY,
    device_id   INTEGER REFERENCES devices(id) ON DELETE CASCADE,
    actor       TEXT,            -- 'web:<admin>' | 'device'
    summary     TEXT,
    version     INTEGER,
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_events_device ON events(device_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_changelog_device ON change_log(device_id, id DESC);
