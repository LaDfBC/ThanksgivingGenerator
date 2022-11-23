package jaerapps.functions.generateNames;

import com.google.api.client.util.Lists;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jaerapps.functions.generateNames.util.*;

import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class GenerateGiftNames implements HttpFunction {
    public static final String PROJECT_ID = "thanksgivinggenerator";
    private static final Logger LOGGER = Logger.getLogger("Generate Gift Names");
    private static final Gson gson = new Gson();
    private static final Random random = new Random();

    private static final Map<String, String> forcedSantas = Maps.newHashMap();
//            ImmutableMap.of("Gary Dugger", "Dave Hillyard", "Vickie Schoening", "Addison Hillyard");

    // Simple function to return "Hello World"
    @Override
    public void service(HttpRequest request, HttpResponse response) throws IOException {
        String spreadsheetId = gson.fromJson(request.getReader(), JsonObject.class).get("spreadsheetId").getAsString();
        try {
            List<String> generations = GoogleSheetsService.getGenerations(spreadsheetId);
            List<Person> peopleFromSheet = GoogleSheetsService.getPeopleFromSheet(spreadsheetId);
            Map<String, Map<Person, Person>> generationToAssignments = Maps.newHashMap();

            for (String generation : generations) {
                Map<Person, Person> assignments = assignRecipients(peopleFromSheet, generationToAssignments);
                generationToAssignments.put(generation, assignments);
            }

            try {
                FirestoreService assignmentDatabase = new FirestoreService();
                boolean databaseInsertSuccessful = assignmentDatabase.insertAssignments(generationToAssignments);
                if (databaseInsertSuccessful) {
                    sendEmails(generationToAssignments);
                }
            } catch (Exception e) {
                response.setStatusCode(500, "Caught error while writing to database and email: " + e.getMessage());
                e.printStackTrace();
            }

            response.setStatusCode(200);
        } catch (GeneralSecurityException gse) {
            String message = "Failed to connect and fetch from Sheet!";
            LOGGER.severe(message);
            response.setStatusCode(500, message);
        }
    }

    private void sendEmails(Map<String, Map<Person, Person>> generationToAssignments) {
        String userName = "christmasemailer.noreply@gmail.com";
        String password = GoogleSecretManagerService.getEmailPassword();
        Properties properties = System.getProperties();
        // Setup mail server
        properties.setProperty("mail.smtp.host", "smtp.gmail.com");
        properties.setProperty("mail.smtp.user", userName);
        properties.setProperty("mail.smtp.password", password);
        properties.setProperty("mail.smtp.port", "465");
        properties.setProperty("mail.smtp.ssl.enable", "true");
        properties.setProperty("mail.smtp.auth", "true");

        // Get the Session object.// and pass username and password
        Session session = Session.getInstance(properties, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(userName, password);
            }
        });

        generationToAssignments.forEach((generationName, assignments) -> {
            Map<String, List<Assignment>> parentGroups = AssignmentUtil.combineChildrenUnderParent(assignments);
            parentGroups.forEach((currentParentName, children) -> {
                Person parent = AssignmentUtil.findPersonByName(assignments, currentParentName);
                MimeMessage message = new MimeMessage(session);
                try {
                    message.setFrom(new InternetAddress("christmasemailer.noreply@gmail.com"));
                    message.setSubject(generationName + " Secret Santa");
                    message.addRecipient(Message.RecipientType.TO, new InternetAddress(parent.getEmail()));

                    StringBuilder text = new StringBuilder("Hi - Here's your secret santa list (Santa on the left, recipient on the right!): \n");
                    children.forEach(currentChild -> {
                        try {
                            text.append(currentChild.getSanta().getName())
                                    .append(" -> ")
                                    .append(currentChild.getAssignment().getName())
                                    .append("\n");
                            System.out.println("Sent message successfully....");
                        } catch (Exception e) {
                            LOGGER.severe("Failed to send email to " + parent.getEmail());
                        }
                    });

                    message.setText(text.toString());
                    Transport.send(message);
                } catch (Exception e) {
                    e.printStackTrace();
                    LOGGER.severe("Failed to send email to " + parent.getEmail());
                }
            });
        });
    }


    private Map<Person, Person> assignRecipients(List<Person> people, Map<String, Map<Person, Person>> generationToAssignments) {
        Map<Person, Person> finishedAssignments = Maps.newHashMap();

        boolean done = false;
        while(!done) {
            try {
                Map<Person, Person> assignments = Maps.newHashMap();
                Multimap<String, Person> notYetAssignedPeopleGrouped = formGroups(people);

                // Maps each person to every person they are already buying for
                Multimap<Person, Person> alreadyMatched = ArrayListMultimap.create();
                generationToAssignments.values().forEach(personPersonMap -> personPersonMap.forEach(alreadyMatched::put));

                List<Person> nonForcedPeople = Lists.newArrayList(people);
                /**
                 * Handles specific requests people have for other individuals for whom they want to play Santa
                 */
                forcedSantas.forEach((santaName, recipientName) -> {
                    final Person recipient = AssignmentUtil.findPersonByName(people, recipientName);
                    final Person santa = AssignmentUtil.findPersonByName(people, santaName);
                    assignments.put(santa, recipient);
                    notYetAssignedPeopleGrouped.remove(recipient.getGroup(), recipient);
                    nonForcedPeople.remove(santa);
                });

                nonForcedPeople.forEach(currentSanta -> {
                    List<Person> largestGroup = getLargestEligibleGroup(currentSanta, notYetAssignedPeopleGrouped);
                    Person selectedAssignment;
                    // Checks if the largest remaining group is equal to all other groups
                    if ((notYetAssignedPeopleGrouped.size() - largestGroup.size()) == largestGroup.size()) {
                        Collection<Person> previouslyMatched = alreadyMatched.get(currentSanta);
                        largestGroup = largestGroup
                                .stream()
                                .filter(person -> !previouslyMatched.contains(person))
                                .collect(Collectors.toList());

                        selectedAssignment = largestGroup.get(random.nextInt(largestGroup.size()));
                    } else {
                        selectedAssignment = selectRandomPersonNotInGroup(currentSanta, notYetAssignedPeopleGrouped, alreadyMatched);
                    }

                    assignments.put(currentSanta, selectedAssignment);
                    notYetAssignedPeopleGrouped.remove(selectedAssignment.getGroup(), selectedAssignment);
                });

                done = true;
                finishedAssignments = assignments;
            } catch (IllegalArgumentException iae) {
                LOGGER.warning("Randomized into an impossible scenario...trying again");
            }
        }


        return finishedAssignments;
    }

    private Person selectRandomPersonNotInGroup(Person currentSanta,
                                                Multimap<String, Person> notYetAssignedPeopleGrouped,
                                                Multimap<Person, Person> alreadyMatched) {
        List<Person> membersNotInGroup = Lists.newArrayList();
        Collection<Person> previouslyMatched = alreadyMatched.get(currentSanta);
        for(String group : notYetAssignedPeopleGrouped.keySet()) {
            List<Person> peopleInGroup = Lists.newArrayList(notYetAssignedPeopleGrouped.get(group));
            if (notYetAssignedPeopleGrouped.size() != 0 && !currentSanta.getGroup().equals(peopleInGroup.get(0).getGroup())) {
                membersNotInGroup.addAll(peopleInGroup);
            }
        }

        membersNotInGroup = membersNotInGroup
                .stream()
                .filter(person -> !previouslyMatched.contains(person))
                .collect(Collectors.toList());

        return membersNotInGroup.get(random.nextInt(membersNotInGroup.size()));
    }

    private List<Person> getLargestEligibleGroup(Person currentSanta, Multimap<String, Person> notYetAssignedPeopleGrouped) {
        Collection<Person> largestGroup = Lists.newArrayList();
        for(String group : notYetAssignedPeopleGrouped.keySet()) {
            List<Person> peopleInGroup = Lists.newArrayList(notYetAssignedPeopleGrouped.get(group));
                if (notYetAssignedPeopleGrouped.size() != 0 && !currentSanta.getGroup().equals(peopleInGroup.get(0).getGroup())) {
                if (largestGroup.size() < peopleInGroup.size()) {
                    largestGroup = peopleInGroup;
                }
            }
        }

        return Lists.newArrayList(largestGroup);
    }

    private Multimap<String, Person> formGroups(List<Person> people) {
        Multimap<String, Person> groups = ArrayListMultimap.create();

        people.forEach(person -> {
            groups.put(person.getGroup(), person);
        });

        return groups;
    }
}