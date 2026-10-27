package jaerapps.functions.generateNames.util;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

public class EmailServiceTest {
    private static Person person(String name, String email) {
        return Person.create().setName(name).setEmail(email).setGroup("").build();
    }

    @Test
    public void everyEmailGetsItsSantasLinesAndSharedEmailsAreCombined() {
        Person ann = person("Ann", "ann@x.com, ann@work.com");
        Person kid = person("Kid", "ann@x.com");
        Person bob = person("Bob", "bob@x.com");
        Map<Person, Person> pairs = new LinkedHashMap<>();
        pairs.put(ann, bob);
        pairs.put(kid, ann);
        pairs.put(bob, kid);

        Map<String, EmailService.Email> emails = EmailService.buildEmails("Larkin", Map.of("Adults", pairs));
        assertThat(emails.keySet()).containsExactly("ann@work.com", "ann@x.com", "bob@x.com");
        assertThat(emails.get("ann@work.com").text()).contains("Ann → Bob");
        assertThat(emails.get("ann@work.com").text()).doesNotContain("Kid → Ann");
        assertThat(emails.get("ann@x.com").text()).contains("Ann → Bob");
        assertThat(emails.get("ann@x.com").text()).contains("Kid → Ann");
    }

    @Test
    public void theHtmlEmailIsATablePerExchangeAndEscapesWhatPeopleTyped() {
        Person ann = person("Ann <b>", "ann@x.com");
        Person bob = person("Bob & Co", "bob@x.com");
        Map<Person, Person> big = new LinkedHashMap<>();
        big.put(ann, bob);
        big.put(bob, ann);
        Map<Person, Person> stockings = new LinkedHashMap<>(big);
        Map<String, Map<Person, Person>> exchanges = new LinkedHashMap<>();
        exchanges.put("Big gift", big);
        exchanges.put("Stockings", stockings);

        String html = EmailService.buildEmails("Larkin's <Christmas>", exchanges).get("ann@x.com").html();
        assertThat(html).contains("<th align=\"left\"");
        assertThat(html).contains(">Big gift</p>");
        assertThat(html).contains(">Stockings</p>");
        assertThat(html).contains("Ann &lt;b&gt;");
        assertThat(html).contains("Bob &amp; Co");
        assertThat(html).contains("Larkin&#39;s &lt;Christmas&gt;");
        assertThat(html).doesNotContain("<b>");
        assertThat(html).contains("href=\"https://grovelight.net/secretsanta\"");
    }
}
