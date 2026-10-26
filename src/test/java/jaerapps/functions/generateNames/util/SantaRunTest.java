package jaerapps.functions.generateNames.util;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

public class SantaRunTest {
    private static Person person(String name, String email) {
        return Person.create().setName(name).setEmail(email).setGroup("").build();
    }

    private static SantaRun run() {
        Person kid = person("Kid", "Parent@x.com");
        Person parent = person("Parent", "parent@x.com");
        Person cat = person("Cat", "cat@x.com");
        Map<Person, Person> adults = new LinkedHashMap<>();
        adults.put(kid, cat);
        adults.put(parent, kid);
        adults.put(cat, parent);
        Map<String, Map<Person, Person>> exchanges = new LinkedHashMap<>();
        exchanges.put("Adults", adults);
        return SantaRun.create("Larkin", "owner@x.com", List.of(kid, parent, cat), exchanges).withId("abc");
    }

    @Test
    public void participantsSeeOnlyTheirOwnLines() {
        List<Map<String, String>> draws = run().drawsFor(" PARENT@x.com ");
        assertThat(draws).hasSize(2);
        assertThat(draws.get(0)).containsEntry("santa", "Kid");
        assertThat(draws.get(0)).containsEntry("recipient", "Cat");
        assertThat(draws.get(1)).containsEntry("santa", "Parent");
        assertThat(draws.get(0).keySet()).containsExactly("runId", "title", "createdAt", "exchange", "santa", "recipient");

        assertThat(run().drawsFor("owner@x.com")).isEmpty();
    }

    @Test
    public void ownerViewNeverIncludesTheDraw() {
        Map<String, Object> view = run().ownerView();
        assertThat(view).doesNotContainKey("pairs");
        assertThat(view.toString()).doesNotContain("recipient");
        assertThat(run().participantEmails()).containsExactly("parent@x.com", "cat@x.com");
    }

    @Test
    public void spoilerLogHasEveryPair() {
        List<Map<String, Object>> log = run().spoilerLog();
        assertThat(log).hasSize(1);
        assertThat(log.get(0)).containsEntry("exchange", "Adults");
        assertThat(log.get(0)).containsEntry("pairs", List.of(
                Map.of("santa", "Kid", "recipient", "Cat"),
                Map.of("santa", "Parent", "recipient", "Kid"),
                Map.of("santa", "Cat", "recipient", "Parent")));
    }
}
