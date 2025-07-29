package com.project.tracking_system.utils;

/**
 * Utility methods for track number normalization.
 */
public final class TrackNumberUtils {

    private TrackNumberUtils() {
    }

    /**
     * Normalizes a track number by trimming whitespace and converting
     * the string to upper case.
     *
     * @param input raw track number, may be {@code null}
     * @return normalized track number or {@code null} if input was null
     */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        return input.toUpperCase().trim();
    }
}
