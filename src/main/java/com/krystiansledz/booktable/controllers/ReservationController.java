package com.krystiansledz.booktable.controllers;

import com.krystiansledz.booktable.dto.RatingDTO;
import com.krystiansledz.booktable.dto.ReservationDTO;
import com.krystiansledz.booktable.models.Customer;
import com.krystiansledz.booktable.models.Reservation;
import com.krystiansledz.booktable.models.Restaurant;
import com.krystiansledz.booktable.models.RestaurantTable;
import com.krystiansledz.booktable.security.jwt.JwtUtils;
import com.krystiansledz.booktable.security.services.CustomerService;
import com.krystiansledz.booktable.security.services.ReservationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    @Autowired
    JwtUtils jwtUtils;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private CustomerService customerService;

    @GetMapping("/my")
    public ResponseEntity<?> getMyReservations(@RequestHeader("Authorization") String token) {
        try {
            String email = jwtUtils.getUserNameFromJwtToken(token.replace("Bearer ", ""));
            Customer customer = customerService.findCustomerByEmail(email);

            List<Reservation> reservations = reservationService.getReservationsByCustomer(customer);

            List<ReservationDTO> reservationDTOList = reservations.stream()
                    .map(reservation -> {
                        ReservationDTO reservationDTO = new ReservationDTO();

                        reservationDTO.setId(reservation.getId());
                        reservationDTO.setStartDateTime(reservation.getStartDateTime());
                        reservationDTO.setEndDateTime(reservation.getEndDateTime());
                        if (reservation.getRating() != null) {
                            reservationDTO.setRating(reservation.getRating());
                        }
                        reservationDTO.setRestaurantTable_id(null);

                        RestaurantTable originalRestaurantTable = reservation.getRestaurantTable();
                        RestaurantTable restaurantTable = new RestaurantTable();
                        restaurantTable.setNumber(originalRestaurantTable.getNumber());
                        restaurantTable.setCapacity(originalRestaurantTable.getCapacity());
                        restaurantTable.setReservations(null);
                        reservationDTO.setRestaurantTable(restaurantTable);

                        Restaurant originalRestaurant = reservation.getRestaurantTable().getRestaurant();
                        Restaurant restaurant = new Restaurant();
                        restaurant.setId(originalRestaurant.getId());
                        restaurant.setName(originalRestaurant.getName());
                        restaurant.setAddress(originalRestaurant.getAddress());
                        restaurant.setRestaurantTables(null);
                        restaurant.setBusinessHours(null);

                        reservationDTO.setRestaurant(restaurant);
                        return reservationDTO;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(reservationDTOList);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getReservationById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(reservationService.getReservationById(id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PutMapping("/{id}/rating")
    public ResponseEntity<?> setReservationRating(@RequestHeader("Authorization") String token, @PathVariable Long id, @RequestBody RatingDTO ratingDTO) {
        try {
            String email = jwtUtils.getUserNameFromJwtToken(token.replace("Bearer ", ""));
            Customer customer = customerService.findCustomerByEmail(email);
            Reservation reservation = reservationService.getReservationById(id);

            if (!reservation.getCustomer().getId().equals(customer.getId())) {
                return new ResponseEntity<>(HttpStatus.FORBIDDEN);
            }

            if (reservation.getEndDateTime().isAfter(LocalDateTime.now())) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            reservation.setRating(ratingDTO.getRating());
            reservationService.updateReservation(reservation);

            return new ResponseEntity<>(HttpStatus.OK);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReservation(@RequestHeader("Authorization") String token, @PathVariable Long id) {
        try {
            String email = jwtUtils.getUserNameFromJwtToken(token.replace("Bearer ", ""));
            Customer customer = customerService.findCustomerByEmail(email);

            Reservation reservation = reservationService.getReservationById(id);

            if (!reservation.getCustomer().getEmail().equals(customer.getEmail())) {
                return new ResponseEntity<>("You don't have a reservation with the given id", HttpStatus.FORBIDDEN);
            }

            if (reservation.getStartDateTime().isBefore(LocalDateTime.now())) {
                return new ResponseEntity<>("You cannot delete a reservation that is currently in progress or has ended", HttpStatus.BAD_REQUEST);
            }

            reservationService.deleteReservation(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

}
