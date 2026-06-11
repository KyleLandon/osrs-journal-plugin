package com.osrsjournal;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.LinkBrowser;

/**
 * Opens the hosted web journal, with a short-lived live session when the
 * character has a sync token.
 */
@Slf4j
@Singleton
class JournalBrowser
{
    @Inject
    private JournalSyncService journalSyncService;

    @Inject
    private HostedApiService hostedApiService;

    @Inject
    JournalBrowser()
    {
    }

    /**
     * Opens the journal in the browser. Performs network I/O (localhost-session
     * exchange) — must be called off the client/Swing threads.
     *
     * @return a short status message describing what happened, for the sidebar.
     */
    String openInBrowser(String rsn)
    {
        String sessionToken = null;
        boolean sessionFailed = false;
        if (rsn != null)
        {
            String syncToken = journalSyncService.getSyncToken(rsn);
            if (syncToken != null)
            {
                sessionToken = hostedApiService.createLocalhostSession(syncToken);
                sessionFailed = sessionToken == null || sessionToken.isEmpty();
            }
        }

        String url = JournalConstants.WEB_APP_URL;
        if (sessionToken != null && !sessionToken.isEmpty())
        {
            url += "?local_session=" + sessionToken;
        }
        else if (rsn != null && !rsn.isEmpty())
        {
            // Fallback: at least load this character's profile on the site.
            url += "?rsn=" + urlEncode(rsn);
        }
        LinkBrowser.browse(url);

        if (sessionFailed)
        {
            log.warn("OSRS Journal: live session unavailable for '{}' — opened profile view instead", rsn);
            return "Couldn't start a live session — opened your profile instead.";
        }
        return "Journal opened in your browser.";
    }

    private static String urlEncode(String value)
    {
        try
        {
            return URLEncoder.encode(value, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            return value;
        }
    }
}
