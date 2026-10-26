package jaerapps.functions.generateNames.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Where runs are kept: {@link DynamoStore} when deployed, {@link InMemory} for local dry runs. */
public interface RunStore {
    /** @return the new run's id */
    String insert(SantaRun run) throws Exception;

    void recordEmailResult(String id, long emailed, List<String> failed) throws Exception;

    /** @return null if there's no such run */
    SantaRun get(String id) throws Exception;

    void delete(String id) throws Exception;

    /** Newest first. */
    List<SantaRun> ownedBy(String email) throws Exception;

    /** Newest first. */
    List<SantaRun> withParticipant(String email) throws Exception;

    Comparator<SantaRun> NEWEST_FIRST = Comparator.comparing(SantaRun::createdAt).reversed();

    class InMemory implements RunStore {
        private final Map<String, SantaRun> runs = new ConcurrentHashMap<>();

        @Override
        public String insert(SantaRun run) {
            String id = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            runs.put(id, run.withId(id));
            return id;
        }

        @Override
        public void recordEmailResult(String id, long emailed, List<String> failed) {
            runs.computeIfPresent(id, (k, run) -> run.withEmailResult(emailed, failed));
        }

        @Override
        public SantaRun get(String id) {
            return runs.get(id);
        }

        @Override
        public void delete(String id) {
            runs.remove(id);
        }

        @Override
        public List<SantaRun> ownedBy(String email) {
            return runs.values().stream().filter(run -> run.ownerEmail().equals(email)).sorted(NEWEST_FIRST).toList();
        }

        @Override
        public List<SantaRun> withParticipant(String email) {
            return runs.values().stream().filter(run -> run.participantEmails().contains(email)).sorted(NEWEST_FIRST).toList();
        }
    }
}
