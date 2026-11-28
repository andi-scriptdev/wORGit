package edu.bsu.worgit.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.service.AccountService;
import edu.bsu.worgit.service.ChatModerationService;
import edu.bsu.worgit.service.NotificationService;
import edu.bsu.worgit.service.PeopleViews;

/** AD-08 Chat Moderation, AD-12 Admin profile, AD-13 Notifications. Role rules are in the services. */
@RestController
@RequestMapping("/api/admin")
public class AdminPeopleController {

    private final AccountService accounts;
    private final ChatModerationService moderation;
    private final NotificationService notifications;

    public AdminPeopleController(AccountService accounts, ChatModerationService moderation,
                                 NotificationService notifications) {
        this.accounts = accounts;
        this.moderation = moderation;
        this.notifications = notifications;
    }

    // ---------- AD-12 ----------

    @GetMapping("/profile")
    public PeopleViews.AdminProfile profile(@AuthenticationPrincipal AuthenticatedUser me) {
        return accounts.adminProfile(me);
    }

    @PutMapping("/profile")
    public PeopleViews.AdminProfile updateProfile(@AuthenticationPrincipal AuthenticatedUser me,
                                                  @RequestBody PeopleViews.AdminProfileInput body) {
        return accounts.updateAdminProfile(me, body);
    }

    @PostMapping("/profile/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser me,
                               @RequestBody PeopleViews.PasswordChange body) {
        accounts.changePassword(me, body);   // /api/admin/** already requires a staff role
    }

    // ---------- AD-08 ----------

    @GetMapping("/chats")
    public List<PeopleViews.RoomCard> rooms(@AuthenticationPrincipal AuthenticatedUser me) {
        return moderation.rooms(me);
    }

    @GetMapping("/chats/{roomId}/messages")
    public PeopleViews.MessagePage messages(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID roomId,
                                            @RequestParam(required = false) Long before,
                                            @RequestParam(required = false) Long after,
                                            @RequestParam(required = false) Integer limit) {
        return moderation.messages(me, roomId, before, after, limit);
    }

    @DeleteMapping("/chats/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long messageId) {
        moderation.deleteMessage(me, messageId);
    }

    // ---------- AD-13 ----------

    @GetMapping("/notifications")
    public PeopleViews.NotificationList notifications(@AuthenticationPrincipal AuthenticatedUser me) {
        return notifications.list(me);
    }

    @PostMapping("/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID notificationId) {
        notifications.markRead(me, notificationId);
    }
}
