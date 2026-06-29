# OSRS Journal Plugin — Setup Guide

## What this does
The plugin runs inside RuneLite and syncs your character data to the OSRS Journal cloud in real time:
- **Skills** — synced 3 seconds after your last XP gain (debounced)
- **Quests** — checked every ~1 minute for state changes
- **Equipment** — synced every time your worn gear changes
- **Bank** — synced every time you open the bank

**In-client UI:** a sidebar panel (book icon on the left) shows a live summary while logged in.
**Open full journal** opens [journal.osrsjournal.com](https://journal.osrsjournal.com) with your
character loaded.

---

## Prerequisites
- Java 11+ JDK (e.g. [Adoptium Temurin 11](https://adoptium.net/))
- IntelliJ IDEA (recommended) or any IDE with Gradle support

---

## Building

```bash
cd osrs-journal-plugin
./gradlew jar
```

The built jar will be at `build/libs/osrs-journal-plugin-1.0.0.jar` (~50 KB).

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
run-dev.exe                   rem builds plugin + launches dev RuneLite (builds client only if jar missing)
run-dev.exe --rebuild         rem force full client rebuild, then launch
run-dev.exe --skip-plugin-build rem skip plugin rebuild when iterating on client only
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

**No configuration needed.** The production endpoints are baked into
`JournalConstants.java`. Install the plugin, log into OSRS, and:

1. The sidebar shows a pairing code (e.g. `K7M2-9X4P`).
2. Go to [journal.osrsjournal.com](https://journal.osrsjournal.com) → **Sign in**
   (Google, Discord, or email).
3. Enter the code under **Link character**.

Done — your stats, quests and gear sync automatically from then on.
**Open full journal** in the sidebar opens the hosted site with your character loaded.
If no code appears (e.g. the backend was unreachable at login), click **Refresh**.

| Setting | Default |
|---|---|
| Enable Sync | ✅ |
| Sync Bank | ❌ off by default (opt-in in plugin settings) |
| Public Profile | ✅ — skills/quests visible like Wise Old Man; bank and gear always private |

The **Advanced** overrides (API override, Web key override, Client ID) should stay
**blank** unless you run your own backend deployment — see `../docs/COMMUNITY.md`
(monorepo root) and `../supabase/README.md`.

---

## Database

Backend schema, RLS policies and Edge Functions live in the monorepo under
`../supabase/` (`migrations/` + `functions/`). The plugin never talks to the
database directly — all writes go through the `/sync` Edge Function,
authenticated by a per-character sync token.

---

## Architecture

```
RuneLite Client
  └── OsrsJournalPlugin      ← event listeners (client thread)
        ├── sidebar panel     → live summary + pairing code + Open full journal
        ├── JournalBrowser    → opens journal.osrsjournal.com (live session or ?rsn= fallback)
        ├── onStatChanged     → debounce → buildSkillRecords() ─┐
        ├── onItemContainerChanged (EQUIPMENT/BANK) → records ──┤
        ├── onGameStateChanged (LOGGED_IN) → performLoginSync() ┤→ executor → JournalSyncService
        └── onGameTick (every 100 ticks) → quest state diffs  ──┘
                                                                  │
                                                                  ▼
                                                        HostedApiService
                                                        → /sync Edge Function
                                                        (X-Sync-Token per character)

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
