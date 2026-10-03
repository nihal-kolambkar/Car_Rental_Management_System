package com.veloce.controller;

import com.veloce.exception.LicenceNotApprovedException;
import com.veloce.model.Booking;
import com.veloce.model.Car;
import com.veloce.model.DrivingLicence;
import com.veloce.model.LicenceStatus;
import com.veloce.model.User;
import com.veloce.repository.BookingRepository;
import com.veloce.repository.CarRepository;
import com.veloce.repository.DrivingLicenceRepository;
import com.veloce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/bookings")
@CrossOrigin(origins = "*")
public class BookingController {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private DrivingLicenceRepository drivingLicenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CarRepository carRepository;

    @PostMapping
    public ResponseEntity<?> createBooking(@RequestBody Booking booking) {
        if (booking.getUser() == null || booking.getUser().getId() == null) {
            throw new IllegalArgumentException("User information is required to create a booking.");
        }
        if (booking.getPickupDate() == null || booking.getDropoffDate() == null) {
            throw new IllegalArgumentException("Pick-up and drop-off date and time are required.");
        }
        if (!booking.getDropoffDate().isAfter(booking.getPickupDate())) {
            throw new IllegalArgumentException("Drop-off date and time must be after pick-up date and time.");
        }

        Long userId = booking.getUser().getId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        // BACKEND ENFORCEMENT: Check driving licence verification
        Optional<DrivingLicence> licenceOpt = drivingLicenceRepository.findByUserIdAndCurrentTrue(userId);

        if (!licenceOpt.isPresent()) {
            throw new LicenceNotApprovedException("Please upload your driving licence before booking.");
        }

        DrivingLicence licence = licenceOpt.get();
        if (licence.getVerificationStatus() == LicenceStatus.PENDING) {
            throw new LicenceNotApprovedException("Your driving licence is currently under verification.");
        } else if (licence.getVerificationStatus() == LicenceStatus.REJECTED) {
            throw new LicenceNotApprovedException("Your driving licence was rejected. Please upload a valid licence.");
        } else if (licence.getVerificationStatus() != LicenceStatus.APPROVED) {
            throw new LicenceNotApprovedException("Your driving licence must be approved before booking.");
        }

        // Attach full User
        booking.setUser(user);

        // Attach full Car if car ID provided
        if (booking.getCar() != null && booking.getCar().getId() != null) {
            Optional<Car> carOpt = carRepository.findById(booking.getCar().getId());
            carOpt.ifPresent(booking::setCar);
        }

        Booking savedBooking = bookingRepository.save(booking);
        return ResponseEntity.ok(savedBooking);
    }

    @GetMapping("/user/{userId}")
    public List<Booking> getUserBookings(@PathVariable Long userId) {
        return bookingRepository.findByUserId(userId);
    }

    // Admin endpoints
    @GetMapping
    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Booking> updateBookingStatus(@PathVariable Long id, @RequestBody java.util.Map<String, String> updates) {
        return bookingRepository.findById(id).map(booking -> {
            booking.setStatus(updates.get("status"));
            return ResponseEntity.ok(bookingRepository.save(booking));
        }).orElse(ResponseEntity.notFound().build());
    }
}
