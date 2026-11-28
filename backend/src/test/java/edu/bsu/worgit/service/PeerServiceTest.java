package edu.bsu.worgit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.NotificationType;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.exception.ForbiddenException;
import edu.bsu.worgit.exception.InvalidInputException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.support.FakePeople;
import edu.bsu.worgit.support.FakeWorld;

class PeerServiceTest {

    private FakeWorld w;
    private FakePeople p;
    private PeerService svc;
    private AuthenticatedUser andrea;

    @BeforeEach
    void setUp() {
        w = new FakeWorld();
        p = new FakePeople(w);
        andrea = p.student("25-09041", "Andrea", true, "Technology", "Design");
        p.student("25-00001", "Mika", true, "Technology", "Design");
        p.student("25-00003", "Nina", true, "Technology");
        p.student("25-00005", "Zed", true, "Academic");               // shares nothing
        p.student("25-00004", "Hal", false, "Technology", "Design");  // hidden
        svc = new PeerService(p, p, w);
    }

    @Test
    void matchesAreRankedBySharedInterestsAndSkipSelfHiddenAndUnrelated() {
        PeopleViews.PeerMatches m = svc.matches(andrea);
        assertThat(m.matches()).extracting(PeopleViews.PeerCard::displayName).containsExactly("Mika", "Nina");
        assertThat(m.matches().get(0).sharedInterests()).containsExactly("Design", "Technology");
        assertThat(m.matches().get(0).sharedCount()).isEqualTo(2);
        assertThat(m.connected()).isEmpty();
        assertThat(m.visibleInMatching()).isTrue();
    }

    @Test
    void connectIsInstantMakesADirectRoomAndNotifiesOnlyTheInitiator() {
        PeopleViews.Connected c = svc.connect(andrea, "25-00001");
        assertThat(c.message()).isEqualTo("You connected with Mika.");
        assertThat(c.roomId()).isNotNull();
        assertThat(w.notifications()).hasSize(1);
        assertThat(w.notifications().get(0).recipientId()).isEqualTo("25-09041");
        assertThat(w.notifications().get(0).type()).isEqualTo(NotificationType.PEER_CONNECTION);
        assertThat(w.messagesFor("25-00001")).isEmpty();

        PeopleViews.PeerMatches m = svc.matches(andrea);
        PeopleViews.PeerCard mika = m.matches().get(0);
        assertThat(mika.connected()).isTrue();
        assertThat(mika.roomId()).isEqualTo(c.roomId());
        assertThat(m.connected()).extracting(PeopleViews.PeerCard::displayName).containsExactly("Mika");
    }

    @Test
    void connectingTwiceInEitherDirectionUsesTheSpecMessageWithTheStoredDisplayName() {
        AuthenticatedUser sam = p.student("25-00002", "Sam P.", true, "Technology");
        svc.connect(andrea, "25-00002");
        assertThatThrownBy(() -> svc.connect(andrea, "25-00002"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("You are already connected with Sam P.");
        assertThatThrownBy(() -> svc.connect(sam, "25-09041"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("You are already connected with Andrea.");
    }

    @Test
    void connectedPeersStayInConnectedEvenWhenHiddenOrNothingShared() {
        p.connectDirectly("25-09041", "25-00004");   // Hal is hidden
        p.connectDirectly("25-00005", "25-09041");   // Zed shares nothing
        PeopleViews.PeerMatches m = svc.matches(andrea);
        assertThat(m.connected()).extracting(PeopleViews.PeerCard::displayName).containsExactly("Hal", "Zed");
        assertThat(m.matches()).extracting(PeopleViews.PeerCard::displayName).containsExactly("Mika", "Nina");
    }

    @Test
    void onlyStudentsUnderMatchesCanBeConnectedWith() {
        assertThatThrownBy(() -> svc.connect(andrea, "25-00004")).isInstanceOf(NotFoundException.class);  // hidden
        assertThatThrownBy(() -> svc.connect(andrea, "25-00005")).isInstanceOf(NotFoundException.class);  // nothing shared
        assertThatThrownBy(() -> svc.connect(andrea, "25-09041")).isInstanceOf(NotFoundException.class);  // self
        assertThatThrownBy(() -> svc.connect(andrea, "99-99999")).isInstanceOf(NotFoundException.class);  // unknown
        assertThat(w.notifications()).isEmpty();
    }

    @Test
    void staffCannotUsePeerMatching() {
        AuthenticatedUser admin = w.addStaff("ADM-002", Role.SYSTEM_ADMINISTRATOR);
        assertThatThrownBy(() -> svc.matches(admin)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.connect(admin, "25-00001")).isInstanceOf(ForbiddenException.class);
    }
}
