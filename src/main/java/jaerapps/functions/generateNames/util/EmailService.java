package jaerapps.functions.generateNames.util;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Sends each address one email listing every santa who uses it (so a parent gets their kids' draws too),
 * across all exchanges in the run. Goes out through SES from FROM_EMAIL, an address at the grovelight.net domain
 * that flyte-frontend's stack verifies.
 */
public class EmailService {
    private static final Logger LOGGER = Logger.getLogger(EmailService.class.getName());

    /** Set SANTA_DRY_RUN=true to log emails instead of sending them (local runs). */
    private final boolean dryRun;
    private final String from;
    private SesV2Client ses;

    public EmailService(boolean dryRun, String from) {
        this.dryRun = dryRun;
        this.from = from;
    }

    /** @return the addresses that couldn't be sent to */
    public List<String> sendAssignments(String title, Map<String, Map<Person, Person>> exchangeToAssignments) {
        Map<String, String> bodies = buildBodies(title, exchangeToAssignments);
        String subject = title + ": your Secret Santa draw";

        if (dryRun) {
            bodies.forEach((email, body) -> LOGGER.info("[dry run] To " + email + " / " + subject + "\n" + body));
            return List.of();
        }

        List<String> failed = new ArrayList<>();
        for (Map.Entry<String, String> entry : bodies.entrySet()) {
            try {
                client().sendEmail(SendEmailRequest.builder()
                        .fromEmailAddress("Secret Santa <" + from + ">")
                        .destination(Destination.builder().toAddresses(entry.getKey()).build())
                        .content(EmailContent.builder().simple(Message.builder()
                                .subject(Content.builder().data(subject).charset("UTF-8").build())
                                .body(Body.builder().text(Content.builder().data(entry.getValue()).charset("UTF-8").build()).build())
                                .build()).build())
                        .build());
            } catch (Exception e) {
                LOGGER.severe("Failed to send email to " + entry.getKey() + ": " + e.getMessage());
                failed.add(entry.getKey());
            }
        }
        return failed;
    }
    static Map<String, String> buildBodies(String title, Map<String, Map<Person, Person>> exchangeToAssignments) {
        boolean severalExchanges = exchangeToAssignments.size() > 1;
        // email -> exchange -> lines, kept in a stable order so the email reads the same way the list does
        Map<String, Map<String, List<String>>> lines = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        exchangeToAssignments.forEach((exchange, assignments) -> assignments.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getName()))
                .forEach(entry -> lines
                        .computeIfAbsent(entry.getKey().getEmail(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(exchange, k -> new ArrayList<>())
                        .add(entry.getKey().getName() + " → " + entry.getValue().getName())));

        Map<String, String> bodies = new LinkedHashMap<>();
        lines.forEach((email, byExchange) -> {
            StringBuilder text = new StringBuilder("Hi! Here's your Secret Santa draw for " + title
                    + " (santa on the left, recipient on the right):\n");
            byExchange.forEach((exchange, exchangeLines) -> {
                text.append('\n');
                if (severalExchanges) {
                    text.append(exchange).append('\n');
                }
                exchangeLines.forEach(line -> text.append("  ").append(line).append('\n'));
            });
            text.append("\nKeep it secret!\n");
            bodies.put(email, text.toString());
        });
        return bodies;
    }

    private SesV2Client client() {
        if (ses == null) {
            ses = SesV2Client.builder().httpClient(UrlConnectionHttpClient.create()).build();
        }
        return ses;
    }
}
