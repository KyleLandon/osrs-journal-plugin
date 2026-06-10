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
 * Writes game data through hosted Supabase Edge Functions (multi-user mode).
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

    boolean isConfigured()
    {
        return config.hostedMode();
    }

    String resolveApiBase()
    {
        return JournalConstants.resolveApiBase(config.apiBaseUrl());
    }

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

    boolean sync(String rsn, String syncToken, SyncPayload payload)
    {
        String base = resolveApiBase();
        if (syncToken == null || syncToken.isEmpty())
        {
            return false;
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
                return false;
            }
            log.debug("hosted sync ok for '{}'", rsn);
            return true;
        }
        catch (IOException e)
        {
            log.error("hosted sync error for '{}'", rsn, e);
            return false;
        }
    }

    private void addPluginClientId(Request.Builder builder)
    {
        String clientId = config.pluginClientId();
        if (clientId != null && !clientId.isEmpty())
        {
            builder.header("X-Plugin-Client-Id", clientId);
        }
    }

    static class SyncPayload
    {
        private String rsn;
        private List<Map<String, Object>> players;
        private List<Map<String, Object>> player_skills;
        private List<Map<String, Object>> player_quests;
        private List<Map<String, Object>> player_equipment;
        private List<Map<String, Object>> player_bank;
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
