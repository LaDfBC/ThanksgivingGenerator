package jaerapps.functions.generateNames.util;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Sends each address one email listing every santa who uses it (so a parent gets their kids' draws too, and someone
 * with several emails gets their draw at each), across all exchanges in the run: a table per exchange in HTML, with
 * a plain-text copy. Goes out through SES from FROM_EMAIL, an address at the grovelight.net domain that
 * flyte-frontend's stack verifies.
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

    /** One address's email: the HTML most mail apps show, and the plain text for the rest. */
    public record Email(String text, String html) {
    }

    /** One line of a draw. */
    record Pair(String santa, String recipient) {
    }

    private static final String SITE = "https://grovelight.net/secretsanta";
    private static final String SIGN_IN_NOTE =
            "As a new addition this year, you can log in with this email address to get a reminder at ";

    /** @return the addresses that couldn't be sent to */
    public List<String> sendAssignments(String title, Map<String, Map<Person, Person>> exchangeToAssignments) {
        Map<String, Email> emails = buildEmails(title, exchangeToAssignments);
        String subject = title + ": your Secret Santa draw";

        if (dryRun) {
            emails.forEach((email, body) -> LOGGER.info("[dry run] To " + email + " / " + subject + "\n" + body.text()));
            return List.of();
        }

        List<String> failed = new ArrayList<>();
        for (Map.Entry<String, Email> entry : emails.entrySet()) {
            try {
                client().sendEmail(SendEmailRequest.builder()
                        .fromEmailAddress("Secret Santa <" + from + ">")
                        .destination(Destination.builder().toAddresses(entry.getKey()).build())
                        .content(EmailContent.builder().simple(Message.builder()
                                .subject(utf8(subject))
                                .body(Body.builder().text(utf8(entry.getValue().text())).html(utf8(entry.getValue().html())).build())
                                .build()).build())
                        .build());
            } catch (Exception e) {
                LOGGER.severe("Failed to send email to " + entry.getKey() + ": " + e.getMessage());
                failed.add(entry.getKey());
            }
        }
        return failed;
    }

    private static Content utf8(String data) {
        return Content.builder().data(data).charset("UTF-8").build();
    }

    static Map<String, Email> buildEmails(String title, Map<String, Map<Person, Person>> exchangeToAssignments) {
        boolean severalExchanges = exchangeToAssignments.size() > 1;
        // email -> exchange -> pairs, kept in a stable order so the email reads the same way the list does
        Map<String, Map<String, List<Pair>>> draws = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        // Every one of a santa's emails gets their line.
        exchangeToAssignments.forEach((exchange, assignments) -> assignments.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getName()))
                .forEach(entry -> entry.getKey().getEmails().forEach(email -> draws
                        .computeIfAbsent(email, k -> new LinkedHashMap<>())
                        .computeIfAbsent(exchange, k -> new ArrayList<>())
                        .add(new Pair(entry.getKey().getName(), entry.getValue().getName())))));

        Map<String, Email> emails = new LinkedHashMap<>();
        draws.forEach((email, byExchange) -> emails.put(email,
                new Email(text(title, byExchange, severalExchanges), html(title, byExchange, severalExchanges))));
        return emails;
    }

    private static String text(String title, Map<String, List<Pair>> byExchange, boolean severalExchanges) {
        StringBuilder text = new StringBuilder("Hi! Here's your Secret Santa draw for " + title
                + " (santa on the left, recipient on the right):\n");
        byExchange.forEach((exchange, pairs) -> {
            text.append('\n');
            if (severalExchanges) {
                text.append(exchange).append('\n');
            }
            pairs.forEach(pair -> text.append("  ").append(pair.santa()).append(" → ").append(pair.recipient()).append('\n'));
        });
        text.append('\n').append(SIGN_IN_NOTE).append("www.grovelight.net/secretsanta\n");
        return text.toString();
    }

    // ---- HTML ----
    // Mail apps support little CSS, so this is the email-safe kind: nested tables, inline styles, no images.

    private static final String INK = "#2b2522";
    private static final String MUTED = "#6b625a";
    private static final String ACCENT = "#a8262e";
    private static final String LINE = "#ece6dc";
    private static final String FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    private static String html(String title, Map<String, List<Pair>> byExchange, boolean severalExchanges) {
        int count = byExchange.values().stream().mapToInt(List::size).sum();
        StringBuilder tables = new StringBuilder();
        byExchange.forEach((exchange, pairs) -> {
            if (severalExchanges) {
                tables.append("<p style=\"margin:28px 0 10px;font-size:13px;font-weight:700;letter-spacing:.06em;"
                        + "text-transform:uppercase;color:" + ACCENT + "\">").append(esc(exchange)).append("</p>");
            } else {
                tables.append("<div style=\"height:22px\"></div>");
            }
            tables.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                    + "style=\"border-collapse:separate;border:1px solid " + LINE + ";border-radius:10px\">"
                    + "<tr>" + th("Santa", "border-radius:10px 0 0 0") + th("Buying for", "border-radius:0 10px 0 0") + "</tr>");
            for (int i = 0; i < pairs.size(); i++) {
                Pair pair = pairs.get(i);
                String border = i == 0 ? "" : "border-top:1px solid " + LINE + ";";
                tables.append("<tr>")
                        .append("<td style=\"" + border + "padding:14px 18px;font-size:16px;color:" + INK + "\">")
                        .append(esc(pair.santa())).append("</td>")
                        .append("<td style=\"" + border + "padding:14px 18px;font-size:17px;font-weight:700;color:" + INK + "\">")
                        .append(esc(pair.recipient())).append("</td>")
                        .append("</tr>");
            }
            tables.append("</table>");
        });

        String intro = count == 1 ? "Here's who you're buying for."
                : "Here's who everyone on this address is buying for.";
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">"
                + "<title>" + esc(title) + "</title></head>"
                + "<body style=\"margin:0;padding:0;background:#f5f2ec\">"
                // The inbox preview line.
                + "<div style=\"display:none;max-height:0;overflow:hidden\">Your Secret Santa draw for " + esc(title) + "</div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f5f2ec\">"
                + "<tr><td align=\"center\" style=\"padding:32px 16px\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:560px;background:#ffffff;border-radius:14px;border-top:5px solid " + ACCENT + "\">"
                + "<tr><td style=\"padding:32px 32px 28px;font-family:" + FONT + ";color:" + INK + "\">"
                + "<p style=\"margin:0 0 6px;font-size:12px;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:"
                + ACCENT + "\">Secret Santa</p>"
                + "<h1 style=\"margin:0;font-size:26px;line-height:1.25;font-weight:800;color:" + INK + "\">" + esc(title) + "</h1>"
                + "<p style=\"margin:12px 0 0;font-size:16px;line-height:1.5;color:" + MUTED + "\">" + intro + "</p>"
                + tables
                + "<p style=\"margin:28px 0 0;padding-top:20px;border-top:1px solid " + LINE + ";font-size:14px;line-height:1.55;color:"
                + MUTED + "\">" + esc(SIGN_IN_NOTE) + "<a href=\"" + SITE + "\" style=\"color:" + ACCENT
                + ";font-weight:600\">grovelight.net/secretsanta</a></p>"
                + "</td></tr></table>"
                + "</td></tr></table></body></html>";
    }

    private static String th(String label, String corner) {
        return "<th align=\"left\" style=\"padding:10px 18px;background:#faf7f2;" + corner + ";font-size:12px;font-weight:700;"
                + "letter-spacing:.06em;text-transform:uppercase;color:" + MUTED + "\">" + label + "</th>";
    }

    /** Names and titles are typed by people; nothing they type should become markup. */
    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private SesV2Client client() {
        if (ses == null) {
            ses = SesV2Client.builder().httpClient(UrlConnectionHttpClient.create()).build();
        }
        return ses;
    }
}
