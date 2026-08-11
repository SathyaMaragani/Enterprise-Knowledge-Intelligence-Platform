package com.eip.backend.controller;
import com.eip.backend.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {
    @GetMapping("/health")
    public HealthResponse getHealth() {
        return new HealthResponse("UP", "enterprise-knowledge-intelligence-backend");
    }
}