package edu.bsu.worgit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.RoomType;
import edu.bsu.worgit.exception.InvalidInputException;
import edu.bsu.worgit.exception.InvalidMessageException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.exception.RoomAccessException;
import edu.bsu.worgit.support.FakePeople;
import edu.bsu.worgit.support.FakeWorld;

class ChatServiceTest {

    private FakeWorld w;
    private FakePeople p;
    private ChatService svc;
    private AuthenticatedUser andrea;
    private AuthenticatedUser mika;
    private AuthenticatedUser nina;
    private UUID guild;

    @BeforeEach
    void setUp() {
        w = new FakeWorld();
        p = new FakePeople(w);
        guild = w.addOrg("Web Developers Guild", "Technology", false, "0");
        w.addOrg("Campus Media Network", "Communication", true, "0");
        andrea = p.student("25-09041", "Andrea", true, "Technology", "Design");
        mika = p.student("25-00001", "Mika", true, "Technology");
        nina = p.student("25-00003", "Nina", true, "Academic");
        w.addMember(guild, "25-09041", LocalDate.of(2026, 11, 1));
        p.connectDirectly("25-00001", "25-09041");
        svc = new ChatService(p, p, w, w);
    }

    private static PeopleViews.NewMessage msg(String text) {
        return new PeopleViews.NewMessage(text);
    }

    @Test
    void chatListHasLobbyAllFiveInterestRoomsOwnOrganizationsAndDirectInSectionOrder() {
        List<PeopleViews.RoomCard> rooms = svc.rooms(andrea);
        assertThat(rooms).extracting(PeopleViews.RoomCard::type).containsExactly(
                RoomType.LOBBY, RoomType.INTEREST, RoomType.INTEREST, RoomType.INTEREST, RoomType.INTEREST,
                RoomType.INTEREST, RoomType.ORGANIZATION, RoomType.PEER);
        assertThat(rooms).extracting(PeopleViews.RoomCard::title).containsExactly(
                "Lobby", "Academic", "Advocacy", "Communication", "Design", "Technology", "Web Developers Guild", "Mika");
        assertThat(rooms).extracting(PeopleViews.RoomCard::locked).containsExactly(
                false, true, true, true, false, false, false, false);
        assertThat(rooms.get(1).lockedMessage())
                .isEqualTo("Academic is locked. Add this interest to your profile to join this room.");
    }

    @Test
    void lockedInterestRoomsHideTheirLastMessage() {
        svc.send(nina, p.interestRoomId("Academic"), msg("secret study plans"));
        PeopleViews.RoomCard academic = svc.rooms(andrea).get(1);
        assertThat(academic.locked()).isTrue();
        assertThat(academic.lastMessage()).isNull();
        assertThat(svc.rooms(nina).get(1).lastMessage().message()).isEqualTo("secret study plans");
    }

    @Test
    void sendingTrimsAndUsesTheCurrentDisplayName() {
        PeopleViews.MessageView sent = svc.send(andrea, p.lobbyId, msg("  hello  "));
        assertThat(sent.message()).isEqualTo("hello");
        assertThat(sent.senderName()).isEqualTo("Andrea");
        assertThat(sent.mine()).isTrue();
        PeopleViews.MessagePage page = svc.messages(mika, p.lobbyId, null, null, null);
        assertThat(page.title()).isEqualTo("Lobby");
        assertThat(page.messages()).hasSize(1);
        assertThat(page.messages().get(0).mine()).isFalse();
    }

    @Test
    void messageRulesUseTheSpecMessages() {
        assertThatThrownBy(() -> svc.send(andrea, p.lobbyId, msg("   ")))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("Message cannot be empty.");
        assertThatThrownBy(() -> svc.send(andrea, p.lobbyId, msg("x".repeat(312))))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("Max 300 characters (312/300).");
        assertThat(svc.send(andrea, p.lobbyId, msg("x".repeat(300))).message()).hasSize(300);
    }

    @Test
    void interestRoomNeedsTheInterest() {
        assertThatThrownBy(() -> svc.send(andrea, p.interestRoomId("Academic"), msg("hi")))
                .isInstanceOf(RoomAccessException.class)
                .hasMessage("Academic is locked. Add this interest to your profile to join this room.");
        assertThatThrownBy(() -> svc.messages(andrea, p.interestRoomId("Academic"), null, null, null))
                .isInstanceOf(RoomAccessException.class);
        assertThat(svc.send(andrea, p.interestRoomId("Design"), msg("hi")).message()).isEqualTo("hi");
    }

    @Test
    void organizationRoomNeedsMembershipWithTheSpecMessage() {
        UUID room = p.orgRoomId(guild);
        assertThatThrownBy(() -> svc.messages(mika, room, null, null, null))
                .isInstanceOf(RoomAccessException.class)
                .hasMessage("You need to be a member of Web Developers Guild to enter this room.");
        assertThat(svc.messages(andrea, room, null, null, null).title()).isEqualTo("Web Developers Guild");
        w.removeMember(guild, "25-09041");
        assertThatThrownBy(() -> svc.send(andrea, room, msg("still here?"))).isInstanceOf(RoomAccessException.class);
    }

    @Test
    void directRoomIsOnlyForTheTwoStudentsAndTitledWithTheOtherName() {
        UUID direct = p.peerRoomId("25-09041", "25-00001").orElseThrow();
        assertThat(svc.messages(andrea, direct, null, null, null).title()).isEqualTo("Mika");
        assertThat(svc.messages(mika, direct, null, null, null).title()).isEqualTo("Andrea");
        assertThatThrownBy(() -> svc.messages(nina, direct, null, null, null)).isInstanceOf(RoomAccessException.class);
    }

    @Test
    void unknownRoomIsNotFound() {
        assertThatThrownBy(() -> svc.messages(andrea, UUID.randomUUID(), null, null, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void pagingByBeforeAndPollingByAfter() {
        for (int i = 1; i <= 5; i++) {
            svc.send(andrea, p.lobbyId, msg("m" + i));
        }
        PeopleViews.MessagePage latest = svc.messages(andrea, p.lobbyId, null, null, 2);
        assertThat(latest.messages()).extracting(PeopleViews.MessageView::message).containsExactly("m4", "m5");
        assertThat(latest.hasOlder()).isTrue();

        long firstId = latest.messages().get(0).id();
        PeopleViews.MessagePage older = svc.messages(andrea, p.lobbyId, firstId, null, 10);
        assertThat(older.messages()).extracting(PeopleViews.MessageView::message).containsExactly("m1", "m2", "m3");
        assertThat(older.hasOlder()).isFalse();

        long lastId = latest.messages().get(1).id();
        assertThat(svc.messages(andrea, p.lobbyId, null, lastId, null).messages()).isEmpty();
        svc.send(mika, p.lobbyId, msg("new"));
        assertThat(svc.messages(andrea, p.lobbyId, null, lastId, null).messages())
                .extracting(PeopleViews.MessageView::message).containsExactly("new");

        assertThatThrownBy(() -> svc.messages(andrea, p.lobbyId, 1L, 1L, null)).isInstanceOf(InvalidInputException.class);
    }
}
