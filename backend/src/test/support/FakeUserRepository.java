package edu.bsu.worgit.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.domain.UserAccount;
import edu.bsu.worgit.repository.UserRepository;

public class FakeUserRepository implements UserRepository {
    private final Map<String, UserAccount> accounts = new HashMap<>();

    public FakeUserRepository add(UserAccount account) {
        accounts.put(account.userId(), account);
        return this;
    }

    @Override
    public Optional<UserAccount> findById(String userId) {
        return Optional.ofNullable(accounts.get(userId));
    }

    @Override
    public List<String> adminIdsManaging(UUID orgId, Role role) {
        return accounts.values().stream()
                .filter(a -> a.role() == role && a.managedOrgIds().contains(orgId))
                .map(UserAccount::userId)
                .toList();
    }
}
