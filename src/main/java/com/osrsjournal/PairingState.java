package com.osrsjournal;

import lombok.Getter;

/**
 * Immutable snapshot of a character's pairing status, shown in the sidebar.
 *
 * <p>Two shapes exist:
 * <ul>
 *   <li><b>Awaiting link</b> — {@code pairCode} set, {@code linked=false}: the user
 *       must enter the code on the website within {@code expiresInSeconds}.</li>
 *   <li><b>Linked</b> — {@code linked=true}, {@code pairCode} may be null when the
 *       state was derived from a sync response rather than pair-init.</li>
 * </ul>
 */
@Getter
class PairingState
{
    private final String rsn;
    private final String pairCode;
    private final String syncToken;
    private final boolean linked;
    private final int expiresInSeconds;
    /** Wall-clock millis when this state was created (for live countdown). */
    private final long issuedAtMs;

    PairingState(String rsn, String pairCode, String syncToken, boolean linked, int expiresInSeconds)
    {
        this(rsn, pairCode, syncToken, linked, expiresInSeconds, System.currentTimeMillis());
    }

    PairingState(
        String rsn,
        String pairCode,
        String syncToken,
        boolean linked,
        int expiresInSeconds,
        long issuedAtMs
    )
    {
        this.rsn = rsn;
        this.pairCode = pairCode;
        this.syncToken = syncToken;
        this.linked = linked;
        this.expiresInSeconds = expiresInSeconds;
        this.issuedAtMs = issuedAtMs;
    }

    boolean needsPairingDisplay()
    {
        return !linked && pairCode != null && !pairCode.isEmpty();
    }

    /** Seconds left before the pairing code expires; 0 if linked / no code / expired. */
    int getSecondsRemaining()
    {
        if (!needsPairingDisplay() || expiresInSeconds <= 0)
        {
            return 0;
        }
        long elapsed = (System.currentTimeMillis() - issuedAtMs) / 1000L;
        long left = expiresInSeconds - elapsed;
        return left > 0 ? (int) left : 0;
    }

    boolean isCodeExpired()
    {
        return needsPairingDisplay() && getSecondsRemaining() <= 0;
    }

    /** Human-readable countdown, e.g. "9:42" or "expired". */
    String getExpiryLabel()
    {
        if (!needsPairingDisplay())
        {
            return null;
        }
        int left = getSecondsRemaining();
        if (left <= 0)
        {
            return "expired — click New code";
        }
        int mins = left / 60;
        int secs = left % 60;
        return String.format("%d:%02d left", mins, secs);
    }
}
