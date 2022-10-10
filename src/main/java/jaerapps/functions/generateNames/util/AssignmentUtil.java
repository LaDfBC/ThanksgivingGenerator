package jaerapps.functions.generateNames.util;

import com.google.api.client.util.Lists;
import com.google.common.collect.Maps;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AssignmentUtil {
    public static Map<String, List<Assignment>> combineChildrenUnderParent(Map<Person, Person> assignments) {
        Map<String, List<Assignment>> parentGroups = Maps.newHashMap();

        assignments.forEach((santa, target) -> {
            if (parentGroups.containsKey(santa.getParentOverride())) {
                List<Assignment> currentPeopleUnderParent= parentGroups.get(santa.getParentOverride());
                currentPeopleUnderParent.add(new Assignment(santa, target));
                parentGroups.put(santa.getParentOverride(), currentPeopleUnderParent);
            } else {
                parentGroups.put(santa.getParentOverride(), Lists.newArrayList(Collections.singletonList(new Assignment(santa, target))));
            }
        });
        return parentGroups;
    }

    public static Person findPersonByName(Map<Person, Person> assignments, String currentParentName) {
        List<Person> personByName = assignments.keySet().stream().filter(person ->
                person.getName().equals(currentParentName)).collect(Collectors.toList());

        if (personByName.size() != 1) {
            throw new IllegalStateException(
                    "Encountered " + personByName.size() + " people with the name " + currentParentName + " instead of 1");
        }

        return personByName.get(0);
    }
}
