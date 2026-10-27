package jaerapps.functions.generateNames.util;

import com.google.gson.Gson;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

public class SantaRequestTest {
    private static SantaRequest parse(String json) {
        return new Gson().fromJson(json, SantaRequest.class);
    }

    private static final String PEOPLE = "[{\"name\":\"Ann\",\"email\":\"a@x.com\",\"group\":\"Smith\"},"
            + "{\"name\":\"Bob\",\"email\":\"b@x.com\",\"group\":\"smith \"},"
            + "{\"name\":\"Cat\",\"email\":\"c@x.com\"},{\"name\":\"Dan\",\"email\":\"d@x.com\"}]";

    @Test
    public void groupsAreTrimmedAndCaseInsensitive() {
        List<Person> people = parse("{\"people\":" + PEOPLE + "}").cleanPeople();
        assertThat(people.get(0).getGroupKey()).isEqualTo(people.get(1).getGroupKey());
        assertThat(people.get(1).getGroup()).isEqualTo("smith");
        assertThat(people.get(2).getGroupKey()).isNotEqualTo(people.get(3).getGroupKey());
    }

    @Test
    public void rejectsOversizedGroups() {
        String json = "{\"people\":[{\"name\":\"Ann\",\"email\":\"a@x.com\",\"group\":\"s\"},"
                + "{\"name\":\"Bob\",\"email\":\"b@x.com\",\"group\":\"s\"},{\"name\":\"Cat\",\"email\":\"c@x.com\"}]}";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse(json).cleanPeople());
        assertThat(e).hasMessageThat().contains("at most half");
    }

    @Test
    public void rejectsDuplicateNames() {
        String json = "{\"people\":[{\"name\":\"Ann\",\"email\":\"a@x.com\"},{\"name\":\"ann\",\"email\":\"b@x.com\"},"
                + "{\"name\":\"Cat\",\"email\":\"c@x.com\"}]}";
        assertThrows(IllegalArgumentException.class, () -> parse(json).cleanPeople());
    }

    @Test
    public void blankForcedExchangeMeansAll() {
        SantaRequest request = parse("{\"people\":" + PEOPLE + ",\"exchanges\":[\"One\",\"Two\"],"
                + "\"forced\":[{\"santa\":\"ann\",\"recipient\":\"Cat\"}]}");
        List<Person> people = request.cleanPeople();
        Map<String, Map<Person, Person>> forced = request.cleanForced(people, request.cleanExchanges());
        assertThat(forced.keySet()).containsExactly("One", "Two");
        assertThat(forced.get("Two").get(people.get(0))).isEqualTo(people.get(2));
    }

    @Test
    public void twoPeopleAreEnoughButOneIsNot() {
        String two = "[{\"name\":\"Ann\",\"email\":\"a@x.com\"},{\"name\":\"Bob\",\"email\":\"b@x.com\"}]";
        assertThat(parse("{\"people\":" + two + "}").cleanPeople()).hasSize(2);

        String one = "[{\"name\":\"Ann\",\"email\":\"a@x.com\"}]";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("{\"people\":" + one + "}").cleanPeople());
        assertThat(e).hasMessageThat().isEqualTo("Add at least two people.");
    }

    @Test
    public void aPersonCanHaveSeveralEmails() {
        String people = "[{\"name\":\"Ann\",\"email\":\"ann@x.com; Ann@Work.com ann@x.com\"},{\"name\":\"Bob\",\"email\":\"b@x.com\"}]";
        Person ann = parse("{\"people\":" + people + "}").cleanPeople().get(0);
        assertThat(ann.getEmail()).isEqualTo("ann@x.com, Ann@Work.com");
        assertThat(ann.getEmails()).containsExactly("ann@x.com", "Ann@Work.com").inOrder();

        String bad = "[{\"name\":\"Ann\",\"email\":\"ann@x.com, nope\"},{\"name\":\"Bob\",\"email\":\"b@x.com\"}]";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("{\"people\":" + bad + "}").cleanPeople());
        assertThat(e).hasMessageThat().isEqualTo("\"nope\" (for \"Ann\") isn't a valid email.");
    }
}
