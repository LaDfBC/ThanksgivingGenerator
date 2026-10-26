package jaerapps.functions.generateNames;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Serves {@link SantaApi} on http://localhost:8080 (PORT to change it) for local runs: `./gradlew runFunction`,
 * usually with SANTA_DRY_RUN=true. Answers CORS for the dev page, which API Gateway does when deployed.
 */
public class LocalServer {
    private static final Logger LOGGER = Logger.getLogger(LocalServer.class.getName());
    private static final Set<String> ALLOWED_ORIGINS =
            Set.of(SantaApi.env("ALLOWED_ORIGINS", "http://localhost:5173").split(","));

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(SantaApi.env("PORT", "8080"));
        SantaApi api = new SantaApi();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> serve(api, exchange));
        server.start();
        LOGGER.info("Secret santa API on http://localhost:" + port + (SantaApi.DRY_RUN ? " (dry run)" : ""));
    }

    private static void serve(SantaApi api, HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().add("Vary", "Origin");
        }
        if (exchange.getRequestMethod().equals("OPTIONS")) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Authorization, Content-Type");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        SantaApi.Response response = api.handle(new SantaApi.Request(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));

        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }
}
