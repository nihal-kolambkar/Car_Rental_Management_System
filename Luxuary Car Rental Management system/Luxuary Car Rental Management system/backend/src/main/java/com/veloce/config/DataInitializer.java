package com.veloce.config;

import com.veloce.model.Car;
import com.veloce.model.User;
import com.veloce.repository.CarRepository;
import com.veloce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CarRepository carRepository;

    @Override
    public void run(String... args) throws Exception {
        // Initialize the ONE single master admin user
        Optional<User> adminOpt = userRepository.findByEmail("admin@veloce.com");
        if (!adminOpt.isPresent()) {
            User admin = new User();
            admin.setFullName("Master Administrator");
            admin.setEmail("admin@veloce.com");
            admin.setPassword("admin123");
            admin.setRole("admin");
            userRepository.save(admin);
        } else {
            User admin = adminOpt.get();
            admin.setRole("admin");
            userRepository.save(admin);
        }

        // Enforce rule: ONLY ONE ADMIN! Demote any other user with admin role to customer
        List<User> allUsers = userRepository.findAll();
        for (User u : allUsers) {
            if (!"admin@veloce.com".equalsIgnoreCase(u.getEmail()) && 
                ("admin".equalsIgnoreCase(u.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(u.getRole()))) {
                u.setRole("ROLE_CUSTOMER");
                userRepository.save(u);
            }
        }

        // Initialize sample fleet cars if table is empty
        if (carRepository.count() == 0) {
            Car car1 = new Car();
            car1.setName("Ares GT Sport");
            car1.setPricePerDay(250.0);
            car1.setImagePath("images/car_sport_1778313498515.png");
            car1.setHorsepower(550);
            car1.setTransmission("Auto");
            car1.setSeats(2);
            carRepository.save(car1);

            Car car2 = new Car();
            car2.setName("Titan Lux SUV");
            car2.setPricePerDay(180.0);
            car2.setImagePath("images/car_suv_1778313477078.png");
            car2.setHorsepower(420);
            car2.setTransmission("Auto");
            car2.setSeats(5);
            carRepository.save(car2);

            Car car3 = new Car();
            car3.setName("Phantom Sovereign");
            car3.setPricePerDay(320.0);
            car3.setImagePath("images/car_sedan_1778313459146.png");
            car3.setHorsepower(600);
            car3.setTransmission("Auto");
            car3.setSeats(4);
            carRepository.save(car3);
        }
    }
}
