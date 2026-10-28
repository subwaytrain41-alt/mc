package com.example.ecomod.core;

import java.util.regex.Pattern;

public final class Names {
    private static final Pattern HOME = Pattern.compile("[A-Za-z0-9_-]{1,16}");

    private Names() {}

    public static boolean validHomeName(String name) {
        return name != null && HOME.matcher(name).matches();
    }
}
