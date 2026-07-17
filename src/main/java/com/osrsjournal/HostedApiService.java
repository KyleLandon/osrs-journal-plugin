package com.osrsjournal;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * HTTP client for the hosted Supabase Edge Functions (multi-user mode).
 *
 * <p>The plugin deliberately holds no database credentials: every write goes
 * through an Edge Function that validates the per-character sync token
 * ({@code X-Sync-Token}) server-side and performs the actual database work with
 * the service role. Endpoints used:
 * <ul>
 *   <li>{@code pair-init} — issue/reuse a sync token + pairing code for an RSN</li>
 *   <li>{@code sync} — batched upsert of skills/quests/equipment/bank</li>
 *   <li>{@code localhost-session} — short-lived read token for "Open full journal"</li>
 * </ul>
 *
 * <p>Uses RuneLite's injected {@link OkHttpClient}; all calls are synchronous and
 * must run on the shared executor, never the client thread.
 */
@Slf4j
@Singleton
public class HostedApiService
{
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    @Inject
    private OkHttpClient httpClient;

    @Inject
    private Gson gson;

    @Inject
    private OsrsJournalConfig config;

    /** Production endpoints by default; self-hosters can override in Advanced config. */
    String resolveApiBase()
    {
        return JournalConstants.resolveApiBase(config.apiBaseUrl());
    }

