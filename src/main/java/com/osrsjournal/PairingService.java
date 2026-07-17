package com.osrsjournal;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks the pairing lifecycle between this RuneLite client and a website account.
 *
 * <p>Ownership of an RSN is proven by the plugin running while logged in as that
 * character: {@code pair-init} issues a short-lived code + sync token, the user
 * enters the code on the website, and {@code pair-claim} binds the token to their
 * account. To keep server load low, pair-init is only called when there is no
 * stored token (or the token went stale) — for already-linked characters the
 * linked state is derived from the {@code claimed} field of sync responses via
 * {@link #updateLinkedState}.
 *
 * <p>{@code lastState} is volatile: it is written from executor threads and read
 * from the client thread when building panel snapshots.
 */
@Slf4j
@Singleton
class PairingService
{
    @Inject
    private HostedApiService hostedApiService;

    @Inject
    private SyncTokenStore syncTokenStore;

    private volatile PairingState lastState;

    /**
     * Calls pair-init and stores the returned sync token. Network I/O — executor
     * threads only. Idempotent: for a known RSN the server reuses the existing
     * token and reports whether it is already linked; otherwise it mints a new
     * token and a fresh pairing code.
     */
    PairingState ensurePairing(String rsn)
    {
        PairingState init = hostedApiService.pairInit(rsn);
        if (init == null)
        {
            log.warn("Failed to initialize pairing for '{}'", rsn);
            return null;
        }

        syncTokenStore.saveToken(rsn, init.getSyncToken());
        lastState = init;
        log.info("OSRS Journal: pairing code for '{}' is {} (linked={})",
            rsn, init.getPairCode(), init.isLinked());
        return init;
    }

    PairingState getCurrentState(String rsn)
    {
        if (lastState != null && rsn.equals(lastState.getRsn()))
        {
            return lastState;
        }
        // No live state from pair-init this session — having a stored token
        // doesn't prove the account was claimed on the website, so report
        // nothing rather than a false "linked".
        return null;
    }

    /**
     * Updates the linked state from a sync response ({@code claimed} field) —
     * lets the sidebar show "Account linked" without a pair-init round trip.
     * Never downgrades a state that is currently showing a pairing code.
     */
    void updateLinkedState(String rsn, String syncToken, boolean linked)
    {
        if (linked)
        {
            lastState = new PairingState(rsn, null, syncToken, true, 0);
        }
    }

    void clearState()
    {
        lastState = null;
    }
}
