package jaerapps.functions.generateNames.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Someone in the exchange. Names are unique within a request and are how everything else refers to a person.
 * A person can have several emails ({@link #getEmails()}); each one gets their draw, and signing in with any of them
 * shows it. People who share an email (kids under a parent, say) get one combined email at it.
 * People in the same group never give to each other; with no group, a person is a group of one.
 * {@link #getGroup()} is the group as typed ("" for none); {@link #getGroupKey()} is what's compared.
 */
public class Person {
    private final String name;
    private final String email;
    private final String group;

    public String getName() {
        return name;
    }

    /** Every email as entered, comma-separated ("ann@x.com, ann@work.com"); see {@link #getEmails()}. */
    public String getEmail() {
        return email;
    }

    /** The separate addresses in {@link #getEmail()}, in order, without case-insensitive repeats. */
    public List<String> getEmails() {
        return splitEmails(email);
    }

    /** Splits "a@x.com, b@y.com" (commas, semicolons or whitespace between) into its addresses. */
    public static List<String> splitEmails(String emails) {
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String part : (emails == null ? "" : emails).split("[,;\\s]+")) {
            if (!part.isEmpty()) {
                byKey.putIfAbsent(part.toLowerCase(Locale.ROOT), part);
            }
        }
        return List.copyOf(byKey.values());
    }

    public String getGroup() {
        return group;
    }

    /** Case-insensitive; a person without a group gets a key of their own that no typed group can match. */
    public String getGroupKey() {
        return group.isEmpty() ? "\0person:" + name.toLowerCase(Locale.ROOT) : group.toLowerCase(Locale.ROOT);
    }

    private Person(String name, String email, String group) {
        this.name = name;
        this.email = email;
        this.group = group;
    }

    public static Builder create() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return Objects.equals(name, person.name) && Objects.equals(email, person.email) && Objects.equals(group, person.group);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email, group);
    }

    @Override
    public String toString() {
        return name;
    }

    public static class Builder {
        private String name;
        private String email;
        private String group;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setEmail(String email) {
            this.email = email;
            return this;
        }

        public Builder setGroup(String group) {
            this.group = group;
            return this;
        }

        public Person build() {
            return new Person(name, email, group == null ? "" : group.trim());
        }
    }
}
