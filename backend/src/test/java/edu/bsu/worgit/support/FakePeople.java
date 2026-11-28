package edu.bsu.worgit.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.PeopleRows;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.domain.RoomType;
import edu.bsu.worgit.domain.UserAccount;
import edu.bsu.worgit.repository.ChatRepository;
import edu.bsu.worgit.repository.PeerRepository;
import edu.bsu.worgit.repository.ProfileRepository;

/**
 * In-memory profiles, peer connections and chat for slice 1C service tests. Accounts, organizations,
 * memberships and notifications live in the FakeWorld it is built on.
 */
public class FakePeople implements ProfileRepository, PeerRepository, ChatRepository {

    private static final class Student {
        String bio = "";
        boolean visible;
        Set<String> interests = new LinkedHashSet<>();
    }

    private final FakeWorld world;
    private final Map<String, Student> students = new HashMap<>();
    private final Map<UUID, PeopleRows.Room> rooms = new LinkedHashMap<>();
    private final List<String[]> connections = new ArrayList<>();
    private final List<PeopleRows.ChatMessage> messages = new ArrayList<>();
    private long nextMessageId = 1;
    public final UUID lobbyId;

    public FakePeople(FakeWorld world) {
        this.world = world;
        lobbyId = addRoom(new PeopleRows.Room(UUID.randomUUID(), RoomType.LOBBY, "Lobby", null, null, null, null));
        for (String interest : FakeWorld.INTERESTS.stream().sorted().toList()) {
            addRoom(new PeopleRows.Room(UUID.randomUUID(), RoomType.INTEREST, interest, interest, null, null, null));
        }
    }

    private UUID addRoom(PeopleRows.Room room) {
        rooms.put(room.roomId(), room);
        return room.roomId();
    }

    // ---------- setup helpers ----------

    public AuthenticatedUser student(String id, String displayName, boolean visible, String... interests) {
        UserAccount account = new UserAccount(id, displayName + " Fullname", displayName,
                id + "@g.batstate-u.edu.ph", "hash", Role.STUDENT, Set.of());
        world.putAccount(account);
        Student s = new Student();
        s.visible = visible;
        s.interests.addAll(List.of(interests));
        students.put(id, s);
        return AuthenticatedUser.from(account);
    }

    public void setPasswordHash(String userId, String hash) {
        updatePasswordHash(userId, hash);
    }

    public String passwordHashOf(String userId) {
        return world.findById(userId).orElseThrow().passwordHash();
    }

    public UUID interestRoomId(String interest) {
        return rooms.values().stream().filter(r -> interest.equals(r.interest())).findFirst().orElseThrow().roomId();
    }

    /** The organization's room (created on first use, with the FakeWorld's room id). */
    public UUID orgRoomId(UUID orgId) {
        UUID id = world.roomIdOf(orgId);
        rooms.computeIfAbsent(id, k -> new PeopleRows.Room(k, RoomType.ORGANIZATION,
                world.findOrganization(orgId).orElseThrow().name(), null, orgId, null, null));
        return id;
    }

    public void connectDirectly(String a, String b) {
        insertConnection(a, b);
    }

    public int messageCount() {
        return messages.size();
    }

    // ---------- ProfileRepository ----------

    @Override
    public Optional<PeopleRows.StudentProfile> findStudentProfile(String studentId) {
        Student s = students.get(studentId);
        if (s == null) {
            return Optional.empty();
        }
        UserAccount a = world.findById(studentId).orElseThrow();
        return Optional.of(new PeopleRows.StudentProfile(a.userId(), a.fullName(), a.displayName(), a.email(),
                s.bio, s.visible, s.interests.stream().sorted().toList()));
    }

    @Override
    public void updateStudentProfile(String studentId, String displayName, String bio, boolean visibleInMatching) {
        updateDisplayName(studentId, displayName);
        Student s = students.get(studentId);
        s.bio = bio;
        s.visible = visibleInMatching;
    }

    @Override
    public void replaceStudentInterests(String studentId, Set<String> interests) {
        students.get(studentId).interests = new LinkedHashSet<>(interests);
    }

    @Override
    public void updateDisplayName(String userId, String displayName) {
        UserAccount a = world.findById(userId).orElseThrow();
        world.putAccount(new UserAccount(a.userId(), a.fullName(), displayName, a.email(), a.passwordHash(),
                a.role(), a.managedOrgIds()));
    }

    @Override
    public void updatePasswordHash(String userId, String passwordHash) {
        UserAccount a = world.findById(userId).orElseThrow();
        world.putAccount(new UserAccount(a.userId(), a.fullName(), a.displayName(), a.email(), passwordHash,
                a.role(), a.managedOrgIds()));
    }

    // ---------- PeerRepository ----------

