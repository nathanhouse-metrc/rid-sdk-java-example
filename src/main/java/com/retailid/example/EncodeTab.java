package com.retailid.example;

import com.retailid.EncodeOptions;
import com.retailid.ObjectId;
import com.retailid.RetailId;
import com.retailid.VarInt;
import com.retailid.api.RetailIdClient;

import java.awt.BorderLayout;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

/** {@code RetailId.encode}: batch ID + index → Retail ID URL. Works offline. */
public class EncodeTab extends JPanel {

    public static final String EXAMPLE_BATCH_ID = "abcdef012345670000027190";
    public static final int EXAMPLE_INDEX = 1;
    public static final String DEFAULT_DOMAIN = "1a4.com";

    public final JTextField batchId = new JTextField(EXAMPLE_BATCH_ID);
    public final JTextField index = new JTextField(String.valueOf(EXAMPLE_INDEX));
    public final JTextField domain = new JTextField(DEFAULT_DOMAIN);
    public final JRadioButton base36 = new JRadioButton("Base36 (uppercase, smaller QR)", true);
    public final JRadioButton base64 = new JRadioButton("Base64 (URL-safe, shorter text)");
    public final ResultView results = new ResultView();

    private final Consumer<String> sendToDecode;
    private final JButton decodeButton;
    private String lastUrl = "";

    public EncodeTab(Consumer<String> sendToDecode) {
        this.sendToDecode = sendToDecode;
        Theme.Fonts fonts = Theme.fonts();

        Card card = new Card("Encode a Retail ID URL",
            "Combine a 24-character hex batch ID (an ObjectId) and an item index into the short URL "
                + "printed in a Retail ID QR code. Uses RetailId.encode().");

        batchId.setFont(fonts.mono);
        index.setFont(fonts.mono);
        JPanel batchRow = new JPanel(new BorderLayout(8, 0));
        batchRow.setOpaque(false);
        batchRow.add(batchId, BorderLayout.CENTER);
        JButton random = Theme.secondary(new JButton("Random"));
        random.addActionListener(e -> randomBatchId());
        batchRow.add(random, BorderLayout.EAST);
        card.addRow("Batch ID", batchRow, "24 hex characters, e.g. " + EXAMPLE_BATCH_ID);
        card.addRow("Index", index, "Whole number ≥ 0 (the item's position in the batch)");
        card.addRow("Domain", domain, "Leave as " + DEFAULT_DOMAIN + " unless you use a custom domain");

        ButtonGroup group = new ButtonGroup();
        group.add(base36);
        group.add(base64);
        JPanel radios = new JPanel();
        radios.setOpaque(false);
        radios.setLayout(new BoxLayout(radios, BoxLayout.Y_AXIS));
        base36.setOpaque(false);
        base64.setOpaque(false);
        base36.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
        radios.add(base36);
        radios.add(base64);
        card.addRow("Encoding", radios);

        card.addButton("Encode", this::encode, Card.Kind.PRIMARY);
        card.addButton("Load example", this::loadExample, Card.Kind.SECONDARY);
        decodeButton = card.addButton("Decode this URL →", this::decodeResult, Card.Kind.SECONDARY);
        decodeButton.setEnabled(false);

        Ui.Column column = new Ui.Column();
        column.addCard(card);
        Ui.twoColumn(this, Ui.scrollColumn(column), results);
    }

    public String getLastUrl() {
        return lastUrl;
    }

    public void randomBatchId() {
        batchId.setText(new ObjectId().toHexString());
    }

    public void loadExample() {
        batchId.setText(EXAMPLE_BATCH_ID);
        index.setText(String.valueOf(EXAMPLE_INDEX));
        domain.setText(DEFAULT_DOMAIN);
        base36.setSelected(true);
        encode();
    }

    public void encode() {
        ObjectId id;
        int indexValue;
        boolean useBase64 = base64.isSelected();
        String url;
        try {
            id = new ObjectId(batchId.getText().trim());
            String indexText = index.getText().trim();
            if (!indexText.matches("\\d+")) {
                throw new IllegalArgumentException("Index must be a whole number ≥ 0, got '" + indexText + "'");
            }
            try {
                indexValue = Integer.parseInt(indexText);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Index is too large; the most is " + Integer.MAX_VALUE);
            }
            EncodeOptions options = new EncodeOptions().setBase64(useBase64);
            if (!domain.getText().isBlank()) {
                options.setDomain(domain.getText().trim());
            }
            url = RetailId.encode(id, indexValue, options);
        } catch (IllegalArgumentException e) {
            lastUrl = "";
            decodeButton.setEnabled(false);
            results.showError("Could not encode", e.getMessage(), "");
            return;
        }

        lastUrl = url;
        decodeButton.setEnabled(true);
        VarInt varint = new VarInt(indexValue);
        results.showFields("Encoded", List.of(
            Map.entry("URL", url),
            Map.entry("Short code", RetailIdClient.shortCodeFromUrl(url)),
            Map.entry("Encoding", useBase64 ? "base64 (URL-safe)" : "base36"),
            Map.entry("Batch ID", id.toHexString()),
            Map.entry("Index", indexValue + "  (varint bytes: " + varint.toHexString() + ")"),
            Map.entry("Payload", (12 + varint.length()) + " bytes = 12-byte batch ID + "
                + varint.length() + "-byte varint index")),
            Set.of("URL"));
    }

    public void decodeResult() {
        if (!lastUrl.isEmpty()) {
            sendToDecode.accept(lastUrl);
        }
    }
}
