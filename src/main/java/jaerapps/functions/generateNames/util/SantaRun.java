package jaerapps.functions.generateNames.util;

import java.util.*;

/**
 * One saved run: everything the organizer entered plus the draw for each exchange. Only the organizer can see
 * the people and exchanges, through {@link #ownerView()}, and the whole draw only when they ask for it, through
 * {@link #spoilerLog()}; otherwise each participant sees just their own line of it, through {@link #drawsFor(String)}.
 *
 * @param emailed null until the emails have gone out
 */
public record SantaRun(String id,
                       String title,
                       String ownerEmail,
                       Date createdAt,
                       List<Person> people,
                       List<Exchange> exchanges,
                       Long emailed,
                       List<String> failed) {

    /** @param pairs santa name -> recipient name */
    public record Exchange(String name, Map<String, String> pairs) {
    }

    public static SantaRun create(String title, String ownerEmail, List<Person> people,
                                  Map<String, Map<Person, Person>> exchangeToAssignments) {
        List<Exchange> exchanges = new ArrayList<>();
        exchangeToAssignments.forEach((name, assignments) -> {
            Map<String, String> pairs = new LinkedHashMap<>();
            assignments.forEach((santa, recipient) -> pairs.put(santa.getName(), recipient.getName()));
            exchanges.add(new Exchange(name, pairs));
        });
        return new SantaRun(null, title, ownerEmail, new Date(), people, exchanges, null, List.of());
    }

    public SantaRun withId(String newId) {
        return new SantaRun(newId, title, ownerEmail, createdAt, people, exchanges, emailed, failed);
    }

    public SantaRun withEmailResult(long newEmailed, List<String> newFailed) {
        return new SantaRun(id, title, ownerEmail, createdAt, people, exchanges, newEmailed, newFailed);
    }

    /** Lower-cased, for finding the runs someone is in. */
    public List<String> participantEmails() {
        return people.stream().flatMap(person -> person.getEmails().stream()).map(SantaRun::normalize).distinct().toList();
    }

    /** What the list of an organizer's exchanges shows. */
    public Map<String, Object> summary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", id);
        summary.put("title", title);
        summary.put("createdAt", createdAt.toInstant().toString());
        summary.put("people", people.size());
        summary.put("exchanges", exchanges.stream().map(Exchange::name).toList());
        summary.put("emailed", emailed);
        summary.put("failed", failed.size());
        return summary;
    }

    /** The organizer's full view: who's in it and how the emails went, but not the draw. */
    public Map<String, Object> ownerView() {
        Map<String, Object> view = summary();
        List<Map<String, String>> peopleViews = new ArrayList<>();
        people.forEach(person -> peopleViews.add(Map.of(
                "name", person.getName(),
                "email", person.getEmail(),
                "group", person.getGroup())));
        view.put("people", peopleViews);
        view.put("failed", failed);
        return view;
    }

    /** The whole draw, for the organizer only and only on request: each exchange with every santa and recipient. */
    public List<Map<String, Object>> spoilerLog() {
        List<Map<String, Object>> log = new ArrayList<>();
        for (Exchange exchange : exchanges) {
            List<Map<String, String>> pairs = new ArrayList<>();
            exchange.pairs().forEach((santa, recipient) -> pairs.add(Map.of("santa", santa, "recipient", recipient)));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("exchange", exchange.name());
            entry.put("pairs", pairs);
            log.add(entry);
        }
        return log;
    }

    /** The draws for santas who use this email: title, exchange, santa and recipient, and nothing else. */
    public List<Map<String, String>> drawsFor(String email) {
        String wanted = normalize(email);
        Set<String> santas = new LinkedHashSet<>();
        people.stream()
                .filter(person -> person.getEmails().stream().anyMatch(e -> normalize(e).equals(wanted)))
                .forEach(person -> santas.add(person.getName()));

        List<Map<String, String>> draws = new ArrayList<>();
        for (Exchange exchange : exchanges) {
            for (String santa : santas) {
                String recipient = exchange.pairs().get(santa);
                if (recipient != null) {
                    Map<String, String> draw = new LinkedHashMap<>();
                    draw.put("runId", id);
                    draw.put("title", title);
                    draw.put("createdAt", createdAt.toInstant().toString());
                    draw.put("exchange", exchange.name());
                    draw.put("santa", santa);
                    draw.put("recipient", recipient);
                    draws.add(draw);
                }
            }
        }
        return draws;
    }

    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
