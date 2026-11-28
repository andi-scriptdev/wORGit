package edu.bsu.worgit.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import edu.bsu.worgit.service.ChatService;
import edu.bsu.worgit.service.NotificationService;
import edu.bsu.worgit.service.PeerService;
import edu.bsu.worgit.service.PeopleViews;
import edu.bsu.worgit.service.ProfileService;

/** ST-04 Peer Matching, ST-05 Chats, ST-08 My Profile, ST-09 Notifications. Students only (SecurityConfig). */
@RestController
@RequestMapping("/api/student")
public class StudentPeopleController {

    private final ProfileService profiles;
    private final AccountService accounts;
    private final PeerService peers;
    private final ChatService chats;
    private final NotificationService notifications;

    public StudentPeopleController(ProfileService profiles, AccountService accounts, PeerService peers,
                                   ChatService chats, NotificationService notifications) {
        this.profiles = profiles;
        this.accounts = accounts;
        this.peers = peers;
        this.chats = chats;
        this.notifications = notifications;
    }

    // ---------- ST-08 ----------

    @GetMapping("/profile")
    public PeopleViews.MyProfile profile(@AuthenticationPrincipal AuthenticatedUser me) {
        return profiles.get(me);
    }

    @PutMapping("/profile")
    public PeopleViews.MyProfile updateProfile(@AuthenticationPrincipal AuthenticatedUser me,
                                               @RequestBody PeopleViews.ProfileInput body) {
        return profiles.update(me, body);
    }

    @PostMapping("/profile/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser me,
                               @RequestBody PeopleViews.PasswordChange body) {
        accounts.changePassword(me, body);
    }

    // ---------- ST-04 ----------

    @GetMapping("/peers")
    public PeopleViews.PeerMatches peers(@AuthenticationPrincipal AuthenticatedUser me) {
        return peers.matches(me);
    }

    @PostMapping("/peers/{studentId}/connect")
    public PeopleViews.Connected connect(@AuthenticationPrincipal AuthenticatedUser me,
                                         @PathVariable String studentId) {
        return peers.connect(me, studentId);
    }

    // ---------- ST-05 ----------

    @GetMapping("/chats")
    public List<PeopleViews.RoomCard> rooms(@AuthenticationPrincipal AuthenticatedUser me) {
        return chats.rooms(me);
    }

    @GetMapping("/chats/{roomId}/messages")
    public PeopleViews.MessagePage messages(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID roomId,
                                            @RequestParam(required = false) Long before,
                                            @RequestParam(required = false) Long after,
                                            @RequestParam(required = false) Integer limit) {
        return chats.messages(me, roomId, before, after, limit);
    }

    @PostMapping("/chats/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public PeopleViews.MessageView send(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID roomId,
                                        @RequestBody PeopleViews.NewMessage body) {
        return chats.send(me, roomId, body);
    }

    // ---------- ST-09 ----------

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
