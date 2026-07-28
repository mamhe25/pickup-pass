package com.pickuppass.util;

/**
 * Formats structured name parts (last, first, middle initial, suffix) into
 * a single "Lastname, Firstname M. Suffix" string — used for BOTH display
 * and sorting everywhere a person's name appears (students, guardians,
 * staff). Deliberately stored as one computed string in the existing
 * fullName/displayName Firestore fields rather than requiring every
 * downstream reader (search, exit logs, scanner verify panel, branded
 * titles, notifications...) to know about the four separate parts —
 * whatever's stored in that field IS the correctly-ordered display string,
 * so Firestore's existing `orderBy("fullName")`/`orderBy("displayName")`
 * queries automatically sort last-name-first with zero query changes.
 */
public final class NameFormatter {

    private NameFormatter() {}

    public static String format(String lastName, String firstName, String middleInitial, String suffix) {
        StringBuilder sb = new StringBuilder();
        sb.append(nullToEmpty(lastName).trim());
        sb.append(", ").append(nullToEmpty(firstName).trim());

        String mi = nullToEmpty(middleInitial).trim();
        if (!mi.isEmpty()) {
            if (!mi.endsWith(".")) {
                mi = mi + ".";
            }
            sb.append(" ").append(mi);
        }

        String suf = nullToEmpty(suffix).trim();
        if (!suf.isEmpty()) {
            sb.append(" ").append(suf);
        }

        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
