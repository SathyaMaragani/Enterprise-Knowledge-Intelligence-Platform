package com.eip.backend.controller;

import com.eip.backend.dto.auth.AuthResponse;
import com.eip.backend.dto.auth.ChangePasswordRequest;
import com.eip.backend.dto.auth.CurrentUserResponse;
import com.eip.backend.dto.auth.LoginRequest;
import com.eip.backend.entity.Permission;
import com.eip.backend.entity.Role;
import com.eip.backend.security.CustomUserDetails;
import com.eip.backend.security.JwtService;
import com.eip.backend.service.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserAdminService userAdminService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
                          UserAdminService userAdminService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userAdminService = userAdminService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String jwtToken = jwtService.generateToken(userDetails);

        return ResponseEntity.ok(new AuthResponse(jwtToken, userDetails.getUsername()));
    }

    /**
     * Changes the signed-in user's own password. A wrong current password is 400,
     * not 401: the token is still good, so the client must not sign the user out.
     */
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal CustomUserDetails principal,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        userAdminService.changeOwnPassword(principal.getUser().getId(), request.currentPassword(),
                                           request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** The signed-in user's profile, roles and permissions. Requires a valid token. */
    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal CustomUserDetails principal) {
        var user = principal.getUser();
        return new CurrentUserResponse(
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRoles().stream().map(Role::getName).sorted().toList(),
                user.getRoles().stream()
                        .flatMap(role -> role.getPermissions().stream())
                        .map(Permission::getName)
                        .distinct()
                        .sorted()
                        .toList());
    }
}
