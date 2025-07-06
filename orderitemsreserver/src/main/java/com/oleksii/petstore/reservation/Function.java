package com.oleksii.petstore.reservation;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.oleksii.petstore.reservation.model.Order;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {

    public static final String STORAGE_BLOB_SERVICE_URI = "AzureWebJobsStorage__blobServiceUri";

    /**
     * This function listens at endpoint "/api/HttpExample". Two ways to invoke it using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/HttpExample
     * 2. curl "{your host}/api/HttpExample?name=HTTP%20Query"
     */
    @FunctionName("reservation")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<Order>> request,
            final ExecutionContext context
    ) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        Optional<Order> orderOpt = request.getBody();

        if (!orderOpt.isPresent()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Missing order").build();
        }

        Order order = orderOpt.get();
        context.getLogger().info("Received order for session ID: " + order.getId());

        try {
            BlobContainerClient containerClient = getOrCreateContainerClient(createBlobServiceClient());

            String blobName = createAndUploadReservation(order, containerClient);

            context.getLogger().info("Reservation written to blob: " + blobName);

            return request.createResponseBuilder(HttpStatus.OK).body("Order stored").build();
        } catch (Exception e) {
            context.getLogger().severe("Error saving to blob: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body("Error writing to storage").build();
        }
    }

    private static BlobServiceClient createBlobServiceClient() {
        return new BlobServiceClientBuilder()
                .endpoint(System.getenv(STORAGE_BLOB_SERVICE_URI))
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }

    private static BlobContainerClient getOrCreateContainerClient(BlobServiceClient blobServiceClient) {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient("reservations");
        if (!containerClient.exists()) {
            containerClient.create();
        }
        return containerClient;
    }

    private static String createAndUploadReservation(Order order, BlobContainerClient containerClient) throws JsonProcessingException {
        String blobName = buildReservationFileName(order);

        String serializedOrder = serializeOrder(order);

        containerClient.getBlobClient(blobName)
                .upload(new ByteArrayInputStream(serializedOrder.getBytes(StandardCharsets.UTF_8)), serializedOrder.length(), true);
        return blobName;
    }

    private static String buildReservationFileName(Order order) {
        return order.getId() + ".json";
    }

    private static String serializeOrder(Order order) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(order);
    }

}
