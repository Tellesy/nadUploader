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
            System.out.println("Number of customers in the file: " + rowCount);

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            IntStream.rangeClosed(1, rowCount).forEach(rowNum -> {
                Row row = sheet.getRow(rowNum);
                if (row != null && !isRowEmpty(row)) {
                    executorService.submit(() -> {
                        enrollAccount(row, failedAccountsFile);
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
            IntStream.rangeClosed(1, rowCount).forEach(rowNum -> {
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

    private void enrollAccount(Row row, String failedFile) {
        try {
            String iban = row.getCell(1).getStringCellValue();
            Map<String, Object> requestBody = Map.of("iban", iban);
            processRequest(requestBody, accountsOutputFile, accountsBaseUrl);
        } catch (Exception e) {
            saveFailedRequest(failedFile, new String[]{"IBAN data", e.getMessage()});
            incrementFailure();
        }
    }

    private void enrollMerchant(Row row, String failedFile) {
        try {
            String iban = row.getCell(1).getStringCellValue();
            Map<String, Object> requestBody = Map.of("iban", iban);
            processRequest(requestBody, merchantsOutputFile, merchantsBaseUrl);
        } catch (Exception e) {
            saveFailedRequest(failedFile, new String[]{"Merchant IBAN", e.getMessage()});
            incrementFailure();
        }
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

    private void processRequest(Map<String, Object> requestBody, String outputFile, String baseUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiToken);
        headers.set("Content-Type", "application/json");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                incrementSuccess();
                synchronized (fileLock) {
                    try (CSVWriter writer = new CSVWriter(new FileWriter(outputFile, true))) {
                        writer.writeNext(new String[]{(String) requestBody.get("iban"), "Success"});
                    }
                }
            } else {
                incrementFailure();
            }
        } catch (Exception e) {
            incrementFailure();
        }
    }
}
