package edu.bsu.worgit.domain;

/**
 * User-facing texts. The first block is copied from the spec (section 8) and must stay exact.
 * The second block is ASSUMED: the spec gives no wording for these cases.
 */
public final class Messages {
    private Messages() {}

    // ---- from the spec ----
    public static final String PASSWORD_TOO_SHORT = "Password must be at least 8 characters";
    public static final String PICK_INTEREST = "Pick at least one interest.";
    public static final String AUTH_FAILED = "ID or password is incorrect";
    public static final String MESSAGE_EMPTY = "Message cannot be empty.";
    public static final String BIO_TOO_LONG = "Bio must be 150 characters or fewer";
    public static final String DATA_STORE = "We could not reach the database. Try again.";

    public static String messageTooLong(int length) {
        return "Max 300 characters (" + length + "/300).";
    }

    public static String roomAccess(String orgName) {
        return "You need to be a member of " + orgName + " to enter this room.";
    }

    public static String alreadyMember(String orgName) {
        return sentence("You are already a member of " + orgName);
    }

    /** "Mika S." must not become "Mika S..": the spec's sample shows a single period. */
    public static String alreadyConnected(String name) {
        return sentence("You are already connected with " + name);
    }

    public static String notEligible(String name) {
        return name + " has no recorded attendance, so no certificate was created.";
    }

    // ---- ASSUMED wording ----
    public static final String DISPLAY_NAME_LENGTH = "Display name must be 1 to 30 characters";
    public static final String UNKNOWN_INTEREST = "Choose interests from the list.";
    public static final String TOO_MANY_ATTEMPTS = "Too many attempts. Try again in a few minutes.";
    public static final String UNAUTHENTICATED = "Please log in.";
    public static final String ACCESS_DENIED = "You do not have access to this.";
    public static final String NOT_FOUND = "Page not found.";
    public static final String INVALID_REQUEST = "A required value was missing or malformed.";
    public static final String SERVICE_ERROR = "Something went wrong on our side. Try again.";

    // ---- slice 1B ----
    public static final String NAME_REQUIRED = "Name is required.";   // from the spec (p27)

    // ASSUMED wording
    public static final String APPROVAL_REQUIRED = "This organization needs approval. Apply instead.";
    public static final String NO_APPROVAL_NEEDED = "This organization does not need approval. Join instead.";
    public static final String ALREADY_PENDING = "Your application is already pending.";
    public static final String ALREADY_DECIDED = "This application was already decided.";
    public static final String BAD_CATEGORY = "Choose a category from the list.";
    public static final String FEE_NEGATIVE = "Membership fee cannot be negative.";
    public static final String FEE_TOO_LARGE = "Membership fee is too large.";
    public static final String BAD_SOCIAL_LINK = "Each social link needs a platform and a link starting with http:// or https://";
    public static final String AMOUNT_POSITIVE = "Amount must be greater than 0.";
    public static final String ALREADY_PAID = "This payment is already marked as paid.";

    public static String notMember(String orgName) {
        return sentence("You are not a member of " + orgName);
    }

    public static String studentNotMember(String studentName, String orgName) {
        return sentence(studentName + " is not a member of " + orgName);
    }

    public static String nameTaken(String name) {
        return sentence("An organization named " + name + " already exists");
    }

    public static String cannotRemoveOrg(String orgName, int certificates) {
        return "Cannot remove " + orgName + ": " + certificates + " issued certificate"
                + (certificates == 1 ? "" : "s") + " would be deleted.";
    }

    /** Student notification; the "approved" wording is from the v2 wireframe. */
    public static String applicationDecided(String orgName, boolean approved) {
        return "Application to " + orgName + " was " + (approved ? "approved" : "rejected");
    }

    public static String newApplication(String studentName, String orgName) {
        return studentName + " applied to " + orgName;
    }

    public static String applicationDecidedForAdviser(String studentName, String orgName, boolean approved) {
        return "Application of " + studentName + " to " + orgName + " was " + (approved ? "approved" : "rejected");
    }

    private static String sentence(String text) {
        return text.endsWith(".") ? text : text + ".";
    }

    public static String orgTextTooLong(String label) {
        return label + " must be 250 characters or fewer";
    }
}