    @Override
    public List<PeopleRows.PeerCandidate> peerCandidates(String studentId) {
        Set<String> mine = students.get(studentId).interests;
        List<PeopleRows.PeerCandidate> result = new ArrayList<>();
        students.forEach((id, s) -> {
            if (id.equals(studentId)) {
                return;
            }
            List<String> shared = s.interests.stream().filter(mine::contains).sorted().toList();
            boolean connected = isConnected(studentId, id);
            if (connected || (s.visible && !shared.isEmpty())) {
                result.add(new PeopleRows.PeerCandidate(id, world.findById(id).orElseThrow().displayName(), shared,
                        s.visible, connected, connected ? peerRoomId(studentId, id).orElseThrow() : null));
            }
        });
        return result;
    }

    @Override
    public boolean isConnected(String a, String b) {
        return connections.stream().anyMatch(c -> (c[0].equals(a) && c[1].equals(b)) || (c[0].equals(b) && c[1].equals(a)));
    }

    /** Like the database: a duplicate in either direction fails, and a trigger creates the Direct room. */
    @Override
    public void insertConnection(String from, String to) {
        if (isConnected(from, to)) {
            throw new DuplicateKeyException("peer_pair_uq");
        }
        connections.add(new String[] { from, to });
        addRoom(new PeopleRows.Room(UUID.randomUUID(), RoomType.PEER, "Direct", null, null, from, to));
    }

    @Override
    public Optional<UUID> peerRoomId(String a, String b) {
        return rooms.values().stream()
                .filter(r -> r.type() == RoomType.PEER && r.hasParticipant(a) && r.hasParticipant(b))
                .map(PeopleRows.Room::roomId)
                .findFirst();
    }

    // ---------- ChatRepository ----------

    @Override
    public Optional<PeopleRows.Room> findRoom(UUID roomId) {
        for (var org : world.searchOrganizations(null, null, null)) {
            orgRoomId(org.orgId());
        }
        return Optional.ofNullable(rooms.get(roomId));
    }

    private PeopleRows.RoomListing listing(PeopleRows.Room r, String viewer) {
        String title = switch (r.type()) {
            case ORGANIZATION -> world.findOrganization(r.orgId()).orElseThrow().name();
            case PEER -> world.findById(r.otherParticipant(viewer)).orElseThrow().displayName();
            default -> r.name();
        };
        Optional<PeopleRows.ChatMessage> last = messages.stream().filter(m -> m.roomId().equals(r.roomId()))
                .max(Comparator.comparingLong(PeopleRows.ChatMessage::id));
        return new PeopleRows.RoomListing(r, title, last.map(PeopleRows.ChatMessage::senderName).orElse(null),
                last.map(PeopleRows.ChatMessage::message).orElse(null), last.map(PeopleRows.ChatMessage::sentAt).orElse(null));
    }

    @Override
    public List<PeopleRows.RoomListing> studentRooms(String studentId) {
        Set<UUID> myOrgs = world.memberOrgIds(studentId);
        myOrgs.forEach(this::orgRoomId);
        return rooms.values().stream()
                .filter(r -> switch (r.type()) {
                    case LOBBY, INTEREST -> true;
                    case ORGANIZATION -> myOrgs.contains(r.orgId());
                    case PEER -> r.hasParticipant(studentId);
                })
                .map(r -> listing(r, studentId))
                .toList();
    }

    @Override
    public List<PeopleRows.RoomListing> moderationRooms(boolean includeCampusRooms, Set<UUID> orgScope) {
        findRoom(lobbyId);   // makes sure every organization room exists
        return rooms.values().stream()
                .filter(r -> switch (r.type()) {
                    case LOBBY, INTEREST -> includeCampusRooms;
                    case ORGANIZATION -> orgScope == null || orgScope.contains(r.orgId());
                    case PEER -> false;
                })
                .map(r -> listing(r, ""))
                .toList();
    }

    @Override
    public List<PeopleRows.ChatMessage> messages(UUID roomId, Long beforeId, Long afterId, int limit) {
        List<PeopleRows.ChatMessage> inRoom = messages.stream().filter(m -> m.roomId().equals(roomId)).toList();
        if (afterId != null) {
            return inRoom.stream().filter(m -> m.id() > afterId).limit(limit).toList();
        }
        List<PeopleRows.ChatMessage> older = inRoom.stream().filter(m -> beforeId == null || m.id() < beforeId).toList();
        return older.subList(Math.max(0, older.size() - limit), older.size());
    }

    @Override
    public PeopleRows.ChatMessage insertMessage(UUID roomId, String senderId, String senderName, String message) {
        long id = nextMessageId++;
        PeopleRows.ChatMessage m = new PeopleRows.ChatMessage(id, roomId, senderId, senderName, message,
                Instant.parse("2026-11-24T02:00:00Z").plusSeconds(id));
        messages.add(m);
        return m;
    }

    @Override
    public Optional<PeopleRows.ChatMessage> findMessage(long messageId) {
        return messages.stream().filter(m -> m.id() == messageId).findFirst();
    }

    @Override
    public boolean deleteMessage(long messageId) {
        return messages.removeIf(m -> m.id() == messageId);
    }
}
