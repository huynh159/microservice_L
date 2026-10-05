package com.example.userservice.mapper;

import com.example.userservice.dto.response.UserProfileResponse;
import com.example.userservice.entity.Role;
import com.example.userservice.entity.User;
import com.example.userservice.entity.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper userMapper = new UserMapper();

    @Test
    void testToProfileResponse() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed_secret");
        user.setFullName("Nguyen Van A");
        user.setPhone("0901234567");
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user.setVersion(0L);

        Role roleUser = new Role(1, "ROLE_USER");
        user.setRoles(Set.of(roleUser));

        UserProfileResponse response = userMapper.toProfileResponse(user);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(user.getId());
        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(response.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(response.getPhone()).isEqualTo("0901234567");
        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.getRoles()).containsExactly("ROLE_USER");
    }

    @Test
    void testToProfileResponseWithNullUser() {
        assertThat(userMapper.toProfileResponse(null)).isNull();
    }
}
