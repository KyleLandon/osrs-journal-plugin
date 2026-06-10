package com.osrsjournal;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

@Singleton
class SyncTokenStore
{
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    @Inject
    private ConfigManager configManager;

    @Inject
    private Gson gson;

    String getToken(String rsn)
    {
        Map<String, String> tokens = load();
        return tokens.get(rsn);
    }

    void saveToken(String rsn, String syncToken)
    {
        Map<String, String> tokens = load();
        tokens.put(rsn, syncToken);
        persist(tokens);
    }

    private Map<String, String> load()
    {
        String json = configManager.getConfiguration("osrsjournal", "syncTokensJson");
        if (json == null || json.isEmpty())
        {
            return new HashMap<>();
        }
        Map<String, String> parsed = gson.fromJson(json, MAP_TYPE);
        return parsed != null ? parsed : new HashMap<>();
    }

    private void persist(Map<String, String> tokens)
    {
        configManager.setConfiguration("osrsjournal", "syncTokensJson", gson.toJson(tokens));
    }
}
