package edu.bsu.worgit.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import edu.bsu.worgit.domain.PeopleRows;

/** Peer matching (ST-04) and peer connections. */
public interface PeerRepository {

    /**
     * Everyone ST-04 shows to this student, unsorted: students who are visible in matching and share at
     * least one interest (Matches), plus every student already connected (Connected), whatever they share.
     */
    List<PeopleRows.PeerCandidate> peerCandidates(String studentId);

    boolean isConnected(String studentA, String studentB);

    /** Inserts the connection; a database trigger creates the Direct room. DuplicateKeyException if it exists. */
    void insertConnection(String fromStudent, String toStudent);

    Optional<UUID> peerRoomId(String studentA, String studentB);
}
