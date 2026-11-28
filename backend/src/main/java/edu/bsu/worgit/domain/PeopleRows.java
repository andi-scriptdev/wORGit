package edu.bsu.worgit.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models of slice 1C (profile, peers, chat, notifications) returned by the repositories. */
public final class PeopleRows {
    private PeopleRows() {}

    /** A student's own profile (users + students + student_interests). Interests are sorted by name. */
    public record StudentProfile(String userId, String fullName, String displayName, String email, String bio,
                                 boolean visibleInMatching, List<String> interests) {}

    /**
     * Another student for ST-04: either visible in matching with at least one shared interest (Matches tab),
     * or already connected with the viewer (Connected tab), or both. roomId is the Direct room, set only
     * when connected.
     */
    public record PeerCandidate(String studentId, String displayName, List<String> sharedInterests,
                                boolean visibleInMatching, boolean connected, UUID roomId) {

        public boolean isMatch() {
            return visibleInMatching && !sharedInterests.isEmpty();
        }
    }

    /** A chat_rooms row. Only the fields of its room type are set (see the CHECK in the schema). */
    public record Room(UUID roomId, RoomType type, String name, String interest, UUID orgId,
                       String studentAId, String studentBId) {

        public boolean hasParticipant(String userId) {
            return userId != null && (userId.equals(studentAId) || userId.equals(studentBId));
        }

        public String otherParticipant(String userId) {
            return userId.equals(studentAId) ? studentBId : studentAId;
        }
    }

    /**
     * A room in a chat list. title is the organization's current name for organization rooms and the
     * other student's display name for Direct rooms (as seen by the viewer). last* are null in an empty room.
     */
    public record RoomListing(Room room, String title, String lastSenderName, String lastMessage,
                              Instant lastSentAt) {}

    public record ChatMessage(long id, UUID roomId, String senderId, String senderName, String message,
                              Instant sentAt) {}

    public record Notification(UUID notificationId, NotificationType type, String message, Instant createdAt,
                               boolean read) {}
}
