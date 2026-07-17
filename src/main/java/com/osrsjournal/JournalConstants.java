package com.osrsjournal;

/**
 * Built-in production endpoints — Plugin Hub users never configure these.
 *
 * <p>Deliberately contains no database keys: the plugin talks only to Edge
 * Functions, which authenticate each request with the per-character sync token.
 * These URLs are public knowledge (they're visible in the website's own config),
 * so shipping them in the jar exposes nothing.
 */
public final class JournalConstants
{
    public static final String WEB_APP_URL = "https://journal.osrsjournal.com";
    public static final String PRIVACY_URL = WEB_APP_URL + "/privacy.html";

    public static final String SUPABASE_PROJECT_URL =
        "https://ahutsqmyahyxmrocrmwd.supabase.co";

    public static final String API_BASE_URL =
        SUPABASE_PROJECT_URL + "/functions/v1";

    private JournalConstants()
    {
    }

    /**
     * Returns the self-hosted override if the user set one, else production.
     * Accepts a Supabase REST URL by mistake ({@code .../rest/v1}) and rewrites
     * it to the functions endpoint, since that's the most common copy-paste error.
     */
    static String resolveApiBase(String configured)
    {
        if (configured != null && !configured.trim().isEmpty())
        {
            return normalizeApiBase(configured.trim());
        }
        return API_BASE_URL;
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
