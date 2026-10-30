package com.retailid.example;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/** Read-only panel showing the outcome of the last action. */
public class ResultView extends Ui.RoundedPanel {

    private final JTextPane text = new JTextPane();
    private final SimpleAttributeSet ok;
    private final SimpleAttributeSet error;
    private final SimpleAttributeSet pending;
    private final SimpleAttributeSet label;
    private final SimpleAttributeSet value;
    private final SimpleAttributeSet url;

    public ResultView() {
        this("Results");
    }

    public ResultView(String title) {
        super(new BorderLayout());
        Theme.Fonts fonts = Theme.fonts();

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(fonts.heading);
        titleLabel.setForeground(Theme.PRIMARY);
        top.add(titleLabel, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        JButton copy = Theme.secondary(new JButton("Copy"));
        copy.addActionListener(e -> copy());
        JButton clear = Theme.secondary(new JButton("Clear"));
        clear.addActionListener(e -> clear());
        buttons.add(copy);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(clear);
        top.add(buttons, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        text.setEditable(false);
        text.setBackground(Theme.SURFACE);
        text.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.setOpaque(false);
        add(scroll, BorderLayout.CENTER);

        // The status marks (✔ ✖ …) are not in every text font; Dialog falls back to one that has them.
        Font status = fonts.bodyBold.canDisplayUpTo("✔✖…") == -1
            ? fonts.bodyBold : new Font(Font.DIALOG, Font.BOLD, fonts.bodyBold.getSize());
        ok = style(status, Theme.SUCCESS);
        error = style(status, Theme.ERROR);
        pending = style(status, Theme.TEXT_MUTED);
        label = style(fonts.body, Theme.TEXT_MUTED);
        value = style(fonts.mono, Theme.TEXT);
        url = style(fonts.resultUrl, Theme.PRIMARY);
    }

    private static SimpleAttributeSet style(Font font, Color color) {
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setFontFamily(set, font.getFamily());
        StyleConstants.setFontSize(set, font.getSize());
        StyleConstants.setBold(set, font.isBold());
        StyleConstants.setForeground(set, color);
        return set;
    }

    // -- writing ----------------------------------------------------------------

    private void write(Object... chunks) {
        StyledDocument doc = text.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
            for (int i = 0; i < chunks.length; i += 2) {
                doc.insertString(doc.getLength(), (String) chunks[i], (SimpleAttributeSet) chunks[i + 1]);
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        text.setCaretPosition(0);
    }

    public void showPending(String title) {
        write("… " + title + "\n", pending);
    }

    public void showSuccess(String title, String body, Double elapsedSeconds) {
        String timing = elapsedSeconds == null ? "" : String.format("  (%.2fs)", elapsedSeconds);
        if (body == null || body.isEmpty()) {
            write("✔ " + title + timing + "\n\n", ok);
        } else {
            write("✔ " + title + timing + "\n\n", ok, body + "\n", value);
        }
    }

    /** Shows labelled values; values whose key is in {@code urlKeys} are shown large. */
    public void showFields(String title, List<Map.Entry<String, String>> fields, Set<String> urlKeys) {
        Object[] chunks = new Object[2 + fields.size() * 4];
        chunks[0] = "✔ " + title + "\n\n";
        chunks[1] = ok;
        int i = 2;
        for (Map.Entry<String, String> field : fields) {
            chunks[i++] = field.getKey() + "\n";
            chunks[i++] = label;
            chunks[i++] = field.getValue() + "\n\n";
            chunks[i++] = urlKeys.contains(field.getKey()) ? url : value;
        }
        write(chunks);
    }

    public void showFields(String title, List<Map.Entry<String, String>> fields) {
        showFields(title, fields, Set.of());
    }

    public void showError(String title, String message, String detail) {
        if (detail == null || detail.isEmpty()) {
            write("✖ " + title + "\n\n", error, message + "\n", value);
        } else {
            write("✖ " + title + "\n\n", error, message + "\n", value, "\n" + detail + "\n", value);
        }
    }

    public void showError(Errors.Description description) {
        showError(description.title, description.message, description.detail);
    }

    public void clear() {
        write();
    }

    /** The shown text, with \n line ends on every platform. */
    public String getText() {
        StyledDocument doc = text.getStyledDocument();
        try {
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    public void copy() {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(getText()), null);
    }
}
