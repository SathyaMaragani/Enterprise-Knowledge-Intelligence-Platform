package com.eip.backend.controller;

import com.eip.backend.dto.admin.AdminDtos.CreateUserRequest;
import com.eip.backend.dto.admin.AdminDtos.PasswordRequest;
import com.eip.backend.dto.admin.AdminDtos.RoleSummary;
import com.eip.backend.dto.admin.AdminDtos.UpdateUserRequest;
import com.eip.backend.dto.admin.AdminDtos.UserSummary;
import com.eip.backend.service.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** User administration. Every endpoint requires USER_MANAGE. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class AdminController {

    private final UserAdminService userAdminService;

    public AdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping("/users")
    public List<UserSummary> listUsers() {
        return userAdminService.listUsers();
    }

    @GetMapping("/roles")
    public List<RoleSummary> listRoles() {
        return userAdminService.listRoles();
    }

    @PostMapping("/users")
    public ResponseEntity<UserSummary> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserSummary created = userAdminService.createUser(request);
        return ResponseEntity.created(URI.create("/api/admin/users/" + created.id())).body(created);
    }

    @PatchMapping("/users/{id}")
    public UserSummary updateUser(@PathVariable Integer id, @Valid @RequestBody UpdateUserRequest request) {
        return userAdminService.updateUser(id, request);
    }

    @PutMapping("/users/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable Integer id, @Valid @RequestBody PasswordRequest request) {
        userAdminService.resetPassword(id, request.password());
        return ResponseEntity.noContent().build();
    }
}
