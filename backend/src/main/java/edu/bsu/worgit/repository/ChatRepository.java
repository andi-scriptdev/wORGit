package edu.bsu.worgit.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import edu.bsu.worgit.domain.PeopleRows;

/** Chat rooms and messages. Messages are read by id (the identity column), which follows sending order. */
public interface ChatRepository {

    Optional<PeopleRows.Room> findRoom(UUID roomId);

    /**
     * The rooms a student's chat list shows, unsorted: the Lobby, all five interest rooms (the service
     * marks the ones the student has not picked as locked), the rooms of the student's organizations and
     * the student's Direct rooms.
     */
    List<PeopleRows.RoomListing> studentRooms(String studentId);

    /**
     * Rooms for Chat Moderation, unsorted. Never Direct rooms. includeCampusRooms adds the Lobby and the
     * interest rooms; orgScope null = every organization room, otherwise only those organizations.
     */
    List<PeopleRows.RoomListing> moderationRooms(boolean includeCampusRooms, Set<UUID> orgScope);

    /**
     * Oldest first. afterId set: up to limit messages newer than it (polling for new messages).
     * Otherwise the newest limit messages older than beforeId (null = the latest ones).
     */
    List<PeopleRows.ChatMessage> messages(UUID roomId, Long beforeId, Long afterId, int limit);

    PeopleRows.ChatMessage insertMessage(UUID roomId, String senderId, String senderName, String message);

    Optional<PeopleRows.ChatMessage> findMessage(long messageId);

    /** True when a message was deleted. */
    boolean deleteMessage(long messageId);
}
