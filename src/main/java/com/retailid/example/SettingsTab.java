package com.retailid.example;

import com.retailid.api.RetailIdClient;
import com.retailid.api.RetailIdConfig;
import com.retailid.api.RetailIdConfigException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Enter, save and check the API settings used by the API tabs. */
public class SettingsTab extends JPanel {

    private static final Map<String, String[]> LABELS = Map.of(
        "vendor_api_key", new String[] {"Vendor API key", "Your software's key (Basic auth username)"},
        "user_api_key", new String[] {"User API key", "The Metrc user's key (Basic auth password)"},
        "state_code", new String[] {"State code", "Two-letter market, e.g. md → https://api-md.metrc.com"},
        "base_url", new String[] {"Base URL", "Optional; overrides the state code (use for the Sandbox)"});

    public final Map<String, JTextField> fields = new LinkedHashMap<>();
    public final JCheckBox showKeys = new JCheckBox("Show keys");
    public final ResultView results = new ResultView();

    private final App app;
    private final List<JPasswordField> keyFields = new ArrayList<>();
    private final JLabel dirtyLabel = new JLabel(" ");
    private final JButton testButton;
    private char echoChar;

    public SettingsTab(App app) {
        this.app = app;
        Theme.Fonts fonts = Theme.fonts();
        Card card = new Card("Retail ID API settings",
            "Saved to " + Settings.displayPath() + " — this file is git-ignored. API tabs use the saved values.");

        DocumentListener dirty = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateDirty();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateDirty();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateDirty();
            }
        };
        for (String key : Settings.FIELDS) {
            JTextField field;
            if (key.endsWith("api_key")) {
                JPasswordField password = new JPasswordField();
                echoChar = password.getEchoChar();
                keyFields.add(password);
                field = password;
            } else {
                field = new JTextField();
            }
            field.setFont(fonts.mono);
            field.setText(app.saved.getOrDefault(key, ""));
            field.getDocument().addDocumentListener(dirty);
            String[] label = LABELS.get(key);
            card.addRow(label[0], field, label[1]);
            fields.put(key, field);
        }

        showKeys.setOpaque(false);
        showKeys.addActionListener(e -> toggleKeys());
        card.addRow("", showKeys);
        dirtyLabel.setFont(fonts.small);
        dirtyLabel.setForeground(Theme.WARNING);
        card.addRow("", dirtyLabel);

        card.addButton("Save", this::save, Card.Kind.PRIMARY);
        card.addButton("Revert", this::revert, Card.Kind.SECONDARY);
        testButton = card.addButton("Test connection", this::testConnection, Card.Kind.SECONDARY);

        Ui.Column column = new Ui.Column();
        column.addCard(card);
        Ui.twoColumn(this, Ui.scrollColumn(column), results);
        showSummary(app.saved.values().stream().anyMatch(v -> !v.isEmpty())
            ? "Loaded settings" : "No settings saved yet");
    }

    /** The values in the form, trimmed. */
    public Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        fields.forEach((key, field) -> values.put(key, field.getText().trim()));
        return values;
    }

    public void setValue(String key, String value) {
        fields.get(key).setText(value);
    }

    private void toggleKeys() {
        for (JPasswordField field : keyFields) {
            field.setEchoChar(showKeys.isSelected() ? (char) 0 : echoChar);
        }
    }

    private void updateDirty() {
        Map<String, String> values = values();
        boolean dirty = Settings.FIELDS.stream()
            .anyMatch(k -> !values.get(k).equals(app.saved.getOrDefault(k, "")));
        dirtyLabel.setText(dirty ? "Unsaved changes" : " ");
    }

    private static String mask(String key) {
        if (key == null || key.isEmpty()) {
            return "(not set)";
        }
        return key.length() > 8 ? "•".repeat(8) + key.substring(key.length() - 4) : "•".repeat(key.length());
    }

    static String target(RetailIdConfig config) {
        if (config.getBaseUrl() != null) {
            return config.getBaseUrl().replaceAll("/+$", "");
        }
        return "https://api-" + config.getStateCode().trim().toLowerCase() + ".metrc.com";
    }

    public void showSummary(String title) {
        Map<String, String> saved = app.saved;
        String target;
        String state;
        try {
            target = target(Settings.toConfig(saved));
            state = "Ready: API tabs will use these settings.";
        } catch (RetailIdConfigException e) {
            target = "(incomplete)";
            state = "Incomplete: " + e.getMessage();
        }
        results.showFields(title, List.of(
            Map.entry("Vendor API key", mask(saved.get("vendor_api_key"))),
            Map.entry("User API key", mask(saved.get("user_api_key"))),
            Map.entry("State code", orNotSet(saved.get("state_code"))),
            Map.entry("Base URL", orNotSet(saved.get("base_url"))),
            Map.entry("Requests go to", target),
            Map.entry("Status", state)));
    }

    private static String orNotSet(String value) {
        return value == null || value.isEmpty() ? "(not set)" : value;
    }

    public void save() {
        Map<String, String> values = new LinkedHashMap<>(app.saved);
        values.putAll(values());
        values.put("state_code", values.get("state_code").toLowerCase());
        setValue("state_code", values.get("state_code"));
        Path path = Settings.save(values);
        app.saved = Settings.load(path);
        updateDirty();
        showSummary("Settings saved");
        app.setStatus("Settings saved to " + Settings.displayPath(), true);
    }

    public void revert() {
        fields.forEach((key, field) -> field.setText(app.saved.getOrDefault(key, "")));
        showSummary("Reverted to saved settings");
    }

    public void testConnection() {
        RetailIdConfig config;
        try {
            config = app.savedConfig();
        } catch (RetailIdConfigException e) {
            results.showError(Errors.describe(e));
            return;
        }
        testButton.setEnabled(false);
        results.showPending("GET " + target(config) + "/facilities/v2/");
        app.runTask(() -> {
            try (RetailIdClient client = RetailIdClient.fromConfig(config)) {
                return client.getFacilities();
            }
        }, (result, error) -> {
            testButton.setEnabled(true);
            if (error != null) {
                results.showError(Errors.describe(error));
                app.setStatus("Connection test failed", false);
                return;
            }
            List<?> facilities = (List<?>) result;
            StringBuilder names = new StringBuilder();
            for (Object facility : facilities) {
                if (names.length() > 0) {
                    names.append('\n');
                }
                names.append(describeFacility(facility));
            }
            results.showSuccess("Connected — " + facilities.size() + " facility(ies) visible",
                names.toString(), null);
            app.setStatus("Connection test succeeded", true);
        });
    }

    private static String describeFacility(Object facility) {
        if (!(facility instanceof Map)) {
            return String.valueOf(facility);
        }
        Map<?, ?> map = (Map<?, ?>) facility;
        Object license = map.get("License");
        Object number = license instanceof Map ? ((Map<?, ?>) license).get("Number") : null;
        Object name = map.get("Name");
        return (number == null ? "?" : number) + "  " + (name == null ? "" : name);
    }
}
