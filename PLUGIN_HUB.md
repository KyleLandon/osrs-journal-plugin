# Plugin Hub submission

## Prerequisites (done)

- Public repo: https://github.com/KyleLandon/osrs-journal-plugin
- BSD-2-Clause `LICENSE`
- `icon.png` at repo root (≤ 48×72 px)
- `build=standard` in `runelite-plugin.properties`
- Privacy policy: https://journal.osrsjournal.com/privacy.html
- Bank sync **opt-in** (default off)
- No reflection, JNI, ProcessBuilder, or runtime code download

## Submit

1. Push the latest `main` on the plugin repo and copy the full commit hash.

2. Fork https://github.com/runelite/plugin-hub

3. Create `plugins/osrs-journal` with:

```
repository=https://github.com/KyleLandon/osrs-journal-plugin.git
commit=<40-char commit hash>
```

4. Open a PR describing:
   - Syncs skills, quests, and worn gear to journal.osrsjournal.com
   - Bank sync is opt-in only
   - Pairing flow links in-game character to website account
   - Privacy policy URL in plugin description

5. Wait for CI (`RuneLite Plugin Hub Checks`) and review.

## After merge

Users install **OSRS Journal** from the RuneLite Plugin Hub — no dev launcher required.

Updates: push to plugin repo, then open a PR on plugin-hub updating the `commit=` hash.
