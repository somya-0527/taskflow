package com.taskflow.auth;

import com.taskflow.user.Role;

public record AuthenticatedUser(Long id, String email, Role role) {
}