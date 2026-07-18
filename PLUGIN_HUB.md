# Plugin Hub submission

Sources of truth for what Hub maintainers allow / reject:

- [Plugin Hub Review](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review)
- [Rejected or Rolled Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
- [example-plugin AGENTS.md](https://github.com/runelite/example-plugin/blob/master/AGENTS.md) (coding + config rules)
- [Jagex third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)

Open PR: https://github.com/runelite/plugin-hub/pull/13940

## Prerequisites (done)

- Public repo: https://github.com/KyleLandon/osrs-journal-plugin
- BSD-2-Clause `LICENSE`
- `icon.png` at repo root (≤ 48×72 px)
- `build=standard` in `runelite-plugin.properties`
- Privacy policy: https://journal.osrsjournal.com/privacy.html
- **Enable Sync** and **Sync Bank** are **opt-in** (default off) with the Hub-required `warning` text
- No reflection, JNI, ProcessBuilder, or runtime code download
- Java only; inject OkHttp/Gson; `LinkBrowser` for URLs; network off client thread

## Hub compliance checklist

### Forbidden (we must not)

| Rule | Our status |
|------|------------|
| Reflection / JNI / ProcessBuilder / runtime code download | Not used |
| Inject input / autotype / modify outgoing chat | Not used |
| Boss prayer/attack helpers, PvP helpers | Not used |
| Menu entry actions / click-zone resizing | Not used |
| Crowdsource **other** players’ data | Only local player; opt-in sync |
| Credential manager / store Jagex passwords | Pairing codes only; no credentials |
| Adult content / content simulation | N/A |
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

### How “no exposing player info over HTTP” applies

Hub rejects plugins that **broadcast / crowdsource** player data or expose it as an open HTTP surface. Personal sync to your own backend is allowed when:

1. It is **opt-in** with the standard warning, and
2. You only sync the **local** player (not other players’ locations/gear).

We sync the logged-in character to journal.osrsjournal.com after the user enables Sync and confirms the warning.

## Submit / update

1. Push the latest `main` on the plugin repo and copy the full commit hash.
2. Fork https://github.com/runelite/plugin-hub (or update the existing PR branch).
3. Create/update `plugins/osrs-journal`:

```
repository=https://github.com/KyleLandon/osrs-journal-plugin.git
commit=<40-char commit hash>
```

Optional hub-file `warning=` line (used by some plugins instead of/in addition to ConfigItem warnings). Prefer ConfigItem warnings when networking is behind opt-in toggles (Hub guidance).

4. PR description should mention: opt-in sync, bank opt-in, pairing (no credentials), privacy policy URL, open-source backend.

5. Wait for CI + human/bot maintainer review. “RuneLite Plugin Hub Checks fail” with “requires a review from a Plugin Hub maintainer” is normal until a maintainer acts.

## After merge

Users install **OSRS Journal** from the Plugin Hub. Updates = push plugin repo, then PR bumping `commit=` on plugin-hub.
