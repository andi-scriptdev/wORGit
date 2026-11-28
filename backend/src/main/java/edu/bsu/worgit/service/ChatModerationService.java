package edu.bsu.worgit.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Capability;
import edu.bsu.worgit.domain.PeopleRows;
import edu.bsu.worgit.domain.Permissions;
import edu.bsu.worgit.exception.ForbiddenException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.repository.ChatRepository;
import edu.bsu.worgit.repository.OrganizationRepository;

/**
 * AD-08 Chat Moderation. System Administrator: Lobby, interest rooms and every organization room.
 * Facilitator: the rooms of the organizations they manage (p40). Advisers have no access. Direct rooms are
 * never shown to staff (privacy, decision 1). Staff read and delete; they do not post. Delete is a hard
 * delete (spec section 11, ASSUMED), so it also lowers the sender's engagement score.
 */
@Service
public class ChatModerationService {

    private final ChatRepository chats;
    private final OrganizationRepository orgs;

    public ChatModerationService(ChatRepository chats, OrganizationRepository orgs) {
        this.chats = chats;
        this.orgs = orgs;
    }

    public List<PeopleViews.RoomCard> rooms(AuthenticatedUser actor) {
        Permissions.requireGlobal(actor, Capability.MODERATE_CHAT);
        Set<UUID> scope = Permissions.visibleOrgIds(actor);
        return chats.moderationRooms(scope == null, scope).stream()
                .map(l -> ChatService.card(l, false, null))
                .sorted(ChatService.ORDER)
                .toList();
    }

    public PeopleViews.MessagePage messages(AuthenticatedUser actor, UUID roomId, Long before, Long after,
                                            Integer limit) {
        PeopleRows.Room room = chats.findRoom(roomId).orElseThrow(NotFoundException::new);
        requireModeration(actor, room);
        return ChatService.page(chats, room, title(room), actor.userId(), before, after, limit);
    }

    @Transactional
    public void deleteMessage(AuthenticatedUser actor, long messageId) {
        PeopleRows.ChatMessage message = chats.findMessage(messageId).orElseThrow(NotFoundException::new);
        PeopleRows.Room room = chats.findRoom(message.roomId()).orElseThrow(NotFoundException::new);
        requireModeration(actor, room);
        if (!chats.deleteMessage(messageId)) {
            throw new NotFoundException();
        }
    }

    private static void requireModeration(AuthenticatedUser actor, PeopleRows.Room room) {
        Permissions.requireGlobal(actor, Capability.MODERATE_CHAT);
        switch (room.type()) {
            case PEER -> throw new NotFoundException();
            case LOBBY, INTEREST -> {
                if (Permissions.visibleOrgIds(actor) != null) {   // only the System Administrator
                    throw new ForbiddenException();
                }
            }
            case ORGANIZATION -> Permissions.requireForOrg(actor, Capability.MODERATE_CHAT, room.orgId());
        }
    }

    private String title(PeopleRows.Room room) {
        if (room.orgId() != null) {
            return orgs.findOrganization(room.orgId()).map(o -> o.name()).orElse(room.name());
        }
        return room.name();
    }
}
