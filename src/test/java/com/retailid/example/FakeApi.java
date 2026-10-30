package com.retailid.example;

import com.retailid.api.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small local HTTP server that stands in for the Retail ID API, so the tests and the
 * screenshot tool need no keys and change nothing in Metrc. It records every request.
 */
public final class FakeApi implements AutoCloseable {

    /** One recorded request. */
    public static final class Call {
        public final String method;
        public final String path;
        public final Map<String, List<String>> query;
        public final Object body;
        public final String auth;

        Call(String method, String path, Map<String, List<String>> query, Object body, String auth) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.body = body;
            this.auth = auth;
        }
    }

    static final Map<String, String> RESPONSES = new LinkedHashMap<>();

    static {
        RESPONSES.put("GET /facilities/v2/",
            "{\"Data\": [{\"Name\": \"Test Dispensary\", \"License\": {\"Number\": \"LIC-1\"}}]}");
        RESPONSES.put("GET /retailid/v2/allotment",
            "{\"FacilityId\": 1, \"FacilityLicenseNumber\": \"LIC-1\", \"Allotment\": 500}");
        RESPONSES.put("GET /tags/v2/package/available", "{\"Data\": [{\"Label\": \"1A4000000000000000000001\"}]}");
        RESPONSES.put("GET /packages/v2/PKG-1", "{\"Id\": 7, \"Label\": \"PKG-1\", \"ProductLabel\": \"Gummies\"}");
        RESPONSES.put("POST /retailid/v2/packages/info",
            "[{\"PackageLabel\": \"PKG-1\", \"QrCount\": 3, \"HasQrs\": true, \"Issuances\": []}]");
        RESPONSES.put("POST /retailid/v2/generate", "{\"IssuanceId\": \"iss-42\"}");
        RESPONSES.put("GET /retailid/v2/receive/PKG-1",
            "{\"Eaches\": [\"HTTPS://1A4.COM/10NOIEYKAJDG6CF0EXGJL\"], \"SiblingTags\": [], "
                + "\"RequiresVerification\": false}");
        RESPONSES.put("GET /retailid/v2/receive/qr/10NOIEYKAJDG6CF0EXGJL",
            "{\"Eaches\": [\"X\"], \"SiblingTags\": [\"PKG-2\"]}");
        RESPONSES.put("POST /retailid/v2/associate", "{\"Ids\": [1]}");
        RESPONSES.put("POST /retailid/v2/merge", "{\"Ids\": [2], \"Warnings\": null}");
        RESPONSES.put("POST /sales/v2/receipts", "");
        RESPONSES.put("GET /globalproducts/v2", "{\"Data\": [{\"Id\": 9}], \"TotalPages\": 1}");
        RESPONSES.put("GET /globalproducts/v2/9", "{\"Id\": 9, \"Name\": \"Widget\"}");
    }

    public final List<Call> calls = Collections.synchronizedList(new ArrayList<>());
    private final HttpServer server;

    public FakeApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public Call lastCall() {
        return calls.get(calls.size() - 1);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        byte[] raw = exchange.getRequestBody().readAllBytes();
        Object body = raw.length == 0 ? null : Json.parse(new String(raw, StandardCharsets.UTF_8));
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        String auth = header == null ? null
            : new String(Base64.getDecoder().decode(header.split(" ")[1]), StandardCharsets.UTF_8);
        calls.add(new Call(method, path, query(exchange.getRequestURI().getRawQuery()), body, auth));

        String key = method + " " + path;
        if (path.equals("/retailid/v2/receive/MISSING")) {
            send(exchange, 404, "{\"Message\": \"Package not found\"}");
        } else if (RESPONSES.containsKey(key)) {
            send(exchange, 200, RESPONSES.get(key));
        } else {
            send(exchange, 404, "{\"Message\": \"no fake for " + key + "\"}");
        }
    }

    private static Map<String, List<String>> query(String raw) {
        Map<String, List<String>> query = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return query;
        }
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            query.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }
        return query;
    }

    private static void send(HttpExchange exchange, int status, String payload) throws IOException {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, data.length == 0 ? -1 : data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
