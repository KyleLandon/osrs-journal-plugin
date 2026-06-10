package com.osrsjournal;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

class OsrsJournalPanel extends PluginPanel
{
    private final JEditorPane summaryPane = new JEditorPane();
    private final JLabel statusLabel = new JLabel("Log in to view your journal.");
    private final JournalWebServer webServer;
    private final ScheduledExecutorService executor;
    private JButton openFullButton;

    @Inject
    OsrsJournalPanel(JournalWebServer webServer, ScheduledExecutorService executor)
    {
        super();
        this.webServer = webServer;
        this.executor = executor;
    }

    void init()
    {
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel heading = new JLabel("OSRS Journal");
        heading.setFont(FontManager.getRunescapeSmallFont());
        heading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        statusLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

        summaryPane.setEditable(false);
        summaryPane.setContentType("text/html");
        summaryPane.addHyperlinkListener(e ->
        {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null)
            {
                net.runelite.client.util.LinkBrowser.browse(e.getURL().toString());
            }
        });
        summaryPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        summaryPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        summaryPane.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        summaryPane.setPreferredSize(new Dimension(PANEL_WIDTH - 12, 280));
        summaryPane.setText(buildHtml(null));

        openFullButton = new JButton("Open full journal");
        openFullButton.addActionListener(this::openFullJournal);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> requestRefresh());

        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 6));
        buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttons.add(openFullButton);
        buttons.add(refresh);

        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.setBackground(ColorScheme.DARK_GRAY_COLOR);
        north.add(heading, BorderLayout.NORTH);
        north.add(statusLabel, BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);
        add(summaryPane, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    void updateSummary(JournalSnapshot snapshot)
    {
        if (snapshot == null)
        {
            statusLabel.setText("Log in to view your journal.");
            summaryPane.setText(buildHtml(null));
            return;
        }

        statusLabel.setText(snapshot.getStatusText());
        summaryPane.setText(buildHtml(snapshot));
    }

    private String currentRsn;

    void setCurrentRsn(String rsn)
    {
        this.currentRsn = rsn;
    }

    private void openFullJournal(ActionEvent e)
    {
        final String rsn = currentRsn;
        openFullButton.setEnabled(false);
        statusLabel.setText("Opening journal...");
        executor.execute(() ->
        {
            String status = webServer.openInBrowser(rsn);
            SwingUtilities.invokeLater(() ->
            {
                openFullButton.setEnabled(true);
                statusLabel.setText(status);
            });
        });
    }

    private void requestRefresh()
    {
        // Plugin listens via panel callback set at startup
        if (refreshListener != null)
        {
            refreshListener.run();
        }
    }

    private Runnable refreshListener;

    void setRefreshListener(Runnable refreshListener)
    {
        this.refreshListener = refreshListener;
    }

    private static String buildHtml(JournalSnapshot s)
    {
        if (s == null)
        {
            return htmlWrap(
                "<p style='color:#94a3b8'>Your stats, quests, and sync status appear here while logged in.</p>"
                    + "<p style='color:#64748b'>Use <b>Open full journal</b> for the complete planner UI in your browser.</p>"
            );
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<p style='color:#f1f5f9;font-size:13px'><b>").append(escape(s.getRsn())).append("</b></p>");
        sb.append("<p style='color:#94a3b8'>Combat ").append(s.getCombatLevel())
            .append(" · QP ").append(s.getQuestPoints())
            .append(" · Total ").append(s.getTotalLevel()).append("</p>");
        sb.append("<hr/>");
        sb.append("<p style='color:#94a3b8;margin-bottom:4px'><b>Skills</b></p>");
        sb.append("<table width='100%' cellpadding='2' cellspacing='0'>");
        for (JournalSnapshot.SkillRow row : s.getSkills())
        {
            sb.append("<tr><td style='color:#cbd5e1'>").append(escape(row.getLabel()))
                .append("</td><td align='right' style='color:#f1f5f9'>")
                .append(row.getLevel()).append("</td></tr>");
        }
        sb.append("</table>");
        sb.append("<hr/>");
        sb.append("<p style='color:#94a3b8'>Quests finished: <span style='color:#f1f5f9'>")
            .append(s.getQuestsFinished()).append("</span></p>");
        if (!s.getRecentQuests().isEmpty())
        {
            sb.append("<p style='color:#64748b;margin-bottom:4px'>In progress / recent:</p><ul style='margin-top:0;padding-left:16px'>");
            for (String quest : s.getRecentQuests())
            {
                sb.append("<li style='color:#cbd5e1;font-size:11px'>").append(escape(quest)).append("</li>");
            }
            sb.append("</ul>");
        }
        if (s.isHostedMode() && s.getPairCode() != null && !s.getPairCode().isEmpty() && !s.isAccountLinked())
        {
            sb.append("<hr/>");
            sb.append("<p style='color:#94a3b8;margin-bottom:4px'><b>Link account</b></p>");
            sb.append("<p style='color:#f1f5f9;font-size:18px;letter-spacing:2px'><b>")
                .append(escape(s.getPairCode())).append("</b></p>");
            sb.append("<p style='color:#64748b;font-size:11px'>1. Sign in at "
                + "<a href='" + JournalConstants.WEB_APP_URL + "' style='color:#60a5fa'>journal.osrsjournal.com</a><br/>"
                + "2. Enter this code under <b>Link character</b><br/>"
                + "Code expires in ~10 minutes.</p>");
        }
        else if (s.isHostedMode() && s.isAccountLinked())
        {
            sb.append("<p style='color:#22c55e;font-size:11px'>✓ Account linked — journal syncs to the cloud.</p>");
        }
        sb.append("<p style='color:#64748b;font-size:11px'>Full quest reqs, gear, and export live in the browser journal.</p>");
        return htmlWrap(sb.toString());
    }

    private static String htmlWrap(String body)
    {
        return "<html><body style='font-family:sans-serif;background:#171b26;margin:0;padding:0'>"
            + body + "</body></html>";
    }

    private static String escape(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
