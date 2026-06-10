package com.osrsjournal;

import lombok.Getter;

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
