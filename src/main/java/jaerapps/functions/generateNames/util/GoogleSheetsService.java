package jaerapps.functions.generateNames.util;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GoogleSheetsService {
    private static final String APPLICATION_NAME = "Thanksgiving Name Builder";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    public static List<Person> getPeopleFromSheet (String spreadsheetId) throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        final String range = "Master List!A1:D";
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
                                .setParentOverride((String) objects.get(2))
                                .setGroup(
                                        Strings.isNullOrEmpty((String) objects.get(3)) ?
                                                (String) objects.get(0) :
                                                (String) objects.get(3))
                                .build()
                )
                .collect(Collectors.toList());
    }



    public static void logAssignments(Map<Person, Person> assignments) throws GeneralSecurityException, IOException {
        String spreadsheetId = "1tkrhLwUwyJpMjxRm81CzYcPxrCPvmcl3Plfh_y4rvoY";

        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Sheets service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

        List<ValueRange> requests = Lists.newArrayList();
        int currentRow = 1;

        for (Map.Entry<Person, Person> currentPair:  assignments.entrySet()) {
            Person santa = currentPair.getKey();
            Person assignment = currentPair.getValue();

            requests.add(
                    new ValueRange()
                            .setRange("Latest Run!" + getLetterFromInteger(currentRow) + "1:" + getLetterFromInteger(currentRow) + "2" )
                            .setValues(Collections.singletonList(Arrays.asList(santa.getName(), assignment.getName()))));
            currentRow = currentRow + 1;
        }

        BatchUpdateValuesRequest batchBody = new BatchUpdateValuesRequest()
                .setValueInputOption("RAW")
                .setData(requests);

        service.spreadsheets().values().batchUpdate(spreadsheetId, batchBody);
    }

    private static String getLetterFromInteger(int currentRow) {
        String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String alphaString = "";
        int adjustingCol = currentRow;

        while (adjustingCol > 26) {
            // Greatest digit
            int modIterations = 0;
            while (Math.floor(adjustingCol / Math.pow(26, (modIterations))) > 0) {
                modIterations += 1;
            }
            modIterations -= 1;

            // Current index of the letter to find
            int multiplications = 0;
            while (Math.floor(adjustingCol / (Math.pow(26, (modIterations)) * (multiplications + 1))) > 0) {
                multiplications += 1;
            }
            multiplications -= 1;

            alphaString = alphaString.concat(Character.toString(ALPHABET.charAt(multiplications)));
            adjustingCol = Double.valueOf(adjustingCol % (Math.pow(26, modIterations))).intValue();
        }

        alphaString = alphaString.concat(Character.toString(ALPHABET.charAt(adjustingCol)));
        return alphaString;
    }

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = GoogleSheetsService.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }
}
