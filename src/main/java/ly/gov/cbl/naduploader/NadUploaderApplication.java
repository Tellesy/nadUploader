package ly.gov.cbl.naduploader;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import com.opencsv.CSVWriter;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootApplication
public class NadUploaderApplication implements CommandLineRunner {

    @Value("${api.base-url.accounts}")
    private String accountsBaseUrl;

    @Value("${api.base-url.merchants}")
    private String merchantsBaseUrl;

    @Value("${api.token}")
    private String apiToken;

    @Value("${output.accounts-file}")
    private String accountsOutputFile;

    @Value("${output.merchants-file}")
    private String merchantsOutputFile;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Object fileLock = new Object();
    private int successCount = 0;
    private int failureCount = 0;

    public static void main(String[] args) {
        SpringApplication.run(NadUploaderApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Welcome to the NAD Uploader!");
        System.out.println("This tool will help you bulk-enroll your bank customers and merchants into the NAD system.");
        System.out.println("Please ensure that the base URLs and token are correctly set in the properties file.");
        System.out.println("Accounts Base URL: " + accountsBaseUrl);
        System.out.println("Merchants Base URL: " + merchantsBaseUrl);
        System.out.println("Please make sure that the file is in the correct location.");

        if (!confirm(scanner, "Do you want to continue? (yes/no): ")) return;

        int threadCount = getThreadCount(scanner);

        while (true) {
            System.out.println("Choose operation:");
            System.out.println("1. Enroll Accounts");
            System.out.println("2. Enroll Merchants");
            System.out.print("Enter your choice (1 or 2): ");

            int choice = scanner.nextInt();
            scanner.nextLine();

            if (choice == 1) {
                if (!processFile(scanner, "ACCOUNTS.xlsx", "Please place the accounts file at: " + new File(".").getAbsolutePath())) {
                    continue;
                }
                processAccounts(threadCount);
                break;
            } else if (choice == 2) {
                if (!processFile(scanner, "Merchants.xlsx", "Please place the merchants file at: " + new File(".").getAbsolutePath())) {
                    continue;
                }
                processMerchants(threadCount);
                break;
            } else {
                System.out.println("Invalid choice. Please try again.");
            }
        }

        System.out.println("Enrollment process completed.");
        System.out.println("Successful enrollments: " + successCount);
        System.out.println("Failed enrollments: " + failureCount);
    }

    private boolean confirm(Scanner scanner, String message) {
        while (true) {
            System.out.print(message);
            String response = scanner.nextLine().trim().toLowerCase();
            if (response.equals("yes")) {
                return true;
            } else if (response.equals("no")) {
                System.out.println("Exiting the tool. Goodbye!");
                return false;
            } else {
                System.out.println("Invalid input. Please enter 'yes' or 'no'.");
            }
        }
    }

    private int getThreadCount(Scanner scanner) {
        while (true) {
            System.out.print("How many simultaneous operations do you want to run? (1-10): ");
            try {
                int threadCount = Integer.parseInt(scanner.nextLine());
                if (threadCount >= 1 && threadCount <= 10) {
                    return threadCount;
                } else {
                    System.out.println("Invalid input. Please enter a number between 1 and 10.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number between 1 and 10.");
            }
        }
    }

    private boolean processFile(Scanner scanner, String fileName, String message) {
        while (true) {
            File file = new File(fileName);
            if (file.exists()) {
                return true;
            } else {
                System.out.println("Error: The file (" + fileName + ") is missing.");
                System.out.println(message);
                if (!confirm(scanner, "Do you want to try again? (yes/no): ")) {
                    return false;
                }
            }
        }
    }

    private void processAccounts(int threadCount) throws Exception {
        // Ensure the output file is created and write the header
        initializeOutputFile(accountsOutputFile, new String[]{"IBAN", "Alias/Response"});

        try (FileInputStream fis = new FileInputStream("ACCOUNTS.xlsx")) {
            Workbook workbook = new XSSFWorkbook(fis);
            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getLastRowNum();
            System.out.println("Number of customers in the file: " + rowCount);

            System.out.println("Starting account enrollment process...");
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

            for (Row row : sheet) {
                if (row.getRowNum() == 0 || isRowEmpty(row)) continue;
                executorService.submit(() -> enrollAccount(row));
            }

            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
    }

    private void processMerchants(int threadCount) throws Exception {
        // Ensure the output file is created and write the header
        initializeOutputFile(merchantsOutputFile, new String[]{"IBAN", "Alias/Response"});

        try (FileInputStream fis = new FileInputStream("Merchants.xlsx")) {
            Workbook workbook = new XSSFWorkbook(fis);
            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getLastRowNum();
            System.out.println("Number of merchants in the file: " + rowCount);

            System.out.println("Starting merchant enrollment process...");
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

            for (Row row : sheet) {
                if (row.getRowNum() == 0 || isRowEmpty(row)) continue;
                executorService.submit(() -> enrollMerchant(row));
            }

            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
    }

    private void initializeOutputFile(String fileName, String[] headers) {
        File file = new File(fileName);
        if (!file.exists()) {
            try (CSVWriter writer = new CSVWriter(new FileWriter(fileName))) {
                writer.writeNext(headers);
            } catch (IOException e) {
                System.out.println("Error initializing output file: " + e.getMessage());
            }
        }
    }


    private void enrollAccount(Row row) {
        int cellNumber = -1;
        try {
            String name = row.getCell(0).getStringCellValue();
            cellNumber=0;
            String iban = row.getCell(1).getStringCellValue();
            cellNumber=1;
            String nationalId = row.getCell(2).getStringCellValue();
            cellNumber=2;
            String passportNo = getCellValue(row.getCell(3));
            cellNumber=3;
            String phoneNumber = getCellValue(row.getCell(4));
            cellNumber=4;
            String accountNo = row.getCell(5).getStringCellValue();
            cellNumber=5;

            Map<String, Object> requestBody = Map.of(
                    "nationalId", nationalId,
                    "phoneNumber", phoneNumber,
                    "passportNumber", passportNo,
                    "account", Map.of(
                            "name", name,
                            "number", accountNo,
                            "iban", iban
                    )
            );

            processRequest(requestBody, accountsOutputFile, accountsBaseUrl);
        } catch (Exception e) {
            System.out.println("Error processing account row: " + row.getRowNum() + " Cell Number: " + cellNumber++ +" "+ e.getMessage() + " " );
            incrementFailure();
        }
    }

    private void enrollMerchant(Row row) {
        try {
            String merchantName = getNonEmptyValue(row.getCell(0)); // Required
            String iban = getNonEmptyValue(row.getCell(1)); // Required
            String nationalId = getNonEmptyValue(row.getCell(2)); // Required
            String passportNo = getOptionalValue(row.getCell(3)); // Optional
            String phoneNumber = getOptionalValue(row.getCell(4)); // Optional
            String accountNo = getNonEmptyValue(row.getCell(5)); // Required
            String mcc = getNonEmptyValue(row.getCell(6)); // Required
            String tradeLicense = getOptionalValue(row.getCell(7)); // Optional

            // Build the request body, allowing null for optional fields
            Map<String, Object> merchantData = new HashMap<>();
            merchantData.put("name", merchantName);
            merchantData.put("mcc", mcc);
            merchantData.put("address", "Libya"); // Default value for address
            if (tradeLicense != null && !tradeLicense.isEmpty()) {
                merchantData.put("tradeLicenseNumber", tradeLicense);
            }

            Map<String, Object> accountData = new HashMap<>();
            accountData.put("iban", iban);
            accountData.put("name", merchantName);
            accountData.put("number", accountNo);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("nationalId", nationalId);
            if (passportNo != null && !passportNo.isEmpty()) {
                requestBody.put("passportNumber", passportNo);
            }
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                requestBody.put("phoneNumber", phoneNumber);
            }
            requestBody.put("merchant", merchantData);
            requestBody.put("account", accountData);

            System.out.println(requestBody);
            processRequest(requestBody, merchantsOutputFile, merchantsBaseUrl);
        } catch (Exception e) {
            System.out.println("Error processing merchant row: " + e.getMessage());
            incrementFailure();
        }
    }
    private String getOptionalValue(Cell cell) {
        String value = getCellValue(cell);
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    private String getNonEmptyValue(Cell cell) {
        String value = getCellValue(cell);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Required field is missing or empty.");
        }
        return value.trim();
    }

    private String getCellValue(Cell cell) {
        return (cell == null || cell.getCellType() == CellType.BLANK) ? null : cell.getStringCellValue();
    }




    private void processRequest(Map<String, Object> requestBody, String outputFile, String baseUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("Authorization", apiToken);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        String[] result;

        try {
            ResponseEntity<Map> apiResponse = restTemplate.postForEntity(baseUrl, request, Map.class);
            if (apiResponse.getStatusCodeValue() == 201) {
                String alias = (String) ((Map) apiResponse.getBody().get("data")).get("alias");
                result = new String[]{requestBody.get("account").toString(), alias};
                incrementSuccess();
            } else {
                result = new String[]{requestBody
                        .get("account").toString(), apiResponse.getBody().toString()};
                incrementFailure();
            }
        } catch (Exception e) {
            result = new String[]{requestBody.get("account").toString(), e.getMessage()};
            incrementFailure();
        }

        synchronized (fileLock) {
            saveResult(result, outputFile);
        }
    }

    private synchronized void incrementSuccess() {
        successCount++;
    }

    private synchronized void incrementFailure() {
        failureCount++;
    }

    private void saveResult(String[] result, String outputFile) {
        try (CSVWriter writer = new CSVWriter(new FileWriter(outputFile, true))) {
            writer.writeNext(result);
        } catch (IOException e) {
            System.out.println("Error writing to CSV: " + e.getMessage());
        }
    }

    private boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}
