package jaerapps.functions.generateNames.util;

import com.google.gson.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;

/**
 * Runs live in one DynamoDB table (aws/lib/app-stack.js), keyed on `pk` (the run id) and `sk`:
 *
 *   sk "RUN"             the run itself, as JSON in `data`, plus `ownerEmail` and `createdAt` for the byOwner index
 *   sk "MEMBER#<email>"  one per participant email (lower-cased), with `member` and `createdAt` for the byMember index
 *
 * so "the runs I organized" and "the runs I'm in" are each one index query. Pairs are stored as lists of
 * {santa, recipient}, like the spoiler log.
 */
public class DynamoStore implements RunStore {
    private static final String RUN = "RUN";
    private static final String MEMBER = "MEMBER#";

    private final String table;
    private final DynamoDbClient db;

    public DynamoStore(String table) {
        this.table = table;
        this.db = DynamoDbClient.builder().httpClient(UrlConnectionHttpClient.create()).build();
    }

    @Override
    public String insert(SantaRun run) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        SantaRun saved = run.withId(id);
        String createdAt = saved.createdAt().toInstant().toString();

        // The run goes in first: participant items without their run would be harmless, the reverse wouldn't.
        db.putItem(PutItemRequest.builder().tableName(table).item(Map.of(
                "pk", s(id), "sk", s(RUN),
                "ownerEmail", s(saved.ownerEmail()), "createdAt", s(createdAt), "data", s(toJson(saved)))).build());
        batchWrite(saved.participantEmails().stream()
                .map(email -> WriteRequest.builder().putRequest(PutRequest.builder().item(Map.of(
                        "pk", s(id), "sk", s(MEMBER + email), "member", s(email), "createdAt", s(createdAt))).build()).build())
                .toList());
        return id;
    }

    @Override
    public void recordEmailResult(String id, long emailed, List<String> failed) {
        SantaRun run = get(id);
        if (run == null) {
            return;
        }
        db.updateItem(UpdateItemRequest.builder().tableName(table)
                .key(Map.of("pk", s(id), "sk", s(RUN)))
                .updateExpression("SET #data = :data")
                .expressionAttributeNames(Map.of("#data", "data"))
                .expressionAttributeValues(Map.of(":data", s(toJson(run.withEmailResult(emailed, failed)))))
                .build());
    }

    @Override
    public SantaRun get(String id) {
        Map<String, AttributeValue> item = db.getItem(GetItemRequest.builder().tableName(table)
                .key(Map.of("pk", s(id), "sk", s(RUN))).build()).item();
        return item == null || item.isEmpty() ? null : fromJson(item.get("data").s());
    }

    @Override
    public void delete(String id) {
        List<WriteRequest> deletes = new ArrayList<>();
        Map<String, AttributeValue> start = null;
        do {
            QueryResponse page = db.query(QueryRequest.builder().tableName(table)
                    .keyConditionExpression("pk = :pk")
                    .expressionAttributeValues(Map.of(":pk", s(id)))
                    .projectionExpression("pk, sk")
                    .exclusiveStartKey(start)
                    .build());
            page.items().forEach(item -> deletes.add(WriteRequest.builder()
                    .deleteRequest(DeleteRequest.builder().key(item).build()).build()));
            start = page.hasLastEvaluatedKey() ? page.lastEvaluatedKey() : null;
        } while (start != null);
        batchWrite(deletes);
    }

    @Override
    public List<SantaRun> ownedBy(String email) {
        List<SantaRun> runs = new ArrayList<>();
        Map<String, AttributeValue> start = null;
        do {
            QueryResponse page = db.query(QueryRequest.builder().tableName(table).indexName("byOwner")
                    .keyConditionExpression("ownerEmail = :email")
                    .expressionAttributeValues(Map.of(":email", s(email)))
                    .scanIndexForward(false)
                    .exclusiveStartKey(start)
                    .build());
            page.items().forEach(item -> runs.add(fromJson(item.get("data").s())));
            start = page.hasLastEvaluatedKey() ? page.lastEvaluatedKey() : null;
        } while (start != null);
        return runs;
    }

    @Override
    public List<SantaRun> withParticipant(String email) {
        List<String> ids = new ArrayList<>();
        Map<String, AttributeValue> start = null;
        do {
            QueryResponse page = db.query(QueryRequest.builder().tableName(table).indexName("byMember")
                    .keyConditionExpression("#member = :email")
                    .expressionAttributeNames(Map.of("#member", "member"))
                    .expressionAttributeValues(Map.of(":email", s(email)))
                    .exclusiveStartKey(start)
                    .build());
            page.items().forEach(item -> ids.add(item.get("pk").s()));
            start = page.hasLastEvaluatedKey() ? page.lastEvaluatedKey() : null;
        } while (start != null);

        List<SantaRun> runs = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 100) {
            List<Map<String, AttributeValue>> keys = ids.subList(i, Math.min(i + 100, ids.size())).stream()
                    .map(id -> Map.of("pk", s(id), "sk", s(RUN))).toList();
            Map<String, KeysAndAttributes> request = Map.of(table, KeysAndAttributes.builder().keys(keys).build());
            while (!request.isEmpty()) {
                BatchGetItemResponse response = db.batchGetItem(BatchGetItemRequest.builder().requestItems(request).build());
                response.responses().getOrDefault(table, List.of()).forEach(item -> runs.add(fromJson(item.get("data").s())));
                request = response.unprocessedKeys();
            }
        }
        runs.sort(NEWEST_FIRST);
        return runs;
    }

    private void batchWrite(List<WriteRequest> writes) {
        for (int i = 0; i < writes.size(); i += 25) {
            Map<String, List<WriteRequest>> request = Map.of(table, writes.subList(i, Math.min(i + 25, writes.size())));
            while (!request.isEmpty()) {
                request = db.batchWriteItem(BatchWriteItemRequest.builder().requestItems(request).build()).unprocessedItems();
            }
        }
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    // ---- JSON ----

    static String toJson(SantaRun run) {
        JsonObject json = new JsonObject();
        json.addProperty("id", run.id());
        json.addProperty("title", run.title());
        json.addProperty("ownerEmail", run.ownerEmail());
        json.addProperty("createdAt", run.createdAt().toInstant().toString());
        JsonArray people = new JsonArray();
        run.people().forEach(person -> {
            JsonObject p = new JsonObject();
            p.addProperty("name", person.getName());
            p.addProperty("email", person.getEmail());
            p.addProperty("group", person.getGroup());
            people.add(p);
        });
        json.add("people", people);
        JsonArray exchanges = new JsonArray();
        run.exchanges().forEach(exchange -> {
            JsonArray pairs = new JsonArray();
            exchange.pairs().forEach((santa, recipient) -> {
                JsonObject pair = new JsonObject();
                pair.addProperty("santa", santa);
                pair.addProperty("recipient", recipient);
                pairs.add(pair);
            });
            JsonObject e = new JsonObject();
            e.addProperty("name", exchange.name());
            e.add("pairs", pairs);
            exchanges.add(e);
        });
        json.add("exchanges", exchanges);
        json.addProperty("emailed", run.emailed());
        JsonArray failed = new JsonArray();
        run.failed().forEach(failed::add);
        json.add("failed", failed);
        return json.toString();
    }

    static SantaRun fromJson(String data) {
        JsonObject json = JsonParser.parseString(data).getAsJsonObject();
        List<Person> people = new ArrayList<>();
        json.getAsJsonArray("people").forEach(element -> {
            JsonObject p = element.getAsJsonObject();
            people.add(Person.create()
                    .setName(p.get("name").getAsString())
                    .setEmail(p.get("email").getAsString())
                    .setGroup(p.get("group").getAsString())
                    .build());
        });
        List<SantaRun.Exchange> exchanges = new ArrayList<>();
        json.getAsJsonArray("exchanges").forEach(element -> {
            JsonObject e = element.getAsJsonObject();
            Map<String, String> pairs = new LinkedHashMap<>();
            e.getAsJsonArray("pairs").forEach(pair -> pairs.put(
                    pair.getAsJsonObject().get("santa").getAsString(),
                    pair.getAsJsonObject().get("recipient").getAsString()));
            exchanges.add(new SantaRun.Exchange(e.get("name").getAsString(), pairs));
        });
        List<String> failed = new ArrayList<>();
        json.getAsJsonArray("failed").forEach(element -> failed.add(element.getAsString()));
        JsonElement emailed = json.get("emailed");
        return new SantaRun(json.get("id").getAsString(), json.get("title").getAsString(),
                json.get("ownerEmail").getAsString(), Date.from(Instant.parse(json.get("createdAt").getAsString())),
                people, exchanges, emailed == null || emailed.isJsonNull() ? null : emailed.getAsLong(), failed);
    }
}
