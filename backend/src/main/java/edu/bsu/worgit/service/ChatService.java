package edu.bsu.worgit.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Messages;
import edu.bsu.worgit.domain.PeopleRows;
import edu.bsu.worgit.domain.Permissions;
import edu.bsu.worgit.domain.RoomType;
import edu.bsu.worgit.domain.Rules;
import edu.bsu.worgit.exception.InvalidInputException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.exception.RoomAccessException;
import edu.bsu.worgit.repository.ChatRepository;
import edu.bsu.worgit.repository.MembershipRepository;
import edu.bsu.worgit.repository.OrganizationRepository;
import edu.bsu.worgit.repository.ProfileRepository;

/**
 * ST-05 Chats for students. Access: Lobby = every student; interest room = the student has that interest
 * (otherwise the inline locked panel); organization room = member; Direct room = one of the two students.
 * The app refreshes by polling with "after" (every 3-5 s, ASSUMED). Message text is stored as plain text.
 */
@Service
public class ChatService {

    static final int DEFAULT_PAGE = 50;   // ASSUMED
    static final int MAX_PAGE = 100;      // ASSUMED

    /** Section order of the chat list (LOBBY, INTEREST ROOMS, MY ORGANIZATIONS, DIRECT), then by title. */
    static final Comparator<PeopleViews.RoomCard> ORDER =
            Comparator.comparingInt((PeopleViews.RoomCard r) -> r.type().ordinal())
                    .thenComparing(r -> r.title() == null ? "" : r.title().toLowerCase(Locale.ROOT))
                    .thenComparing(PeopleViews.RoomCard::roomId);

    private final ChatRepository chats;
    private final ProfileRepository profiles;
    private final MembershipRepository members;
    private final OrganizationRepository orgs;

    public ChatService(ChatRepository chats, ProfileRepository profiles, MembershipRepository members,
                       OrganizationRepository orgs) {
        this.chats = chats;
        this.profiles = profiles;
        this.members = members;
        this.orgs = orgs;
    }

    /** All rooms of the chat list. Locked interest rooms carry the locked-panel text and no last message. */
    public List<PeopleViews.RoomCard> rooms(AuthenticatedUser student) {
        Permissions.requireStudent(student);
        Set<String> interests = Set.copyOf(requireProfile(student.userId()).interests());
        return chats.studentRooms(student.userId()).stream()
                .map(l -> {
                    PeopleRows.Room room = l.room();
                    boolean locked = room.type() == RoomType.INTEREST && !interests.contains(room.interest());
                    return card(l, locked, locked ? Messages.interestRoomLocked(room.interest()) : null);
                })
                .sorted(ORDER)
                .toList();
    }

    /**
     * Messages oldest first. after: only newer messages (polling). before: older messages (scrolling up).
     * Neither: the latest page. limit defaults to 50, at most 100.
     */
    public PeopleViews.MessagePage messages(AuthenticatedUser student, UUID roomId, Long before, Long after,
                                            Integer limit) {
        Permissions.requireStudent(student);
        PeopleRows.Room room = requireAccess(student, roomId);
        return page(chats, room, titleFor(room, student.userId()), student.userId(), before, after, limit);
    }

    /** 1-300 characters after trimming; the sender name is the display name at the time of sending. */
    @Transactional
    public PeopleViews.MessageView send(AuthenticatedUser student, UUID roomId, PeopleViews.NewMessage body) {
        Permissions.requireStudent(student);
        PeopleRows.Room room = requireAccess(student, roomId);
        String text = Rules.requireValidMessage(body == null ? null : body.message());
        PeopleRows.ChatMessage saved = chats.insertMessage(room.roomId(), student.userId(), student.displayName(), text);
        return view(saved, student.userId());
    }

    private PeopleRows.Room requireAccess(AuthenticatedUser student, UUID roomId) {
        PeopleRows.Room room = chats.findRoom(roomId).orElseThrow(NotFoundException::new);
        switch (room.type()) {
            case LOBBY -> { }
            case INTEREST -> {
                if (!requireProfile(student.userId()).interests().contains(room.interest())) {
                    throw new RoomAccessException(Messages.interestRoomLocked(room.interest()));
                }
            }
            case ORGANIZATION -> {
                if (!members.isMember(student.userId(), room.orgId())) {
                    String orgName = orgs.findOrganization(room.orgId()).map(o -> o.name()).orElse(room.name());
                    throw new RoomAccessException(Messages.roomAccess(orgName));
                }
            }
            case PEER -> {
                if (!room.hasParticipant(student.userId())) {
                    throw new RoomAccessException(Messages.ACCESS_DENIED);
                }
            }
        }
        return room;
    }

    private String titleFor(PeopleRows.Room room, String viewerId) {
        return switch (room.type()) {
            case LOBBY, INTEREST -> room.name();
            case ORGANIZATION -> orgs.findOrganization(room.orgId()).map(o -> o.name()).orElse(room.name());
            case PEER -> profiles.findStudentProfile(room.otherParticipant(viewerId))
                    .map(PeopleRows.StudentProfile::displayName).orElse(room.name());
        };
    }

    private PeopleRows.StudentProfile requireProfile(String studentId) {
        return profiles.findStudentProfile(studentId).orElseThrow(NotFoundException::new);
    }

    // ---------- shared with ChatModerationService ----------

    static PeopleViews.RoomCard card(PeopleRows.RoomListing l, boolean locked, String lockedMessage) {
        PeopleRows.Room room = l.room();
        PeopleViews.LastMessage last = locked || l.lastMessage() == null ? null
                : new PeopleViews.LastMessage(l.lastSenderName(), l.lastMessage(), l.lastSentAt());
        return new PeopleViews.RoomCard(room.roomId(), room.type(), l.title(), room.interest(), room.orgId(),
                locked, lockedMessage, last);
    }

    static PeopleViews.MessagePage page(ChatRepository chats, PeopleRows.Room room, String title, String viewerId,
                                        Long before, Long after, Integer limit) {
        if (before != null && after != null) {
            throw new InvalidInputException(Messages.INVALID_REQUEST);
        }
        int size = limit == null ? DEFAULT_PAGE : Math.max(1, Math.min(MAX_PAGE, limit));
        List<PeopleRows.ChatMessage> found;
        boolean hasOlder;
        if (after != null) {
            found = chats.messages(room.roomId(), null, after, size);
            hasOlder = false;   // the app already holds everything up to "after"
        } else {
            List<PeopleRows.ChatMessage> plusOne = chats.messages(room.roomId(), before, null, size + 1);
            hasOlder = plusOne.size() > size;
            found = hasOlder ? plusOne.subList(1, plusOne.size()) : plusOne;
        }
        return new PeopleViews.MessagePage(room.roomId(), room.type(), title, hasOlder,
                found.stream().map(m -> view(m, viewerId)).toList());
    }

    static PeopleViews.MessageView view(PeopleRows.ChatMessage m, String viewerId) {
        return new PeopleViews.MessageView(m.id(), m.senderId(), m.senderName(), m.message(), m.sentAt(),
                m.senderId().equals(viewerId));
    }
}
