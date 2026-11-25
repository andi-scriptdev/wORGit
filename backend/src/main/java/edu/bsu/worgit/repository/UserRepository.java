package edu.bsu.worgit.repository;

import java.util.Optional;

import edu.bsu.worgit.domain.UserAccount;

public interface UserRepository {

    /**
     * The account with its role and (for facilitators and advisers) managed organizations.
     * Empty when the id is unknown or an ADMIN row has no valid admins row (fail closed).
     */
    Optional<UserAccount> findById(String userId);
}