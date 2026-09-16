package com.eip.backend.controller;

import com.eip.backend.dto.auth.AuthResponse;
import com.eip.backend.dto.auth.CurrentUserResponse;
import com.eip.backend.dto.auth.LoginRequest;
import com.eip.backend.entity.Permission;
import com.eip.backend.entity.Role;
import com.eip.backend.security.CustomUserDetails;
import com.eip.backend.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
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
