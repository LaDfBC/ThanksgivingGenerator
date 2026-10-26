package jaerapps.functions.generateNames.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import java.util.*;

/**
 * Picks a recipient for every santa, once per exchange. Within an exchange everyone gives exactly once and
 * receives exactly once, nobody gets themselves or someone in their own group, and nobody gets a person they
 * already drew in an earlier exchange of the same run. Forced pairs are placed first and skip those rules.
 *
 * Each exchange is a randomized backtracking search, so unlike guess-and-retry it notices when the rules
 * can't be met instead of spinning forever.
 */
public class AssignmentGenerator {
    /** Search steps per exchange before giving up on this attempt. */
    private static final int STEP_BUDGET = 200_000;
    /** Earlier exchanges' draws shape later ones, so a dead end there is worth a fresh run. */
    private static final int RUN_ATTEMPTS = 25;

    public static class ImpossibleAssignmentException extends Exception {
        public ImpossibleAssignmentException(String message) {
            super(message);
        }
    }

    private final Random random;

    public AssignmentGenerator(Random random) {
        this.random = random;
    }

    /**
     * @param forced exchange name -> santa -> recipient, all names of people in {@code people}
     * @return exchange name (in the given order) -> santa -> recipient
     */
    public Map<String, Map<Person, Person>> assign(List<Person> people,
                                                   List<String> exchanges,
                                                   Map<String, Map<Person, Person>> forced) throws ImpossibleAssignmentException {
        for (int attempt = 0; attempt < RUN_ATTEMPTS; attempt++) {
            Map<String, Map<Person, Person>> result = new LinkedHashMap<>();
            SetMultimap<Person, Person> alreadyDrawn = HashMultimap.create();
            boolean ok = true;

            for (String exchange : exchanges) {
                Search search = new Search(people, alreadyDrawn, forced.getOrDefault(exchange, Map.of()));
                Map<Person, Person> assignments = search.run();
                if (assignments == null) {
                    // The first exchange has no history to blame: an exhausted search there is a real "impossible".
                    if (result.isEmpty() && !search.ranOutOfSteps) {
                        throw new ImpossibleAssignmentException(
                                "No assignment fits the groups" + (forced.isEmpty() ? "" : " and forced pairs")
                                        + " for \"" + exchange + "\".");
                    }
                    ok = false;
                    break;
                }
                assignments.forEach(alreadyDrawn::put);
                result.put(exchange, assignments);
            }

            if (ok) {
                return result;
            }
        }

        throw new ImpossibleAssignmentException(
                "Couldn't find assignments for every exchange without someone drawing the same person twice. "
                        + "Try fewer exchanges or smaller groups.");
    }

    private class Search {
        private final List<Person> santas = new ArrayList<>();
        private final Set<Person> unclaimed = new LinkedHashSet<>();
        private final SetMultimap<Person, Person> alreadyDrawn;
        private final Map<Person, Person> assignments = new LinkedHashMap<>();
        private int steps = 0;
        boolean ranOutOfSteps = false;

        Search(List<Person> people, SetMultimap<Person, Person> alreadyDrawn, Map<Person, Person> forced) {
            this.alreadyDrawn = alreadyDrawn;
            assignments.putAll(forced);
            for (Person person : people) {
                if (!forced.containsKey(person)) {
                    santas.add(person);
                }
                if (!forced.containsValue(person)) {
                    unclaimed.add(person);
                }
            }
        }

        Map<Person, Person> run() {
            return solve() ? assignments : null;
        }

        private boolean eligible(Person santa, Person recipient) {
            return !santa.equals(recipient)
                    && !santa.getGroupKey().equals(recipient.getGroupKey())
                    && !alreadyDrawn.containsEntry(santa, recipient);
        }

        private boolean solve() {
            if (++steps > STEP_BUDGET) {
                ranOutOfSteps = true;
                return false;
            }

            // Fill the most constrained santa next; that's what keeps the search small.
            Person santa = null;
            List<Person> options = null;
            for (Person candidate : santas) {
                if (assignments.containsKey(candidate)) {
                    continue;
                }
                List<Person> candidateOptions = new ArrayList<>();
                for (Person recipient : unclaimed) {
                    if (eligible(candidate, recipient)) {
                        candidateOptions.add(recipient);
                    }
                }
                if (options == null || candidateOptions.size() < options.size()) {
                    santa = candidate;
                    options = candidateOptions;
                }
            }

            if (santa == null) {
                return true;
            }

            Collections.shuffle(options, random);
            for (Person recipient : options) {
                assignments.put(santa, recipient);
                unclaimed.remove(recipient);
                if (solve()) {
                    return true;
                }
                if (ranOutOfSteps) {
                    return false;
                }
                assignments.remove(santa);
                unclaimed.add(recipient);
            }
            return false;
        }
    }
}
