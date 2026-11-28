-- =====================================================================
-- wORGit EMPTY schema  (PostgreSQL 13+ / Supabase)   file 1 of 2
-- Creates a dedicated schema "worgit" with all 20 tables and ZERO rows.
-- Same tables, constraints and triggers as worgit_schema.sql (tested on PostgreSQL 16).
-- Then run worgit_seed_required.sql once (5 interests, Lobby, 5 interest rooms).
--
-- Spring Boot: add  ?currentSchema=worgit  to the JDBC URL.
-- Start over at any time (deletes everything in the schema):
--     DROP SCHEMA worgit CASCADE;
-- Tags: [C] class diagram/wireframes, [D] your decisions, [A] assumed default.
-- Removed on purpose: icebreakers, typing state, registration, salt column.
-- =====================================================================
BEGIN;

-- Stop with a clear message if this file was already run (it is not meant to run twice).
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'worgit') THEN
    RAISE EXCEPTION 'Schema worgit already has tables. To start over, run  DROP SCHEMA worgit CASCADE;  and then run this file again. To keep what is there, skip this file.';
  END IF;
END $$;

CREATE SCHEMA IF NOT EXISTS worgit;
SET LOCAL search_path TO worgit;

-- ---------- fixed lists ------------------------------------------------
-- [C] the UI shows exactly these five interests; organizations.interest_name uses the same five [D].
CREATE TABLE interest_options (
  name text PRIMARY KEY
);
-- (no rows here: the five interests are in worgit_seed_required.sql)

-- ---------- users (abstract User -> Student | Admin) --------------------
-- Everyone logs in with user_id: student 'YY-XXXXX' (e.g. 23-10457), admin 'ADM-###'.  [C]
-- password_hash holds a full BCrypt string (salt included); the diagram's separate
-- `salt` column is therefore not needed.  [A]
CREATE TABLE users (
  user_id       text PRIMARY KEY,
  user_type     text NOT NULL CHECK (user_type IN ('STUDENT', 'ADMIN')),
  full_name     text NOT NULL,
  display_name  text NOT NULL CHECK (char_length(display_name) BETWEEN 1 AND 30),
  email         text NOT NULL,
  password_hash text NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_user_id_format CHECK (
        (user_type = 'STUDENT' AND user_id ~ '^[0-9]{2}-[0-9]{5}$')
     OR (user_type = 'ADMIN'   AND user_id ~ '^ADM-[0-9]+$')),
  CONSTRAINT chk_student_email CHECK (
        user_type <> 'STUDENT'
     OR email ~* '@(g\.)?batstate-u\.edu\.ph$')
);
CREATE UNIQUE INDEX users_email_lower_uq ON users (lower(email));

CREATE TABLE students (
  user_id             text PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
  bio                 text NOT NULL DEFAULT '' CHECK (char_length(bio) <= 150),
  avatar_id           text,
  visible_in_matching boolean NOT NULL DEFAULT true      -- toggle shown ON in wireframe [A default]
);

-- [D] roles: facilitator, adviser, system administrator.
CREATE TABLE admins (
  user_id    text PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
  admin_role text NOT NULL CHECK (admin_role IN
    ('ORGANIZATION_FACILITATOR', 'FACULTY_ADVISER', 'SYSTEM_ADMINISTRATOR'))
);

CREATE TABLE student_interests (
  user_id  text NOT NULL REFERENCES students(user_id) ON DELETE CASCADE,
  interest text NOT NULL REFERENCES interest_options(name),
  PRIMARY KEY (user_id, interest)
);

