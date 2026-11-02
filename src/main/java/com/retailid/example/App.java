package com.retailid.example;

import com.retailid.api.RetailIdConfig;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

/** The main window's content: header, tabs and status bar. */
public class App extends JPanel {

    /** Work run off the Swing thread. */
    @FunctionalInterface
    public interface Work {
        Object call() throws Exception;
    }

    /** Shared by every API tab's license number field. */
    public final Document licenseNumber = new PlainDocument();
    public final JTabbedPane tabs = new JTabbedPane();
    public final SettingsTab settingsTab;
    public final EncodeTab encodeTab;
    public final DecodeTab decodeTab;
    public final Map<String, ApiTab> apiTabs = new LinkedHashMap<>();

    Map<String, String> saved;
    private final boolean background;
    private final JLabel status = new JLabel("Ready");
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "rid-api-call");
        thread.setDaemon(true);
        return thread;
    });

    /** The application. {@code background = false} runs API calls inline (used by tests). */
    public App(boolean background) {
        super(new BorderLayout());
        this.background = background;
        Theme.install();
        setBackground(Theme.BACKGROUND);

        saved = Settings.load();
        setLicenseNumber(saved.getOrDefault(Settings.LICENSE_FIELD, ""));

        add(buildHeader(), BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        status.setOpaque(true);
        status.setBackground(Theme.SURFACE);
        status.setForeground(Theme.TEXT_MUTED);
        status.setFont(Theme.fonts().small);
        status.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
            BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        add(status, BorderLayout.SOUTH);

        settingsTab = new SettingsTab(this);
        decodeTab = new DecodeTab();
        encodeTab = new EncodeTab(this::sendToDecode);
        tabs.addTab("Settings", settingsTab);
        tabs.addTab("Encode", encodeTab);
        tabs.addTab("Decode", decodeTab);
        ApiActions.TABS.forEach((title, actions) -> {
            ApiTab tab = new ApiTab(this, actions);
            tabs.addTab(title, tab);
            apiTabs.put(title, tab);
        });
        // Start on Encode when settings are already saved, otherwise on Settings.
        if (Settings.FIELDS.stream().anyMatch(k -> !saved.getOrDefault(k, "").isEmpty())) {
            tabs.setSelectedComponent(encodeTab);
        }
    }

    private JComponent buildHeader() {
        Theme.Fonts fonts = Theme.fonts();
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.PRIMARY);
        bar.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(new Monogram());
        left.add(Box.createHorizontalStrut(14));
        JLabel brand = new JLabel("Metrc");
        brand.setFont(fonts.title);
        brand.setForeground(Theme.TEXT_ON_PRIMARY);
        left.add(brand);
        left.add(Box.createHorizontalStrut(10));
        JLabel title = new JLabel("Retail ID SDK Explorer");
        title.setFont(fonts.heading);
        title.setForeground(Theme.TEXT_ON_PRIMARY_SOFT);
        left.add(title);
        bar.add(left, BorderLayout.WEST);

        JLabel version = new JLabel("retailid-sdk " + sdkVersion() + "  ·  Java SDK showcase");
        version.setFont(fonts.small);
        version.setForeground(Theme.TEXT_ON_PRIMARY);
        // Room for the text when it is drawn at another scale (high-DPI screens, screenshots).
        version.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        bar.add(version, BorderLayout.EAST);

        JPanel header = new JPanel(new BorderLayout());
        header.add(bar, BorderLayout.CENTER);
        JPanel rule = new JPanel();
        rule.setBackground(Theme.ACCENT);
        rule.setPreferredSize(new Dimension(1, 3));
        header.add(rule, BorderLayout.SOUTH);
        return header;
    }

    /** White rounded-square "RID" monogram, as in Metrc's prototype headers. */
    private static final class Monogram extends JComponent {
        Monogram() {
            setPreferredSize(new Dimension(34, 34));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(Theme.SURFACE);
            g2.fillRoundRect(1, 1, 32, 32, 14, 14);
            g2.setColor(Theme.MARK_TEXT);
            g2.setFont(Theme.fonts().mark);
            var metrics = g2.getFontMetrics();
            String text = "RID";
            int x = (34 - metrics.stringWidth(text)) / 2;
            int y = (34 - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.drawString(text, x, y);
            g2.dispose();
        }
    }

    static String sdkVersion() {
        Properties properties = new Properties();
        try (InputStream in = App.class.getResourceAsStream("/rid-example.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            // Fall through to "?".
        }
        return properties.getProperty("sdk.version", "?");
    }

    // -- shared services for tabs ------------------------------------------------------

    public RetailIdConfig savedConfig() {
        return Settings.toConfig(saved);
    }

    public String getLicenseNumber() {
        try {
            return licenseNumber.getText(0, licenseNumber.getLength()).trim();
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    public void setLicenseNumber(String value) {
        try {
            licenseNumber.remove(0, licenseNumber.getLength());
            licenseNumber.insertString(0, value, null);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Saves the license number when it has changed, so it is there next time. */
    public void rememberLicense() {
        String value = getLicenseNumber();
        if (!value.equals(saved.getOrDefault(Settings.LICENSE_FIELD, ""))) {
            saved.put(Settings.LICENSE_FIELD, value);
            Settings.save(saved);
        }
    }

    public void setStatus(String text, boolean ok) {
        status.setText(text);
        status.setForeground(ok ? Theme.TEXT_MUTED : Theme.ERROR);
    }

    public void sendToDecode(String url) {
        tabs.setSelectedComponent(decodeTab);
        decodeTab.decodeUrl(url);
    }

    /** Runs {@code work} off the Swing thread, then calls {@code done(result, error)} on it. */
    public void runTask(Work work, BiConsumer<Object, Throwable> done) {
        if (!background) {
            Object result = null;
            Throwable error = null;
            try {
                result = work.call();
            } catch (Exception e) {
                error = e;
            }
            done.accept(result, error);
            return;
        }
        executor.submit(() -> {
            Object result = null;
            Throwable error = null;
            try {
                result = work.call();
            } catch (Exception e) {
                error = e;
            }
            Object finalResult = result;
            Throwable finalError = error;
            SwingUtilities.invokeLater(() -> done.accept(finalResult, finalError));
        });
    }
}
