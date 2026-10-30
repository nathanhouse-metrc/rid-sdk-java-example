package com.retailid.example;

import com.retailid.api.Json;
import com.retailid.api.RetailIdClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Retail ID API calls the app offers: one {@link Action} per {@code RetailIdClient} method,
 * grouped into tabs. Each action describes its inputs and the client call, so the tabs are
 * mostly data.
 */
public final class ApiActions {

    /** How an input is shown and parsed. */
    public enum Kind { TEXT, INT, OPTIONAL_INT, LINES, JSON, CHOICE }

    /** One input of an action. */
    public static final class Field {
        public final String key;
        public final String label;
        public final Kind kind;
        public final String defaultValue;
        public final String hint;
        public final List<String> choices;

        Field(String key, String label, Kind kind, String defaultValue, String hint, List<String> choices) {
            this.key = key;
            this.label = label;
            this.kind = kind;
            this.defaultValue = defaultValue;
            this.hint = hint;
            this.choices = choices;
        }

        static Field text(String key, String label, String hint) {
            return new Field(key, label, Kind.TEXT, "", hint, List.of());
        }

        static Field of(String key, String label, Kind kind, String defaultValue, String hint) {
            return new Field(key, label, kind, defaultValue, hint, List.of());
        }

        /** Parses the raw input; throws {@link IllegalArgumentException} with a readable message. */
        public Object parse(String raw) {
            String value = raw.trim();
            switch (kind) {
                case INT:
                    if (!value.matches("-?\\d+")) {
                        throw new IllegalArgumentException(label + " must be a whole number");
                    }
                    return Integer.parseInt(value);
                case OPTIONAL_INT:
                    if (value.isEmpty()) {
                        return null;
                    }
                    if (!value.matches("\\d+")) {
                        throw new IllegalArgumentException(label + " must be a whole number or blank");
                    }
                    return Integer.parseInt(value);
                case LINES:
                    List<String> items = new ArrayList<>();
                    for (String line : value.replace(",", "\n").split("\\R")) {
                        if (!line.isBlank()) {
                            items.add(line.trim());
                        }
                    }
                    if (items.isEmpty()) {
                        throw new IllegalArgumentException(label + ": enter at least one value");
                    }
                    return items;
                case JSON:
                    try {
                        return Json.parse(value);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException(label + " is not valid JSON: " + e.getMessage(), e);
                    }
                default:
                    if (value.isEmpty()) {
                        throw new IllegalArgumentException(label + " is required");
                    }
                    return value;
            }
        }
    }

    /** The client call behind an action. */
    @FunctionalInterface
    public interface Call {
        Object run(RetailIdClient client, String licenseNumber, Map<String, Object> values);
    }

    /** One card on an API tab. */
    public static final class Action {
        public final String key;
        public final String title;
        public final String endpoint;
        public final String hint;
        public final Call call;
        public final List<Field> fields = new ArrayList<>();
        public boolean needsLicense = true;
        public boolean changesData;

        Action(String key, String title, String endpoint, String hint, Call call) {
            this.key = key;
            this.title = title;
            this.endpoint = endpoint;
            this.hint = hint;
            this.call = call;
        }

        Action fields(Field... more) {
            fields.addAll(Arrays.asList(more));
            return this;
        }

        Action noLicense() {
            needsLicense = false;
            return this;
        }

        Action changesData() {
            changesData = true;
            return this;
        }
    }

    private ApiActions() {
    }

