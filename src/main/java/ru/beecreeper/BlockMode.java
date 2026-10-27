package ru.beecreeper;

/** Режим разрушения блоков взрывом. */
public enum BlockMode {
    NONE,
    ALL,
    WHITELIST,
    BLACKLIST;

    public static BlockMode parse(String raw, BlockMode fallback) {
        if (raw == null) return fallback;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
