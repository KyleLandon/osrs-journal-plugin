package com.osrsjournal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.LinkBrowser;

@Slf4j
@Singleton
public class JournalWebServer
{
    private static final String RESOURCE_ROOT = "/osrsjournal/web";
    private static final Map<String, String> CONTENT_TYPES = new HashMap<>();

    static
    {
        CONTENT_TYPES.put("html", "text/html; charset=utf-8");
        CONTENT_TYPES.put("htm", "text/html; charset=utf-8");
        CONTENT_TYPES.put("json", "application/json; charset=utf-8");
        CONTENT_TYPES.put("png", "image/png");
        CONTENT_TYPES.put("css", "text/css; charset=utf-8");
        CONTENT_TYPES.put("js", "application/javascript; charset=utf-8");
    }

    private HttpServer server;
    private int port = -1;

    @Inject
    private OsrsJournalConfig config;

    @Inject
    private JournalSyncService journalSyncService;

    @Inject
    private HostedApiService hostedApiService;

    @Inject
    JournalWebServer()
    {
    }

    synchronized String ensureRunning()
    {
        if (server != null)
        {
            return baseUrl(null);
        }

        try
        {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", new ResourceHandler());
            server.setExecutor(null);
            server.start();
            port = server.getAddress().getPort();
            log.info("OSRS Journal webapp serving at {}", baseUrl(null));
            return baseUrl(null);
        }
        catch (IOException e)
        {
            log.error("Failed to start journal web server", e);
            return null;
        }
    }

    synchronized void stop()
    {
        if (server != null)
        {
            server.stop(0);
            server = null;
            port = -1;
        }
    }

    String baseUrl(String sessionToken)
    {
        if (port <= 0)
        {
            return null;
        }
        String url = "http://127.0.0.1:" + port + "/osrs-journal.html";
        if (sessionToken != null && !sessionToken.isEmpty())
        {
            url += "?local_session=" + sessionToken;
        }
        return url;
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
        if (journalSyncService.isHostedMode() && rsn != null)
        {
            String syncToken = journalSyncService.getSyncToken(rsn);
            if (syncToken != null)
            {
                sessionToken = hostedApiService.createLocalhostSession(syncToken);
                sessionFailed = sessionToken == null || sessionToken.isEmpty();
            }
        }

        if (config.hostedMode())
        {
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

        if (JournalWebServer.class.getResource(RESOURCE_ROOT + "/osrs-journal.html") == null)
        {
            // Slim (Plugin Hub) build without the bundled webapp — self-hosted
            // mode needs a jar built with: gradlew jar -PbundleWebapp
            log.warn("OSRS Journal: bundled webapp missing from this build — opening hosted site instead");
            LinkBrowser.browse(JournalConstants.WEB_APP_URL);
            return "This build has no local journal — opened the hosted site instead.";
        }

        String url = ensureRunning();
        if (url == null)
        {
            return "Couldn't start the local journal server — check the logs.";
        }

        if (sessionToken != null)
        {
            url = baseUrl(sessionToken);
        }

        LinkBrowser.browse(url);
        return "Journal opened in your browser.";
    }

    private static String urlEncode(String value)
    {
        try
        {
            return java.net.URLEncoder.encode(value, "UTF-8");
        }
        catch (java.io.UnsupportedEncodingException e)
        {
            return value;
        }
    }

    private class ResourceHandler implements HttpHandler
    {
        @Override
        public void handle(HttpExchange exchange) throws IOException
        {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.isEmpty() || "/".equals(path))
            {
                path = "/osrs-journal.html";
            }

            if ("/supabase-config.json".equals(path))
            {
                serveSupabaseConfig(exchange);
                return;
            }

            String resourcePath = RESOURCE_ROOT + path;
            InputStream stream = JournalWebServer.class.getResourceAsStream(resourcePath);
            if (stream == null)
            {
                byte[] msg = ("Not found: " + path).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, msg.length);
                try (OutputStream os = exchange.getResponseBody())
                {
                    os.write(msg);
                }
                return;
            }

            byte[] body = stream.readAllBytes();
            stream.close();

            String ext = extension(path);
            String contentType = CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody())
            {
                os.write(body);
            }
        }
    }

    private void serveSupabaseConfig(HttpExchange exchange) throws IOException
    {
        String projectUrl;
        String anonKey;
        String apiBase;
        boolean hosted;

        if (config.hostedMode())
        {
            hosted = true;
            apiBase = JournalConstants.resolveApiBase(config.apiBaseUrl());
            projectUrl = JournalConstants.resolveProjectUrl(
                config.apiBaseUrl(), config.supabaseUrl(), true);
            anonKey = JournalConstants.resolveAnonKey(config.hostedAnonKey());
        }
        else
        {
            hosted = false;
            apiBase = "";
            projectUrl = config.supabaseUrl();
            anonKey = config.supabaseAnonKey();
        }

        String json = "{"
            + "\"url\":\"" + escapeJson(projectUrl) + "\","
            + "\"anonKey\":\"" + escapeJson(anonKey) + "\","
            + "\"apiBaseUrl\":\"" + escapeJson(apiBase) + "\","
            + "\"webAppUrl\":\"" + escapeJson(JournalConstants.WEB_APP_URL) + "\","
            + "\"hostedMode\":" + hosted
            + "}";

        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody())
        {
            os.write(body);
        }
    }

    private static String extension(String path)
    {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1).toLowerCase() : "";
    }

    private static String escapeJson(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }
}
