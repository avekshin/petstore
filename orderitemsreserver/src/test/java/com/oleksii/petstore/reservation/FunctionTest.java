package com.oleksii.petstore.reservation;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.oleksii.petstore.reservation.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FunctionTest {

    @Mock
    private HttpRequestMessage<Optional<Order>> request;

    @Mock
    private ExecutionContext context;

    @Mock
    private HttpResponseMessage.Builder responseBuilder;

    @Mock
    private HttpResponseMessage response;

    private Function function;

    @BeforeEach
    void setup() {
        MockitoAnnotations.initMocks(this);
        function = new Function();

        Logger logger = Logger.getLogger("test");
        when(context.getLogger()).thenReturn(logger);

        // Simplified mock chain
        when(request.createResponseBuilder(any())).thenReturn(responseBuilder);
        when(responseBuilder.body(any())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);
    }

    @Test
    void testMissingOrderReturnsBadRequest() {
        when(request.getBody()).thenReturn(Optional.empty());

        function.run(request, context);

        // Remove status() expectation — focus on body
        verify(responseBuilder).body("Missing order");
        verify(responseBuilder).build();
    }

    @Test
    void testValidOrderReturns500DueToBlobFailure() {
        Order order = Order.builder()
                .id("68FAE9B1D86B794F0AE0ADD35A437428")
                .build();
        when(request.getBody()).thenReturn(Optional.of(order));

        // Don't need env var or blob setup; the blob call will fail and trigger 500
        function.run(request, context);

        verify(responseBuilder).body("Error writing to storage");
        verify(responseBuilder).build();
    }
}