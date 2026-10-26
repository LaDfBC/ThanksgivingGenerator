package jaerapps.functions.generateNames.util;

import java.util.*;
import java.util.regex.Pattern;

/**
 * The JSON body the /secretsanta page posts to create an exchange, and the checks that turn it into people, exchanges and forced
 * pairs. Anything wrong with it is an {@link IllegalArgumentException} whose message is shown to the user.
 */
public class SantaRequest {
    public static final int MAX_PEOPLE = 150;
    public static final int MAX_EXCHANGES = 5;
    private static final int MAX_NAME = 60;
    private static final int MAX_TITLE = 80;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public String title;
    public List<PersonInput> people;
    public List<String> exchanges;
    public List<ForcedInput> forced;

    public static class PersonInput {
        public String name;
        public String email;
        public String group;
    }

    public static class ForcedInput {
        public String santa;
        public String recipient;
        /** Blank means every exchange. */
        public String exchange;
    }

    public boolean hasForced() {
        return forced != null && !forced.isEmpty();
    }

    public String cleanTitle() {
        String t = trim(title);
        if (t.isEmpty()) {
            return "Secret Santa";
        }
        require(t.length() <= MAX_TITLE, "The title can be at most " + MAX_TITLE + " characters.");
        return t;
    }

    public List<Person> cleanPeople() {
        require(people != null && people.size() >= 3, "Add at least three people.");
        require(people.size() <= MAX_PEOPLE, "At most " + MAX_PEOPLE + " people per run.");

        Set<String> seen = new HashSet<>();
        List<Person> result = new ArrayList<>();
        for (PersonInput input : people) {
            String name = trim(input.name);
            String email = trim(input.email);
            String group = trim(input.group);
            require(!name.isEmpty(), "Every person needs a name.");
            require(name.length() <= MAX_NAME, "\"" + name + "\" is longer than " + MAX_NAME + " characters.");
            require(seen.add(name.toLowerCase(Locale.ROOT)), "\"" + name + "\" is listed twice; names must be unique.");
            require(EMAIL.matcher(email).matches() && email.length() <= 254, "\"" + name + "\" needs a valid email.");
            require(group.length() <= MAX_NAME, "The group for \"" + name + "\" is longer than " + MAX_NAME + " characters.");
            result.add(Person.create()
                    .setName(name)
                    .setEmail(email)
                    .setGroup(group)
                    .build());
        }

        checkGroupSizes(result);
        return result;
    }

    public List<String> cleanExchanges() {
        List<String> result = new ArrayList<>();
        for (String exchange : exchanges == null ? List.<String>of() : exchanges) {
            String name = trim(exchange);
            require(!name.isEmpty(), "Every exchange needs a name.");
            require(name.length() <= MAX_NAME, "Exchange \"" + name + "\" is longer than " + MAX_NAME + " characters.");
            require(!result.contains(name), "Exchange \"" + name + "\" is listed twice.");
            result.add(name);
        }
        require(!result.isEmpty(), "Add at least one exchange.");
        require(result.size() <= MAX_EXCHANGES, "At most " + MAX_EXCHANGES + " exchanges per run.");
        return result;
    }

    /** @return exchange -> santa -> recipient */
    public Map<String, Map<Person, Person>> cleanForced(List<Person> people, List<String> exchanges) {
        Map<String, Person> byName = new HashMap<>();
        people.forEach(person -> byName.put(person.getName().toLowerCase(Locale.ROOT), person));

        Map<String, Map<Person, Person>> result = new HashMap<>();
        for (ForcedInput input : forced == null ? List.<ForcedInput>of() : forced) {
            Person santa = byName.get(trim(input.santa).toLowerCase(Locale.ROOT));
            Person recipient = byName.get(trim(input.recipient).toLowerCase(Locale.ROOT));
            require(santa != null && recipient != null, "A forced pair names someone who isn't in the list.");
            require(!santa.equals(recipient), santa.getName() + " can't be forced to give to themselves.");

            String exchange = trim(input.exchange);
            require(exchange.isEmpty() || exchanges.contains(exchange), "Forced pair uses unknown exchange \"" + exchange + "\".");
            for (String target : exchange.isEmpty() ? exchanges : List.of(exchange)) {
                Map<Person, Person> pairs = result.computeIfAbsent(target, k -> new HashMap<>());
                require(!pairs.containsKey(santa), santa.getName() + " is forced twice in \"" + target + "\".");
                require(!pairs.containsValue(recipient), recipient.getName() + " is a forced recipient twice in \"" + target + "\".");
                pairs.put(santa, recipient);
            }
        }
        return result;
    }

    /** Everyone in a group needs someone outside it, so no group can be more than half the list. */
    private static void checkGroupSizes(List<Person> people) {
        Map<String, List<Person>> groups = new HashMap<>();
        people.forEach(person -> groups.computeIfAbsent(person.getGroupKey(), k -> new ArrayList<>()).add(person));
        groups.forEach((group, members) -> require(members.size() * 2 <= people.size(),
                "The group with " + members.get(0).getName() + " has " + members.size() + " of " + people.size()
                        + " people; a group can be at most half the list."));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
