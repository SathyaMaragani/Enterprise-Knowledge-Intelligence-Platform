package com.eip.backend.controller;

import com.eip.backend.dto.shellforge.ShellForgeDtos.Overview;
import com.eip.backend.dto.shellforge.ShellForgeDtos.RunRequest;
import com.eip.backend.service.ShellForgeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The OSSP ShellForge shell, run on the server from the web app. Sessions are
 * fixed (see {@link ShellForgeService}) and read no documents, so any signed-in
 * user may run them.
 */
@RestController
@RequestMapping("/api/shellforge")
public class ShellForgeController {

    private final ShellForgeService shellForge;

    public ShellForgeController(ShellForgeService shellForge) {
        this.shellForge = shellForge;
    }

    @GetMapping
    public Overview overview() {
        return new Overview(shellForge.available(), shellForge.sessions());
    }

    /** 400 for an unknown session or out-of-range numbers; 503 if the shell is missing or busy. */
    @PostMapping("/run")
    public ShellForgeService.Run run(@Valid @RequestBody RunRequest request) {
        return shellForge.run(request.session(), request.threads(), request.increments());
    }
}