    // -- helpers for the calls -----------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object value) {
        return (List<String>) value;
    }

    /** Sales records: one JSON object or an array of objects. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> salesRecords(Object value) {
        List<?> items = value instanceof List ? (List<?>) value : List.of(value);
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("Records must be a JSON object or an array of objects");
            }
            records.add((Map<String, Object>) item);
        }
        return records;
    }

    static final String SAMPLE_RECEIPT = JsonText.pretty(List.of(sample()), 2);

    private static Map<String, Object> sample() {
        Map<String, Object> transaction = new LinkedHashMap<>();
        transaction.put("PackageLabel", "1A4060300007777000000001");
        transaction.put("Quantity", 1);
        transaction.put("UnitOfMeasure", "Each");
        transaction.put("TotalAmount", 10.0);
        transaction.put("QrCodes", List.of(DecodeTab.EXAMPLE_URL));
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("SalesDateTime", "2026-01-01T12:00:00Z");
        receipt.put("SalesCustomerType", "Consumer");
        receipt.put("Transactions", List.of(transaction));
        return receipt;
    }

    // -- the actions ---------------------------------------------------------------

    private static final Field PACKAGE = Field.text("package_label", "Package label", "e.g. 1A4060300007777000000001");

    public static final List<Action> ACCESS = List.of(
        new Action("facilities", "Check access: list facilities", "GET /facilities/v2/",
            "Confirms the saved keys work and lists the facilities this user can see. "
                + "Use one of the license numbers above.",
            (c, lic, v) -> c.getFacilities()).noLicense());

    public static final List<Action> LABEL_PRINTING = List.of(
        new Action("allotment", "Allotment", "GET /retailid/v2/allotment",
            "Remaining Retail Item IDs for the facility (New York only).",
            (c, lic, v) -> c.getAllotment(lic)),
        new Action("available_tags", "Available tags", "GET /tags/v2/package/available",
            "Unused package tags, for staging labels.",
            (c, lic, v) -> c.getAvailableTags(lic)),
        new Action("package", "Get package", "GET /packages/v2/{id or label}",
            "Package details, including ProductLabel.",
            (c, lic, v) -> c.getPackage(lic, (String) v.get("id_or_label")))
            .fields(Field.text("id_or_label", "ID or label", "Numeric package ID or package label")),
        new Action("packages_info", "Package Retail ID info", "POST /retailid/v2/packages/info",
            "QR count, issuances and verification status. Read-only; sent in batches of 10.",
            (c, lic, v) -> c.getPackagesInfo(lic, strings(v.get("labels"))))
            .fields(Field.of("labels", "Package labels", Kind.LINES, "", "One per line (or comma-separated)")),
        new Action("generate", "Generate Retail IDs", "POST /retailid/v2/generate",
            "Creates Retail IDs for a package. Changes data and is not retried; if it times out, "
                + "check Package Retail ID info before trying again.",
            (c, lic, v) -> c.generate(lic, (String) v.get("package_label"), (Integer) v.get("quantity")))
            .fields(PACKAGE, Field.of("quantity", "Quantity", Kind.INT, "1", ""))
            .changesData());

    public static final List<Action> RECEIVING = List.of(
        new Action("receive", "Receive by package label", "GET /retailid/v2/receive/{label}",
            "QR URLs (eaches) and receiving status for a package.",
            (c, lic, v) -> c.receive(lic, (String) v.get("package_label")))
            .fields(PACKAGE),
        new Action("receive_qr", "Receive by scanned QR", "GET /retailid/v2/receive/qr/{shortCode}",
            "Start from one scanned unit: paste its QR URL or short code.",
            (c, lic, v) -> c.receiveByQr(lic, (String) v.get("qr")))
            .fields(Field.of("qr", "QR URL or code", Kind.TEXT, DecodeTab.EXAMPLE_URL, "")),
        new Action("associate", "Scan and associate", "POST /retailid/v2/associate",
            "Link scanned units to the package they are physically in. Use full QR URLs, "
                + "not short codes. Changes data.",
            (c, lic, v) -> c.associatePackage(lic, (String) v.get("package_label"), strings(v.get("qr_urls"))))
            .fields(PACKAGE, Field.of("qr_urls", "QR URLs", Kind.LINES, "", "One full URL per line"))
            .changesData(),
        new Action("merge", "Merge packages", "POST /retailid/v2/merge",
            "The target keeps and receives all Retail ID links; the source gives its links up. "
                + "Changes data.",
            (c, lic, v) -> c.merge(lic, (String) v.get("target"), (String) v.get("source")))
            .fields(Field.text("target", "Target label", ""), Field.text("source", "Source label", ""))
            .changesData());

    public static final List<Action> POINT_OF_SALE = List.of(
        new Action("sales", "Report sales with Retail IDs", "POST /sales/v2/…",
            "Sales objects from the market's Sales docs; Retail IDs go in Transactions[].QrCodes "
                + "next to PackageLabel. At most 10 records. Changes data.",
            (c, lic, v) -> c.postSales(lic, salesRecords(v.get("records")), (String) v.get("endpoint")))
            .fields(new Field("endpoint", "Endpoint", Kind.CHOICE, "receipts", "",
                    List.copyOf(RetailIdClient.SALES_ENDPOINTS.keySet())),
                Field.of("records", "Records (JSON)", Kind.JSON, SAMPLE_RECEIPT, ""))
            .changesData());

    public static final List<Action> CATALOG = List.of(
        new Action("global_products", "Global products for a brand", "GET /globalproducts/v2?brandId=",
            "Every Global Product for the brand, across all pages.",
            (c, lic, v) -> c.getGlobalProducts((String) v.get("brand_id"), (Integer) v.get("page_size")))
            .fields(Field.text("brand_id", "Brand ID", ""),
                Field.of("page_size", "Page size", Kind.OPTIONAL_INT, "", "Optional"))
            .noLicense(),
        new Action("global_product", "Global product by ID", "GET /globalproducts/v2/{id}",
            "One Global Product.",
            (c, lic, v) -> c.getGlobalProduct((String) v.get("product_id")))
            .fields(Field.text("product_id", "Product ID", ""))
            .noLicense());

    /** Tab title → actions, in tab order. */
    public static final Map<String, List<Action>> TABS = tabs();

    private static Map<String, List<Action>> tabs() {
        Map<String, List<Action>> tabs = new LinkedHashMap<>();
        tabs.put("Access", ACCESS);
        tabs.put("Label printing", LABEL_PRINTING);
        tabs.put("Receiving", RECEIVING);
        tabs.put("Point of sale", POINT_OF_SALE);
        tabs.put("Catalog", CATALOG);
        return tabs;
    }
}
