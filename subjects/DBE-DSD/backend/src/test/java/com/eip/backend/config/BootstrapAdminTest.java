package com.eip.backend.config;

import com.eip.backend.entity.Role;
import com.eip.backend.entity.User;
import com.eip.backend.repository.RoleRepository;
import com.eip.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BootstrapAdminTest {

    private boolean adminExists;
    private boolean usernameTaken;
    private boolean adminRoleMissing;
    private final List<User> saved = new ArrayList<>();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private BootstrapAdmin bootstrap;

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) ->
                switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "Stub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> handler.invoke(proxy, method, args);
                });
    }

    @BeforeEach
    void setUp() {
        UserRepository users = stub(UserRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "existsByRoles_Name" -> adminExists;
            case "findByUsername" -> usernameTaken ? Optional.of(new User()) : Optional.empty();
            case "save" -> {
                saved.add((User) args[0]);
                yield args[0];
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        Role admin = new Role();
        admin.setName("ADMIN");
        RoleRepository roles = stub(RoleRepository.class, (proxy, method, args) ->
                adminRoleMissing ? Optional.empty() : Optional.of(admin));
        bootstrap = new BootstrapAdmin(users, roles, encoder);
    }

    @Test
    void doesNothingWithoutAUsername() {
        assertFalse(bootstrap.createIfNeeded("", "whatever", null));
        assertFalse(bootstrap.createIfNeeded(null, null, null));
        assertTrue(saved.isEmpty());
    }

    @Test
    void createsTheFirstAdministrator() {
        assertTrue(bootstrap.createIfNeeded(" ops_admin ", "correct-horse-battery", "Ops@Example.com"));

        User user = saved.get(0);
        assertEquals("ops_admin", user.getUsername());
        assertEquals("ops@example.com", user.getEmail());
        assertTrue(user.getIsActive());
        assertEquals(List.of("ADMIN"), user.getRoles().stream().map(Role::getName).toList());
        assertTrue(encoder.matches("correct-horse-battery", user.getPasswordHash()));
    }

    @Test
    void defaultsTheEmail() {
        bootstrap.createIfNeeded("ops_admin", "correct-horse-battery", null);
        assertEquals("ops_admin@localhost", saved.get(0).getEmail());
    }

    @Test
    void neverActsOnceAnAdministratorExists() {
        adminExists = true;
        assertFalse(bootstrap.createIfNeeded("ops_admin", "correct-horse-battery", null));
        assertTrue(saved.isEmpty());
    }

    @Test
    void refusesUnusableConfiguration() {
        assertThrows(IllegalStateException.class, () -> bootstrap.createIfNeeded("ops_admin", "too-short", null));
        assertThrows(IllegalStateException.class, () -> bootstrap.createIfNeeded("ops_admin", "x".repeat(73), null));

        usernameTaken = true;
        IllegalStateException clash = assertThrows(IllegalStateException.class,
                () -> bootstrap.createIfNeeded("ops_admin", "correct-horse-battery", null));
        assertTrue(clash.getMessage().contains("not an administrator"));

        usernameTaken = false;
        adminRoleMissing = true;
        assertThrows(IllegalStateException.class, () -> bootstrap.createIfNeeded("ops_admin", "correct-horse-battery", null));
        assertTrue(saved.isEmpty());
    }
}
