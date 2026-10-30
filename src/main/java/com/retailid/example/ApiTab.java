package com.retailid.example;

import com.retailid.api.RetailIdClient;
import com.retailid.api.RetailIdConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** One API tab: a card per {@link ApiActions.Action}, and a results panel. */
public class ApiTab extends JPanel {

    public final ResultView results = new ResultView();

    private final App app;
    private final Map<String, ApiActions.Action> actions = new LinkedHashMap<>();
    private final Map<String, Map<String, Supplier<String>>> getters = new LinkedHashMap<>();
    private final Map<String, Map<String, Consumer<String>>> setters = new LinkedHashMap<>();
    private final Map<String, JButton> buttons = new LinkedHashMap<>();

    public ApiTab(App app, List<ApiActions.Action> tabActions) {
        this.app = app;
        Theme.Fonts fonts = Theme.fonts();
        Ui.Column column = new Ui.Column();

        if (tabActions.stream().anyMatch(a -> a.needsLicense)) {
            Card license = new Card("Facility",
                "The license number is sent as ?licenseNumber= on each call. It is shared by every "
                    + "API tab and remembered between runs.");
            // Every tab's field edits the same document, so they always show the same number.
            JTextField field = new JTextField(app.licenseNumber, null, 0);
            field.setFont(fonts.mono);
            license.addRow("License number", field);
            license.removeButtonRow();
            column.addCard(license);
        }
        for (ApiActions.Action action : tabActions) {
            column.addCard(buildCard(action));
        }
        Ui.twoColumn(this, Ui.scrollColumn(column), results);
    }

    private Card buildCard(ApiActions.Action action) {
        Theme.Fonts fonts = Theme.fonts();
        actions.put(action.key, action);
        Card card = new Card(action.title, action.hint);
        JLabel endpoint = new JLabel(action.endpoint);
        endpoint.setFont(fonts.mono.deriveFont(12f));
        endpoint.setForeground(Theme.TEXT_MUTED);
        card.addWide(endpoint);

        Map<String, Supplier<String>> get = new LinkedHashMap<>();
        Map<String, Consumer<String>> set = new LinkedHashMap<>();
        for (ApiActions.Field f : action.fields) {
            switch (f.kind) {
                case LINES:
                case JSON: {
                    JTextArea area = Ui.textArea(f.kind == ApiActions.Kind.JSON ? 10 : 4);
                    area.setText(f.defaultValue);
                    area.setCaretPosition(0);
                    card.addRow(f.label, Ui.scrolled(area), f.hint);
                    get.put(f.key, area::getText);
                    set.put(f.key, area::setText);
                    break;
                }
                case CHOICE: {
                    JComboBox<String> combo = new JComboBox<>(f.choices.toArray(new String[0]));
                    combo.setSelectedItem(f.defaultValue);
                    card.addRow(f.label, combo, f.hint);
                    get.put(f.key, () -> (String) combo.getSelectedItem());
                    set.put(f.key, combo::setSelectedItem);
                    break;
                }
                default: {
                    JTextField field = new JTextField(f.defaultValue);
                    field.setFont(fonts.mono);
                    card.addRow(f.label, field, f.hint);
                    get.put(f.key, field::getText);
                    set.put(f.key, field::setText);
                }
            }
        }
        getters.put(action.key, get);
        setters.put(action.key, set);
        String label = action.changesData ? "Send (changes data)" : "Send";
        Card.Kind kind = action.changesData ? Card.Kind.DANGER : Card.Kind.PRIMARY;
        buttons.put(action.key, card.addButton(label, () -> invoke(action.key, true), kind));
        return card;
    }

    // -- running ----------------------------------------------------------------------

    public void setInput(String actionKey, String fieldKey, String value) {
        setters.get(actionKey).get(fieldKey).accept(value);
    }

    public void invoke(String actionKey) {
        invoke(actionKey, true);
    }

    /** Runs an action; {@code confirm} asks before calls that change data. */
    public void invoke(String actionKey, boolean confirm) {
        ApiActions.Action action = actions.get(actionKey);
        Map<String, Object> values = new LinkedHashMap<>();
        String licenseNumber;
        RetailIdConfig config;
        try {
            for (ApiActions.Field f : action.fields) {
                values.put(f.key, f.parse(getters.get(action.key).get(f.key).get()));
            }
            licenseNumber = app.getLicenseNumber();
            if (action.needsLicense && licenseNumber.isEmpty()) {
                throw new IllegalArgumentException("Enter a facility license number first");
            }
            config = app.savedConfig();
        } catch (IllegalArgumentException e) {
            results.showError(Errors.describe(e));
            return;
        }

        if (confirm && action.changesData) {
            String target = config.getBaseUrl() != null ? config.getBaseUrl() : config.getStateCode();
            int answer = JOptionPane.showConfirmDialog(this,
                action.title + " changes data in Metrc (" + target + ").\n\nSend it?",
                "Send request?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }

        app.rememberLicense();
        JButton button = buttons.get(action.key);
        button.setEnabled(false);
        results.showPending(action.title + ": " + action.endpoint);
        long started = System.nanoTime();

        app.runTask(() -> {
            try (RetailIdClient client = RetailIdClient.fromConfig(config)) {
                return action.call.run(client, licenseNumber, values);
            }
        }, (result, error) -> {
            button.setEnabled(true);
            double elapsed = (System.nanoTime() - started) / 1e9;
            if (error != null) {
                results.showError(Errors.describe(error));
                app.setStatus(action.title + " failed", false);
                return;
            }
            String count = result instanceof List ? " — " + ((List<?>) result).size() + " record(s)" : "";
            results.showSuccess(action.title + count, JsonText.format(result), elapsed);
            app.setStatus(String.format("%s succeeded in %.2fs", action.title, elapsed), true);
        });
    }
}
