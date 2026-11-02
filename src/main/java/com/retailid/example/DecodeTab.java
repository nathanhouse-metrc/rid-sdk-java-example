package com.retailid.example;

import com.retailid.EncodeOptions;
import com.retailid.RetailIdPair;
import com.retailid.api.RetailIdClient;

import java.util.List;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** {@code RetailIdPair}: Retail ID URL → batch ID + index. Works offline. */
public class DecodeTab extends JPanel {

    public static final String EXAMPLE_URL = "HTTPS://1A4.COM/10NOIEYKAJDG6CF0EXGJL";

    /** Hex prefixes of Retail ID batch IDs (the SDK checks the same ones in strict mode). */
    public static final List<String> RETAILID_PREFIXES = List.of("1a4", "abc");

    public final JTextField url = new JTextField(EXAMPLE_URL);
    public final JCheckBox strict = new JCheckBox("Require a known prefix or a valid ObjectId timestamp");
    public final ResultView results = new ResultView();

    public DecodeTab() {
        Card card = new Card("Decode a Retail ID URL",
            "Paste a scanned QR URL (or just its short code). RetailIdPair detects base36 or base64 "
                + "and returns the batch ID and index.");
        url.setFont(Theme.fonts().mono);
        card.addRow("URL or code", url, "e.g. " + EXAMPLE_URL);
        strict.setOpaque(false);
        card.addRow("Strict", strict);
        card.addButton("Decode", this::decode, Card.Kind.PRIMARY);
        card.addButton("Load example", this::loadExample, Card.Kind.SECONDARY);

        Ui.Column column = new Ui.Column();
        column.addCard(card);
        Ui.twoColumn(this, Ui.scrollColumn(column), results);
    }

    public void loadExample() {
        url.setText(EXAMPLE_URL);
        strict.setSelected(false);
        decode();
    }

    public void decodeUrl(String value) {
        url.setText(value);
        decode();
    }

    public void decode() {
        String text = url.getText().trim();
        RetailIdPair pair;
        String shortCode;
        try {
            if (text.isEmpty()) {
                throw new IllegalArgumentException("Enter a Retail ID URL or short code");
            }
            pair = new RetailIdPair(text, strict.isSelected());
            shortCode = RetailIdClient.shortCodeFromUrl(text);
        } catch (IllegalArgumentException e) {
            results.showError("Could not decode", e.getMessage(), "");
            return;
        }

        String domain = EncodeTab.DEFAULT_DOMAIN;
        int scheme = text.indexOf("://");
        if (scheme >= 0) {
            String host = text.substring(scheme + 3).split("/", 2)[0].toLowerCase();
            if (!host.isEmpty()) {
                domain = host;
            }
        }
        String hexId = pair.getBatchId().toHexString();
        boolean known = RETAILID_PREFIXES.stream().anyMatch(hexId::startsWith);
        EncodeOptions asBase36 = new EncodeOptions().setDomain(domain);
        EncodeOptions asBase64 = new EncodeOptions().setDomain(domain).setBase64(true);
        results.showFields("Decoded", List.of(
            Map.entry("Batch ID", hexId),
            Map.entry("Index", String.valueOf(pair.getIndex())),
            Map.entry("Short code", shortCode),
            Map.entry("Detected encoding", guessEncoding(shortCode, pair, domain)),
            Map.entry("Known Retail ID prefix", known ? "yes"
                : "no (known prefixes: " + String.join(", ", RETAILID_PREFIXES) + ")"),
            Map.entry("Re-encoded as base36", pair.encode(asBase36)),
            Map.entry("Re-encoded as base64", pair.encode(asBase64))));
    }

    private static String guessEncoding(String shortCode, RetailIdPair pair, String domain) {
        String base64 = RetailIdClient.shortCodeFromUrl(
            pair.encode(new EncodeOptions().setDomain(domain).setBase64(true)));
        if (base64.equals(shortCode)) {
            return "base64 (URL-safe)";
        }
        String base36 = RetailIdClient.shortCodeFromUrl(pair.encode(new EncodeOptions().setDomain(domain)));
        if (base36.equalsIgnoreCase(shortCode)) {
            return "base36";
        }
        return "unknown";
    }
}
