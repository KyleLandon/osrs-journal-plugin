# OSRS Journal

Track your Old School RuneScape account at **[journal.osrsjournal.com](https://journal.osrsjournal.com)** — automatically.

The plugin syncs your character while you play. The web journal gives you quest
planning, gear tracking, boss unlock checks, public profiles (like Wise Old Man),
and a one-click export of your whole account for AI assistants like ChatGPT or Claude.

## What it syncs

| Data | When |
|------|------|
| Skills (levels + XP) | A few seconds after you gain XP |
| Quest states | Checked about once a minute |
| Worn equipment | Whenever your gear changes |
| Bank contents | Each time you open your bank (optional) |

## Getting started

No configuration needed.

1. Install **OSRS Journal** from the Plugin Hub and log into OSRS.
2. The sidebar panel (book icon) shows a pairing code like `K7M2-9X4P`.
3. Go to [journal.osrsjournal.com](https://journal.osrsjournal.com) and **Sign in**
   (Google, Discord, or email).
4. Enter the code under **Link character**.

That's it — your character now syncs automatically every time you play.
**Open full journal** in the sidebar takes you straight to your journal.

You can link multiple characters (each gets its own pairing code when you log in).

## The web journal

- **Quest planner** — see which quests you can do now, which are blocked, and what's missing
- **Gear & bank tracker** — worn equipment and bank gear, with a what-if gear simulator
- **Boss unlocks** — which bosses your stats and quests give you access to
- **Public profile** — share your progress like Wise Old Man
- **AI export** — copy your full account state as Markdown/JSON for ChatGPT, Claude, etc.

## Privacy

- **Public profile** (skills and quests) is on by default — toggle it off on the
  website or in plugin settings if you want to be invisible.
- **Bank and gear are always private** — only you can see them, signed in.
- **Sync Bank** can be turned off in plugin settings if you don't want bank
  data leaving your client at all.
- No passwords or account credentials are ever read or transmitted — the plugin
  only reads game state RuneLite already exposes (skills, quests, items).

## Sidebar panel

While logged in, the panel shows your combat level, quest points, skill levels,
quests in progress, and sync status — plus the pairing code until your account
is linked.

## Support

- Issues / feature requests: [GitHub issues](https://github.com/KyleLandon/osrs-journal-plugin/issues)
- Web journal: [journal.osrsjournal.com](https://journal.osrsjournal.com)

## Developers

Build and development docs live in [SETUP.md](SETUP.md).