-- ---------- organizations ----------------------------------------------
-- [D] System Admin adds/edits; org profile has logo, cover photo, description,
-- interests, contact details and social links. Exact contact fields are [A].
CREATE TABLE organizations (
  org_id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name              text NOT NULL UNIQUE,                 -- "Name is required." [C]; uniqueness [A]
  interest_name     text NOT NULL REFERENCES interest_options(name),   -- the PDF's "Category" [D]
  description       text NOT NULL DEFAULT '',
  logo_url          text,                                 -- [D]
  cover_photo_url   text,                                 -- [D]
  contact_email     text,                                 -- [D/A]
  contact_phone     text,                                 -- [D/A]
  requires_approval boolean NOT NULL DEFAULT false,       -- [C]
  membership_fee    numeric(10,2) NOT NULL DEFAULT 0 CHECK (membership_fee >= 0),  -- PHP [C]
  created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE org_interests (                               -- [D] "org interests alike"
  org_id   uuid NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
  interest text NOT NULL REFERENCES interest_options(name),
  PRIMARY KEY (org_id, interest)
);

CREATE TABLE org_social_links (                            -- [D] platform is free text [A]
  link_id  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id   uuid NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
  platform text NOT NULL,
  url      text NOT NULL CHECK (url ~* '^https?://')
);

-- Organization.memberIds + memberJoinDates  [C]
CREATE TABLE org_members (
  org_id     uuid NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
  student_id text NOT NULL REFERENCES students(user_id) ON DELETE CASCADE,
  joined_on  date NOT NULL DEFAULT current_date,
  PRIMARY KEY (org_id, student_id)
);

-- Admin.managedOrgIds  [C]. Facilitators/advisers see only these orgs;
-- System Administrators have access to all organizations regardless of rows here.
CREATE TABLE admin_managed_orgs (
  admin_id text NOT NULL REFERENCES admins(user_id) ON DELETE CASCADE,
  org_id   uuid NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
  PRIMARY KEY (admin_id, org_id)
);

CREATE TABLE membership_applications (
  application_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  student_id     text NOT NULL REFERENCES students(user_id) ON DELETE CASCADE,
  org_id         uuid NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
  status         text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  date_applied   date NOT NULL DEFAULT current_date
);
-- one open application per student+org; re-applying after a rejection is allowed [A]
CREATE UNIQUE INDEX one_pending_application_uq
  ON membership_applications (student_id, org_id) WHERE status = 'PENDING';

-- Records only; no online payment [C]
CREATE TABLE payment_records (
  payment_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  student_id text NOT NULL REFERENCES students(user_id) ON DELETE CASCADE,
  org_id     uuid NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
  amount     numeric(10,2) NOT NULL CHECK (amount >= 0),
  date_paid  date,                                          -- NULL while unpaid ("-" in UI) [C]
  status     text NOT NULL DEFAULT 'UNPAID' CHECK (status IN ('UNPAID', 'PAID'))
);

-- ---------- peers -------------------------------------------------------
CREATE TABLE peer_connections (
  student_a_id text NOT NULL REFERENCES students(user_id) ON DELETE CASCADE,
  student_b_id text NOT NULL REFERENCES students(user_id) ON DELETE CASCADE,
  connected_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (student_a_id, student_b_id),
  CHECK (student_a_id <> student_b_id)
);
-- A-B and B-A are the same connection ("already connected" error) [C]
CREATE UNIQUE INDEX peer_pair_uq
  ON peer_connections (LEAST(student_a_id, student_b_id), GREATEST(student_a_id, student_b_id));

-- ---------- chat --------------------------------------------------------
CREATE TABLE chat_rooms (
  room_id      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name         text NOT NULL,
  room_type    text NOT NULL CHECK (room_type IN ('LOBBY', 'INTEREST', 'ORGANIZATION', 'PEER')),
  interest     text REFERENCES interest_options(name),
  org_id       uuid REFERENCES organizations(org_id) ON DELETE CASCADE,
  student_a_id text REFERENCES students(user_id) ON DELETE CASCADE,
  student_b_id text REFERENCES students(user_id) ON DELETE CASCADE,
  CHECK (
       (room_type = 'LOBBY'        AND interest IS NULL AND org_id IS NULL AND student_a_id IS NULL AND student_b_id IS NULL)
    OR (room_type = 'INTEREST'     AND interest IS NOT NULL AND org_id IS NULL AND student_a_id IS NULL AND student_b_id IS NULL)
    OR (room_type = 'ORGANIZATION' AND org_id IS NOT NULL AND interest IS NULL AND student_a_id IS NULL AND student_b_id IS NULL)
    OR (room_type = 'PEER'         AND student_a_id IS NOT NULL AND student_b_id IS NOT NULL AND interest IS NULL AND org_id IS NULL)
  )
);
CREATE UNIQUE INDEX one_lobby_uq     ON chat_rooms (room_type) WHERE room_type = 'LOBBY';
CREATE UNIQUE INDEX one_interest_room_uq ON chat_rooms (interest) WHERE room_type = 'INTEREST';
CREATE UNIQUE INDEX one_org_room_uq  ON chat_rooms (org_id) WHERE room_type = 'ORGANIZATION';
CREATE UNIQUE INDEX one_peer_room_uq ON chat_rooms (LEAST(student_a_id, student_b_id), GREATEST(student_a_id, student_b_id))
  WHERE room_type = 'PEER';

CREATE TABLE chat_messages (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  room_id     uuid NOT NULL REFERENCES chat_rooms(room_id) ON DELETE CASCADE,
  sender_id   text NOT NULL REFERENCES users(user_id),
  sender_name text NOT NULL,
  message     text NOT NULL CHECK (char_length(message) BETWEEN 1 AND 300),   -- MAX_MESSAGE_LENGTH [C]
  sent_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX chat_messages_room_time_idx ON chat_messages (room_id, sent_at);

-- ---------- events and certificates ------------------------------------
CREATE TABLE events (
  event_id    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  title       text NOT NULL,
  event_date  date NOT NULL,
  org_id      uuid NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
  description text NOT NULL DEFAULT ''
);

CREATE TABLE event_participations (
  event_id   uuid NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
  student_id text NOT NULL REFERENCES students(user_id) ON DELETE CASCADE,
  role       text NOT NULL DEFAULT 'ATTENDEE' CHECK (role IN ('ORGANIZER', 'ATTENDEE')),
  attended   boolean NOT NULL DEFAULT false,
  PRIMARY KEY (event_id, student_id)
);

CREATE TABLE achievements (
  achievement_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  student_id     text NOT NULL REFERENCES students(user_id) ON DELETE CASCADE,
  title          text NOT NULL,         -- 'First Event', '5 Events Attended', '10 Events Attended' [C]
  date_earned    date NOT NULL DEFAULT current_date,
  UNIQUE (student_id, title)
);

CREATE SEQUENCE certificate_seq START 1;
CREATE TABLE certificates (
  certificate_id   text PRIMARY KEY DEFAULT ('CERT-' || lpad(nextval('certificate_seq')::text, 4, '0')),  -- CERT-0001 [C]
  student_id       text NOT NULL REFERENCES students(user_id) ON DELETE CASCADE,
  event_id         uuid NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
  event_title      text NOT NULL,       -- stored on the certificate [C]; org name is looked up
  date_issued      date NOT NULL DEFAULT current_date,
  certificate_type text NOT NULL CHECK (certificate_type IN ('PARTICIPATION', 'ORGANIZER')),
  UNIQUE (student_id, event_id)         -- one certificate per student per event [A]
);
-- NOTE: "certificate only if attended" is enforced by the backend (CertificateNotEligibleException).

-- ---------- notifications ----------------------------------------------
-- [D] Students, facilitators and advisers have a Notifications tab; System Administrator does not.
CREATE TABLE notifications (
  notification_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  recipient_id    text NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  type            text NOT NULL CHECK (type IN ('PEER_CONNECTION', 'APPLICATION', 'ACHIEVEMENT')),  -- [A]
  message         text NOT NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  is_read         boolean NOT NULL DEFAULT false     -- diagram field is `read`
);
CREATE INDEX notifications_recipient_idx ON notifications (recipient_id, is_read, created_at DESC);

-- ---------- automatic rooms (triggers) ------------------------------------

-- Organization room is created automatically for every new organization, so rows you add by
-- hand get a room too. Peer room is created automatically for every new connection. [A]
CREATE FUNCTION create_org_room() RETURNS trigger LANGUAGE plpgsql SET search_path = worgit AS $$
BEGIN
  INSERT INTO chat_rooms (name, room_type, org_id) VALUES (NEW.name, 'ORGANIZATION', NEW.org_id);
  RETURN NEW;
END $$;
CREATE TRIGGER trg_org_room AFTER INSERT ON organizations
  FOR EACH ROW EXECUTE FUNCTION create_org_room();

CREATE FUNCTION create_peer_room() RETURNS trigger LANGUAGE plpgsql SET search_path = worgit AS $$
BEGIN
  INSERT INTO chat_rooms (name, room_type, student_a_id, student_b_id)
  VALUES ('Direct', 'PEER', NEW.student_a_id, NEW.student_b_id)
  ON CONFLICT DO NOTHING;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_peer_room AFTER INSERT ON peer_connections
  FOR EACH ROW EXECUTE FUNCTION create_peer_room();

-- ---------- security (Supabase) ------------------------------------------
-- Keep the worgit schema OUT of Supabase's Data API (Settings > API > Exposed schemas), turn RLS
-- on for everything and add NO policies: only your Spring Boot backend (connecting with the database user, which bypasses
-- RLS) can read or write. Never ship the anon/service key inside the Expo app.
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'worgit' LOOP
    EXECUTE format('ALTER TABLE worgit.%I ENABLE ROW LEVEL SECURITY', r.tablename);
  END LOOP;
END $$;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN   -- only exists on Supabase
    EXECUTE 'REVOKE ALL ON SCHEMA worgit FROM anon, authenticated';
    EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA worgit FROM anon, authenticated';
    EXECUTE 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA worgit FROM anon, authenticated';
    EXECUTE 'REVOKE ALL ON ALL FUNCTIONS IN SCHEMA worgit FROM anon, authenticated';
  END IF;
END $$;


COMMIT;

-- =====================================================================
-- HOW TO ADD ACCOUNTS (all users just log in; the app routes by role)
-- 0) Run  SET search_path TO worgit;  first (or write worgit.users, worgit.students ...).
-- 1) Make a BCrypt hash. On Supabase (pgcrypto lives in the "extensions" schema):
--      SELECT extensions.crypt('TempPass123', extensions.gen_salt('bf', 10));
--    Spring's BCryptPasswordEncoder verifies these ($2a$) hashes. Test one login first.
-- 2) Insert the user, then the role row. Examples (commented out):
--
-- INSERT INTO users (user_id, user_type, full_name, display_name, email, password_hash)
-- VALUES ('ADM-001', 'ADMIN', 'Prof. Reyes', 'Prof. Reyes', 'reyes@batstate-u.edu.ph', '<bcrypt hash>');
-- INSERT INTO admins (user_id, admin_role) VALUES ('ADM-001', 'FACULTY_ADVISER');
--   admin_role: ORGANIZATION_FACILITATOR | FACULTY_ADVISER | SYSTEM_ADMINISTRATOR
-- -- facilitators/advisers: link them to the organizations they handle
-- INSERT INTO admin_managed_orgs (admin_id, org_id)
--   SELECT 'ADM-001', org_id FROM organizations WHERE name = 'Web Developers Guild';
--
-- INSERT INTO users (user_id, user_type, full_name, display_name, email, password_hash)
-- VALUES ('23-10457', 'STUDENT', 'Alex Rivera', 'Alex R.', 'alex.rivera@g.batstate-u.edu.ph', '<bcrypt hash>');
-- INSERT INTO students (user_id) VALUES ('23-10457');
-- INSERT INTO student_interests (user_id, interest) VALUES ('23-10457', 'Technology');
-- =====================================================================
