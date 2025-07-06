package com.chtrembl.petstore.order.service;

import com.chtrembl.petstore.order.model.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReservationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${petstore.service.reservation.url:http://localhost:7071}")
    private String reservationServiceUrl;

    public void createReservation(Order order) {
        log.info("Reserving stock for order {} in: {}/api/reservations", order.getId(), reservationServiceUrl);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    getReservationUrl(),
                    createRequest(order, createHeaders()), String.class
            );

            log.info("Reservation service response code : {}", response.getStatusCode());
            log.info("Reservation for order {} successfully created", order.getId());

        } catch (Exception e) {
            log.error("Error creating reservation for order: {}", e.getMessage(), e);
        }
    }

    private static HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    private HttpEntity<Order> createRequest(Order order, HttpHeaders headers) throws JsonProcessingException {
        return new HttpEntity<>(order, headers);
    }

    private String getReservationUrl() {
        return "%s/api/reservations".formatted(reservationServiceUrl);
    }
}
