package com.retailid.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.retailid.RetailIdPair;
import com.retailid.api.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives every panel of the app against the real SDK. API calls go to {@link FakeApi}, so the
 * tests need no keys and change nothing in Metrc.
 */
class AppTest {

    private static final String VENDOR = "vendor-key-123";
    private static final String USER = "user-key-456";

    private static FakeApi api;

    @TempDir
    Path tmp;

    private Path settingsFile;
    private App app;

    @BeforeAll
    static void startApi() throws IOException {
        api = new FakeApi();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    @BeforeEach
    void setUp() {
        settingsFile = tmp.resolve("settings.json");
        System.setProperty(Settings.SYSTEM_PROPERTY, settingsFile.toString());
        app = new App(false);
        api.calls.clear();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(Settings.SYSTEM_PROPERTY);
    }

    private void configure() {
        SettingsTab tab = app.settingsTab;
        tab.setValue("vendor_api_key", VENDOR);
        tab.setValue("user_api_key", USER);
        tab.setValue("state_code", "MD");
        tab.setValue("base_url", api.baseUrl());
        tab.save();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> savedFile() throws IOException {
        return (Map<String, Object>) Json.parse(Files.readString(settingsFile, StandardCharsets.UTF_8));
    }

    // -- settings ------------------------------------------------------------------------

    @Test
    void settingsPersistBetweenRuns() throws IOException {
        configure();
        Map<String, Object> saved = savedFile();
        assertEquals(VENDOR, saved.get("vendor_api_key"));
        assertEquals("md", saved.get("state_code"));
        assertEquals(api.baseUrl(), saved.get("base_url"));
        // A fresh app reads the same values back.
        App second = new App(false);
        assertEquals(USER, second.settingsTab.values().get("user_api_key"));
        // Keys are masked in the summary.
        assertFalse(app.settingsTab.results.getText().contains(VENDOR));
    }

    @Test
    void savingKeepsExtraConfigKeys() throws IOException {
        Files.writeString(settingsFile, "{\"timeout\": 10, \"vendor_api_key\": \"old\"}");
        app = new App(false);
        configure();
        Map<String, Object> saved = savedFile();
        assertEquals(10L, saved.get("timeout"));
        assertEquals(VENDOR, saved.get("vendor_api_key"));
    }

    @Test
    void testConnection() {
        configure();
        app.settingsTab.testConnection();
        String out = app.settingsTab.results.getText();
        assertTrue(out.contains("Connected — 1 facility"), out);
        assertTrue(out.contains("LIC-1  Test Dispensary"), out);
        assertEquals(VENDOR + ":" + USER, api.lastCall().auth);
    }

    @Test
    void apiWithoutSettingsReportsIncomplete() {
        ApiTab tab = app.apiTabs.get("Access");
        tab.invoke("facilities");
        assertTrue(tab.results.getText().contains("Settings incomplete"));
        assertTrue(api.calls.isEmpty());
    }

    // -- encode / decode -------------------------------------------------------------------

    @Test
    void encodeKnownVector() {
        EncodeTab tab = app.encodeTab;
        tab.loadExample();
        assertEquals("HTTPS://1A4.COM/10NOIEYKAJDG6CF0EXGJL", tab.getLastUrl());
        tab.base64.setSelected(true);
        tab.domain.setText("example.com");
        tab.encode();
        assertTrue(tab.getLastUrl().startsWith("https://example.com/"), tab.getLastUrl());
        RetailIdPair pair = new RetailIdPair(tab.getLastUrl());
        assertEquals("abcdef012345670000027190", pair.getBatchId().toHexString());
        assertEquals(1, pair.getIndex());
    }

    @Test
    void encodeBadInput() {
        EncodeTab tab = app.encodeTab;
        tab.batchId.setText("xyz");
        tab.encode();
        assertTrue(tab.results.getText().contains("Could not encode"));
        tab.batchId.setText("abcdef012345670000027190");
        tab.index.setText("-1");
        tab.encode();
        assertTrue(tab.results.getText().contains("whole number"));
    }

    @Test
    void encodeThenDecodeRoundTrip() {
        EncodeTab encode = app.encodeTab;
        encode.index.setText("300");
        encode.base64.setSelected(true);
        encode.encode();
        encode.decodeResult();
        String out = app.decodeTab.results.getText();
        assertTrue(out.contains("abcdef012345670000027190"), out);
        assertTrue(out.contains("Index\n300\n"), out);
        assertTrue(out.contains("Detected encoding\nbase64"), out);
        assertEquals(app.decodeTab, app.tabs.getSelectedComponent());
    }

    @Test
    void decodeKnownVector() {
        DecodeTab tab = app.decodeTab;
        tab.loadExample();
        String out = tab.results.getText();
        assertTrue(out.contains("abcdef012345670000027190"), out);
        assertTrue(out.contains("Index\n1\n"), out);
        assertTrue(out.contains("Detected encoding\nbase36"), out);
    }

    @Test
    void decodeBadInput() {
        DecodeTab tab = app.decodeTab;
        tab.url.setText("https://1a4.com/!!");
        tab.decode();
        assertTrue(tab.results.getText().contains("Could not decode"));
    }

    // -- API tabs ----------------------------------------------------------------------------

    private String runAction(String tabTitle, String key, String... inputs) {
        ApiTab tab = app.apiTabs.get(tabTitle);
        for (int i = 0; i < inputs.length; i += 2) {
            tab.setInput(key, inputs[i], inputs[i + 1]);
        }
        tab.invoke(key, false);
        String out = tab.results.getText();
        assertTrue(out.startsWith("✔"), key + " failed:\n" + out);
        return out;
    }

    @Test
    void everyApiAction() throws IOException {
        configure();
        app.setLicenseNumber("LIC-1");

        String out = runAction("Access", "facilities");
        assertTrue(out.contains("Test Dispensary"));
        assertFalse(api.lastCall().query.containsKey("licenseNumber"));

        out = runAction("Label printing", "allotment");
        assertTrue(out.contains("\"allotment\": 500"), out);
        assertEquals(List.of("LIC-1"), api.lastCall().query.get("licenseNumber"));

        out = runAction("Label printing", "available_tags");
        assertTrue(out.contains("1A4000000000000000000001"));

        out = runAction("Label printing", "package", "id_or_label", "PKG-1");
        assertTrue(out.contains("Gummies"));

        out = runAction("Label printing", "packages_info", "labels", "PKG-1\nPKG-2");
        assertEquals(Map.of("packageLabels", List.of("PKG-1", "PKG-2")), api.lastCall().body);
        assertTrue(out.contains("\"qrCount\": 3"), out);

        out = runAction("Label printing", "generate", "package_label", "PKG-1", "quantity", "3");
        assertEquals(Map.of("PackageLabel", "PKG-1", "Quantity", 3L), api.lastCall().body);
        assertTrue(out.contains("iss-42"));

        out = runAction("Receiving", "receive", "package_label", "PKG-1");
        assertTrue(out.contains("10NOIEYKAJDG6CF0EXGJL"));

        runAction("Receiving", "receive_qr");
        assertEquals("/retailid/v2/receive/qr/10NOIEYKAJDG6CF0EXGJL", api.lastCall().path);

        runAction("Receiving", "associate", "package_label", "PKG-1",
            "qr_urls", "https://1a4.com/a\nhttps://1a4.com/b");
        assertEquals(List.of(Map.of("PackageLabel", "PKG-1",
            "QrUrls", List.of("https://1a4.com/a", "https://1a4.com/b"))), api.lastCall().body);

        runAction("Receiving", "merge", "target", "PKG-1", "source", "PKG-2");
        assertEquals(Map.of("packageLabels", List.of("PKG-1", "PKG-2")), api.lastCall().body);

        out = runAction("Point of sale", "sales");
        assertEquals("/sales/v2/receipts", api.lastCall().path);
        @SuppressWarnings("unchecked")
        Map<String, Object> receipt = (Map<String, Object>) ((List<?>) api.lastCall().body).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> transaction = (Map<String, Object>) ((List<?>) receipt.get("Transactions")).get(0);
        assertEquals(List.of("HTTPS://1A4.COM/10NOIEYKAJDG6CF0EXGJL"), transaction.get("QrCodes"));
        assertTrue(out.contains("(empty response)"), out);

        out = runAction("Catalog", "global_products", "brand_id", "5", "page_size", "20");
        assertEquals(List.of("5"), api.lastCall().query.get("brandId"));
        assertEquals(List.of("20"), api.lastCall().query.get("pageSize"));
        assertTrue(out.contains("1 record"), out);

        out = runAction("Catalog", "global_product", "product_id", "9");
        assertTrue(out.contains("Widget"));

        // The license number is remembered between runs.
        assertEquals("LIC-1", savedFile().get("license_number"));
    }

    @Test
    void apiErrorIsShown() {
        configure();
        app.setLicenseNumber("LIC-1");
        ApiTab tab = app.apiTabs.get("Receiving");
        tab.setInput("receive", "package_label", "MISSING");
        tab.invoke("receive");
        String out = tab.results.getText();
        assertTrue(out.contains("HTTP 404 (NotFoundException)"), out);
        assertTrue(out.contains("Package not found"), out);
    }

    @Test
    void licenseRequired() {
        configure();
        app.setLicenseNumber("");
        ApiTab tab = app.apiTabs.get("Label printing");
        tab.invoke("allotment");
        assertTrue(tab.results.getText().contains("license number"));
    }

    @Test
    void invalidJsonInput() {
        configure();
        app.setLicenseNumber("LIC-1");
        ApiTab tab = app.apiTabs.get("Point of sale");
        tab.setInput("sales", "records", "{not json");
        tab.invoke("sales", false);
        assertTrue(tab.results.getText().contains("not valid JSON"));
    }
}
