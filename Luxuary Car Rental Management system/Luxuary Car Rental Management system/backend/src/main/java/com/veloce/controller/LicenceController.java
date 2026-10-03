package com.veloce.controller;

import com.veloce.dto.LicenceResponseDto;
import com.veloce.dto.LicenceStatsDto;
import com.veloce.dto.RejectionRequestDto;
import com.veloce.exception.LicenceNotFoundException;
import com.veloce.exception.UnauthorizedLicenceAccessException;
import com.veloce.model.DrivingLicence;
import com.veloce.model.LicenceStatus;
import com.veloce.model.User;
import com.veloce.repository.UserRepository;
import com.veloce.service.LicenceService;
import com.veloce.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class LicenceController {

    @Autowired
    private LicenceService licenceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StorageService storageService;

    // Helper to resolve and authenticate client ID
    private Long resolveUserId(Long headerId, Long paramId) {
        Long userId = headerId != null ? headerId : paramId;
        if (userId == null) {
            throw new UnauthorizedLicenceAccessException("User authentication is required.");
        }
        return userId;
    }

    // Helper to verify admin authority
    private User verifyAdmin(Long requestingUserId, String requestingRole) {
        if (requestingUserId != null) {
            Optional<User> userOpt = userRepository.findById(requestingUserId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if ("admin".equalsIgnoreCase(user.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(user.getRole())
                    || "admin@veloce.com".equalsIgnoreCase(user.getEmail())) {
                    return user;
                }
            }
        }
        if (requestingRole != null && 
           ("admin".equalsIgnoreCase(requestingRole) || "ROLE_ADMIN".equalsIgnoreCase(requestingRole))) {
            User dummy = new User();
            dummy.setEmail("admin@veloce.com");
            dummy.setFullName("Master Administrator");
            dummy.setRole("admin");
            return dummy;
        }
        throw new UnauthorizedLicenceAccessException("Access denied. Administrator privileges required.");
    }

    // ==========================================
    // CLIENT ENDPOINTS
    // ==========================================

    @PostMapping("/client/licence/upload")
    public ResponseEntity<?> uploadLicence(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId) {

        Long userId = resolveUserId(headerUserId, paramUserId);
        LicenceResponseDto responseDto = licenceService.uploadLicence(userId, file);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Driving licence uploaded successfully");
        response.put("status", responseDto.getVerificationStatus().name());
        response.put("licence", responseDto);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/client/licence/status")
    public ResponseEntity<?> getLicenceStatus(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId) {

        Long userId = resolveUserId(headerUserId, paramUserId);
        Optional<LicenceResponseDto> licenceOpt = licenceService.getCurrentLicence(userId);

        Map<String, Object> response = new HashMap<>();
        if (licenceOpt.isPresent()) {
            LicenceResponseDto licence = licenceOpt.get();
            response.put("status", licence.getVerificationStatus().name());
            response.put("licence", licence);
            response.put("rejectionReason", licence.getRejectionReason());
        } else {
            response.put("status", "NOT_UPLOADED");
            response.put("licence", null);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/client/licence/file")
    public ResponseEntity<Resource> streamClientLicenceFile(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId) {

        Long userId = resolveUserId(headerUserId, paramUserId);

        LicenceResponseDto licence = licenceService.getCurrentLicence(userId)
                .orElseThrow(() -> new LicenceNotFoundException("No driving licence uploaded yet."));

        Resource resource = storageService.loadFileAsResource(licence.getStoredFileName());

        String mediaType = licence.getFileType() != null ? licence.getFileType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + licence.getOriginalFileName() + "\"")
                .body(resource);
    }

    // ==========================================
    // ADMIN ENDPOINTS
    // ==========================================

    @GetMapping("/admin/licences")
    public ResponseEntity<List<LicenceResponseDto>> getAllLicences(
            @RequestParam(value = "status", required = false) LicenceStatus status,
            @RequestHeader(value = "X-User-Id", required = false) Long requestingUserId,
            @RequestHeader(value = "X-User-Role", required = false) String requestingRole) {

        verifyAdmin(requestingUserId, requestingRole);
        List<LicenceResponseDto> licences = licenceService.getLicences(status);
        return ResponseEntity.ok(licences);
    }

    @GetMapping("/admin/licences/stats")
    public ResponseEntity<LicenceStatsDto> getLicenceStats(
            @RequestHeader(value = "X-User-Id", required = false) Long requestingUserId,
            @RequestHeader(value = "X-User-Role", required = false) String requestingRole) {

        verifyAdmin(requestingUserId, requestingRole);
        LicenceStatsDto stats = licenceService.getLicenceStats();
        return ResponseEntity.ok(stats);
    }

    @PutMapping("/admin/licences/{id}/approve")
    public ResponseEntity<LicenceResponseDto> approveLicence(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long requestingUserId,
            @RequestHeader(value = "X-User-Role", required = false) String requestingRole) {

        User admin = verifyAdmin(requestingUserId, requestingRole);
        LicenceResponseDto approved = licenceService.approveLicence(id, admin.getFullName() != null ? admin.getFullName() : admin.getEmail());
        return ResponseEntity.ok(approved);
    }

    @PutMapping("/admin/licences/{id}/reject")
    public ResponseEntity<LicenceResponseDto> rejectLicence(
            @PathVariable("id") Long id,
            @RequestBody RejectionRequestDto rejectionRequest,
            @RequestHeader(value = "X-User-Id", required = false) Long requestingUserId,
            @RequestHeader(value = "X-User-Role", required = false) String requestingRole) {

        User admin = verifyAdmin(requestingUserId, requestingRole);
        LicenceResponseDto rejected = licenceService.rejectLicence(id, 
                admin.getFullName() != null ? admin.getFullName() : admin.getEmail(),
                rejectionRequest != null ? rejectionRequest.getReason() : null);
        return ResponseEntity.ok(rejected);
    }

    /**
     * Streams the licence file for viewing.
     * Verifies authorization: admin or owner of the licence document.
     */
    @GetMapping("/admin/licences/{id}/file")
    public ResponseEntity<Resource> streamLicenceFileForAdmin(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @RequestParam(value = "adminId", required = false) Long paramAdminId,
            @RequestHeader(value = "X-User-Role", required = false) String headerRole,
            @RequestParam(value = "role", required = false) String paramRole) {

        Long requestingUserId = headerUserId != null ? headerUserId : (paramAdminId != null ? paramAdminId : paramUserId);
        String requestingRole = headerRole != null ? headerRole : paramRole;

        Resource resource = licenceService.loadLicenceResource(id, requestingUserId, requestingRole);
        DrivingLicence licence = licenceService.getLicenceEntity(id);

        String mediaType = licence.getFileType() != null ? licence.getFileType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + licence.getOriginalFileName() + "\"")
                .body(resource);
    }
}
