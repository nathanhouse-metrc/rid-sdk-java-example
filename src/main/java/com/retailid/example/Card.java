package com.retailid.example;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** A white panel with a title, an optional hint, a grid of labelled inputs and a row of buttons. */
public class Card extends Ui.RoundedPanel {

    /** How a button looks. */
    public enum Kind { PRIMARY, SECONDARY, DANGER }

    private final JPanel form = new JPanel(new GridBagLayout());
    private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private int row;

    public Card(String title, String hint) {
        super(new BorderLayout());
        Theme.Fonts fonts = Theme.fonts();

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(fonts.heading);
        titleLabel.setForeground(Theme.PRIMARY);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        header.add(titleLabel);
        if (hint != null && !hint.isEmpty()) {
            Ui.WrapText hintText = new Ui.WrapText(hint, fonts.small, Theme.TEXT_MUTED);
            hintText.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
            hintText.setAlignmentX(LEFT_ALIGNMENT);
            header.add(hintText);
        }
        add(header, BorderLayout.NORTH);

        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        add(form, BorderLayout.CENTER);

        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        add(buttons, BorderLayout.SOUTH);
    }

    /** Adds a labelled input, with an optional hint underneath it. */
    public <T extends JComponent> T addRow(String label, T widget, String hint) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = 0;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(4, 0, 4, 12);
        JLabel labelComponent = new JLabel(label);
        labelComponent.setFont(Theme.fonts().body);
        labelComponent.setForeground(Theme.TEXT);
        // Line the label up with the text inside a single-line input.
        labelComponent.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        form.add(labelComponent, c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);
        form.add(widget, c);
        row++;
        if (hint != null && !hint.isEmpty()) {
            c.gridy = row;
            c.insets = new Insets(0, 0, 4, 0);
            form.add(new Ui.WrapText(hint, Theme.fonts().small, Theme.TEXT_MUTED), c);
            row++;
        }
        return widget;
    }

    public <T extends JComponent> T addRow(String label, T widget) {
        return addRow(label, widget, null);
    }

    /** Adds a component across both columns of the form. */
    public <T extends JComponent> T addWide(T widget) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row++;
        c.gridx = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 4, 0);
        form.add(widget, c);
        return widget;
    }

    public JButton addButton(String text, Runnable action, Kind kind) {
        JButton button = new JButton(text);
        switch (kind) {
            case PRIMARY:
                Theme.primary(button);
                break;
            case DANGER:
                Theme.danger(button);
                break;
            default:
                Theme.secondary(button);
        }
        button.addActionListener(e -> action.run());
        if (buttons.getComponentCount() > 0) {
            buttons.add(javax.swing.Box.createHorizontalStrut(8));
        }
        buttons.add(button);
        return button;
    }

    /** For cards that only hold inputs. */
    public void removeButtonRow() {
        remove(buttons);
    }

    @Override
    public Dimension getMaximumSize() {
        // Stay at the natural height inside a vertical stack.
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
