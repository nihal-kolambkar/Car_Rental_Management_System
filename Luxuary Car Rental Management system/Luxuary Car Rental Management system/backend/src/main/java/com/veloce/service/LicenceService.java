package com.veloce.service;

import com.veloce.dto.LicenceResponseDto;
import com.veloce.dto.LicenceStatsDto;
import com.veloce.exception.*;
import com.veloce.model.DrivingLicence;
import com.veloce.model.LicenceStatus;
import com.veloce.model.User;
import com.veloce.repository.DrivingLicenceRepository;
import com.veloce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LicenceService {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "image/jpeg",
            "image/png",
            "application/pdf"
    );

    private static final List<String> DANGEROUS_EXTENSIONS = Arrays.asList(
            ".exe", ".sh", ".bat", ".zip", ".rar", ".jsp", ".html", ".js",
            ".cmd", ".vbs", ".scr", ".msi", ".jar", ".bin", ".dll"
    );

    @Autowired
    private DrivingLicenceRepository drivingLicenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StorageService storageService;

    @Transactional
    public LicenceResponseDto uploadLicence(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new UnauthorizedLicenceAccessException("User authentication is required to upload a driving licence.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new LicenceNotFoundException("User not found with ID: " + userId));

        // Validate File
        validateLicenceFile(file);

        // Deactivate previous active licences for this user to maintain clean version history
        Optional<DrivingLicence> existingCurrent = drivingLicenceRepository.findByUserIdAndCurrentTrue(userId);
        existingCurrent.ifPresent(licence -> {
            licence.setCurrent(false);
            drivingLicenceRepository.save(licence);
        });

        // Store file safely
        String storedFileName = storageService.storeFile(file);

        // Create new DrivingLicence entity
        DrivingLicence licence = new DrivingLicence();
        licence.setUser(user);
        licence.setOriginalFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : storedFileName);
        licence.setStoredFileName(storedFileName);
        licence.setFileType(file.getContentType());
        licence.setFileSize(file.getSize());
        licence.setStoragePath("uploads/licenses/" + storedFileName);
        licence.setVerificationStatus(LicenceStatus.PENDING);
        licence.setUploadDate(LocalDateTime.now());
        licence.setCurrent(true);

        DrivingLicence saved = drivingLicenceRepository.save(licence);
        return LicenceResponseDto.fromEntity(saved);
    }

    public Optional<LicenceResponseDto> getCurrentLicence(Long userId) {
        return drivingLicenceRepository.findByUserIdAndCurrentTrue(userId)
                .map(LicenceResponseDto::fromEntity);
    }

    public DrivingLicence getLicenceEntity(Long licenceId) {
        return drivingLicenceRepository.findById(licenceId)
                .orElseThrow(() -> new LicenceNotFoundException("Driving licence not found with ID: " + licenceId));
    }

    public List<LicenceResponseDto> getLicences(LicenceStatus status) {
        List<DrivingLicence> list;
        if (status != null) {
            list = drivingLicenceRepository.findByVerificationStatusOrderByUploadDateDesc(status);
        } else {
            list = drivingLicenceRepository.findAllByOrderByUploadDateDesc();
        }
        return list.stream()
                .map(LicenceResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public LicenceResponseDto approveLicence(Long licenceId, String adminUsername) {
        DrivingLicence licence = getLicenceEntity(licenceId);
        licence.setVerificationStatus(LicenceStatus.APPROVED);
        licence.setVerifiedBy(adminUsername != null ? adminUsername : "Admin");
        licence.setVerifiedAt(LocalDateTime.now());
        licence.setRejectionReason(null);

        DrivingLicence updated = drivingLicenceRepository.save(licence);
        return LicenceResponseDto.fromEntity(updated);
    }

    @Transactional
    public LicenceResponseDto rejectLicence(Long licenceId, String adminUsername, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Rejection reason is required.");
        }

        DrivingLicence licence = getLicenceEntity(licenceId);
        licence.setVerificationStatus(LicenceStatus.REJECTED);
        licence.setVerifiedBy(adminUsername != null ? adminUsername : "Admin");
        licence.setVerifiedAt(LocalDateTime.now());
        licence.setRejectionReason(reason.trim());

        DrivingLicence updated = drivingLicenceRepository.save(licence);
        return LicenceResponseDto.fromEntity(updated);
    }

    public LicenceStatsDto getLicenceStats() {
        long pending = drivingLicenceRepository.countByVerificationStatus(LicenceStatus.PENDING);
        long approved = drivingLicenceRepository.countByVerificationStatus(LicenceStatus.APPROVED);
        long rejected = drivingLicenceRepository.countByVerificationStatus(LicenceStatus.REJECTED);
        return new LicenceStatsDto(pending, approved, rejected);
    }

    public Resource loadLicenceResource(Long licenceId, Long requestingUserId, String requestingUserRole) {
        DrivingLicence licence = getLicenceEntity(licenceId);

        boolean isAdmin = requestingUserRole != null && 
                (requestingUserRole.equalsIgnoreCase("admin") || requestingUserRole.equalsIgnoreCase("ROLE_ADMIN"));

        if (!isAdmin) {
            if (requestingUserId == null || !licence.getUser().getId().equals(requestingUserId)) {
                throw new UnauthorizedLicenceAccessException("Access denied. You are not authorized to access this licence document.");
            }
        }

        return storageService.loadFileAsResource(licence.getStoredFileName());
    }

    public void validateLicenceFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileTypeException("Please select a valid file to upload.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new FileSizeExceededException("File size must not exceed 5 MB.");
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.trim().isEmpty()) {
            throw new InvalidFileTypeException("Invalid file name.");
        }

        String lowerCaseName = originalFileName.toLowerCase();

        // Reject dangerous extensions
        for (String dangerousExt : DANGEROUS_EXTENSIONS) {
            if (lowerCaseName.endsWith(dangerousExt)) {
                throw new InvalidFileTypeException("Invalid file type. Potentially dangerous file type rejected.");
            }
        }

        // Validate extension
        boolean validExtension = lowerCaseName.endsWith(".jpg") ||
                lowerCaseName.endsWith(".jpeg") ||
                lowerCaseName.endsWith(".png") ||
                lowerCaseName.endsWith(".pdf");

        if (!validExtension) {
            throw new InvalidFileTypeException("Invalid file type. Please upload JPG, JPEG, PNG or PDF.");
        }

        // Validate MIME type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidFileTypeException("Invalid file type. Please upload JPG, JPEG, PNG or PDF.");
        }

        // Validate file content signature (magic bytes)
        validateMagicBytes(file);
    }

    private void validateMagicBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[8];
            int read = is.read(header);
            if (read < 4) {
                throw new InvalidFileTypeException("Corrupt or invalid file content.");
            }

            // Check JPEG (FF D8 FF)
            boolean isJpeg = (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF);

            // Check PNG (89 50 4E 47 0D 0A 1A 0A)
            boolean isPng = (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 &&
                    header[2] == (byte) 0x4E && header[3] == (byte) 0x47);

            // Check PDF (%PDF -> 25 50 44 46)
            boolean isPdf = (header[0] == (byte) 0x25 && header[1] == (byte) 0x50 &&
                    header[2] == (byte) 0x44 && header[3] == (byte) 0x46);

            if (!isJpeg && !isPng && !isPdf) {
                throw new InvalidFileTypeException("Invalid file content. File does not match supported image or PDF format.");
            }
        } catch (IOException e) {
            throw new InvalidFileTypeException("Could not verify file content: " + e.getMessage());
        }
    }
}
