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

        Map<String, String> bodies = EmailService.buildBodies("Larkin", Map.of("Adults", pairs));
        assertThat(bodies.keySet()).containsExactly("ann@work.com", "ann@x.com", "bob@x.com");
        assertThat(bodies.get("ann@work.com")).contains("Ann → Bob");
        assertThat(bodies.get("ann@work.com")).doesNotContain("Kid → Ann");
        assertThat(bodies.get("ann@x.com")).contains("Ann → Bob");
        assertThat(bodies.get("ann@x.com")).contains("Kid → Ann");
    }
}
