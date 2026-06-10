package com.osrsjournal;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
class PairingService
{
    @Inject
    private HostedApiService hostedApiService;

    @Inject
    private SyncTokenStore syncTokenStore;

    private volatile PairingState lastState;

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

        String token = syncTokenStore.getToken(rsn);
        if (token != null)
        {
            return new PairingState(rsn, null, token, true, 0);
        }
        return null;
    }

    void clearState()
    {
        lastState = null;
    }
}
