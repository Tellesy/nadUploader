# NAD Uploader

NAD Uploader is a Spring Boot application designed to facilitate the bulk enrollment of bank customers and merchants into the National Alias Directory (NAD) system. It reads customer and merchant data from Excel files, processes the data, and sends it to the NAD API for enrollment. The application tracks successful and failed enrollments by generating CSV reports.

---

## Features

- **Bulk Enrollment**: Supports enrollment of both individual accounts and merchants.
- **Excel File Input**: Reads data from `ACCOUNTS.xlsx` and `Merchants.xlsx`.
- **Multi-threading**: Allows up to 10 simultaneous operations.
- **Error Handling**: Gracefully handles missing fields and invalid data.
- **CSV Output**: Generates separate files for successful and failed enrollments.
- **Configurable**: Easily update API URLs and authentication tokens in the configuration file.

---

## Prerequisites

- **Java**: 17 or higher
- **Spring Boot**: 3.3.5
- **Dependencies**:
    - Apache POI for Excel processing
    - OpenCSV for CSV file generation
    - RestTemplate for HTTP requests

---

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/Tellesy/nadUploader.git
   cd nadUploader
2. Update the `application.properties` file:
    - Located in `src/main/resources/application.properties`:
      ```properties
      api.base-url.accounts=http://your-api-url/accounts/enroll
      api.base-url.merchants=http://your-api-url/merchants/enroll
      api.token=Bearer your-auth-token
      output.accounts-file=accounts_output.csv
      output.merchants-file=merchants_output.csv
      ```

3. Build the application:
   ```bash
   ./gradlew build
   

4. Run the application:
   ```bash
   java -jar build/libs/nad-uploader.jar

## Input Excel Format

### Accounts (`ACCOUNTS.xlsx`)

| **Column**       | **Description**         | **Required** |
|-------------------|-------------------------|--------------|
| Name             | Account holder's name  | Yes          |
| IBAN             | Account IBAN           | Yes          |
| National ID      | National ID number     | Yes          |
| Passport No      | Passport number        | No           |
| Phone Number     | Phone number           | No           |
| Account No       | Account number         | Yes          |

### Merchants (`Merchants.xlsx`)

| **Column**       | **Description**                | **Required** |
|-------------------|--------------------------------|--------------|
| Merchant Name    | Merchant's name               | Yes          |
| IBAN             | Merchant's IBAN               | Yes          |
| National ID      | Merchant's national ID        | Yes          |
| Passport No      | Passport number               | No           |
| Phone Number     | Phone number                  | No           |
| Account No       | Merchant's account number     | Yes          |
| MCC              | Merchant Category Code        | Yes          |
| Trade License    | Trade license number          | No           |


### Output CSV Format

#### Successful Enrollments

| **Column** | **Description**          |
|------------|--------------------------|
| IBAN       | The IBAN of the entity  |
| Alias      | The generated alias     |

#### Failed Enrollments

| **Column** | **Description**          |
|------------|--------------------------|
| IBAN       | The IBAN of the entity  |
| Response   | Error message or response|


## Configuration

Edit the `application.properties` file to customize:
- API base URLs for accounts and merchants.
- Authentication token for API requests.
- Output file paths.

## Error Handling

- **Missing Excel File**:
    - The application prompts the user to place the required file in the project directory if it is missing.
- **Null or Empty Optional Fields**:
    - Fields such as `passportNo`, `phoneNumber`, and `tradeLicense` can be left empty or null.
- **Required Fields**:
    - Missing required fields will result in the row being skipped and the error logged.


## Contributing

Contributions are welcome! To contribute:

1. Fork the repository.
2. Create a new branch for your feature or bugfix:
   ```bash
   git checkout -b feature-name
3. Commit your changes:
   ```bash
   git commit -m 'Add new feature'
4. Push your branch:
   ```bash
    git push origin feature-name
5. Create a pull request.
6. After your pull request is reviewed, it will be merged.



## License
This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Author

**Muhammad Tellesy**  
[GitHub Profile](https://github.com/Tellesy)

Feel free to open issues, suggest features, or contribute to the project! 😊

