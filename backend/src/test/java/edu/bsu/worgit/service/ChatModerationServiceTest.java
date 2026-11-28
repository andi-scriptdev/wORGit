package edu.bsu.worgit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.domain.RoomType;
import edu.bsu.worgit.exception.ForbiddenException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.support.FakePeople;
import edu.bsu.worgit.support.FakeWorld;

class ChatModerationServiceTest {

    private FakeWorld w;
    private FakePeople p;
    private ChatService chat;
    private ChatModerationService svc;
    private AuthenticatedUser andrea;
    private AuthenticatedUser frenzy;
    private AuthenticatedUser charles;
    private AuthenticatedUser adviser;
    private UUID guild;
    private UUID media;

    @BeforeEach
    void setUp() {
        w = new FakeWorld();
        p = new FakePeople(w);
        guild = w.addOrg("Web Developers Guild", "Technology", false, "0");
        media = w.addOrg("Campus Media Network", "Communication", true, "0");
        andrea = p.student("25-09041", "Andrea", true, "Technology");
        p.student("25-00001", "Mika", true, "Technology");
        w.addMember(guild, "25-09041", LocalDate.of(2026, 11, 1));
        w.addMember(media, "25-09041", LocalDate.of(2026, 11, 1));
        p.connectDirectly("25-09041", "25-00001");
        frenzy = w.addStaff("ADM-001", Role.ORGANIZATION_FACILITATOR, media);
        charles = w.addStaff("ADM-002", Role.SYSTEM_ADMINISTRATOR);
        adviser = w.addStaff("ADM-003", Role.FACULTY_ADVISER, media);
        chat = new ChatService(p, p, w, w);
        svc = new ChatModerationService(p, w);
    }

    @Test
    void systemAdministratorSeesCampusAndAllOrganizationRoomsButNoDirectRooms() {
        assertThat(svc.rooms(charles)).extracting(PeopleViews.RoomCard::type).containsExactly(
                RoomType.LOBBY, RoomType.INTEREST, RoomType.INTEREST, RoomType.INTEREST, RoomType.INTEREST,
                RoomType.INTEREST, RoomType.ORGANIZATION, RoomType.ORGANIZATION);
    }

    @Test
    void facilitatorSeesOnlyOwnOrganizationRooms() {
        assertThat(svc.rooms(frenzy)).extracting(PeopleViews.RoomCard::title).containsExactly("Campus Media Network");
        assertThatThrownBy(() -> svc.messages(frenzy, p.orgRoomId(guild), null, null, null))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.messages(frenzy, p.lobbyId, null, null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void advisersHaveNoChatModeration() {
        assertThatThrownBy(() -> svc.rooms(adviser)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.messages(adviser, p.orgRoomId(media), null, null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void directRoomsAreInvisibleToStaff() {
        UUID direct = p.peerRoomId("25-09041", "25-00001").orElseThrow();
        long id = chat.send(andrea, direct, new PeopleViews.NewMessage("private")).id();
        assertThatThrownBy(() -> svc.messages(charles, direct, null, null, null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> svc.deleteMessage(charles, id)).isInstanceOf(NotFoundException.class);
        assertThat(p.messageCount()).isEqualTo(1);
    }

    @Test
    void facilitatorDeletesInOwnOrganizationRoomOnly() {
        long inMedia = chat.send(andrea, p.orgRoomId(media), new PeopleViews.NewMessage("spam")).id();
        long inGuild = chat.send(andrea, p.orgRoomId(guild), new PeopleViews.NewMessage("ok")).id();
        assertThat(svc.messages(frenzy, p.orgRoomId(media), null, null, null).messages()).hasSize(1);
        svc.deleteMessage(frenzy, inMedia);
        assertThat(svc.messages(frenzy, p.orgRoomId(media), null, null, null).messages()).isEmpty();
        assertThatThrownBy(() -> svc.deleteMessage(frenzy, inGuild)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> svc.deleteMessage(frenzy, inMedia)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void systemAdministratorDeletesInTheLobby() {
        long id = chat.send(andrea, p.lobbyId, new PeopleViews.NewMessage("rude")).id();
        svc.deleteMessage(charles, id);
        assertThat(p.messageCount()).isEqualTo(0);
    }

    @Test
    void studentsCannotModerate() {
        assertThatThrownBy(() -> svc.rooms(andrea)).isInstanceOf(ForbiddenException.class);
    }
}
