package jaerapps.functions.generateNames;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import jaerapps.functions.generateNames.util.*;

import java.security.SecureRandom;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The secret santa API behind grovelight.net/secretsanta, whatever it's served by: {@link LambdaHandler} on AWS,
 * {@link LocalServer} locally. Every request needs a signed-in user (a Cognito ID token, see {@link CognitoVerifier}),
 * and everything is scoped to their verified email:
 *
 *   GET    /me               { email, admin }
 *   GET    /exchanges        the runs I organized, newest first
 *   POST   /exchanges        draw a new run ({@link SantaRequest}), save it, email everyone → { id, emailed, failed }
 *   GET    /exchanges/{id}   one of my runs: its people and how the emails went, never the draw
 *   GET    /exchanges/{id}/draw  one of my runs' whole draw (the spoiler log), only when I ask for it
 *   DELETE /exchanges/{id}   delete one of my runs (its participants lose it from /draws too)
 *   GET    /draws            for runs my email is in, who I (or anyone sharing my email) give to; nothing else
 *
 * Errors are { error: "..." } written for people. Environment:
 *   COGNITO_USER_POOL_ID, COGNITO_CLIENT_ID  the /secretsanta user pool (flyte-frontend stack outputs)
 *   ADMIN_EMAIL       the one account allowed to force pairs (default george.mausshardt@gmail.com)
 *   TABLE_NAME        the DynamoDB table runs are kept in
 *   FROM_EMAIL        the SES-verified address the draw emails come from
 *   SANTA_DRY_RUN     "true" for local runs only: in-memory storage, logged emails, and mock sign-in tokens
 */
public class SantaApi {
    private static final Logger LOGGER = Logger.getLogger("Secret Santa");
    private static final Gson gson = new GsonBuilder().serializeNulls().create();
    private static final Pattern EXCHANGE_PATH = Pattern.compile("^/exchanges/([A-Za-z0-9]{1,40})(/draw)?$");

    public record Request(String method, String path, String authorization, String body) {
    }

    public record Response(int status, String body) {
        static Response json(int status, Object body) {
            return new Response(status, gson.toJson(body));
        }
    }

    static final boolean DRY_RUN = Boolean.parseBoolean(env("SANTA_DRY_RUN", "false"));

    private final CognitoVerifier verifier = new CognitoVerifier(
            env("COGNITO_USER_POOL_ID", ""), env("COGNITO_CLIENT_ID", ""),
            env("ADMIN_EMAIL", "george.mausshardt@gmail.com"), DRY_RUN);
    private final AssignmentGenerator generator = new AssignmentGenerator(new SecureRandom());
    private final RunStore store = DRY_RUN ? new RunStore.InMemory() : new DynamoStore(env("TABLE_NAME", ""));
    private final EmailService email = new EmailService(DRY_RUN, env("FROM_EMAIL", ""));

    public Response handle(Request request) {
        CognitoVerifier.Caller caller = verifier.verify(request.authorization());
        if (caller == null) {
            return Response.json(401, Map.of("error", "Sign in to continue."));
        }

        String method = request.method();
        String path = request.path().replaceAll("/+$", "");
        Matcher exchangePath = EXCHANGE_PATH.matcher(path);
        try {
            if (path.equals("/me") && method.equals("GET")) {
                return Response.json(200, Map.of("email", caller.email(), "admin", caller.admin()));
            } else if (path.equals("/exchanges") && method.equals("GET")) {
                return Response.json(200, Map.of("exchanges", store.ownedBy(caller.email()).stream().map(SantaRun::summary).toList()));
            } else if (path.equals("/exchanges") && method.equals("POST")) {
                return createRun(request.body(), caller);
            } else if (exchangePath.matches() && (method.equals("GET") || (method.equals("DELETE") && exchangePath.group(2) == null))) {
                SantaRun run = store.get(exchangePath.group(1));
                // Someone else's run looks the same as a missing one.
                if (run == null || !run.ownerEmail().equals(caller.email())) {
                    return Response.json(404, Map.of("error", "There's no exchange like that in your account."));
                } else if (exchangePath.group(2) != null) {
                    LOGGER.info("Run " + run.id() + " spoiler log viewed by its organizer");
                    return Response.json(200, Map.of("exchanges", run.spoilerLog()));
                } else if (method.equals("GET")) {
                    return Response.json(200, run.ownerView());
                } else {
                    store.delete(run.id());
                    LOGGER.info("Run " + run.id() + " deleted by its organizer");
                    return new Response(204, "");
                }
            } else if (path.equals("/draws") && method.equals("GET")) {
                List<Map<String, String>> draws = new ArrayList<>();
                for (SantaRun run : store.withParticipant(caller.email())) {
                    draws.addAll(run.drawsFor(caller.email()));
                }
                return Response.json(200, Map.of("draws", draws));
            } else {
                return Response.json(404, Map.of("error", "Not found."));
            }
        } catch (Exception e) {
            LOGGER.severe(method + " " + path + " failed: " + e);
            return Response.json(500, Map.of("error", "Something went wrong on our side. Try again in a minute."));
        }
    }

    private Response createRun(String body, CognitoVerifier.Caller caller) throws Exception {
        SantaRequest santaRequest;
        try {
            santaRequest = gson.fromJson(body, SantaRequest.class);
        } catch (JsonParseException e) {
            santaRequest = null;
        }
        if (santaRequest == null) {
            return Response.json(400, Map.of("error", "Couldn't read the request."));
        }
        if (santaRequest.hasForced() && !caller.admin()) {
            return Response.json(403, Map.of("error", "Only the site admin can force pairs."));
        }

        String title;
        List<Person> people;
        List<String> exchanges;
        Map<String, Map<Person, Person>> forced;
        try {
            title = santaRequest.cleanTitle();
            people = santaRequest.cleanPeople();
            exchanges = santaRequest.cleanExchanges();
            forced = santaRequest.cleanForced(people, exchanges);
        } catch (IllegalArgumentException e) {
            return Response.json(400, Map.of("error", e.getMessage()));
        }

        Map<String, Map<Person, Person>> exchangeToAssignments;
        try {
            exchangeToAssignments = generator.assign(people, exchanges, forced);
        } catch (AssignmentGenerator.ImpossibleAssignmentException e) {
            return Response.json(422, Map.of("error", e.getMessage()));
        }

        // Saved before anything is sent, so every email that goes out belongs to a run someone can see.
        String runId;
        try {
            runId = store.insert(SantaRun.create(title, caller.email(), people, exchangeToAssignments));
        } catch (Exception e) {
            LOGGER.severe("Failed to save run: " + e);
            return Response.json(500, Map.of("error", "Couldn't save the draw, so no emails were sent. Try again."));
        }

        List<String> failed = email.sendAssignments(title, exchangeToAssignments);

        long addresses = people.stream().flatMap(p -> p.getEmails().stream()).map(SantaRun::normalize).distinct().count();
        long emailed = addresses - failed.size();
        store.recordEmailResult(runId, emailed, failed);
        LOGGER.info("Run " + runId + " by " + caller.email() + ": " + people.size() + " people, " + exchanges.size()
                + " exchange(s), " + emailed + "/" + addresses + " emails sent" + (forced.isEmpty() ? "" : " (forced pairs)"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", runId);
        result.put("emailed", emailed);
        result.put("failed", failed);
        return Response.json(201, result);
    }

    static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
