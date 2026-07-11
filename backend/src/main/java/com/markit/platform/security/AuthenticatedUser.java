package com.markit.platform.security;

import com.markit.identity.domain.UserId;

/**
 * The security principal — just the authenticated user's id. Endpoints derive ownership from this,
 * never from client-supplied ids (FR-IDN-005, R-SEC-01).
 */
public record AuthenticatedUser(UserId id) {}
