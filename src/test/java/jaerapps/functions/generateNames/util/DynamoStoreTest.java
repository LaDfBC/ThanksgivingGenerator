package jaerapps.functions.generateNames.util;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

public class DynamoStoreTest {
    @Test
    public void runsSurviveTheRoundTripThroughJson() {
        Person ann = Person.create().setName("Ann").setEmail("ann@x.com").setGroup("Smiths").build();
        Person bob = Person.create().setName("Bob").setEmail("bob@x.com").setGroup("").build();
        Person cat = Person.create().setName("Cat").setEmail("cat@x.com").setGroup("").build();
        Map<Person, Person> pairs = new LinkedHashMap<>();
        pairs.put(ann, bob);
        pairs.put(bob, cat);
        pairs.put(cat, ann);
        SantaRun run = SantaRun.create("Larkin", "owner@x.com", List.of(ann, bob, cat), Map.of("Adults", pairs))
                .withId("abc").withEmailResult(2, List.of("cat@x.com"));

        SantaRun back = DynamoStore.fromJson(DynamoStore.toJson(run));
        assertThat(back.id()).isEqualTo("abc");
        assertThat(back.createdAt()).isEqualTo(run.createdAt());
        assertThat(back.ownerView()).isEqualTo(run.ownerView());
        assertThat(back.spoilerLog()).isEqualTo(run.spoilerLog());
        assertThat(back.drawsFor("ann@x.com")).isEqualTo(run.drawsFor("ann@x.com"));

        SantaRun unsent = DynamoStore.fromJson(DynamoStore.toJson(run.withEmailResult(0, List.of())
                .withId("abc")));
        assertThat(unsent.failed()).isEmpty();
    }
}
