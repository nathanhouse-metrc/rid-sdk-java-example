package com.retailid.example;

import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Starts the Retail ID SDK Explorer. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            JFrame frame = new JFrame("Retail ID SDK Explorer");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new App(true));
            frame.setSize(1280, 820);
            frame.setMinimumSize(new Dimension(980, 640));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
