package jaerapps.functions.generateNames.util;

import org.junit.Test;

import java.util.*;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

public class AssignmentGeneratorTest {
    private static Person person(String name, String group) {
        return Person.create().setName(name).setEmail(name.toLowerCase() + "@example.com").setGroup(group).build();
    }

    private static List<Person> family() {
        return List.of(
                person("Ann", "a"), person("Bob", "a"),
                person("Cat", "c"), person("Dan", "c"), person("Eve", "c"),
                person("Fay", "f"), person("Gus", "g"), person("Hal", "h"));
    }

    @Test
    public void everyoneGivesAndReceivesOnceOutsideTheirGroup() throws Exception {
        for (int seed = 0; seed < 200; seed++) {
            List<Person> people = family();
            Map<String, Map<Person, Person>> result = new AssignmentGenerator(new Random(seed))
                    .assign(people, List.of("Adults", "Kids", "Stockings"), Map.of());

            assertThat(result.keySet()).containsExactly("Adults", "Kids", "Stockings").inOrder();
            Map<Person, Set<Person>> drawn = new HashMap<>();
            for (Map<Person, Person> assignments : result.values()) {
                assertThat(assignments.keySet()).containsExactlyElementsIn(people);
                assertThat(new HashSet<>(assignments.values())).containsExactlyElementsIn(people);
                assignments.forEach((santa, recipient) -> {
                    assertThat(recipient.getGroupKey()).isNotEqualTo(santa.getGroupKey());
                    assertThat(drawn.computeIfAbsent(santa, k -> new HashSet<>()).add(recipient)).isTrue();
                });
            }
        }
    }

    @Test
    public void forcedPairsAreKept() throws Exception {
        List<Person> people = family();
        Person ann = people.get(0);
        Person bob = people.get(1);
        // Forced pairs may even break the group rule.
        Map<String, Map<Person, Person>> forced = Map.of("Adults", Map.of(ann, bob));
        for (int seed = 0; seed < 50; seed++) {
            Map<String, Map<Person, Person>> result = new AssignmentGenerator(new Random(seed))
                    .assign(people, List.of("Adults", "Kids"), forced);
            assertThat(result.get("Adults").get(ann)).isEqualTo(bob);
            assertThat(result.get("Kids").get(ann)).isNotEqualTo(bob);
        }
    }

    @Test
    public void reportsImpossibleSetups() {
        // Two couples: each person has only the other couple to draw from, so a third exchange can't avoid repeats.
        List<Person> people = List.of(person("Ann", "a"), person("Bob", "a"), person("Cat", "c"), person("Dan", "c"));
        assertThrows(AssignmentGenerator.ImpossibleAssignmentException.class, () ->
                new AssignmentGenerator(new Random(1)).assign(people, List.of("One", "Two", "Three"), Map.of()));
    }

    @Test
    public void emailsAreCombinedPerAddress() {
        Person kid1 = Person.create().setName("Kid One").setEmail("parent@example.com").setGroup("k").build();
        Person kid2 = Person.create().setName("Kid Two").setEmail("Parent@example.com").setGroup("k").build();
        Person other = person("Zed", "z");
        Person other2 = person("Yan", "y");
        Map<String, Map<Person, Person>> run = Map.of("Kids", Map.of(kid1, other, kid2, other2, other, kid1, other2, kid2));

        Map<String, EmailService.Email> emails = EmailService.buildEmails("Larkin Christmas", run);
        assertThat(emails).hasSize(3);
        assertThat(emails.get("parent@example.com").text()).contains("Kid One → Zed");
        assertThat(emails.get("parent@example.com").text()).contains("Kid Two → Yan");
        assertThat(emails.get("parent@example.com").html()).contains(">Kid One</td>");
    }

    @Test
    public void twoPeopleDrawEachOther() throws Exception {
        Person ann = person("Ann", "");
        Person bob = person("Bob", "");
        Map<Person, Person> pairs = new AssignmentGenerator(new Random(0))
                .assign(List.of(ann, bob), List.of("Secret Santa"), Map.of()).get("Secret Santa");
        assertThat(pairs).containsExactly(ann, bob, bob, ann);
    }
}
