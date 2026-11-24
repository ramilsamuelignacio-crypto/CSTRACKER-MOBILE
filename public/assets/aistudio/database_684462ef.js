// Storage layer.
//  - With DATABASE_URL set (Supabase/Postgres): data lives in the cst_collections table.
//  - Without it: data lives in database/cstracker-data.json (local/offline mode).
const fs = require('fs');
const path = require('path');
const DATA_DIR = path.join(__dirname, 'database');
const DB_FILE = path.join(DATA_DIR, 'cstracker-data.json');
const DATABASE_URL = process.env.DATABASE_URL || '';
const DEFAULT = {
  users: [], subjects: [], schedules: [], announcements: [], attendance: [], excuse_letters: [],
  warnings: [], resources: [], calendar_events: [], messages: []
};

const data = {};
for (const k of Object.keys(DEFAULT)) data[k] = [];
const hidden = (name, value) => Object.defineProperty(data, name, { value, enumerable: false, writable: true });

function loadFile() {
  if (!fs.existsSync(DB_FILE)) return;
  try { Object.assign(data, JSON.parse(fs.readFileSync(DB_FILE, 'utf8'))); } catch {}
}
function saveFile() {
  if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
  const tmp = DB_FILE + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2));
  fs.renameSync(tmp, DB_FILE);
}

let pool = null;
const lastSaved = new Map();
let chain = Promise.resolve();
let queued = false;

async function flushNow() {
  for (const name of Object.keys(data)) {
    const json = JSON.stringify(data[name]);
    if (lastSaved.get(name) === json) continue;
    await pool.query(
      `INSERT INTO cst_collections (name, data, updated_at) VALUES ($1, $2::jsonb, now())
       ON CONFLICT (name) DO UPDATE SET data = EXCLUDED.data, updated_at = now()`,
      [name, json]
    );
    lastSaved.set(name, json);
  }
}

async function connect() {
  const { Pool } = require('pg');
  const dns = require('dns').promises;
  const net = require('net');
  let u;
  try { u = new URL(DATABASE_URL); } catch { throw new Error('DATABASE_URL is not a valid postgresql:// link.'); }
  const host = u.hostname;
  let ip = host;
  if (!net.isIP(host)) {
    try { ip = (await dns.resolve4(host))[0]; }
    catch (e) { throw new Error('Host "' + host + '" has no IPv4 address (' + e.code + '). Render free cannot use IPv6. Use the Session pooler string (host ends in pooler.supabase.com), not the Direct one.'); }
  }
  console.log('Connecting to database host ' + host + ' (IPv4 ' + ip + '), user ' + decodeURIComponent(u.username));
  pool = new Pool({
    host: ip, port: Number(u.port || 5432),
    user: decodeURIComponent(u.username), password: decodeURIComponent(u.password),
    database: decodeURIComponent(u.pathname.replace(/^\//, '') || 'postgres'),
    ssl: { rejectUnauthorized: false, servername: host }, max: 3
  });
  await pool.query(`CREATE TABLE IF NOT EXISTS cst_collections (
    name text PRIMARY KEY,
    data jsonb NOT NULL DEFAULT '[]'::jsonb,
    updated_at timestamptz NOT NULL DEFAULT now())`);
  await pool.query('ALTER TABLE cst_collections ENABLE ROW LEVEL SECURITY');
  const { rows } = await pool.query('SELECT name, data FROM cst_collections');
  if (rows.length) {
    for (const r of rows) { data[r.name] = r.data; lastSaved.set(r.name, JSON.stringify(r.data)); }
  } else if (fs.existsSync(DB_FILE)) {
    loadFile();
    console.log('Supabase table is empty: importing local database file.');
    await flushNow();
  }
  for (const k of Object.keys(DEFAULT)) if (data[k] === undefined) data[k] = [];
  process.on('SIGTERM', async () => {
    try { await chain; await flushNow(); } catch {}
    process.exit(0);
  });
  console.log('Connected to Supabase.');
}

function save() {
  if (!pool) return saveFile();
  if (queued) return;
  queued = true;
  chain = chain.then(async () => {
    queued = false;
    try { await flushNow(); } catch (e) { console.error('Supabase save failed:', e.message); }
  });
}

if (DATABASE_URL) hidden('ready', connect());
else { loadFile(); hidden('ready', Promise.resolve()); }
hidden('save', save);
hidden('flush', () => (pool ? chain.then(flushNow) : Promise.resolve()));
module.exports = data;
