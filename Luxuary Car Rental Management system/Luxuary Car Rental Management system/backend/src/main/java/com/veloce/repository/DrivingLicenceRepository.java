package com.veloce.repository;

import com.veloce.model.DrivingLicence;
import com.veloce.model.LicenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DrivingLicenceRepository extends JpaRepository<DrivingLicence, Long> {

    Optional<DrivingLicence> findByUserIdAndCurrentTrue(Long userId);

    List<DrivingLicence> findByUserIdOrderByUploadDateDesc(Long userId);

    List<DrivingLicence> findByVerificationStatusOrderByUploadDateDesc(LicenceStatus status);

    List<DrivingLicence> findAllByOrderByUploadDateDesc();

    long countByVerificationStatus(LicenceStatus status);
}
