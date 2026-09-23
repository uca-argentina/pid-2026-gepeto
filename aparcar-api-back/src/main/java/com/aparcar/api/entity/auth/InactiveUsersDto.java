package com.aparcar.api.entity.auth;

import java.util.Set;

public record InactiveUsersDto(
        Set<String> emails
) {
}
