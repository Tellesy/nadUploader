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

    @Value("${api.base-url}")
    private String apiBaseUrl;

    @Value("${api.token}")
    private String apiToken;

    @Value("${output.accounts-file}")
    private String accountsOutputFile;

    @Value("${output.merchants-file}")
    private String merchantsOutputFile;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Object fileLock = new Object();

    public static void main(String[] args) {
        SpringApplication.run(NadUploaderApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Welcome to the NAD Uploader!");
        System.out.println("This tool will help you bulk-enroll your bank customers and merchants into the NAD system.");
        System.out.println("Please ensure that the base URL and token are correctly set in the properties file.");
        System.out.println("Base URL: " + apiBaseUrl);
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
        FileInputStream fis = new FileInputStream("ACCOUNTS.xlsx");
        Workbook workbook = new XSSFWorkbook(fis);
        Sheet sheet = workbook.getSheetAt(0);
        int rowCount = sheet.getLastRowNum();
        System.out.println("Number of customers in the file: " + rowCount);

        System.out.println("Starting account enrollment process...");
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            executorService.submit(() -> enrollAccount(row));
        }

        executorService.shutdown();
        executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        System.out.println("Account enrollment process completed. Results saved to: " + accountsOutputFile);
    }

    private void enrollAccount(Row row) {
        String name = row.getCell(0).getStringCellValue();
        String iban = row.getCell(1).getStringCellValue();
        String nationalId = row.getCell(2).getStringCellValue();
        String passportNo = row.getCell(3).getStringCellValue();
        String phoneNumber = row.getCell(4).getStringCellValue();
        String accountNo = row.getCell(5).getStringCellValue();

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

        System.out.println(requestBody);

        processRequest(requestBody, accountsOutputFile);
    }

    private void processMerchants(int threadCount) throws Exception {
        FileInputStream fis = new FileInputStream("Merchants.xlsx");
        Workbook workbook = new XSSFWorkbook(fis);
        Sheet sheet = workbook.getSheetAt(0);
        int rowCount = sheet.getLastRowNum();
        System.out.println("Number of merchants in the file: " + rowCount);

        System.out.println("Starting merchant enrollment process...");
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            executorService.submit(() -> enrollMerchant(row));
        }

        executorService.shutdown();
        executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        System.out.println("Merchant enrollment process completed. Results saved to: " + merchantsOutputFile);
    }

    private void enrollMerchant(Row row) {
        String iban = row.getCell(1).getStringCellValue();
        String nationalId = row.getCell(2).getStringCellValue();
        String passportNo = getCellValue(row.getCell(3));
        String phoneNumber = getCellValue(row.getCell(4));
        String tradeLicense = getCellValue(row.getCell(7));
        String merchantName = row.getCell(0).getStringCellValue();
        String mcc = row.getCell(6).getStringCellValue();

        Map<String, Object> requestBody = Map.of(
                "nationalId", nationalId,
                "phoneNumber", phoneNumber,
                "passportNumber", passportNo,
                "merchant", Map.of(
                        "tradeLicenseNumber", tradeLicense,
                        "name", merchantName,
                        "mcc", mcc,
                        "address", "Libya"
                ),
                "account", Map.of(
                        "iban", iban,
                        "name", merchantName,
                        "number", row.getCell(5).getStringCellValue()
                )
        );

        processRequest(requestBody, merchantsOutputFile);
    }

    private String getCellValue(Cell cell) {
        return cell == null ? null : cell.getStringCellValue();
    }

    private void processRequest(Map<String, Object> requestBody, String outputFile) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("Authorization", apiToken);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        String[] result;

        try {
            ResponseEntity<Map> apiResponse = restTemplate.postForEntity(apiBaseUrl, request, Map.class);
            if (apiResponse.getStatusCodeValue() == 201) {
                String alias = (String) ((Map) apiResponse.getBody().get("data")).get("alias");
                result = new String[]{requestBody.get("account").toString(), alias};
            } else {
                result = new String[]{requestBody.get("account").toString(), apiResponse.getBody().toString()};
            }
        } catch (Exception e) {
            result = new String[]{requestBody.get("account").toString(), e.getMessage()};
        }

        synchronized (fileLock) {
            saveResult(result, outputFile);
        }
    }

    private void saveResult(String[] result, String outputFile) {
        try (CSVWriter writer = new CSVWriter(new FileWriter(outputFile, true))) {
            writer.writeNext(result);
        } catch (IOException e) {
            System.out.println("Error writing to CSV: " + e.getMessage());
        }
    }
}
