package com.taskflow.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesUserAndFindsItByEmail() {
        userRepository.save(newUser("alice@example.com"));

        var found = userRepository.findByEmail("alice@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getRole()).isEqualTo(Role.USER);
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void existsByEmailReturnsFalseForUnknownEmail() {
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void rejectsDuplicateEmail() {
        userRepository.saveAndFlush(newUser("bob@example.com"));

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(newUser("bob@example.com")));
    }

    private User newUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("not-a-real-hash");
        user.setFullName("Test User");
        return user;
    }
}