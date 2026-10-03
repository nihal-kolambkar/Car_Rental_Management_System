package com.veloce.controller;

import com.veloce.model.Car;
import com.veloce.repository.CarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cars")
public class CarController {

    @Autowired
    private CarRepository carRepository;

    @GetMapping
    public List<Car> getAllCars() {
        return carRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Car> getCarById(@PathVariable Long id) {
        return carRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Car createCar(@RequestBody Car car) {
        return carRepository.save(car);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Car> updateCar(@PathVariable Long id, @RequestBody Car carDetails) {
        return carRepository.findById(id).map(car -> {
            car.setName(carDetails.getName());
            car.setPricePerDay(carDetails.getPricePerDay());
            car.setImagePath(carDetails.getImagePath());
            car.setHorsepower(carDetails.getHorsepower());
            car.setTransmission(carDetails.getTransmission());
            car.setSeats(carDetails.getSeats());
            return ResponseEntity.ok(carRepository.save(car));
        }).orElse(ResponseEntity.notFound().build());
    }
}
