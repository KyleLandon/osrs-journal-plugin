package com.osrsjournal;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import java.io.IOException;
import java.time.Instant;
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
 * Handles all HTTP communication with the Supabase REST API.
 *
 * <p><strong>Threading:</strong> Every public method in this class performs network I/O
 * and MUST NOT be called on the RuneLite client thread. Call them via the injected
 * {@code ScheduledExecutorService} or {@code executor.execute(...)}.</p>
 */
@Slf4j
@Singleton
public class SupabaseService
{
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    /**
     * RuneLite provides a shared OkHttpClient — we must use it rather than constructing
     * our own, to respect the client's connection pool and TLS settings.
     */
    @Inject
    private OkHttpClient httpClient;

    /**
     * RuneLite provides a shared Gson instance configured for its needs.
     */
    @Inject
    private Gson gson;

    @Inject
    private OsrsJournalConfig config;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Upserts (insert-or-update) a list of records into a Supabase table.
     * Uses {@code Prefer: resolution=merge-duplicates} so duplicate primary keys
     * are updated rather than rejected.
     *
     * @param table   the Supabase table name
     * @param records list of rows as key-value maps
     * @return true if the request succeeded
     */
    public boolean upsert(String table, List<Map<String, Object>> records)
    {
        if (records.isEmpty())
        {
            return true;
        }

        String baseUrl = config.supabaseUrl();
        String apiKey = config.supabaseAnonKey();

        if (!isConfigured(baseUrl, apiKey))
        {
            return false;
        }

        String json = gson.toJson(records);
        RequestBody body = RequestBody.create(JSON_MEDIA, json);

        Request request = new Request.Builder()
            .url(baseUrl + "/rest/v1/" + table)
            .post(body)
            .header("apikey", apiKey)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            // merge-duplicates: update the row on primary key conflict
            .header("Prefer", "resolution=merge-duplicates")
            .build();

        return execute(request, "upsert", table, records.size());
    }

    /**
     * Deletes all rows in {@code table} where the {@code rsn} column matches.
     * Used to clear stale bank data before a full re-sync.
     *
     * @param table the Supabase table name
     * @param rsn   the player's RSN (primary key component)
     * @return true if the request succeeded
     */
    public boolean deleteByRsn(String table, String rsn)
    {
        String baseUrl = config.supabaseUrl();
        String apiKey = config.supabaseAnonKey();

        if (!isConfigured(baseUrl, apiKey))
        {
            return false;
        }

        Request request = new Request.Builder()
            .url(baseUrl + "/rest/v1/" + table + "?" + rsnFilter(rsn))
            .delete()
            .header("apikey", apiKey)
            .header("Authorization", "Bearer " + apiKey)
            .build();

        return execute(request, "delete", table, -1);
    }

    /**
     * Updates {@code last_synced} on the player row without touching other columns.
     * Called after incremental syncs (skills, quests, equipment, bank).
     */
    public boolean patchLastSynced(String rsn)
    {
        String baseUrl = config.supabaseUrl();
        String apiKey = config.supabaseAnonKey();

        if (!isConfigured(baseUrl, apiKey))
        {
            return false;
        }

        String json = gson.toJson(ImmutableMap.of("last_synced", Instant.now().toString()));
        RequestBody body = RequestBody.create(JSON_MEDIA, json);

        Request request = new Request.Builder()
            .url(baseUrl + "/rest/v1/players?" + rsnFilter(rsn))
            .patch(body)
            .header("apikey", apiKey)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .build();

        return execute(request, "patch", "players", 1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** PostgREST equality filter for the {@code rsn} column (handles spaces in names). */
    private static String rsnFilter(String rsn)
    {
        if (rsn.matches("^[\\w]+$"))
        {
            return "rsn=eq." + rsn;
        }
        return "rsn=eq.\"" + rsn.replace("\"", "\\\"") + "\"";
    }

    private boolean isConfigured(String url, String key)
    {
        if (url == null || url.isEmpty() || key == null || key.isEmpty())
        {
            log.debug("Supabase credentials not configured — skipping sync");
            return false;
        }
        return true;
    }

    private boolean execute(Request request, String op, String table, int count)
    {
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                log.warn("Supabase {} failed on '{}': HTTP {} {}",
                    op, table, response.code(), response.message());
                return false;
            }
            if (count >= 0)
            {
                log.debug("Supabase {}: {} rows → {}", op, count, table);
            }
            else
            {
                log.debug("Supabase {}: {}", op, table);
            }
            return true;
        }
        catch (IOException e)
        {
            log.error("Supabase {} error on '{}'", op, table, e);
            return false;
        }
    }
}
