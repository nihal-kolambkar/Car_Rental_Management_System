package com.veloce.dto;

import com.veloce.model.DrivingLicence;
import com.veloce.model.LicenceStatus;

import java.time.LocalDateTime;

public class LicenceResponseDto {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String originalFileName;
    private String storedFileName;
    private String fileType;
    private Long fileSize;
    private LicenceStatus verificationStatus;
    private String rejectionReason;
    private String verifiedBy;
    private LocalDateTime verifiedAt;
    private LocalDateTime uploadDate;
    private boolean current;

    public LicenceResponseDto() {
    }

    public static LicenceResponseDto fromEntity(DrivingLicence entity) {
        LicenceResponseDto dto = new LicenceResponseDto();
        dto.setId(entity.getId());
        if (entity.getUser() != null) {
            dto.setUserId(entity.getUser().getId());
            dto.setUserName(entity.getUser().getFullName());
            dto.setUserEmail(entity.getUser().getEmail());
        }
        dto.setOriginalFileName(entity.getOriginalFileName());
        dto.setStoredFileName(entity.getStoredFileName());
        dto.setFileType(entity.getFileType());
        dto.setFileSize(entity.getFileSize());
        dto.setVerificationStatus(entity.getVerificationStatus());
        dto.setRejectionReason(entity.getRejectionReason());
        dto.setVerifiedBy(entity.getVerifiedBy());
        dto.setVerifiedAt(entity.getVerifiedAt());
        dto.setUploadDate(entity.getUploadDate());
        dto.setCurrent(entity.isCurrent());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getStoredFileName() {
        return storedFileName;
    }

    public void setStoredFileName(String storedFileName) {
        this.storedFileName = storedFileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public LicenceStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(LicenceStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(String verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public LocalDateTime getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(LocalDateTime uploadDate) {
        this.uploadDate = uploadDate;
    }

    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }
}
