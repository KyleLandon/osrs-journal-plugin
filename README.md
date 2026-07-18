# OSRS Journal

Track your Old School RuneScape account at **[journal.osrsjournal.com](https://journal.osrsjournal.com)**.

After you opt in to sync, the plugin updates your character while you play. The web
journal gives you quest planning, gear tracking, boss unlock checks, public profiles
(like Wise Old Man), and a one-click export of your whole account for AI assistants
like ChatGPT or Claude.

**Privacy policy:** [journal.osrsjournal.com/privacy.html](https://journal.osrsjournal.com/privacy.html)

## What it syncs

| Data | When |
|------|------|
| Skills (levels + XP) | A few seconds after you gain XP |
| Quest states | Checked about once a minute |
| Worn equipment | Whenever your gear changes |
| Bank contents | **Only if you enable Sync Bank** — each time you open the bank |

## Getting started

1. Install **OSRS Journal** from the RuneLite Plugin Hub and log into OSRS.
2. In plugin settings, turn on **Enable Sync** and confirm the 3rd-party server warning
   (required by the Plugin Hub — sync is off by default).
3. The sidebar panel (book icon) shows a pairing code like `K7M2-9X4P`.
4. Go to [journal.osrsjournal.com](https://journal.osrsjournal.com) and **Sign in**
   (Google, Discord, or email).
5. Enter the code under **Link character**.

That's it — your character syncs while you play (with sync left on).
**Open full journal** in the sidebar takes you straight to your journal.

You can link multiple characters (each gets its own pairing code when you log in).

## The web journal

- **Quest planner** — see which quests you can do now, which are blocked, and what's missing
- **Gear & bank tracker** — worn equipment and bank gear (bank requires opt-in sync)
- **Boss unlocks** — which bosses your stats and quests give you access to
- **Public profile** — share your progress like Wise Old Man
- **AI export** — copy your full account state as Markdown/JSON for ChatGPT, Claude, etc.

## Privacy

- **Public profile** (skills and quests) is on by default — the same data the
  official OSRS hiscores already publish. Toggle it off on the website or in
  plugin settings if you want to be invisible.
- **Bank and gear are always private** on public profiles — only you can see bank/gear when signed in.
- **Enable Sync is off by default** — you must opt in (and confirm the Hub warning) before any data is sent.
- **Sync Bank is a second opt-in** — enable it only if you want bank data synced.
- No passwords or account credentials are ever read or transmitted — the plugin
  only reads game state RuneLite already exposes (skills, quests, items).
- **Delete everything anytime** — Account → *Delete account & data* on the website
  removes all synced data and your account (see the [privacy policy](https://journal.osrsjournal.com/privacy.html)).

## Transparency

The entire service is open source, so you can verify exactly what happens to your data:

| Component | Repo |
|-----------|------|
| RuneLite plugin | [osrs-journal-plugin](https://github.com/KyleLandon/osrs-journal-plugin) (this repo) |
| Backend (Edge Functions, database schema, security policies) | [osrs-journal-backend](https://github.com/KyleLandon/osrs-journal-backend) |
| Website | [osrs-journal-web](https://github.com/KyleLandon/osrs-journal-web) |

The plugin ships **no database credentials** — every write goes through an
authenticated backend function scoped to your character's sync token, and
row-level security keeps private data (bank, gear) readable only by you.

## Sidebar panel

While logged in, the panel shows your combat level, quest points, skill levels,
quests in progress, sync status, and bank sync on/off — plus the pairing code until your account
is linked.

## Support

- Issues / feature requests: [GitHub issues](https://github.com/KyleLandon/osrs-journal-plugin/issues)
- Web journal: [journal.osrsjournal.com](https://journal.osrsjournal.com)
- Plugin Hub submission guide: [PLUGIN_HUB.md](PLUGIN_HUB.md)

## Developers

Build and development docs live in [SETUP.md](SETUP.md).
