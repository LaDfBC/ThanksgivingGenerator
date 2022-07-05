package jaerapps.functions.generateNames;

import com.google.api.client.util.Lists;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jaerapps.functions.generateNames.util.Assignment;
import jaerapps.functions.generateNames.util.GoogleSecretManagerService;
import jaerapps.functions.generateNames.util.GoogleSheetsService;
import jaerapps.functions.generateNames.util.Person;

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

import static jaerapps.functions.generateNames.util.GoogleSheetsService.logAssignments;


public class GenerateGiftNames implements HttpFunction {
    private static final Logger LOGGER = Logger.getLogger("Generate Gift Names");
    private static final Gson gson = new Gson();
    private static final Random random = new Random();

    // Simple function to return "Hello World"
    @Override
    public void service(HttpRequest request, HttpResponse response) throws IOException {
        String spreadsheetId = gson.fromJson(request.getReader(), JsonObject.class).get("spreadsheetId").getAsString();
        try {
            List<Person> peopleFromSheet = GoogleSheetsService.getPeopleFromSheet(spreadsheetId);
            Map<Person, Person> assignments = assignRecipients(peopleFromSheet);

            logAssignments(assignments);
            sendEmails(assignments);
        } catch (GeneralSecurityException gse) {
            String message = "Failed to connect and fetch from Sheet!";
            LOGGER.severe(message);
            response.setStatusCode(500, message);
        }
    }

    private void sendEmails(Map<Person, Person> assignments) {
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

        Map<String, List<Assignment>> parentGroups = combineChildrenUnderParent(assignments);
        parentGroups.forEach((currentParentName, children) -> {
            Person parent = findPersonByName(assignments, currentParentName);
            MimeMessage message = new MimeMessage(session);
            try {
                message.setFrom(new InternetAddress("christmasemailer.noreply@gmail.com"));
                message.setSubject("Larkin Secret Santa!");
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
    }

    private Person findPersonByName(Map<Person, Person> assignments, String currentParentName) {
            List<Person> personByName = assignments.keySet().stream().filter(person ->
                person.getName().equals(currentParentName)).collect(Collectors.toList());

        if (personByName.size() != 1) {
            throw new IllegalStateException(
                    "Encountered " + personByName.size() + " people with the name " + currentParentName + " instead of 1");
        }

        return personByName.get(0);
    }


    private Map<String, List<Assignment>> combineChildrenUnderParent(Map<Person, Person> assignments) {
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

    private Map<Person, Person> assignRecipients(List<Person> people) {
        Map<Person, Person> assignments = Maps.newHashMap();
        Multimap<String, Person> notYetAssignedPeopleGrouped = formGroups(people);

        people.forEach(currentSanta -> {
            List<Person> largestGroup = getLargestEligibleGroup(currentSanta, notYetAssignedPeopleGrouped);
            Person selectedAssignment;
            // Checks if the largest remaining group is equal to all other groups
            if((notYetAssignedPeopleGrouped.size() - largestGroup.size()) == largestGroup.size()) {
                selectedAssignment = largestGroup.get(random.nextInt(largestGroup.size()));
            } else {
                selectedAssignment = selectRandomPersonNotInGroup(currentSanta, notYetAssignedPeopleGrouped);
            }

            assignments.put(currentSanta, selectedAssignment);
            notYetAssignedPeopleGrouped.remove(selectedAssignment.getGroup(), selectedAssignment);
        });


        return assignments;
    }

    private Person selectRandomPersonNotInGroup(Person currentSanta, Multimap<String, Person> notYetAssignedPeopleGrouped) {
        List<Person> membersNotInGroup = Lists.newArrayList();
        for(String group : notYetAssignedPeopleGrouped.keySet()) {
            List<Person> peopleInGroup = Lists.newArrayList(notYetAssignedPeopleGrouped.get(group));
            if (notYetAssignedPeopleGrouped.size() != 0 && !currentSanta.getGroup().equals(peopleInGroup.get(0).getGroup())) {
                membersNotInGroup.addAll(peopleInGroup);
            }
        }

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