package com.ogulniega;

public final class OgulniegaModApi {
    public static String language = "ENGLISH";
    public static RuntimeException failure;
    public static Error error;
    public static int calls;

    public static String getLanguage() {
        calls++;
        if (failure != null) throw failure;
        if (error != null) throw error;
        return language;
    }

    public static void reset() {
        language = "ENGLISH";
        failure = null;
        error = null;
        calls = 0;
    }
}
