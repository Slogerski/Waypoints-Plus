package pl.slogerski.waypointsplus.fabric.remote;

import java.util.Objects;

public record TopDonateEntry(String name, String amount, String currency, String url, String color) {
    private static final int DEFAULT_COLOR = 0xFFFEC110;

    public TopDonateEntry {
        name = text(name, 48, "name");
        amount = text(amount, 24, "amount");
        currency = text(currency, 8, "currency");
        url = RemoteLinks.url(url, true);
        color = normalizeColor(color);
    }

    public int colorArgb() {
        return color.isEmpty() ? DEFAULT_COLOR : (int) Long.parseLong(color, 16);
    }

    public String formattedAmount() {
        boolean currencyCode = currency.codePoints().allMatch(Character::isLetter);
        return amount + (currencyCode ? " " : "") + currency;
    }

    private static String text(String value, int maximum, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(name);
        }
        return value;
    }

    private static String normalizeColor(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.startsWith("#") ? value.substring(1) : value;
        if (normalized.length() == 6) {
            normalized = "FF" + normalized;
        }
        if (!normalized.matches("[0-9a-fA-F]{8}")) {
            throw new IllegalArgumentException("color");
        }
        return normalized.toUpperCase(java.util.Locale.ROOT);
    }
}
