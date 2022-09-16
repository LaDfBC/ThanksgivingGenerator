package jaerapps.functions.generateNames.util;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Lists;
import com.google.api.client.util.Strings;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;

public class GoogleSheetsService {
    private static final String APPLICATION_NAME = "Thanksgiving Name Builder";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String CREDENTIALS_FILE_PATH = "/home/jaera/configs/oauth_client.json";
    private static final LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();

    public static List<String> getGenerations (String spreadsheetId) throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        final String range = "Master List!G2:G";
        Sheets service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

        return response.getValues()
                .stream()
                .map(objects -> (String) objects.get(0))
                .collect(Collectors.toList());
    }

    public static List<Person> getPeopleFromSheet (String spreadsheetId) throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        final String range = "Master List!A2:D";
        Sheets service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

        return response.getValues()
                .stream()
                .map(objects ->
                        Person.create()
                                .setName((String) objects.get(0))
                                .setEmail((String) objects.get(1))
                                .setParentOverride(getParentOverride(objects))
                                .setGroup(
                                        objects.size() > 3 ?
                                            (String) objects.get(3) :
                                            (String) objects.get(0))
                                .build()
                )
                .collect(Collectors.toList());
    }

    private static String getParentOverride(List<Object> objects) {
        if (objects.size() > 2) {
            if (Strings.isNullOrEmpty((String) objects.get(2))) {
                return (String) objects.get(0);
            }
            return (String) objects.get(2);
        } else {
            return (String) objects.get(0);
        }
    }

    public static void logAssignments(Map<Person, Person> assignments) throws GeneralSecurityException, IOException {
        String spreadsheetId = "1tkrhLwUwyJpMjxRm81CzYcPxrCPvmcl3Plfh_y4rvoY";

        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Sheets service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

        List<ValueRange> requests = Lists.newArrayList();
        int currentRow = 2;

        for (Map.Entry<Person, Person> currentPair:  assignments.entrySet()) {
            Person santa = currentPair.getKey();
            Person assignment = currentPair.getValue();

            requests.add(
                    new ValueRange()
                            .setRange("Latest Run!A" + currentRow + ":B" + currentRow )
                            .setValues(Collections.singletonList(Arrays.asList(santa.getName(), assignment.getName()))));
            currentRow = currentRow + 1;
        }

        BatchUpdateValuesRequest batchBody = new BatchUpdateValuesRequest()
                .setValueInputOption("RAW")
                .setData(requests);

        service.spreadsheets().values().batchUpdate(spreadsheetId, batchBody).execute();
    }

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = new FileInputStream(CREDENTIALS_FILE_PATH);
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }
}
