package com.retailid.example;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Regenerates the README screenshots in docs/screenshots/.
 *
 * <p>Opens the app with sample settings, runs one action per tab (API calls go to the local
 * {@link FakeApi}, so no keys are needed and nothing changes in Metrc) and paints the window into
 * an image. Swing paints itself, so the window never needs to be on screen. Run from the
 * repository root:
 *
 * <pre>
 * ./mvnw -q test-compile exec:exec@screenshots
 * </pre>
 */
public final class TakeScreenshots {

    private static final Path OUT = Paths.get("docs", "screenshots");
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 820;
    /** Rendered at twice the size so the images stay sharp on high-DPI screens. */
    private static final double SCALE = 2;

    private TakeScreenshots() {
    }

    private static final class Step {
        final String name;
        final Component tab;
        final Runnable prepare;

        Step(String name, Component tab, Runnable prepare) {
            this.name = name;
            this.tab = tab;
            this.prepare = prepare;
        }
    }

    public static void main(String[] args) {
        int status = 0;
        try {
            run();
        } catch (Throwable e) {
            e.printStackTrace();
            status = 1;
        }
        // The Swing event thread would otherwise keep the JVM (and mvn exec:java) running.
        System.exit(status);
    }

    private static void run() throws Exception {
        Files.createDirectories(OUT);
        Path settings = Files.createTempDirectory("rid-screenshots").resolve("settings.json");
        System.setProperty(Settings.SYSTEM_PROPERTY, settings.toString());
        // Show the default location rather than this run's temporary file.
        Settings.displayPathOverride = Settings.DEFAULT_FILE;

        try (FakeApi api = new FakeApi()) {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    shoot(api);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }

    private static void shoot(FakeApi api) throws IOException {
        Theme.install();
        App app = new App(false);
        JFrame frame = new JFrame("Retail ID SDK Explorer");
        frame.setContentPane(app);
        frame.setSize(WIDTH, HEIGHT);
        frame.addNotify();  // displayable without being shown

        // Sample settings (not real keys).
        SettingsTab settingsTab = app.settingsTab;
        settingsTab.setValue("vendor_api_key", "sample-vendor-key-0001");
        settingsTab.setValue("user_api_key", "sample-user-key-0002");
        settingsTab.setValue("state_code", "md");
        settingsTab.save();
        app.setLicenseNumber("LIC-1");

        List<Step> steps = new ArrayList<>();
        steps.add(new Step("settings", app.settingsTab, () -> { }));
        steps.add(new Step("encode", app.encodeTab, app.encodeTab::loadExample));
        steps.add(new Step("decode", app.decodeTab, app.decodeTab::loadExample));
        steps.add(apiStep(app, "access", "Access", "facilities"));
        steps.add(apiStep(app, "label-printing", "Label printing", "packages_info", "labels", "PKG-1"));
        steps.add(apiStep(app, "receiving", "Receiving", "receive", "package_label", "PKG-1"));
        steps.add(apiStep(app, "point-of-sale", "Point of sale", "sales"));
        steps.add(apiStep(app, "catalog", "Catalog", "global_product", "product_id", "9"));

        for (int i = 0; i < steps.size(); i++) {
            if (i == 1) {
                // After the Settings screenshot: point API calls at the fake server.
                app.saved.put("base_url", api.baseUrl());
            }
            Step step = steps.get(i);
            app.tabs.setSelectedComponent(step.tab);
            step.prepare.run();
            app.setStatus("Ready", true);
            Path file = OUT.resolve(step.name + ".png");
            ImageIO.write(paint(frame), "png", file.toFile());
            System.out.println("wrote " + file);
        }
        frame.dispose();
    }

    private static Step apiStep(App app, String name, String tabTitle, String action, String... inputs) {
        ApiTab tab = app.apiTabs.get(tabTitle);
        return new Step(name, tab, () -> {
            for (int i = 0; i < inputs.length; i += 2) {
                tab.setInput(action, inputs[i], inputs[i + 1]);
            }
            tab.invoke(action, false);
        });
    }

    private static BufferedImage paint(JFrame frame) {
        Component content = frame.getContentPane();
        // Lay out twice: wrapping text only knows its height once it has a width. (In the running
        // app a resize listener does the second pass; here no events are processed in between.)
        frame.validate();
        invalidateTree(content);
        frame.validate();
        BufferedImage image = new BufferedImage((int) (content.getWidth() * SCALE),
            (int) (content.getHeight() * SCALE), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(SCALE, SCALE);
        content.printAll(g);
        g.dispose();
        return image;
    }

    private static void invalidateTree(Component component) {
        component.invalidate();
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) {
                invalidateTree(child);
            }
        }
    }
}
