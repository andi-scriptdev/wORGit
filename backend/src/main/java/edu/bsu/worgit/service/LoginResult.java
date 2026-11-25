package edu.bsu.worgit.service;

import java.time.Instant;

import edu.bsu.worgit.domain.UserAccount;

public record LoginResult(String token, Instant expiresAt, UserAccount user) {}