    /**
     * Requests a pairing code + sync token for {@code rsn}.
     * Returns null on any failure — callers treat that as "pairing unavailable"
     * and retry later (rate limits on the server make hammering pointless).
     */
    PairingState pairInit(String rsn)
    {
        String base = resolveApiBase();

        JsonObject body = new JsonObject();
        body.addProperty("rsn", rsn);

        Request.Builder builder = new Request.Builder()
            .url(base + "/pair-init")
            .post(RequestBody.create(JSON_MEDIA, body.toString()))
            .header("Content-Type", "application/json");

        addPluginClientId(builder);

        try (Response response = httpClient.newCall(builder.build()).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                log.warn("pair-init failed for '{}': HTTP {}", rsn, response.code());
                return null;
            }

            JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
            return new PairingState(
                rsn,
                json.get("code").getAsString(),
                json.get("sync_token").getAsString(),
                json.has("linked") && json.get("linked").getAsBoolean(),
                json.has("expires_in") ? json.get("expires_in").getAsInt() : 600
            );
        }
        catch (IOException e)
        {
            log.error("pair-init error for '{}'", rsn, e);
            return null;
        }
    }

    /**
     * Exchanges the sync token for a ~5 minute read-only session token, so
     * "Open full journal" can show bank/gear in the browser without the user
     * signing in. Returns null if the exchange fails (caller falls back to the
     * public profile URL).
     */
    String createLocalhostSession(String syncToken)
    {
        String base = resolveApiBase();
        if (syncToken == null || syncToken.isEmpty())
        {
            return null;
        }

        Request request = new Request.Builder()
            .url(base + "/localhost-session")
            .post(RequestBody.create(JSON_MEDIA, "{}"))
            .header("Content-Type", "application/json")
            .header("X-Sync-Token", syncToken)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                log.warn("localhost-session failed: HTTP {}", response.code());
                return null;
            }

            JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
            return json.get("session_token").getAsString();
        }
        catch (IOException e)
        {
            log.error("localhost-session error", e);
            return null;
        }
    }

    /**
     * Posts a batched sync payload. One request carries any combination of
     * players/skills/quests/equipment/bank rows — the server upserts them in
     * FK-safe order and answers with {@code claimed}, which doubles as a free
     * "is this character linked yet?" check on every sync.
     */
    SyncResult sync(String rsn, String syncToken, SyncPayload payload)
    {
        String base = resolveApiBase();
        if (syncToken == null || syncToken.isEmpty())
        {
            return SyncResult.failed(false);
        }

        payload.setRsn(rsn);

        Request request = new Request.Builder()
            .url(base + "/sync")
            .post(RequestBody.create(JSON_MEDIA, gson.toJson(payload)))
            .header("Content-Type", "application/json")
            .header("X-Sync-Token", syncToken)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                log.warn("hosted sync failed for '{}': HTTP {}", rsn, response.code());
                // 401/403 means the stored token is stale (e.g. rotated server-side)
                return SyncResult.failed(response.code() == 401 || response.code() == 403);
            }

            boolean claimed = false;
            if (response.body() != null)
            {
                try
                {
                    JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
                    claimed = json != null && json.has("claimed") && json.get("claimed").getAsBoolean();
                }
                catch (RuntimeException e)
                {
                    log.debug("could not parse sync response for '{}'", rsn, e);
                }
            }
            log.debug("hosted sync ok for '{}' (claimed={})", rsn, claimed);
            return SyncResult.ok(claimed);
        }
        catch (IOException e)
        {
            log.error("hosted sync error for '{}'", rsn, e);
            return SyncResult.failed(false);
        }
    }

    /** Outcome of a sync call, including the server-reported pairing state. */
    static final class SyncResult
    {
        private final boolean success;
        private final boolean claimed;
        private final boolean authFailed;

        private SyncResult(boolean success, boolean claimed, boolean authFailed)
        {
            this.success = success;
            this.claimed = claimed;
            this.authFailed = authFailed;
        }

        static SyncResult ok(boolean claimed)
        {
            return new SyncResult(true, claimed, false);
        }

        static SyncResult failed(boolean authFailed)
        {
            return new SyncResult(false, false, authFailed);
        }

        boolean isSuccess()
        {
            return success;
        }

        /** True when the server confirmed this character is linked to a website account. */
        boolean isClaimed()
        {
            return claimed;
        }

        /** True when the request was rejected because the sync token is invalid/stale. */
        boolean isAuthFailed()
        {
            return authFailed;
        }
    }

    private void addPluginClientId(Request.Builder builder)
    {
        String clientId = config.pluginClientId();
        if (clientId == null || clientId.isEmpty())
        {
            clientId = JournalConstants.DEFAULT_PLUGIN_CLIENT_ID;
        }
        builder.header("X-Plugin-Client-Id", clientId);
    }

    /**
     * Request body for {@code /sync}, serialized by Gson — field names match the
     * Edge Function's expected JSON keys exactly (hence snake_case).
     * {@code replace_*} flags make equipment/bank a full delete-then-insert so
     * removed items disappear; skills/quests are plain upserts.
     */
    static class SyncPayload
    {
        private String rsn;
        private List<Map<String, Object>> players;
        private List<Map<String, Object>> player_skills;
        private List<Map<String, Object>> player_quests;
        private List<Map<String, Object>> player_equipment;
        private List<Map<String, Object>> player_bank;
        private List<Map<String, Object>> player_diaries;
        private List<Map<String, Object>> player_combat_achievements;
        private boolean replace_equipment;
        private boolean replace_bank;
        private boolean touch_last_synced = true;
        private Boolean profile_public;

        void setRsn(String rsn)
        {
            this.rsn = rsn;
        }

        SyncPayload players(List<Map<String, Object>> records)
        {
            this.players = records;
            return this;
        }

        SyncPayload skills(List<Map<String, Object>> records)
        {
            this.player_skills = records;
            return this;
        }

        SyncPayload quests(List<Map<String, Object>> records)
        {
            this.player_quests = records;
            return this;
        }

        SyncPayload equipment(List<Map<String, Object>> records, boolean replace)
        {
            this.player_equipment = records;
            this.replace_equipment = replace;
            return this;
        }

        SyncPayload bank(List<Map<String, Object>> records, boolean replace)
        {
            this.player_bank = records;
            this.replace_bank = replace;
            return this;
        }

        SyncPayload diaries(List<Map<String, Object>> records)
        {
            this.player_diaries = records;
            return this;
        }

        SyncPayload combatAchievements(List<Map<String, Object>> records)
        {
            this.player_combat_achievements = records;
            return this;
        }

        SyncPayload touchLastSynced(boolean touch)
        {
            this.touch_last_synced = touch;
            return this;
        }

        SyncPayload profilePublic(boolean isPublic)
        {
            this.profile_public = isPublic;
            return this;
        }
    }
}
