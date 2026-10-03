package com.veloce;

import com.veloce.dto.LicenceResponseDto;
import com.veloce.dto.LicenceStatsDto;
import com.veloce.dto.RejectionRequestDto;
import com.veloce.exception.FileSizeExceededException;
import com.veloce.exception.InvalidFileTypeException;
import com.veloce.exception.LicenceNotApprovedException;
import com.veloce.exception.UnauthorizedLicenceAccessException;
import com.veloce.model.*;
import com.veloce.repository.BookingRepository;
import com.veloce.repository.CarRepository;
import com.veloce.repository.DrivingLicenceRepository;
import com.veloce.repository.UserRepository;
import com.veloce.service.LicenceService;
import com.veloce.controller.LicenceController;
import com.veloce.controller.BookingController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class LicenceVerificationTest {

    @Autowired
    private LicenceService licenceService;

    @Autowired
    private LicenceController licenceController;

    @Autowired
    private BookingController bookingController;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DrivingLicenceRepository drivingLicenceRepository;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private User clientA;
    private User clientB;
    private User adminUser;
    private Car testCar;

    @BeforeEach
    public void setup() {
        bookingRepository.deleteAll();
        drivingLicenceRepository.deleteAll();
        userRepository.deleteAll();
        carRepository.deleteAll();

        // Setup Client A
        clientA = new User();
        clientA.setFullName("Nihal Customer");
        clientA.setEmail("nihal@test.com");
        clientA.setPassword("password123");
        clientA.setRole("customer");
        clientA = userRepository.save(clientA);

        // Setup Client B
        clientB = new User();
        clientB.setFullName("Rahul Customer");
        clientB.setEmail("rahul@test.com");
        clientB.setPassword("password123");
        clientB.setRole("customer");
        clientB = userRepository.save(clientB);

        // Setup Admin User
        adminUser = new User();
        adminUser.setFullName("Veloce Administrator");
        adminUser.setEmail("admin@veloce.com");
        adminUser.setPassword("admin123");
        adminUser.setRole("admin");
        adminUser = userRepository.save(adminUser);

        // Setup Test Car
        testCar = new Car();
        testCar.setName("Ares GT Sport");
        testCar.setPricePerDay(250.0);
        testCar.setImagePath("images/car_sport_1778313498515.png");
        testCar = carRepository.save(testCar);
    }

    // Helper to generate mock files with valid magic bytes
    private MockMultipartFile createMockJpg(String paramName, String filename) {
        byte[] content = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 74, 70};
        return new MockMultipartFile(paramName, filename, "image/jpeg", content);
    }

    private MockMultipartFile createMockPng(String paramName, String filename) {
        byte[] content = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        return new MockMultipartFile(paramName, filename, "image/png", content);
    }

    private MockMultipartFile createMockPdf(String paramName, String filename) {
        byte[] content = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34}; // %PDF-1.4
        return new MockMultipartFile(paramName, filename, "application/pdf", content);
    }

    @Test
    public void test1_UploadValidJpg() {
        MockMultipartFile file = createMockJpg("file", "licence.jpg");
        LicenceResponseDto dto = licenceService.uploadLicence(clientA.getId(), file);

        assertNotNull(dto.getId());
        assertEquals(LicenceStatus.PENDING, dto.getVerificationStatus());
        assertTrue(dto.isCurrent());
        assertEquals("licence.jpg", dto.getOriginalFileName());
    }

    @Test
    public void test2_UploadValidPng() {
        MockMultipartFile file = createMockPng("file", "driving_license.png");
        LicenceResponseDto dto = licenceService.uploadLicence(clientA.getId(), file);

        assertNotNull(dto.getId());
        assertEquals(LicenceStatus.PENDING, dto.getVerificationStatus());
        assertEquals("image/png", dto.getFileType());
    }

    @Test
    public void test3_UploadValidPdf() {
        MockMultipartFile file = createMockPdf("file", "licence_doc.pdf");
        LicenceResponseDto dto = licenceService.uploadLicence(clientA.getId(), file);

        assertNotNull(dto.getId());
        assertEquals(LicenceStatus.PENDING, dto.getVerificationStatus());
        assertEquals("application/pdf", dto.getFileType());
    }

    @Test
    public void test4_UploadUnsupportedFileRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "malicious.exe", "application/x-msdownload", new byte[]{1, 2, 3, 4});
        assertThrows(InvalidFileTypeException.class, () -> {
            licenceService.uploadLicence(clientA.getId(), file);
        });
    }

    @Test
    public void test5_UploadFileExceedingMaxSizeRejected() {
        byte[] oversized = new byte[6 * 1024 * 1024]; // 6MB > 5MB
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;
        oversized[3] = (byte) 0xE0;
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", oversized);

        assertThrows(FileSizeExceededException.class, () -> {
            licenceService.uploadLicence(clientA.getId(), file);
        });
    }

    @Test
    public void test6_AdminCanSeePendingLicences() {
        MockMultipartFile file = createMockJpg("file", "nihal_licence.jpg");
        licenceService.uploadLicence(clientA.getId(), file);

        List<LicenceResponseDto> pending = licenceService.getLicences(LicenceStatus.PENDING);
        assertFalse(pending.isEmpty());
        assertEquals(clientA.getId(), pending.get(0).getUserId());
    }

    @Test
    public void test7_AdminApproveLicence() {
        MockMultipartFile file = createMockJpg("file", "licence.jpg");
        LicenceResponseDto uploaded = licenceService.uploadLicence(clientA.getId(), file);

        LicenceResponseDto approved = licenceService.approveLicence(uploaded.getId(), "admin@veloce.com");
        assertEquals(LicenceStatus.APPROVED, approved.getVerificationStatus());
        assertEquals("admin@veloce.com", approved.getVerifiedBy());
        assertNotNull(approved.getVerifiedAt());

        // Check client status
        Optional<LicenceResponseDto> currentOpt = licenceService.getCurrentLicence(clientA.getId());
        assertTrue(currentOpt.isPresent());
        assertEquals(LicenceStatus.APPROVED, currentOpt.get().getVerificationStatus());
    }

    @Test
    public void test8_AdminRejectLicenceWithReason() {
        MockMultipartFile file = createMockJpg("file", "blurry.jpg");
        LicenceResponseDto uploaded = licenceService.uploadLicence(clientA.getId(), file);

        LicenceResponseDto rejected = licenceService.rejectLicence(uploaded.getId(), "admin@veloce.com", "Licence image is blurry. Please upload a clearer document.");
        assertEquals(LicenceStatus.REJECTED, rejected.getVerificationStatus());
        assertEquals("Licence image is blurry. Please upload a clearer document.", rejected.getRejectionReason());
        assertNotNull(rejected.getVerifiedAt());

        // Check client status
        Optional<LicenceResponseDto> currentOpt = licenceService.getCurrentLicence(clientA.getId());
        assertTrue(currentOpt.isPresent());
        assertEquals(LicenceStatus.REJECTED, currentOpt.get().getVerificationStatus());
        assertEquals("Licence image is blurry. Please upload a clearer document.", currentOpt.get().getRejectionReason());
    }

    @Test
    public void test9_ClientWithoutApprovedLicenceCannotBook() {
        // No licence uploaded
        Booking booking = new Booking();
        booking.setUser(clientA);
        booking.setCar(testCar);
        booking.setPickupLocation("JFK");
        booking.setPickupDate(LocalDateTime.now().plusDays(1));
        booking.setDropoffDate(LocalDateTime.now().plusDays(3));
        booking.setPhoneNumber("9876543210");

        LicenceNotApprovedException ex1 = assertThrows(LicenceNotApprovedException.class, () -> {
            bookingController.createBooking(booking);
        });
        assertEquals("Please upload your driving licence before booking.", ex1.getMessage());

        // Upload licence (PENDING)
        licenceService.uploadLicence(clientA.getId(), createMockJpg("file", "licence.jpg"));
        LicenceNotApprovedException ex2 = assertThrows(LicenceNotApprovedException.class, () -> {
            bookingController.createBooking(booking);
        });
        assertEquals("Your driving licence is currently under verification.", ex2.getMessage());

        // Reject licence (REJECTED)
        Optional<LicenceResponseDto> current = licenceService.getCurrentLicence(clientA.getId());
        licenceService.rejectLicence(current.get().getId(), "admin@veloce.com", "Unreadable text");
        LicenceNotApprovedException ex3 = assertThrows(LicenceNotApprovedException.class, () -> {
            bookingController.createBooking(booking);
        });
        assertEquals("Your driving licence was rejected. Please upload a valid licence.", ex3.getMessage());
    }

    @Test
    public void test10_ClientWithApprovedLicenceCanBook() {
        MockMultipartFile file = createMockJpg("file", "licence.jpg");
        LicenceResponseDto uploaded = licenceService.uploadLicence(clientA.getId(), file);
        licenceService.approveLicence(uploaded.getId(), "admin@veloce.com");

        Booking booking = new Booking();
        booking.setUser(clientA);
        booking.setCar(testCar);
        booking.setPickupLocation("JFK");
        booking.setPickupDate(LocalDateTime.now().plusDays(1));
        booking.setDropoffDate(LocalDateTime.now().plusDays(3));
        booking.setPhoneNumber("9876543210");

        ResponseEntity<?> response = bookingController.createBooking(booking);
        assertEquals(200, response.getStatusCode().value());
        Booking saved = (Booking) response.getBody();
        assertNotNull(saved.getId());
    }

    @Test
    public void test11_ClientCannotAccessAnotherClientsLicenceFile() {
        MockMultipartFile fileA = createMockJpg("file", "nihal_licence.jpg");
        LicenceResponseDto licA = licenceService.uploadLicence(clientA.getId(), fileA);

        // Client B attempts to load Client A's licence resource
        assertThrows(UnauthorizedLicenceAccessException.class, () -> {
            licenceService.loadLicenceResource(licA.getId(), clientB.getId(), "customer");
        });
    }

    @Test
    public void test12_NormalClientCannotAccessAdminEndpoints() {
        assertThrows(UnauthorizedLicenceAccessException.class, () -> {
            licenceController.getAllLicences(null, clientA.getId(), "customer");
        });

        assertThrows(UnauthorizedLicenceAccessException.class, () -> {
            licenceController.getLicenceStats(clientA.getId(), "customer");
        });
    }

    @Test
    public void test13_ReplacementLicenceUploadSetsStatusBackToPending() {
        // 1. Initial upload
        MockMultipartFile file1 = createMockJpg("file", "initial.jpg");
        LicenceResponseDto initial = licenceService.uploadLicence(clientA.getId(), file1);

        // 2. Admin rejects
        licenceService.rejectLicence(initial.getId(), "admin@veloce.com", "Bad quality");
        Optional<LicenceResponseDto> rejectedOpt = licenceService.getCurrentLicence(clientA.getId());
        assertEquals(LicenceStatus.REJECTED, rejectedOpt.get().getVerificationStatus());

        // 3. Client uploads replacement
        MockMultipartFile file2 = createMockPng("file", "replacement.png");
        LicenceResponseDto replacement = licenceService.uploadLicence(clientA.getId(), file2);

        // 4. Verification: replacement is PENDING and current
        assertEquals(LicenceStatus.PENDING, replacement.getVerificationStatus());
        assertTrue(replacement.isCurrent());

        Optional<LicenceResponseDto> current = licenceService.getCurrentLicence(clientA.getId());
        assertTrue(current.isPresent());
        assertEquals(LicenceStatus.PENDING, current.get().getVerificationStatus());
        assertEquals("replacement.png", current.get().getOriginalFileName());
    }

    @Test
    public void test14_UnauthorizedLicenceFileStreamAccessDenied() {
        MockMultipartFile fileA = createMockJpg("file", "nihal_licence.jpg");
        LicenceResponseDto licA = licenceService.uploadLicence(clientA.getId(), fileA);

        // Client B cannot stream Client A's file via admin file endpoint without admin rights
        assertThrows(UnauthorizedLicenceAccessException.class, () -> {
            licenceController.streamLicenceFileForAdmin(licA.getId(), clientB.getId(), null, null, "customer", null);
        });

        // Unauthenticated client file stream request is rejected
        assertThrows(UnauthorizedLicenceAccessException.class, () -> {
            licenceController.streamClientLicenceFile(null, null);
        });
    }
}
