package com.retailid.example;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.text.View;

/** Small layout helpers and building blocks shared by the tabs. */
public final class Ui {

    private Ui() {
    }

    /**
     * Lays out a tab as two columns: inputs on the left (2/5 of the width, scrolling) and the
     * results panel on the right (3/5).
     */
    public static void twoColumn(JPanel tab, JComponent left, JComponent right) {
        tab.setLayout(new GridBagLayout());
        tab.setBackground(Theme.BACKGROUND);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 2;
        c.insets = new Insets(16, 16, 16, 8);
        // Zero preferred widths so the weights alone decide the split.
        left.setPreferredSize(new Dimension(0, 0));
        right.setPreferredSize(new Dimension(0, 0));
        tab.add(left, c);
        c.gridx = 1;
        c.weightx = 3;
        c.insets = new Insets(16, 8, 16, 16);
        tab.add(right, c);
    }

    /** A vertical stack of cards that scrolls when it is taller than the window. */
    public static JScrollPane scrollColumn(Column column) {
        JScrollPane scroll = new JScrollPane(column,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        return scroll;
    }

    /** Monospaced multi-line input. */
    public static JTextArea textArea(int rows) {
        JTextArea area = new JTextArea(rows, 20);
        area.setFont(Theme.fonts().mono);
        area.setTabSize(2);
        return area;
    }

    /** Wraps a multi-line input in a bordered scroll pane. */
    public static JScrollPane scrolled(JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setMinimumSize(new Dimension(0, area.getPreferredSize().height));
        return scroll;
    }

    /** Vertical panel that always matches the scroll viewport's width, so text wraps. */
    public static final class Column extends JPanel implements Scrollable {
        public Column() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(Theme.BACKGROUND);
        }

        /** Adds a card with a gap below it. */
        public void addCard(JComponent card) {
            card.setAlignmentX(LEFT_ALIGNMENT);
            add(card);
            add(Box.createVerticalStrut(12));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visible.height - 24 : visible.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** A white panel with a rounded 1px border, like the cards in Metrc's prototypes. */
    public static class RoundedPanel extends JPanel {
        private static final int ARC = 10;

        public RoundedPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBackground(Theme.SURFACE);
            setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            g2.setColor(Theme.BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Read-only text that wraps to the width it is given (a label with word wrap). */
    public static final class WrapText extends JTextArea {
        private int laidOutWidth = -1;

        public WrapText(String text, java.awt.Font font, Color color) {
            super(text);
            setFont(font);
            setForeground(color);
            setLineWrap(true);
            setWrapStyleWord(true);
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder());
            setHighlighter(null);
            // Height depends on width: lay the parent out again once the width is known.
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if (getWidth() != laidOutWidth) {
                        laidOutWidth = getWidth();
                        revalidate();
                    }
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            int width = getWidth() > 0 ? getWidth() : 320;
            View root = getUI().getRootView(this);
            root.setSize(width, Float.MAX_VALUE);
            int height = (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS));
            Insets insets = getInsets();
            // A small preferred width lets the layout give the text whatever width is free.
            return new Dimension(40, height + insets.top + insets.bottom);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** A panel holding one component, stretched to the full width. */
    public static JPanel fill(JComponent component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }
}
