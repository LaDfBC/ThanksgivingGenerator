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
import jaerapps.functions.generateNames.util.Person;
import jaerapps.functions.generateNames.util.GoogleSheetsService;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.logging.Logger;

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
        Properties properties = System.getProperties();
        // Setup mail server
        properties.put("mail.smtp.host", "smtp.gmail.com");
        properties.put("mail.smtp.port", "465");
        properties.put("mail.smtp.ssl.enable", "true");
        properties.put("mail.smtp.auth", "true");

        // Get the Session object.// and pass username and password
        Session session = Session.getInstance(properties, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("fromaddress@gmail.com", "*******");
            }
        });

        Multimap<String, Person> parentGroups = combineChildrenUnderParent(assignments);
        parentGroups.keys().forEach(currentEmail -> {
            MimeMessage message = new MimeMessage(session);
            try {
                message.setFrom(new InternetAddress("christmasemailer_noreply@gmail.com"));
                message.setSubject("Larkin Secret Santa!");

                parentGroups.get(currentEmail).forEach(person -> {
                    try {
                        message.addRecipient(Message.RecipientType.TO, new InternetAddress());
                        message.setText("Hi - you're buying for ");

                        Transport.send(message);
                        System.out.println("Sent message successfully....");
                    } catch (Exception e) {
                        LOGGER.severe("Failed to send email to " + currentEmail);
                    }
                });
            } catch (Exception e) {
                LOGGER.severe("Failed to send email to " + currentEmail);
            }
        });

    }

    private Multimap<String, Person> combineChildrenUnderParent(Map<Person, Person> assignments) {
        Multimap<String, Person> parentGroups = ArrayListMultimap.create();
        assignments.forEach((santa, assignment) -> parentGroups.put(santa.getEmail(), assignment));
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
        for(String group : notYetAssignedPeopleGrouped.keys()) {
            Collection<Person> peopleInGroup = notYetAssignedPeopleGrouped.get(group);
            if (!peopleInGroup.contains(currentSanta)) {
                membersNotInGroup.addAll(peopleInGroup);
            }
        }

        return membersNotInGroup.get(random.nextInt(membersNotInGroup.size()));
    }

    private List<Person> getLargestEligibleGroup(Person currentSanta, Multimap<String, Person> notYetAssignedPeopleGrouped) {
        Collection<Person> largestGroup = Lists.newArrayList();
        for(String group : notYetAssignedPeopleGrouped.keys()) {
            Collection<Person> peopleInGroup = notYetAssignedPeopleGrouped.get(group);
            if (!peopleInGroup.contains(currentSanta)) {
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