# OSRS Journal Plugin — Setup Guide

## What this does
The plugin runs inside RuneLite and syncs your character data to Supabase in real time:
- **Skills** — synced 3 seconds after your last XP gain (debounced)
- **Quests** — checked every ~1 minute for state changes
- **Equipment** — synced every time your worn gear changes
- **Bank** — synced every time you open the bank

**In-client UI:** a sidebar panel (book icon on the left) shows a live summary while logged in.
**Open full journal** opens [journal.osrsjournal.com](https://journal.osrsjournal.com) with your
character loaded (hosted mode, default). In self-hosted mode it instead serves the bundled
`osrs-journal.html` from a local HTTP server.

---

## Prerequisites
- Java 11+ JDK (e.g. [Adoptium Temurin 11](https://adoptium.net/))
- IntelliJ IDEA (recommended) or any IDE with Gradle support

---

## Building

```bash
cd osrs-journal-plugin
./gradlew jar                 # slim build (hosted mode only, Plugin Hub)
./gradlew jar -PbundleWebapp  # includes local webapp for self-hosted mode
```

The default jar is slim (~90 KB) — hosted users never use the local web server.
`-PbundleWebapp` bundles `osrs-journal.html`, quest JSON, and the small web assets
for self-hosted **Open full journal** support.

The built jar will be at `build/libs/osrs-journal-plugin-1.0.0.jar`.

---

## Loading into RuneLite

### Official Jagex Launcher — sideloading does NOT work

RuneLite **intentionally disables** sideloaded plugins when started from the official launcher
(`launcher version 2.7.5` in logs), even if `settings.json` includes `--developer-mode`.

Your log will show the flag, but **no** `Side-loading plugin ...osrs-journal-plugin` line — that is expected.

**Personal use options:**

| Method | Plugin loads? |
|--------|----------------|
| **`run-dev.exe`** (self-built client) | Yes |
| **Jagex Launcher + sideload jar** | No |
| **Plugin Hub** (after PR merge) | Yes, in normal RuneLite |

For now, use **`run-dev.exe`** to play with the OSRS Journal plugin.

### Jagex Account login (required for dev client)

If your account uses a **Jagex Account**, the dev client cannot accept username/password.
You must export your session once from the official launcher:

1. Run `export-credentials.bat` from the project root.
2. Launch OSRS through the **Jagex Launcher** and log in normally.
3. RuneLite writes `%USERPROFILE%\.runelite\credentials.properties`.
4. Run `run-dev.exe` — it auto-logs in using those credentials.

See the [RuneLite Jagex Accounts wiki](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

### Quick start (already set up)

```bat
export-credentials.bat          rem one-time, if you have a Jagex Account
run-dev.exe                   rem launches dev RuneLite (builds only if jar missing)
run-dev.exe --rebuild         rem force full client rebuild, then launch
osrs-journal-plugin\build.bat rem rebuild plugin after code changes
```

`run-dev.bat` is a thin wrapper around `run-dev.exe` if you prefer the batch file.

Rebuild the launcher after editing `scripts/RunDevLauncher.cs`:

```powershell
powershell -File scripts/build-run-dev-exe.ps1
```

### IntelliJ alternative

1. Clone the main RuneLite repo alongside this plugin (already at `../runelite`).
2. Open the `runelite` project in IntelliJ with JDK 11.
3. Run `net.runelite.client.RuneLite` with:
   - VM options: `-ea --add-opens=java.desktop/sun.awt=ALL-UNNAMED`
   - Program arguments: `--developer-mode`

---

## Configuration

Once loaded, click the **OSRS Journal** icon in the left sidebar for the in-game summary panel.

### Hosted service (default — journal.osrsjournal.com)

**No configuration needed.** The production endpoints are baked into
`JournalConstants.java`. Install the plugin, log into OSRS, and:

1. The sidebar shows a pairing code (e.g. `K7M2-9X4P`).
2. Go to [journal.osrsjournal.com](https://journal.osrsjournal.com) → **Sign in**
   (Google, Discord, or email).
3. Enter the code under **Link character**.

Done — your stats, quests and gear sync automatically from then on.
**Open full journal** in the sidebar opens the hosted site with your character loaded.

The advanced overrides (API override, Web key override, Client ID) in plugin
settings should stay **blank** unless you run your own deployment — see
`../docs/COMMUNITY.md` (repo root) and `../supabase/README.md`.

| Setting | Value |
|---|---|
| Cloud Sync | ✅ (default) |
| Enable Sync | ✅ (default) |
| Sync Bank | ✅ (opt-in — bank data leaves your client) |
| Public Profile | ✅ — skills/quests visible like Wise Old Man; bank and gear always private |

### Self-hosted (advanced, single user)

Disable **Cloud Sync** and set:

| Setting | Value |
|---|---|
| Supabase URL | Your project URL |
| Anon Key | Your anon key |

Credentials are **not** stored in source code.

### Standalone web journal

Copy the example config and fill in your project credentials:

```bat
copy supabase-config.example.json supabase-config.json
```

Edit `supabase-config.json` with your Supabase URL and anon key. This file is gitignored — never commit it.

When you click **Open full journal** in the plugin sidebar, credentials are injected automatically from your plugin settings (no `supabase-config.json` needed).

### Rotating exposed keys

If your anon key was ever committed to git, rotate it in Supabase → Settings → API → Regenerate anon key, then update plugin settings and `supabase-config.json`.

---

## Database

Create a Supabase project and run the table SQL below (or use the Supabase SQL editor).

Tables:
- `players` — RSN + last_synced timestamp + quest_points (from game var 101)

If upgrading an existing database, add the column once:

```sql
ALTER TABLE players ADD COLUMN IF NOT EXISTS quest_points integer;
```
- `player_skills` — (rsn, skill) → level, xp
- `player_quests` — (rsn, quest_name) → state (NOT_STARTED / IN_PROGRESS / FINISHED)
- `player_equipment` — (rsn, slot_id) → item details
- `player_bank` — (rsn, item_id) → item name + quantity

### Row Level Security (recommended)

The webapp uses the Supabase **anon** key. For a personal project this is fine; if the repo is shared or deployed publicly, enable RLS so anyone with the key cannot read other players' data:

```sql
ALTER TABLE players ENABLE ROW LEVEL SECURITY;
ALTER TABLE player_skills ENABLE ROW LEVEL SECURITY;
ALTER TABLE player_quests ENABLE ROW LEVEL SECURITY;
ALTER TABLE player_equipment ENABLE ROW LEVEL SECURITY;
ALTER TABLE player_bank ENABLE ROW LEVEL SECURITY;

-- Allow anonymous read/write only for rows the plugin upserts (service role bypasses RLS).
-- Tighten these policies if you add per-user auth later.
CREATE POLICY "anon_read_players" ON players FOR SELECT TO anon USING (true);
CREATE POLICY "anon_read_skills" ON player_skills FOR SELECT TO anon USING (true);
CREATE POLICY "anon_read_quests" ON player_quests FOR SELECT TO anon USING (true);
CREATE POLICY "anon_read_equipment" ON player_equipment FOR SELECT TO anon USING (true);
CREATE POLICY "anon_read_bank" ON player_bank FOR SELECT TO anon USING (true);
```

The site prefers your saved RSN from `localStorage` when loading, falling back to the most recently synced player only if that RSN is not in the database.

---

## Architecture

```
RuneLite Client
  └── OsrsJournalPlugin      ← event listeners (client thread)
        ├── sidebar panel     → live summary + pairing code + Open full journal
        ├── JournalWebServer  → hosted: opens journal.osrsjournal.com
        │                       self-hosted: serves bundled osrs-journal.html on localhost
        ├── onStatChanged     → debounce → buildSkillRecords() ─┐
        ├── onItemContainerChanged (EQUIPMENT/BANK) → records ──┤
        ├── onGameStateChanged (LOGGED_IN) → performLoginSync() ┤→ executor → JournalSyncService
        └── onGameTick (every 100 ticks) → quest state diffs  ──┘
                                                                  │
                              ┌───────────────────────────────────┤
                              │ [hosted, default]                 │ [self-hosted]
                              ▼                                   ▼
                    HostedApiService                       SupabaseService
                    → /sync Edge Function                  → Supabase REST API
                    (X-Sync-Token per character)           (anon key)

journal.osrsjournal.com — reads via Supabase Auth + RLS; public profiles via anon key
  └── quest-reqs-data.json → skill/quest requirements for blocked-quest checks
```

---

## Quest requirements data

Blocked-quest checks (missing skills + prerequisite quests) use **`quest-reqs-data.json`**, generated from the [OSRS Wiki](https://oldschool.runescape.wiki):

```bash
node scripts/gen-quest-reqs.js
```

This fetches all ~200 RuneLite quest names, parses each wiki `{{Quest details}}` block, and writes skill + direct quest requirements. Re-run after game updates.

**Serving the webapp:** When using the plugin, click **Open full journal** in the sidebar — the jar serves the HTML locally. For standalone development, open `osrs-journal.html` through a local HTTP server (e.g. VS Code Live Server, `npx serve`, or Python `python -m http.server`) so `quest-reqs-data.json` loads via `fetch()`.

Manual fixes for parser gaps live in `QUEST_REQ_OVERRIDES` inside `osrs-journal.html`.

---

## Threading rules followed
Per the [RuneLite Developer Guide](https://github.com/runelite/runelite/wiki/Developer-Guide):

- All `@Subscribe` handlers run on the **client thread** — data is collected there
- Network I/O is pushed to the **injected `ScheduledExecutorService`** — never the client thread
- `ClientThread.invokeLater()` is used to re-enter the client thread from scheduled tasks
- RuneLite's **injected `OkHttpClient`** and **`Gson`** are used — no custom instances
- Skills are **debounced** to avoid dozens of requests firing at login
