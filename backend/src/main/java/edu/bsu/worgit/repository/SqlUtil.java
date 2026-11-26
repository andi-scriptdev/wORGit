package edu.bsu.worgit.repository;

/** Small helpers for building SQL. */
final class SqlUtil {
    private SqlUtil() {}

    /** Escapes LIKE wildcards (PostgreSQL's default escape character is the backslash). */
    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static String likePattern(String text) {
        return "%" + escapeLike(text.toLowerCase(java.util.Locale.ROOT)) + "%";
    }
}
