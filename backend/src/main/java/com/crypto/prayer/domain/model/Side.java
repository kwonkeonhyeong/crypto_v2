package com.crypto.prayer.domain.model;

public enum Side {
    UP("up", "상승"),
    DOWN("down", "하락");

    private final String key;
    private final String displayName;

    Side(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Side fromKey(String key) {
        for (Side side : values()) {
            if (side.key.equalsIgnoreCase(key)) {
                return side;
            }
        }
        throw new IllegalArgumentException("Unknown side: " + key);
    }
}
