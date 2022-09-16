package jaerapps.functions.generateNames.util;

import com.google.cloud.secretmanager.v1.*;

import java.io.IOException;

public class GoogleSecretManagerService {
    public static String getEmailPassword() {
        String projectId = "ThanksgivingGenerator";
        String secretId = "ServiceEmailPassword";
        try {
            return getSecret(projectId, secretId);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw new RuntimeException("Failed to fetch email password!");
        }
    }

    // Get an existing secret.
    private static String getSecret(String projectId, String secretId) throws IOException {
        // Initialize client that will be used to send requests. This client only needs to be created
        // once, and can be reused for multiple requests. After completing all of your requests, call
        // the "close" method on the client to safely clean up any remaining background resources.
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            // Build the name.
            SecretName secretName = SecretName.of(projectId, secretId);

            // Create the secret.
            AccessSecretVersionResponse secret = client.accessSecretVersion("projects/6546897510/secrets/ServiceEmailPassword/versions/latest");

            return secret.getPayload().getData().toStringUtf8();
        }
    }
}