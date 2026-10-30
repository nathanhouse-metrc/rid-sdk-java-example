package com.retailid.example;

import com.retailid.api.RetailIdApiException;
import com.retailid.api.RetailIdConfigException;
import com.retailid.api.RetailIdTimeoutException;

import java.io.UncheckedIOException;

/** Turns exceptions from the SDK into a title, message and detail for the results panel. */
public final class Errors {

    /** What to show for an error. */
    public static final class Description {
        public final String title;
        public final String message;
        public final String detail;

        Description(String title, String message, String detail) {
            this.title = title;
            this.message = message == null ? "" : message;
            this.detail = detail == null ? "" : detail;
        }
    }

    private Errors() {
    }

    public static Description describe(Throwable e) {
        // RetailIdConfigException is an IllegalArgumentException, so it is checked first.
        if (e instanceof RetailIdConfigException) {
            return new Description("Settings incomplete", e.getMessage(),
                "Open the Settings tab, fill in the keys and a state code or base URL, and Save.");
        }
        if (e instanceof RetailIdApiException) {
            RetailIdApiException api = (RetailIdApiException) e;
            Object body = api.getBody();
            String detail = body == null || "".equals(body) ? "" : JsonText.format(body);
            return new Description(api.getMethod() + " " + api.getPath() + " → HTTP " + api.getStatusCode()
                + " (" + e.getClass().getSimpleName() + ")", api.getApiMessage(), detail);
        }
        if (e instanceof RetailIdTimeoutException) {
            return new Description("Request timed out", e.getMessage(),
                "Calls that change data are not retried; check the result before sending again.");
        }
        if (e instanceof UncheckedIOException) {
            return new Description("Could not connect", e.getMessage(),
                "Check the state code or base URL in Settings.");
        }
        if (e instanceof IllegalArgumentException) {
            return new Description("Invalid input", e.getMessage(), "");
        }
        return new Description(e.getClass().getSimpleName(), e.getMessage(), "");
    }
}
