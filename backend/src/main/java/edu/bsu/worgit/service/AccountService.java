package edu.bsu.worgit.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Capability;
import edu.bsu.worgit.domain.Messages;
import edu.bsu.worgit.domain.Permissions;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.domain.Rules;
import edu.bsu.worgit.domain.UserAccount;
import edu.bsu.worgit.exception.InvalidPasswordException;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.repository.OrganizationRepository;
import edu.bsu.worgit.repository.ProfileRepository;
import edu.bsu.worgit.repository.UserRepository;

/** Password change for everyone, and the staff profile (AD-12). */
@Service
public class AccountService {

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final OrganizationRepository orgs;
    private final PasswordEncoder encoder;

    public AccountService(UserRepository users, ProfileRepository profiles, OrganizationRepository orgs,
                          PasswordEncoder encoder) {
        this.users = users;
        this.profiles = profiles;
        this.orgs = orgs;
        this.encoder = encoder;
    }

    /**
     * The current password must match ("Current password is incorrect." otherwise), then the new one needs
     * at least 8 characters. Tokens already issued stay valid until they expire (stateless JWT).
     */
    @Transactional
    public void changePassword(AuthenticatedUser user, PeopleViews.PasswordChange change) {
        UserAccount account = requireAccount(user);
        String current = change == null || change.currentPassword() == null ? "" : change.currentPassword();
        if (!encoder.matches(current, account.passwordHash())) {
            throw new InvalidPasswordException(Messages.CURRENT_PASSWORD_WRONG, "currentPassword");
        }
        String next = change.newPassword();
        Rules.requireValidNewPassword(next);
        profiles.updatePasswordHash(account.userId(), encoder.encode(next));
    }

    // ---------- AD-12 ----------

    public PeopleViews.AdminProfile adminProfile(AuthenticatedUser actor) {
        Permissions.requireGlobal(actor, Capability.EDIT_OWN_ADMIN_PROFILE);
        UserAccount account = requireAccount(actor);
        boolean all = account.role() == Role.SYSTEM_ADMINISTRATOR;
        List<PeopleViews.OrgRef> managed = all ? List.of()
                : orgs.searchOrganizations(null, null, account.managedOrgIds()).stream()
                        .map(o -> new PeopleViews.OrgRef(o.orgId(), o.name()))
                        .toList();
        return new PeopleViews.AdminProfile(account.userId(), account.fullName(), account.displayName(),
                account.email(), account.role().name(), all, managed);
    }

    /** Only the display name can change; role and organizations are read-only (p37). */
    @Transactional
    public PeopleViews.AdminProfile updateAdminProfile(AuthenticatedUser actor, PeopleViews.AdminProfileInput input) {
        Permissions.requireGlobal(actor, Capability.EDIT_OWN_ADMIN_PROFILE);
        String name = Rules.requireValidDisplayName(input == null ? null : input.displayName());
        profiles.updateDisplayName(actor.userId(), name);
        return adminProfile(actor);
    }

    private UserAccount requireAccount(AuthenticatedUser user) {
        return users.findById(user.userId()).orElseThrow(NotFoundException::new);
    }
}
