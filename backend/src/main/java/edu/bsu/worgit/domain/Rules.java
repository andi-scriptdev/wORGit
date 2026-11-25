package edu.bsu.worgit.domain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.OptionalInt;
import java.util.Set;

import edu.bsu.worgit.exception.InvalidInputException;
import edu.bsu.worgit.exception.InvalidMessageException;
import edu.bsu.worgit.exception.InvalidPasswordException;
import edu.bsu.worgit.exception.InvalidProfileException;

/**
 * Pure domain rules of spec section 8. Lengths are counted in Unicode code points, the same way
 * PostgreSQL's char_length() counts them, so a value accepted here can never fail a DB CHECK.
 */
public final class Rules {
    private Rules() {}

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_MESSAGE_LENGTH = 300;
    public static final int MAX_DISPLAY_NAME_LENGTH = 30;
    public static final int MAX_BIO_LENGTH = 150;
    public static final int MAX_ORG_TEXT_LENGTH = 250;

    static int length(String s) {
        return s.codePointCount(0, s.length());
    }

    /** New password >= 8 characters. */
    public static void requireValidNewPassword(String password) {
        if (password == null || length(password) < MIN_PASSWORD_LENGTH) {
            throw new InvalidPasswordException();
        }
    }

    /** Message 1-300 characters after trimming (trimming is ASSUMED). Returns the text to store. */
    public static String requireValidMessage(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty()) {
            throw new InvalidMessageException(Messages.MESSAGE_EMPTY);
        }
        int n = length(text);
        if (n > MAX_MESSAGE_LENGTH) {
            throw new InvalidMessageException(Messages.messageTooLong(n));
        }
        return text;
    }

    /** Display name 1-30 characters after trimming. Returns the text to store. */
    public static String requireValidDisplayName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty() || length(name) > MAX_DISPLAY_NAME_LENGTH) {
            throw new InvalidProfileException(Messages.DISPLAY_NAME_LENGTH, "displayName");
        }
        return name;
    }

    /** Bio <= 150 characters; null becomes an empty bio. */
    public static String requireValidBio(String raw) {
        String bio = raw == null ? "" : raw;
        if (length(bio) > MAX_BIO_LENGTH) {
            throw new InvalidProfileException(Messages.BIO_TOO_LONG, "bio");
        }
        return bio;
    }

    /** At least one interest, each one from the allowed list. Returns them without duplicates. */
    public static Set<String> requireInterests(Collection<String> chosen, Set<String> allowed) {
        if (chosen == null || chosen.isEmpty()) {
            throw new InvalidInputException(Messages.PICK_INTEREST, "interests");
        }
        Set<String> result = new LinkedHashSet<>();
        for (String interest : chosen) {
            if (interest == null || !allowed.contains(interest)) {
                throw new InvalidInputException(Messages.UNKNOWN_INTEREST, "interests");
            }
            result.add(interest);
        }
        return result;
    }

    /** Organization profile text (description, contact, social URL) <= 250 characters. */
    public static String requireOrgText(String label, String field, String value) {
        if (value == null) {
            return null;
        }
        if (length(value) > MAX_ORG_TEXT_LENGTH) {
            throw new InvalidInputException(Messages.orgTextTooLong(label), field);
        }
        return value;
    }

    /** Score = orgs x 10 + messages x 2 + peers x 5. */
    public static int engagementScore(int orgs, int messages, int peers) {
        return orgs * 10 + messages * 2 + peers * 5;
    }

    /** Participation rate = attended / joined events x 100, rounded; empty when there are no events ("-"). */
    public static OptionalInt participationRate(int attended, int joinedEvents) {
        if (joinedEvents <= 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) Math.round(attended * 100.0 / joinedEvents));
    }
}
