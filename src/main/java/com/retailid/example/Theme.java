package com.retailid.example;

import com.formdev.flatlaf.FlatLightLaf;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.UIManager;

/**
 * Colours, fonts and Swing look-and-feel settings for the app.
 *
 * <p>Every colour and font the UI uses is defined here, so the look can be changed in one
 * place. The values match the Metrc design used in Metrc's shared prototype artifacts
 * (MetrcOne Inspect Insights, QR Code Audits Using Retail ID, Preconfigured Inspections): a
 * deep forest-green brand colour, warm off-white surfaces, Inter for text and IBM Plex Mono for
 * codes. Those prototypes use no logo image; the header carries a white rounded-square
 * monogram instead, as they do. The same tokens are used by the Python example's theme.py.
 */
public final class Theme {

    // -- colours --------------------------------------------------------------

    public static final Color PRIMARY = hex("#26413A");         // brand green: header bar, primary buttons, titles
    public static final Color PRIMARY_ACTIVE = hex("#2F5D4E");  // primary button hover/pressed
    public static final Color ACCENT = hex("#149F69");          // kelly green: header rule, focus highlights
    public static final Color HIGHLIGHT = hex("#E9F800");       // Metrc highlight yellow-green, used sparingly
    public static final Color BACKGROUND = hex("#F3F5F2");      // window background
    public static final Color SURFACE = hex("#FFFFFF");         // cards and inputs
    public static final Color SURFACE_SOFT = hex("#ECEEEA");    // unselected tabs
    public static final Color BORDER = hex("#DCE2D9");
    public static final Color BUTTON_BORDER = hex("#BFC8BE");
    public static final Color TEXT = hex("#16241D");
    public static final Color TEXT_MUTED = hex("#5B6A5F");
    public static final Color TEXT_ON_PRIMARY = hex("#FFFFFF");
    public static final Color TEXT_ON_PRIMARY_SOFT = hex("#CFE3D6");  // secondary text on the brand green
    public static final Color MARK_TEXT = hex("#2F5A45");       // monogram letters on the white mark
    public static final Color SUCCESS = hex("#2E7D32");
    public static final Color ERROR = hex("#B3261E");
    public static final Color WARNING = hex("#7A5B00");

    // -- fonts ----------------------------------------------------------------

    // First installed family wins. Inter and IBM Plex Mono are the Metrc choices (free from
    // Google Fonts); the others are fallbacks for Windows, macOS and Linux.
    private static final String[] HEADING_FAMILIES = {
        "Inter SemiBold", "Inter", "Segoe UI Semibold", "Segoe UI", "Helvetica Neue", "Arial"};
    private static final String[] BODY_FAMILIES = {"Inter", "Segoe UI", "Helvetica Neue", "Arial"};
    private static final String[] MONO_FAMILIES = {
        "IBM Plex Mono", "Cascadia Mono", "Consolas", "Menlo", "Courier New"};

    /** The fonts in use; sizes are in pixels at 100% scaling. */
    public static final class Fonts {
        public final Font title;
        public final Font heading;
        public final Font body;
        public final Font bodyBold;
        public final Font small;
        public final Font mono;
        public final Font resultUrl;
        public final Font mark;

        Fonts() {
            Set<String> installed = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            String heading = firstInstalled(installed, HEADING_FAMILIES, Font.SANS_SERIF);
            String body = firstInstalled(installed, BODY_FAMILIES, Font.SANS_SERIF);
            String mono = firstInstalled(installed, MONO_FAMILIES, Font.MONOSPACED);
            // A family that is already a semibold face needs no synthetic bold.
            int headingStyle = heading.toLowerCase().contains("semibold") ? Font.PLAIN : Font.BOLD;
            title = new Font(heading, headingStyle, 21);
            this.heading = new Font(heading, headingStyle, 16);
            this.body = new Font(body, Font.PLAIN, 13);
            bodyBold = new Font(body, Font.BOLD, 13);
            small = new Font(body, Font.PLAIN, 12);
            this.mono = new Font(mono, Font.PLAIN, 13);
            resultUrl = new Font(mono, Font.BOLD, 17);
            mark = new Font(body, Font.BOLD, 12);
        }

