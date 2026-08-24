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

**Users** install **OSRS Journal** from the Plugin Hub. Shipping an update is a
`commit=` bump on [runelite/plugin-hub](https://github.com/runelite/plugin-hub) —
see [PLUGIN_HUB.md](PLUGIN_HUB.md).

**Local development** uses the Hub-documented Gradle task (same as
[example-plugin](https://github.com/runelite/example-plugin)). Jagex Launcher
**disables sideloading**, so do not expect a copied jar to load there.

```bash
cd osrs-journal-plugin
./gradlew run
```

That starts a `--developer-mode` client with this plugin loaded as builtin.

### Jagex Account login (required for the Gradle client)

The Gradle client cannot take a Jagex username/password. Log in through the
**Jagex Launcher** once so RuneLite writes `~/.runelite/credentials.properties`
(Windows: `%USERPROFILE%\.runelite\credentials.properties`), then `./gradlew run`
will pick it up.

See the [RuneLite Jagex Accounts wiki](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

### IntelliJ

Open this plugin project with JDK 11 and run the Gradle `run` task (green arrow
next to `tasks.register('run', JavaExec)` in `build.gradle`).

---

## Configuration

Once loaded, click the **OSRS Journal** icon in the left sidebar for the in-game summary panel.

Production endpoints are baked into `JournalConstants.java`. Plugin Hub rules require
third-party sync to be **opt-in**. Install the plugin, log into OSRS, and:

1. Enable **Enable Sync** in plugin settings and confirm the 3rd-party warning.
2. The sidebar shows a pairing code (e.g. `K7M2-9X4P`).
3. Go to [journal.osrsjournal.com](https://journal.osrsjournal.com) → **Sign in**
   (Google, Discord, or email).
4. Enter the code under **Link character**.

Done — your stats, quests and gear sync while Enable Sync stays on.
**Open full journal** in the sidebar opens the hosted site with your character loaded.
If no code appears (e.g. the backend was unreachable at login), click **Refresh**.

| Setting | Default |
|---|---|
| Enable Sync | ❌ off (Plugin Hub opt-in + warning) |
| Sync Bank | ❌ off (second opt-in + warning) |
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

**Serving the webapp:** When using the plugin, click **Open full journal** in the sidebar — it opens the hosted site at [journal.osrsjournal.com](https://journal.osrsjournal.com) (with a short-lived session token so your character loads without signing in). For standalone web development, open `osrs-journal.html` through a local HTTP server (e.g. VS Code Live Server, `npx serve`, or Python `python -m http.server`) so `quest-reqs-data.json` loads via `fetch()`.

Manual fixes for parser gaps live in `QUEST_REQ_OVERRIDES` inside `osrs-journal.html`.

---

## Threading rules followed
Per the [RuneLite Developer Guide](https://github.com/runelite/runelite/wiki/Developer-Guide):

- All `@Subscribe` handlers run on the **client thread** — data is collected there
- Network I/O is pushed to the **injected `ScheduledExecutorService`** — never the client thread
- `ClientThread.invokeLater()` is used to re-enter the client thread from scheduled tasks
- RuneLite's **injected `OkHttpClient`** and **`Gson`** are used — no custom instances
- Skills are **debounced** to avoid dozens of requests firing at login
