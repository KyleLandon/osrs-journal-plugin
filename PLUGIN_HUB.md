# Plugin Hub — how this plugin ships and updates

Hub does **not** build from a jar on your machine. It clones this GitHub repo at a
pinned commit hash listed in [runelite/plugin-hub](https://github.com/runelite/plugin-hub).

Sources of truth:

- [plugin-hub README — Updating a plugin](https://github.com/runelite/plugin-hub#updating-a-plugin)
- [Plugin Hub Review](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review)
- [Rejected or Rolled Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
- [example-plugin AGENTS.md](https://github.com/runelite/example-plugin/blob/master/AGENTS.md)
- [Jagex third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)

Current Hub manifest (`plugins/osrs-journal`):

```
repository=https://github.com/KyleLandon/osrs-journal-plugin.git
commit=<40-char hash of this repo>
warning=This plugin submits your IP address, and may submit various account data, to a 3rd-party server not controlled or verified by Runelite developers.
```

First listing (merged): https://github.com/runelite/plugin-hub/pull/13940

`build=standard` in `runelite-plugin.properties` means Hub **replaces** our
`build.gradle` / `settings.gradle` when packaging. Do not add third-party
dependencies — that forces `build=gradle` plus dependency verification and
slows review.

## Local development (before a Hub PR)

Jagex Launcher **disables sideloading**. Use the Hub-documented client:

```bash
cd osrs-journal-plugin
./gradlew run
```

Jagex accounts: [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

Do not mix drive-by reformatting with feature commits (Hub reviewers reject noisy diffs).

## Update pipeline (existing listing)

1. Land the change on **this** repo’s `main` and **push to GitHub**.
   Hub CI clones GitHub; unpushed commits never ship.
2. Copy the full 40-character commit hash (`git rev-parse HEAD`).
3. On the [plugin-hub fork](https://github.com/KyleLandon/plugin-hub):

```bash
git remote add upstream https://github.com/runelite/plugin-hub.git   # once
git fetch upstream
git checkout -B osrs-journal upstream/master
# set commit=<hash> in plugins/osrs-journal  (leave repository= and warning=)
git add plugins/osrs-journal
git commit -m "update osrs-journal"
git push -f -u origin osrs-journal
```

The `-f` is only on the fork’s `osrs-journal` branch (Hub’s documented flow),
never on `main`/`master`.

4. Open **one** PR against `runelite/plugin-hub` (`master`). Or:

```bash
./scripts/hub-update.sh --pr
```

5. Watch Hub CI. If the build fails, fix **this** plugin repo, push, bump
   `commit=` on the **same** PR. Do not open a second PR.

6. Wait for the auto-review bot and/or a maintainer. “requires a review from a
   Plugin Hub maintainer” is normal until they act.

7. After merge, users get the update from Plugin Hub in RuneLite. Delete the
   `osrs-journal` hub branch; recreate it next time from `upstream/master`.

## Compliance (must stay true on every update)

### Forbidden

| Rule | Our status |
|------|------------|
| Reflection / JNI / ProcessBuilder / runtime code download | Not used |
| Inject input / autotype / modify outgoing chat | Not used |
| Boss prayer/attack helpers, PvP helpers | Not used |
| Menu entry actions / click-zone resizing | Not used |
| Crowdsource **other** players’ data | Only local player; opt-in sync |
| Credential manager / store Jagex passwords | Pairing codes only; no credentials |
| Kotlin/Scala | Java only |

### Required patterns for third-party HTTP

| Rule | Our status |
|------|------------|
| Third-party sync **disabled by default** | `syncEnabled()` → `false` |
| ConfigItem `warning` with exact Hub wording | On Enable Sync + Sync Bank |
| Bank / sensitive data separate opt-in | `syncBank()` → `false` |
| No HTTP until opt-in | pair-init, sync, privacy push, live session all gated on `syncEnabled` |
| Use injected `OkHttpClient` + `Gson` | `HostedApiService` |
| No HTTP on client thread | Executor + `clientThread.invokeLater` for reads |
| Open URLs via `LinkBrowser` | `JournalBrowser` / panel links |
| Files only under `.runelite` if writing disk | Tokens in RuneLite config store (`ConfigManager`) |
