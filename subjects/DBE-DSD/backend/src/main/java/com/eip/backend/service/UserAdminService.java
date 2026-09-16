package com.eip.backend.service;

import com.eip.backend.dto.admin.AdminDtos.CreateUserRequest;
import com.eip.backend.dto.admin.AdminDtos.RoleSummary;
import com.eip.backend.dto.admin.AdminDtos.UpdateUserRequest;
import com.eip.backend.dto.admin.AdminDtos.UserSummary;
import com.eip.backend.entity.Permission;
import com.eip.backend.entity.Role;
import com.eip.backend.entity.User;
import com.eip.backend.exception.ConflictException;
import com.eip.backend.exception.ResourceNotFoundException;
import com.eip.backend.repository.RoleRepository;
import com.eip.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Creates and maintains user accounts. Every user holds exactly one role here:
 * the schema allows several, but one role per user is what the product uses.
 *
 * <p>An administrator cannot disable their own account or change their own role,
 * so an installation cannot lock out its last administrator by accident.
 */
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final DocumentAccessService documentAccessService;

    public UserAdminService(UserRepository userRepository, RoleRepository roleRepository,
                            PasswordEncoder passwordEncoder, DocumentAccessService documentAccessService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.documentAccessService = documentAccessService;
    }

    @Transactional(readOnly = true)
    public List<UserSummary> listUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getUsername))
                .map(UserAdminService::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleSummary> listRoles() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getName))
                .map(role -> new RoleSummary(role.getName(), role.getDescription(),
                        role.getPermissions().stream().map(Permission::getName).sorted().toList()))
                .toList();
    }

    @Transactional
    public UserSummary createUser(CreateUserRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ConflictException("Username " + username + " is already taken");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("Email " + email + " is already in use");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setIsActive(true);
        user.setRoles(new HashSet<>(Set.of(requireRole(request.role()))));
        return summary(userRepository.save(user));
    }

    @Transactional
    public UserSummary updateUser(Integer id, UpdateUserRequest request) {
        User user = requireUser(id);
        boolean self = isCurrentUser(user);

        if (request.fullName() != null) {
            if (request.fullName().isBlank()) {
                throw new IllegalArgumentException("Full name cannot be blank");
            }
            user.setFullName(request.fullName().trim());
        }
        if (request.active() != null && !request.active().equals(user.getIsActive())) {
            if (self && !request.active()) {
                throw new IllegalArgumentException("You cannot disable your own account");
            }
            user.setIsActive(request.active());
        }
        if (request.role() != null) {
            Role role = requireRole(request.role());
            boolean changes = user.getRoles().size() != 1 || !user.getRoles().iterator().next().getName().equals(role.getName());
            if (changes) {
                if (self) {
                    throw new IllegalArgumentException("You cannot change your own role");
                }
                user.setRoles(new HashSet<>(Set.of(role)));
            }
        }
        user.setUpdatedAt(ZonedDateTime.now());
        return summary(userRepository.save(user));
    }

    @Transactional
    public void resetPassword(Integer id, String password) {
        User user = requireUser(id);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setUpdatedAt(ZonedDateTime.now());
        userRepository.save(user);
    }

    private boolean isCurrentUser(User user) {
        User current = documentAccessService.currentUser();
        return current != null && current.getId().equals(user.getId());
    }

    private User requireUser(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User " + id + " does not exist"));
    }

    private Role requireRole(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }
        return roleRepository.findByName(name.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + name.trim()));
    }

    private static UserSummary summary(User user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                Boolean.TRUE.equals(user.getIsActive()),
                user.getRoles().stream().map(Role::getName).sorted().toList(),
                user.getCreatedAt());
    }
}
