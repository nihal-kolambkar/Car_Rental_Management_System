package com.veloce.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.veloce.dto.LicenceResponseDto;
import com.veloce.model.User;
import com.veloce.repository.UserRepository;
import com.veloce.service.LicenceService;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    public static final String MASTER_ADMIN_EMAIL = "admin@veloce.com";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LicenceService licenceService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        if (MASTER_ADMIN_EMAIL.equalsIgnoreCase(user.getEmail())) {
            return ResponseEntity.badRequest().body("Error: Email is reserved for the system administrator!");
        }
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Error: Email is already in use!");
        }

        // Rule: Only one admin allowed in the system. All new registrations are forced to ROLE_CUSTOMER.
        user.setRole("ROLE_CUSTOMER");
        User savedUser = userRepository.save(user);
        return ResponseEntity.ok(savedUser);
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody User loginRequest) {
        Optional<User> userOptional = userRepository.findByEmail(loginRequest.getEmail());
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getPassword().equals(loginRequest.getPassword())) {
                // Rule: If an account has admin role, only the single designated master admin is allowed to log in!
                if ("admin".equalsIgnoreCase(user.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(user.getRole())) {
                    if (!MASTER_ADMIN_EMAIL.equalsIgnoreCase(user.getEmail())) {
                        return ResponseEntity.status(403).body("Unauthorized: Only the designated system administrator can access the admin portal.");
                    }
                }
                return ResponseEntity.ok(user);
            }
        }
        return ResponseEntity.status(401).body("Invalid email or password");
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserById(@PathVariable Long userId) {
        return userRepository.findById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Backward-compatible upload endpoint delegating to LicenceService
    @PostMapping("/{userId}/upload-license")
    public ResponseEntity<?> uploadLicense(@PathVariable Long userId, @RequestParam("file") MultipartFile file) {
        LicenceResponseDto dto = licenceService.uploadLicence(userId, file);
        return ResponseEntity.ok("File uploaded successfully: " + dto.getStoredFileName());
    }

    // Backward-compatible streaming endpoint
    @GetMapping("/{userId}/license-image")
    public ResponseEntity<Resource> getLicenseImage(@PathVariable Long userId) {
        Optional<LicenceResponseDto> licenceOpt = licenceService.getCurrentLicence(userId);
        if (!licenceOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        LicenceResponseDto licence = licenceOpt.get();
        Resource resource = licenceService.loadLicenceResource(licence.getId(), userId, "admin");

        String contentType = licence.getFileType() != null ? licence.getFileType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + licence.getOriginalFileName() + "\"")
                .body(resource);
    }
}
