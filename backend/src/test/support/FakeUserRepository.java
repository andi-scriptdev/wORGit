package edu.bsu.worgit.support;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
}
