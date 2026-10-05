package com.example.userservice.repository;

import com.example.userservice.entity.Role;
import com.example.userservice.entity.User;
import com.example.userservice.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private Role userRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));
        adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));
    }

    @Test
    void testSaveAndFindById() {
        User user = new User();
        user.setEmail("john.doe@example.com");
        user.setPasswordHash("hashed_pw_123");
        user.setFullName("John Doe");
        user.setPhone("0987654321");
        user.setRoles(Set.of(userRole));

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(0L);

        Optional<User> found = userRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("john.doe@example.com");
        assertThat(found.get().getRoles()).hasSize(1);
    }

    @Test
    void testFindByEmailIgnoreCase() {
        User user = new User();
        user.setEmail("alice@example.com");
        user.setPasswordHash("hashed_pw_123");
        user.setFullName("Alice Smith");
        user.setRoles(Set.of(userRole));
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmailIgnoreCase("ALICE@EXAMPLE.COM");
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Alice Smith");

        boolean existsUpper = userRepository.existsByEmailIgnoreCase("Alice@Example.com");
        assertThat(existsUpper).isTrue();

        boolean existsNonExistent = userRepository.existsByEmailIgnoreCase("unknown@example.com");
        assertThat(existsNonExistent).isFalse();
    }

    @Test
    void testSearchUsersWithPagination() {
        User u1 = new User();
        u1.setEmail("alpha@test.com");
        u1.setPasswordHash("pw1");
        u1.setFullName("Alpha Developer");
        u1.setPhone("0911000001");
        u1.setStatus(UserStatus.ACTIVE);
        u1.setRoles(Set.of(userRole));
        userRepository.save(u1);

        User u2 = new User();
        u2.setEmail("beta@test.com");
        u2.setPasswordHash("pw2");
        u2.setFullName("Beta Tester");
        u2.setPhone("0911000002");
        u2.setStatus(UserStatus.LOCKED);
        u2.setRoles(Set.of(userRole));
        userRepository.save(u2);

        // Search by keyword "developer"
        Page<User> resultKeyword = userRepository.searchUsers(
                "developer",
                null,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "fullName"))
        );
        assertThat(resultKeyword.getTotalElements()).isEqualTo(1L);
        assertThat(resultKeyword.getContent().get(0).getEmail()).isEqualTo("alpha@test.com");

        // Filter by status LOCKED
        Page<User> resultStatus = userRepository.searchUsers(
                null,
                UserStatus.LOCKED,
                PageRequest.of(0, 10)
        );
        assertThat(resultStatus.getTotalElements()).isEqualTo(1L);
        assertThat(resultStatus.getContent().get(0).getEmail()).isEqualTo("beta@test.com");

        // Search all
        Page<User> resultAll = userRepository.searchUsers(
                null,
                null,
                PageRequest.of(0, 10)
        );
        assertThat(resultAll.getTotalElements()).isEqualTo(2L);
    }

    @Test
    void testOptimisticLockingIncrementsVersion() {
        User user = new User();
        user.setEmail("version.test@example.com");
        user.setPasswordHash("pw");
        user.setFullName("Version Test");
        user.setRoles(Set.of(userRole));

        User saved = userRepository.saveAndFlush(user);
        assertThat(saved.getVersion()).isEqualTo(0L);

        saved.setFullName("Version Test Updated");
        User updated = userRepository.saveAndFlush(saved);
        assertThat(updated.getVersion()).isEqualTo(1L);
    }
}
