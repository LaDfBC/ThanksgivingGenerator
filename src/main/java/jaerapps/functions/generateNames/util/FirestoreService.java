package jaerapps.functions.generateNames.util;

import com.google.api.core.ApiFuture;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.firestore.v1.FirestoreClient;
import com.google.cloud.firestore.v1.FirestoreSettings;
import com.google.common.collect.Maps;
import com.google.firestore.v1.Document;
import com.google.firestore.v1.UpdateDocumentRequest;
import com.google.firestore.v1.UpdateDocumentRequestOrBuilder;
import com.google.protobuf.Descriptors;
import jaerapps.functions.generateNames.GenerateGiftNames;
import org.checkerframework.checker.signature.qual.FieldDescriptor;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FirestoreService {
    private static final Logger LOGGER = Logger.getLogger(FirestoreService.class.getName());

    private final Firestore db;

    public FirestoreService() throws Exception {
        FirestoreOptions firestoreOptions =
                FirestoreOptions.getDefaultInstance().toBuilder()
                        .setProjectId(GenerateGiftNames.PROJECT_ID)
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .build();
        this.db = firestoreOptions.getService();
    }

    public boolean insertAssignments(Map<String, Map<Person, Person>> generationToAssignments) {
        try {
            Map<String, Object> overallDocumentMap = Maps.newHashMap();
            generationToAssignments.forEach((generationName, santaToRecepientMap) -> {
                Map<String, List<Assignment>> parentToChildrenAssignments = AssignmentUtil.combineChildrenUnderParent(santaToRecepientMap);

                DocumentReference docRef = db.collection("santaGenerations").document(generationName);
                Map<String, Object> allAssignmentsDoc = Maps.newHashMap();

                parentToChildrenAssignments.forEach((parent, familyAssignments) -> {
                    Person fullParent = AssignmentUtil.findPersonByName(santaToRecepientMap, parent);
                    Map<String, String> childSantaToRecipient = Maps.newHashMap();
                    familyAssignments.forEach(assignment ->
                            childSantaToRecipient.put(assignment.getSanta().getName(), assignment.getAssignment().getName()));

                    Map<String, Object> parentDoc = Maps.newHashMap();
                    parentDoc.put("email", fullParent.getEmail());
                    parentDoc.put("group", fullParent.getGroup());
                    parentDoc.put("assignments", childSantaToRecipient);

                    allAssignmentsDoc.put(fullParent.getName(), parentDoc);
                });

                overallDocumentMap.put("assignments", allAssignmentsDoc);
                overallDocumentMap.put("creationDate", new Date());

                ApiFuture<WriteResult> result = docRef.set(overallDocumentMap);

                try {
                    result.get();
                } catch (InterruptedException | ExecutionException ie) {
                    throw new RuntimeException(ie);
                }
            });
        } catch (RuntimeException re) {
            LOGGER.log(Level.SEVERE, "Failed to write to Firestore " + re.getMessage());
            re.printStackTrace();
            return false;
        }

        return true;
    }
}
