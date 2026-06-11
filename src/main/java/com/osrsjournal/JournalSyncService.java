package com.osrsjournal;

import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Syncs game data to the OSRS Journal cloud through Edge Functions,
 * authenticated by a per-character sync token.
 */
@Slf4j
@Singleton
class JournalSyncService
{
    @Inject
    private OsrsJournalConfig config;

    @Inject
    private HostedApiService hostedApiService;

    @Inject
    private SyncTokenStore syncTokenStore;

    @Inject
    private PairingService pairingService;

    /** Ensures a sync token exists for {@code rsn}; may call pair-init. */
    PairingState ensurePairing(String rsn)
    {
        return pairingService.ensurePairing(rsn);
    }

    PairingState getPairingState(String rsn)
    {
        return pairingService.getCurrentState(rsn);
    }

    String getSyncToken(String rsn)
    {
        return syncTokenStore.getToken(rsn);
    }

    boolean syncLogin(
        String rsn,
        List<Map<String, Object>> playerRecord,
        List<Map<String, Object>> skillRecords,
        List<Map<String, Object>> questRecords,
        List<Map<String, Object>> equipRecords
    )
    {
        String token = syncTokenStore.getToken(rsn);
        if (token == null)
        {
            log.warn("No sync token for '{}' — sync skipped", rsn);
            return false;
        }

        // Note: profile_public is intentionally NOT sent here — the website's
        // privacy toggle is the source of truth once an account is linked.
        // It is only pushed from syncPrivacy() when the user changes the
        // RuneLite config toggle.
        HostedApiService.SyncPayload payload = new HostedApiService.SyncPayload()
            .players(playerRecord)
            .skills(skillRecords)
            .quests(questRecords)
            .equipment(equipRecords, true)
            .touchLastSynced(true);

        return hostedApiService.sync(rsn, token, payload);
    }

    boolean syncSkills(String rsn, List<Map<String, Object>> records)
    {
        return syncPartial(rsn, new HostedApiService.SyncPayload().skills(records));
    }

    boolean syncQuests(String rsn, List<Map<String, Object>> records)
    {
        return syncPartial(rsn, new HostedApiService.SyncPayload().quests(records));
    }

    boolean syncEquipment(String rsn, List<Map<String, Object>> records)
    {
        return syncPartial(rsn, new HostedApiService.SyncPayload().equipment(records, true));
    }

    boolean syncBank(String rsn, List<Map<String, Object>> records)
    {
        return syncPartial(rsn, new HostedApiService.SyncPayload().bank(records, true));
    }

    /** Pushes the profile privacy flag; only called when the user flips the config toggle. */
    boolean syncPrivacy(String rsn, boolean isPublic)
    {
        return syncPartial(rsn, new HostedApiService.SyncPayload()
            .profilePublic(isPublic)
            .touchLastSynced(false));
    }

    private boolean syncPartial(String rsn, HostedApiService.SyncPayload payload)
    {
        String token = syncTokenStore.getToken(rsn);
        if (token == null)
        {
            return false;
        }
        return hostedApiService.sync(rsn, token, payload);
    }
}
