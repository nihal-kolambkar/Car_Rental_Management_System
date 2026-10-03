package com.veloce.dto;

public class RejectionRequestDto {
    private String reason;

    public RejectionRequestDto() {
    }

    public RejectionRequestDto(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