        private static String firstInstalled(Set<String> installed, String[] families, String fallback) {
            for (String family : families) {
                if (installed.contains(family)) {
                    return family;
                }
            }
            return fallback;
        }
    }

    private static Fonts fonts;

    private Theme() {
    }

    /** The app fonts; {@link #install()} must have been called. */
    public static Fonts fonts() {
        if (fonts == null) {
            install();
        }
        return fonts;
    }

    /** Sets the look and feel and the Metrc colours. Call once, before building any component. */
    public static synchronized void install() {
        if (fonts != null) {
            return;
        }
        FlatLightLaf.setup();
        fonts = new Fonts();

        UIManager.put("defaultFont", fonts.body);
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Component.accentColor", ACCENT);
        UIManager.put("Component.focusColor", ACCENT);
        UIManager.put("Component.focusedBorderColor", ACCENT);
        UIManager.put("Component.borderColor", BORDER);
        UIManager.put("Component.arc", 6);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("Button.arc", 6);
        UIManager.put("Button.borderColor", BUTTON_BORDER);
        UIManager.put("Button.font", fonts.bodyBold);
        UIManager.put("Button.margin", new java.awt.Insets(5, 14, 5, 14));
        UIManager.put("CheckBox.icon.checkmarkColor", SURFACE);
        UIManager.put("CheckBox.icon.selectedBackground", PRIMARY);
        UIManager.put("CheckBox.icon.selectedBorderColor", PRIMARY);
        UIManager.put("TextField.background", SURFACE);
        UIManager.put("PasswordField.background", SURFACE);
        UIManager.put("TextArea.background", SURFACE);
        UIManager.put("ComboBox.background", SURFACE);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollPane.background", BACKGROUND);

        UIManager.put("TabbedPane.background", BACKGROUND);
        UIManager.put("TabbedPane.foreground", TEXT_MUTED);
        UIManager.put("TabbedPane.selectedForeground", PRIMARY);
        UIManager.put("TabbedPane.selectedBackground", SURFACE);
        UIManager.put("TabbedPane.underlineColor", ACCENT);
        UIManager.put("TabbedPane.inactiveUnderlineColor", ACCENT);
        UIManager.put("TabbedPane.hoverColor", SURFACE_SOFT);
        UIManager.put("TabbedPane.contentAreaColor", BORDER);
        UIManager.put("TabbedPane.tabSeparatorsFullHeight", true);
        UIManager.put("TabbedPane.tabHeight", 36);
        UIManager.put("TabbedPane.tabInsets", new java.awt.Insets(4, 16, 4, 16));
        UIManager.put("TabbedPane.tabAreaInsets", new java.awt.Insets(8, 12, 0, 12));
        UIManager.put("TabbedPane.font", fonts.body);
    }

    /** Brand-green filled button for the main action of a card. */
    public static JButton primary(JButton button) {
        button.putClientProperty("FlatLaf.style", "background: " + css(PRIMARY)
            + "; foreground: " + css(TEXT_ON_PRIMARY)
            + "; hoverBackground: " + css(PRIMARY_ACTIVE)
            + "; pressedBackground: " + css(PRIMARY_ACTIVE)
            + "; borderColor: " + css(PRIMARY)
            + "; hoverBorderColor: " + css(PRIMARY_ACTIVE)
            + "; focusedBorderColor: " + css(ACCENT)
            + "; disabledBackground: " + css(BORDER)
            + "; disabledText: " + css(TEXT_MUTED));
        return button;
    }

    /** Red outlined button for calls that change data in Metrc. */
    public static JButton danger(JButton button) {
        button.putClientProperty("FlatLaf.style", "background: " + css(SURFACE)
            + "; foreground: " + css(ERROR)
            + "; borderColor: " + css(ERROR)
            + "; hoverBorderColor: " + css(ERROR)
            + "; hoverBackground: " + css(BACKGROUND));
        return button;
    }

    /** Plain secondary button. */
    public static JButton secondary(JButton button) {
        button.putClientProperty("FlatLaf.style", "background: " + css(SURFACE)
            + "; foreground: " + css(TEXT)
            + "; hoverBackground: " + css(BACKGROUND));
        return button;
    }

    private static Color hex(String value) {
        return Color.decode(value);
    }

    private static String css(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }
}
