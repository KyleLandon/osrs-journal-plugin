package com.osrsjournal;

import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Routes sync operations to hosted Edge Functions or direct Supabase REST (self-hosted).
 */
@Slf4j
@Singleton
class JournalSyncService
{
    @Inject
    private OsrsJournalConfig config;

    @Inject
    private SupabaseService supabaseService;

    @Inject
    private HostedApiService hostedApiService;

    @Inject
    private SyncTokenStore syncTokenStore;

    @Inject
    private PairingService pairingService;

    boolean isHostedMode()
    {
        return config.hostedMode() && hostedApiService.isConfigured();
    }

    /** Ensures a sync token exists for {@code rsn}; may call pair-init. */
    PairingState ensurePairing(String rsn)
    {
        if (!isHostedMode())
        {
            return null;
        }
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
        if (isHostedMode())
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

        boolean ok = supabaseService.upsert("players", playerRecord);
        ok = supabaseService.upsert("player_skills", skillRecords) && ok;
        ok = supabaseService.upsert("player_quests", questRecords) && ok;
        ok = deleteAndUpsert("player_equipment", rsn, equipRecords) && ok;
        return ok;
    }

    boolean syncSkills(String rsn, List<Map<String, Object>> records)
    {
        if (isHostedMode())
        {
            return hostedSyncPartial(rsn, new HostedApiService.SyncPayload().skills(records));
        }

        if (supabaseService.upsert("player_skills", records))
        {
            return supabaseService.patchLastSynced(rsn);
        }
        return false;
    }

    boolean syncQuests(String rsn, List<Map<String, Object>> records)
    {
        if (isHostedMode())
        {
            return hostedSyncPartial(rsn, new HostedApiService.SyncPayload().quests(records));
        }

        if (supabaseService.upsert("player_quests", records))
        {
            return supabaseService.patchLastSynced(rsn);
        }
        return false;
    }

    boolean syncEquipment(String rsn, List<Map<String, Object>> records)
    {
        if (isHostedMode())
        {
            return hostedSyncPartial(rsn,
                new HostedApiService.SyncPayload().equipment(records, true));
        }

        if (deleteAndUpsert("player_equipment", rsn, records))
        {
            return supabaseService.patchLastSynced(rsn);
        }
        return false;
    }

    boolean syncBank(String rsn, List<Map<String, Object>> records)
    {
        if (isHostedMode())
        {
            return hostedSyncPartial(rsn,
                new HostedApiService.SyncPayload().bank(records, true));
        }

        boolean ok = supabaseService.deleteByRsn("player_bank", rsn);
        if (!records.isEmpty())
        {
            ok = supabaseService.upsert("player_bank", records) && ok;
        }
        if (ok)
        {
            return supabaseService.patchLastSynced(rsn);
        }
        return false;
    }

    /** Pushes the profile privacy flag; only called when the user flips the config toggle. */
    boolean syncPrivacy(String rsn, boolean isPublic)
    {
        if (!isHostedMode())
        {
            return false;
        }
        return hostedSyncPartial(rsn, new HostedApiService.SyncPayload()
            .profilePublic(isPublic)
            .touchLastSynced(false));
    }

    private boolean hostedSyncPartial(String rsn, HostedApiService.SyncPayload payload)
    {
        String token = syncTokenStore.getToken(rsn);
        if (token == null)
        {
            return false;
        }
        return hostedApiService.sync(rsn, token, payload);
    }

    private boolean deleteAndUpsert(String table, String rsn, List<Map<String, Object>> records)
    {
        boolean ok = supabaseService.deleteByRsn(table, rsn);
        if (!records.isEmpty())
        {
            ok = supabaseService.upsert(table, records) && ok;
        }
        return ok;
    }
}
