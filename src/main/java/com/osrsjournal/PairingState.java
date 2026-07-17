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

    PairingState(String rsn, String pairCode, String syncToken, boolean linked, int expiresInSeconds)
    {
        this.rsn = rsn;
        this.pairCode = pairCode;
        this.syncToken = syncToken;
        this.linked = linked;
        this.expiresInSeconds = expiresInSeconds;
    }

    boolean needsPairingDisplay()
    {
        return pairCode != null && !pairCode.isEmpty();
    }
}
