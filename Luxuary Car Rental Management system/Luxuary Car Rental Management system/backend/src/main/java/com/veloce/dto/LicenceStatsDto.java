package com.veloce.dto;

public class LicenceStatsDto {
    private long pending;
    private long approved;
    private long rejected;

    public LicenceStatsDto() {
    }

    public LicenceStatsDto(long pending, long approved, long rejected) {
        this.pending = pending;
        this.approved = approved;
        this.rejected = rejected;
    }

    public long getPending() {
        return pending;
    }

    public void setPending(long pending) {
        this.pending = pending;
    }

    public long getApproved() {
        return approved;
    }

    public void setApproved(long approved) {
        this.approved = approved;
    }

    public long getRejected() {
        return rejected;
    }

    public void setRejected(long rejected) {
        this.rejected = rejected;
    }
}
