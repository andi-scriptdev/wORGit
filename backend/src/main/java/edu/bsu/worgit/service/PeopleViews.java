package edu.bsu.worgit.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import edu.bsu.worgit.domain.NotificationType;
import edu.bsu.worgit.domain.RoomType;

/** What the app receives for slice 1C (sent as JSON; null fields are left out). */
public final class PeopleViews {
    private PeopleViews() {}

    // ---------- My Profile (ST-08) ----------

    /** allInterests: the five chips the profile form offers. */
    public record MyProfile(String userId, String fullName, String displayName, String email, String bio,
                            List<String> interests, boolean visibleInMatching, List<String> allInterests) {}

    /** PUT body. visibleInMatching null keeps the current value; bio null clears it. */
    public record ProfileInput(String displayName, String bio, List<String> interests, Boolean visibleInMatching) {}

    public record PasswordChange(String currentPassword, String newPassword) {}

    // ---------- admin profile (AD-12) ----------

    public record OrgRef(UUID orgId, String name) {}

    /**
     * Read-only role and organizations. allOrganizations is true for the System Administrator,
     * who manages every organization (managedOrganizations is then empty).
     */
    public record AdminProfile(String userId, String fullName, String displayName, String email, String role,
                               boolean allOrganizations, List<OrgRef> managedOrganizations) {}

    public record AdminProfileInput(String displayName) {}

    // ---------- Peer Matching (ST-04) ----------

    /** roomId (the Direct room) is set only when connected. */
    public record PeerCard(String studentId, String displayName, List<String> sharedInterests, int sharedCount,
                           boolean connected, UUID roomId) {}

    /**
     * The two tabs of ST-04, each ranked by shared interests. matches: visible students sharing at least one
     * interest (connected ones included, shown with "Message" + "Connected"). connected: every connected peer.
     * visibleInMatching is the viewer's own setting.
     */
    public record PeerMatches(boolean visibleInMatching, List<PeerCard> matches, List<PeerCard> connected) {}

    /** message: "You connected with {name}." (also stored as the initiator's notification). */
    public record Connected(String studentId, UUID roomId, String message) {}

    // ---------- Chats ----------

    public record LastMessage(String senderName, String message, Instant sentAt) {}

    /**
     * One row of the chat list. locked is only ever true for an interest room the student has not picked;
     * lockedMessage is then the text of the inline locked panel and lastMessage is left out.
     */
    public record RoomCard(UUID roomId, RoomType type, String title, String interest, UUID orgId, boolean locked,
                           String lockedMessage, LastMessage lastMessage) {}

    public record MessageView(long id, String senderId, String senderName, String message, Instant sentAt,
                              boolean mine) {}

    /** hasOlder: there are older messages before the first one (load them with before = first id). */
    public record MessagePage(UUID roomId, RoomType type, String title, boolean hasOlder, List<MessageView> messages) {}

    public record NewMessage(String message) {}

    // ---------- Notifications ----------

    public record NotificationView(UUID notificationId, NotificationType type, String message, Instant createdAt,
                                   boolean read) {}

    public record NotificationList(int unread, List<NotificationView> items) {}
}
