package com.osrsjournal;

/**
 * Built-in production endpoints — Plugin Hub users never configure these.
 * Update before public release if the backend project changes.
 */
public final class JournalConstants
{
    public static final String WEB_APP_URL = "https://journal.osrsjournal.com";

    public static final String SUPABASE_PROJECT_URL =
        "https://ahutsqmyahyxmrocrmwd.supabase.co";

    public static final String API_BASE_URL =
        SUPABASE_PROJECT_URL + "/functions/v1";

    /** Public anon key — safe to ship in the plugin; RLS enforces access. */
    public static final String SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFodXRzcW15YWh5eG1yb2NybXdkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODEwMzE0MDcsImV4cCI6MjA5NjYwNzQwN30.DqawvPa6wpdHhRR4NQa_paioIUQFYXhcNyPWd7j7tto";

    static String resolveAnonKey(String configured)
    {
        if (configured != null && !configured.trim().isEmpty())
        {
            return configured.trim();
        }
        return SUPABASE_ANON_KEY;
    }

    private JournalConstants()
    {
    }

    static String resolveApiBase(String configured)
    {
        if (configured != null && !configured.trim().isEmpty())
        {
            return normalizeApiBase(configured.trim());
        }
        return API_BASE_URL;
    }

    static String resolveProjectUrl(String configuredHosted, String configuredSelfHosted, boolean hostedMode)
    {
        if (hostedMode)
        {
            String base = resolveApiBase(configuredHosted);
            if (base.endsWith("/functions/v1"))
            {
                return base.substring(0, base.length() - "/functions/v1".length());
            }
            return SUPABASE_PROJECT_URL;
        }
        if (configuredSelfHosted != null && !configuredSelfHosted.trim().isEmpty())
        {
            return configuredSelfHosted.trim();
        }
        return SUPABASE_PROJECT_URL;
    }

    private static String normalizeApiBase(String url)
    {
        String trimmed = url;
        while (trimmed.endsWith("/"))
        {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/rest/v1"))
        {
            return trimmed.substring(0, trimmed.length() - "/rest/v1".length()) + "/functions/v1";
        }
        return trimmed;
    }
}
