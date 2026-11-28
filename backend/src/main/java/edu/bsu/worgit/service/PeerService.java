package edu.bsu.worgit.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Messages;
import edu.bsu.worgit.domain.NotificationType;
import edu.bsu.worgit.domain.PeopleRows;
import edu.bsu.worgit.domain.Permissions;
import edu.bsu.worgit.exception.InvalidInputException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.repository.NotificationRepository;
import edu.bsu.worgit.repository.PeerRepository;
import edu.bsu.worgit.repository.ProfileRepository;

/** ST-04 Peer Matching: tabs Matches / Connected, and the instant Connect. */
@Service
public class PeerService {

    /** Most shared interests first, then by display name. */
    static final Comparator<PeopleRows.PeerCandidate> ORDER =
            Comparator.comparingInt((PeopleRows.PeerCandidate c) -> c.sharedInterests().size()).reversed()
                    .thenComparing(c -> c.displayName().toLowerCase(Locale.ROOT))
                    .thenComparing(PeopleRows.PeerCandidate::studentId);

    private final PeerRepository peers;
    private final ProfileRepository profiles;
    private final NotificationRepository notifications;

    public PeerService(PeerRepository peers, ProfileRepository profiles, NotificationRepository notifications) {
        this.peers = peers;
        this.profiles = profiles;
        this.notifications = notifications;
    }

    /**
     * Matches: visible students who share at least one interest (self and hidden students excluded, ASSUMED).
     * Connected: everyone the student is connected with. Turning one's own visibility off only hides
     * oneself from other students' Matches.
     */
    public PeopleViews.PeerMatches matches(AuthenticatedUser student) {
        Permissions.requireStudent(student);
        PeopleRows.StudentProfile me = profiles.findStudentProfile(student.userId()).orElseThrow(NotFoundException::new);
        List<PeopleRows.PeerCandidate> all = peers.peerCandidates(student.userId()).stream().sorted(ORDER).toList();
        return new PeopleViews.PeerMatches(me.visibleInMatching(),
                all.stream().filter(PeopleRows.PeerCandidate::isMatch).map(PeerService::card).toList(),
                all.stream().filter(PeopleRows.PeerCandidate::connected).map(PeerService::card).toList());
    }

    /**
     * Connect is instant (no accept step, ASSUMED). Only a student listed under Matches can be connected
     * with; anyone else (self, hidden, no shared interest, unknown) answers 404. The database creates the
     * Direct room. Only the initiator gets the PEER_CONNECTION notification (spec section 6).
     */
    @Transactional
    public PeopleViews.Connected connect(AuthenticatedUser student, String peerId) {
        Permissions.requireStudent(student);
        PeopleRows.PeerCandidate peer = peers.peerCandidates(student.userId()).stream()
                .filter(c -> c.studentId().equals(peerId))
                .findFirst()
                .orElseThrow(NotFoundException::new);
        if (peer.connected()) {
            throw new InvalidInputException(Messages.alreadyConnected(peer.displayName()));
        }
        if (!peer.isMatch()) {
            throw new NotFoundException();
        }
        try {
            peers.insertConnection(student.userId(), peerId);
        } catch (DuplicateKeyException e) {
            throw new InvalidInputException(Messages.alreadyConnected(peer.displayName()));
        }
        UUID roomId = peers.peerRoomId(student.userId(), peerId).orElse(null);
        String message = Messages.connected(peer.displayName());
        notifications.insertNotification(student.userId(), NotificationType.PEER_CONNECTION, message);
        return new PeopleViews.Connected(peerId, roomId, message);
    }

    private static PeopleViews.PeerCard card(PeopleRows.PeerCandidate c) {
        return new PeopleViews.PeerCard(c.studentId(), c.displayName(), c.sharedInterests(),
                c.sharedInterests().size(), c.connected(), c.roomId());
    }
}
