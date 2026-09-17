package com.eip.backend.config;

import com.eip.backend.entity.Role;
import com.eip.backend.entity.User;
import com.eip.backend.repository.RoleRepository;
import com.eip.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Creates the first administrator of a fresh deployment from configuration:
 * {@code eip.bootstrap-admin.username}, {@code .password} and optionally
 * {@code .email} (environment: {@code EIP_BOOTSTRAPADMIN_USERNAME}, ...).
 *
 * <p>It only ever acts once: as soon as any account holds the ADMIN role it does
 * nothing, so changing the variables later cannot reset an administrator's
 * password. Without a username it does nothing at all. A deployment therefore
 * never needs seed users with known passwords.
 */
@Component
public class BootstrapAdmin implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(BootstrapAdmin.class);
    static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${eip.bootstrap-admin.username:}")
    private String username;

    @Value("${eip.bootstrap-admin.password:}")
    private String password;

    @Value("${eip.bootstrap-admin.email:}")
    private String email;

    public BootstrapAdmin(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfNeeded(username, password, email);
    }

    /**
     * @return whether an administrator was created
     * @throws IllegalStateException when the configuration is unusable; startup stops
     *                               rather than running without a way to administer it
     */
    boolean createIfNeeded(String username, String password, String email) {
        if (username == null || username.isBlank()) {
            return false;
        }
        String name = username.trim();
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalStateException("The bootstrap admin password must be "
                    + MIN_PASSWORD_LENGTH + " to " + MAX_PASSWORD_LENGTH + " characters");
        }
        Role admin = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException(
                        "Role ADMIN is missing: load database/postgresql/reference-data.sql first"));
        if (userRepository.existsByRoles_Name("ADMIN")) {
            logger.info("An administrator already exists; bootstrap admin not created");
            return false;
        }
        if (userRepository.findByUsername(name).isPresent()) {
            throw new IllegalStateException("User " + name + " already exists but is not an administrator");
        }

        User user = new User();
        user.setUsername(name);
        user.setEmail((email == null || email.isBlank() ? name + "@localhost" : email.trim()).toLowerCase(Locale.ROOT));
        user.setFullName("Administrator");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setIsActive(true);
        user.setRoles(new HashSet<>(Set.of(admin)));
        userRepository.save(user);
        logger.info("Created bootstrap administrator {}", name);
        return true;
    }
}
