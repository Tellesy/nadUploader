NAD Uploader

NAD Uploader is a Spring Boot application designed to streamline the bulk enrollment of bank customers and merchants into the National Alias Directory (NAD) system. The application reads customer and merchant data from Excel files, processes the data, and communicates with the NAD API for enrollment. Results are saved into CSV files for tracking successful and failed enrollments.

Features

Bulk Enrollment:
Supports enrolling both individual accounts and merchants.
Excel File Input:
Reads data from ACCOUNTS.xlsx and Merchants.xlsx files.
Multi-threading:
Allows simultaneous processing of multiple records.
Error Handling:
Handles missing or invalid fields gracefully.
CSV Output:
Saves successful and failed enrollments to separate CSV files.
Dynamic Configuration:
Configurable API endpoints and authentication token.
Requirements

Java: 17 or higher
Spring Boot: 3.3.5
Dependencies:
Apache POI for Excel processing
OpenCSV for CSV file generation
RestTemplate for HTTP requests
Installation

Clone the repository:
git clone https://github.com/your-username/nad-uploader.git
cd nad-uploader
Update application.properties:
Located in src/main/resources/application.properties:
api.base-url.accounts=http://your-api-url/accounts/enroll
api.base-url.merchants=http://your-api-url/merchants/enroll
api.token=Bearer your-auth-token
output.accounts-file=accounts_output.csv
output.merchants-file=merchants_output.csv
Build the application:
./gradlew build
Run the application:
java -jar build/libs/nad-uploader.jar
Usage

Application Flow
Welcome Message:
The application displays a welcome message and confirms that the configuration is correct.
Operation Selection:
Choose to enroll either Accounts or Merchants.
File Validation:
Ensures the required Excel file (ACCOUNTS.xlsx or Merchants.xlsx) is in the project directory.
Prompts the user to place the file if it is missing.
Simultaneous Operations:
Choose the number of simultaneous operations (1-10).
Enrollment Process:
Processes each row from the Excel file.
Sends the data to the corresponding API endpoint.
Saves successful and failed results in separate CSV files.
Completion:
Displays the total number of successful and failed enrollments.
Input Excel Format
For Accounts (ACCOUNTS.xlsx):

Column	Description	Required
Name	Account holder's name	Yes
IBAN	Account IBAN	Yes
National ID	National ID number	Yes
Passport No	Passport number	No
Phone Number	Phone number	No
Account No	Account number	Yes
For Merchants (Merchants.xlsx):

Column	Description	Required
Merchant Name	Merchant's name	Yes
IBAN	Merchant's IBAN	Yes
National ID	Merchant's national ID	Yes
Passport No	Passport number	No
Phone Number	Phone number	No
Account No	Merchant's account number	Yes
MCC	Merchant Category Code	Yes
Trade License	Trade license number	No
Output CSV Format
Successful Enrollments:

Column	Description
IBAN	The IBAN of the entity
Alias	The generated alias
Failed Enrollments:

Column	Description
IBAN	The IBAN of the entity
Response	Error message or response
Configuration

Edit the application.properties file to customize:

API base URLs for accounts and merchants.
Authentication token for API requests.
Output file paths.
Error Handling

If an Excel file is missing, the application prompts the user to place the file in the correct location.
Missing or empty optional fields are ignored for merchants.
Required fields must be filled; otherwise, the row will be skipped, and the error is logged.
Contributing

Fork the repository.
Create a new feature branch:
git checkout -b feature-name
Commit your changes:
git commit -m "Add your message"
Push to the branch:
git push origin feature-name
Submit a pull request.
License

This project is licensed under the MIT License. See the LICENSE file for details.

Author

Your Name
GitHub Profile

Feel free to contribute, report issues, or suggest improvements! 😊
