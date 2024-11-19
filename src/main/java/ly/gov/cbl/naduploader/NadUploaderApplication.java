package ly.gov.cbl.naduploader;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

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

        if (!confirm(scanner, "Do you want to continue? (yes/no): ")) return;

        int threadCount = getThreadCount(scanner);

        while (true) {
            System.out.println("Choose operation:");
            System.out.println("1. Enroll Accounts");
            System.out.println("2. Enroll Merchants");
            System.out.print("Enter your choice (1 or 2): ");

            int choice = scanner.nextInt();
            scanner.nextLine();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            accountsOutputFile = accountsOutputFile.replace(".csv", "_" + timestamp + ".csv");
            merchantsOutputFile = merchantsOutputFile.replace(".csv", "_" + timestamp + ".csv");

            if (choice == 1) {
                String fileName = getFileName(scanner, "ACCOUNTS.xlsx");
                if (!processFile(fileName, "Please place the accounts file at: " + new File(".").getAbsolutePath())) {
                    continue;
                }
                processAccounts(threadCount, fileName, timestamp);
                break;
            } else if (choice == 2) {
                String fileName = getFileName(scanner, "Merchants.xlsx");
                if (!processFile(fileName, "Please place the merchants file at: " + new File(".").getAbsolutePath())) {
                    continue;
                }
                processMerchants(threadCount, fileName, timestamp);
                break;
            } else {
                System.out.println("Invalid choice. Please try again.");
            }
        }

        System.out.println("\nEnrollment process completed.");
        System.out.println("Successful enrollments: " + successCount);
        System.out.println("Failed enrollments: " + failureCount);
    }

    private String getFileName(Scanner scanner, String defaultFileName) {
        System.out.print("Enter the file name (" + defaultFileName + "): ");
        String input = scanner.nextLine().trim();
        return input.isEmpty() ? defaultFileName : input;
    }

    private void processAccounts(int threadCount, String fileName, String timestamp) throws Exception {
        initializeOutputFile(accountsOutputFile, new String[]{"IBAN", "Alias/Response"});
        String failedAccountsFile = "failed-ACCOUNT_" + timestamp + ".csv";
        initializeOutputFile(failedAccountsFile, new String[]{"IBAN", "Error"});

        try (FileInputStream fis = new FileInputStream(fileName)) {
            Workbook workbook = new XSSFWorkbook(fis);
            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getLastRowNum();
            System.out.println("Number of accounts in the file: " + rowCount);

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            IntStream.rangeClosed(1, rowCount).forEach(rowNum -> { // Start from the second row (rowNum == 1)
                Row row = sheet.getRow(rowNum);
                if (row != null && !isRowEmpty(row)) {
                    executorService.submit(() -> {
                        enrollAccount(row, failedAccountsFile, failedAccountsFile);
                        printProgress(rowNum, rowCount);
                    });
                }
            });

            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
    }


    private void processMerchants(int threadCount, String fileName, String timestamp) throws Exception {
        initializeOutputFile(merchantsOutputFile, new String[]{"IBAN", "Alias/Response"});
        String failedMerchantsFile = "failed-MERCHANT_" + timestamp + ".csv";
        initializeOutputFile(failedMerchantsFile, new String[]{"IBAN", "Error"});

        try (FileInputStream fis = new FileInputStream(fileName)) {
            Workbook workbook = new XSSFWorkbook(fis);
            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getLastRowNum();
            System.out.println("Number of merchants in the file: " + rowCount);

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            IntStream.rangeClosed(1, rowCount).forEach(rowNum -> { // Start from the second row (rowNum == 1)
                Row row = sheet.getRow(rowNum);
                if (row != null && !isRowEmpty(row)) {
                    executorService.submit(() -> {
                        enrollMerchant(row, failedMerchantsFile);
                        printProgress(rowNum, rowCount);
                    });
                }
            });

            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
    }


    private boolean processFile(String fileName, String message) {
        File file = new File(fileName);
        if (!file.exists()) {
            System.out.println("Error: File " + fileName + " does not exist.");
            System.out.println(message);
            return false;
        }
        return true;
    }

    private void enrollAccount(Row row, String outputFile, String failedFile) {
        try {
            String nationalId = row.getCell(0).getStringCellValue();
            String phoneNumber = row.getCell(1).getStringCellValue();
            String passportNumber = getOptionalValue(row.getCell(2));
            String accountName = row.getCell(3).getStringCellValue();
            String accountNumber = row.getCell(4).getStringCellValue();
            String iban = row.getCell(5).getStringCellValue();

            Map<String, Object> requestBody = Map.of(
                    "nationalId", nationalId,
                    "phoneNumber", phoneNumber,
                    "passportNumber", passportNumber,
                    "account", Map.of(
                            "name", accountName,
                            "number", accountNumber,
                            "iban", iban
                    )
            );

            processRequest(requestBody, outputFile, failedFile, accountsBaseUrl);
        } catch (Exception e) {
            saveFailedRequest(failedFile, new String[]{"IBAN not available", e.getMessage()});
            incrementFailure();
        }
    }


    private void enrollMerchant(Row row, String failedFile) {
        try {
            // Safely retrieve the cell values with null checks
            String nationalId = getCellValue(row.getCell(2)); // Column: NATIONAL_ID
            String phoneNumber = getOptionalValue(row.getCell(4)); // Column: MOBILE_NUMBER (optional)
            String passportNumber = getOptionalValue(row.getCell(3)); // Column: PASSPORT_NO (optional)
            String merchantName = getCellValue(row.getCell(0)); // Column: AC_DESC
            String email = getOptionalValue(row.getCell(5)); // Column: EMAIL (if exists, optional)
            String mcc = getCellValue(row.getCell(6)); // Column: MCC
            String address = "Libya"; // Default value for address
            String tradeLicenseNumber = getOptionalValue(row.getCell(7)); // Column: Trade_lic (optional)
            String accountName = getCellValue(row.getCell(0)); // Column: AC_DESC (also account name)
            String accountNumber = getCellValue(row.getCell(9)); // Column: Account_no
            String iban = getCellValue(row.getCell(1)); // Column: IBAN

            // Create the request body map for merchants
            Map<String, Object> requestBody = Map.of(
                    "nationalId", nationalId,
                    "phoneNumber", phoneNumber,
                    "passportNumber", passportNumber,
                    "merchant", Map.of(
                            "tradeLicenseNumber", tradeLicenseNumber,
                            "name", merchantName,
                            "email", email,
                            "mcc", mcc,
                            "address", address
                    ),
                    "account", Map.of(
                            "name", accountName,
                            "number", accountNumber,
                            "iban", iban
                    )
            );

            // Call the processRequest method to handle API calls and CSV outputs
            processRequest(requestBody, merchantsOutputFile, failedFile, merchantsBaseUrl);
        } catch (Exception e) {
            // Handle exceptions and save failed requests to the failed file
            saveFailedRequest(failedFile, new String[]{"IBAN not available", e.getMessage()});
            incrementFailure();
        }
    }
    private String getCellValue(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            throw new IllegalArgumentException("Required field is missing or empty.");
        }
        return cell.getStringCellValue().trim();
    }



//    private void saveFailedRequest(String failedFile, String[] failedData) {
//        synchronized (fileLock) {
//            try (CSVWriter writer = new CSVWriter(new FileWriter(failedFile, true))) {
//                writer.writeNext(failedData);
//            } catch (IOException e) {
//                System.out.println("Error writing to failed file: " + e.getMessage());
//            }
//        }
//    }

    private String getOptionalValue(Cell cell) {
        return (cell == null || cell.getCellType() == CellType.BLANK) ? null : cell.getStringCellValue().trim();
    }


    private void saveFailedRequest(String failedFile, String[] failedData) {
        synchronized (fileLock) {
            try (CSVWriter writer = new CSVWriter(new FileWriter(failedFile, true))) {
                writer.writeNext(failedData);
            } catch (IOException e) {
                System.out.println("Error writing to failed file: " + e.getMessage());
            }
        }
    }




    private void printProgress(int current, int total) {
        int percent = (int) ((current / (float) total) * 100);
        System.out.print("\rProgress: " + percent + "% | Successful: " + successCount + " | Failed: " + failureCount);
    }

    private void initializeOutputFile(String fileName, String[] headers) {
        try (CSVWriter writer = new CSVWriter(new FileWriter(fileName))) {
            writer.writeNext(headers);
        } catch (IOException e) {
            System.out.println("Error initializing output file: " + e.getMessage());
        }
    }

    private boolean isRowEmpty(Row row) {
        return IntStream.range(0, row.getLastCellNum()).allMatch(i -> {
            Cell cell = row.getCell(i);
            return cell == null || cell.getCellType() == CellType.BLANK;
        });
    }

    private boolean confirm(Scanner scanner, String message) {
        System.out.print(message);
        String input = scanner.nextLine().trim();
        return input.equalsIgnoreCase("yes");
    }

    private int getThreadCount(Scanner scanner) {
        System.out.print("Enter the number of threads to use (1-10): ");
        int threadCount = scanner.nextInt();
        scanner.nextLine();
        return Math.min(10, Math.max(1, threadCount));
    }

    private void incrementSuccess() {
        synchronized (fileLock) {
            successCount++;
        }
    }

    private void incrementFailure() {
        synchronized (fileLock) {
            failureCount++;
        }
    }

//    private void processRequest(Map<String, Object> requestBody, String outputFile, String baseUrl) {
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Authorization", "Bearer " + apiToken);
//        headers.set("Content-Type", "application/json");
//
//        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
//        try {
//            ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl, request, Map.class);
//            if (response.getStatusCode().is2xxSuccessful()) {
//                incrementSuccess();
//                synchronized (fileLock) {
//                    try (CSVWriter writer = new CSVWriter(new FileWriter(outputFile, true))) {
//                        writer.writeNext(new String[]{(String) requestBody.get("iban"), "Success"});
//                    }
//                }
//            } else {
//                incrementFailure();
//            }
//        } catch (Exception e) {
//            incrementFailure();
//        }
//    }
private void processRequest(Map<String, Object> requestBody, String outputFile, String failedFile, String baseUrl) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + apiToken);
    headers.set("Content-Type", "application/json");

    HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
    String iban = (String) ((Map) requestBody.get("account")).get("iban");

    try {
        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl, request, Map.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            String alias = (String) data.get("alias");

            incrementSuccess();
            synchronized (fileLock) {
                try (CSVWriter writer = new CSVWriter(new FileWriter(outputFile, true))) {
                    writer.writeNext(new String[]{iban, alias});
                }
            }
        } else {
            incrementFailure();
            saveFailedRequest(failedFile, new String[]{iban, response.getBody().toString()});
        }
    } catch (Exception e) {
        incrementFailure();
        saveFailedRequest(failedFile, new String[]{iban, e.getMessage()});
    }
}


}
