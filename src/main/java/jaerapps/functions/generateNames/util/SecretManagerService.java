package jaerapps.functions.generateNames.util;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretName;

import java.io.IOException;

public class SecretManagerService {
    public static String getSecret(String projectId, String secretId) throws IOException {
        // Initialize client that will be used to send requests. This client only needs to be created
        // once, and can be reused for multiple requests. After completing all of your requests, call
        // the "close" method on the client to safely clean up any remaining background resources.
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretName secretName = SecretName.of(projectId, secretId);
            Secret secret = client.getSecret(secretName);

            return secret.getName();
        }
    }
}
