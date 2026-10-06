# IoT Based Toll Booth Manager System

### OOPJ (Object Oriented Programming using Java) — Academic Laboratory Project

A **command-line Core Java application** that simulates an IoT enabled toll booth:
it identifies a vehicle through RFID, calculates the toll according to the vehicle
type, collects the money (Cash / UPI / FASTag), opens the barrier, stores the
transaction and shows the full history.

> **No Spring Boot, no React, no Node.js, no database, no external library.**
> Only `java.*` packages of the JDK are used. The project compiles with `javac`
> and runs with `java`.

| Item | Value |
|---|---|
| Language | Core Java (Java 8 or above, verified on JDK 17) |
| Type | Console application (no GUI) |
| Packages | 7 (`tollbooth` + 6 sub packages) |
| Java files | 21 |
| Lines of code | ~3050 (with concept comments) |
| External libraries | **None** |
| Data storage | `transactions.txt` (text file) |
| Concepts demonstrated | Encapsulation, Inheritance, Polymorphism, Abstraction, Interface, Constructor, Overloading, Overriding, Exception Handling, Collections, File Handling, Multithreading |

---

## Table of Contents

1. [Project Objective](#1-project-objective)
2. [Features Completed](#2-features-completed)
3. [Project Folder Structure](#3-project-folder-structure)
4. [File by File Description (all 21 files)](#4-file-by-file-description-all-21-files)
5. [How to Compile and Run](#5-how-to-compile-and-run)
6. [Sample Input and Output](#6-sample-input-and-output)
7. [OOP Concept Mapping Table](#7-oop-concept-mapping-table)
8. [Toll Rates and How to Change Them](#8-toll-rates-and-how-to-change-them)
9. [Transaction File Format](#9-transaction-file-format)
10. [Exception Handling Summary](#10-exception-handling-summary)
11. [Collections Used](#11-collections-used)
12. [Multithreading Explanation](#12-multithreading-explanation)
13. [Menu Options](#13-menu-options)
14. [Full Documentation and Viva Preparation](#14-full-documentation-and-viva-preparation)
15. [Troubleshooting](#15-troubleshooting)

---

## 1. Project Objective

Develop a Java application that simulates an IoT enabled toll booth which:

1. Registers vehicles.
2. Identifies different vehicle types (Car, Bike, Truck, Bus).
3. Calculates the toll automatically.
4. Simulates RFID based vehicle identification.
5. Processes the toll payment.
6. Opens the toll barrier after a successful payment.
7. Stores the toll transaction details.
8. Displays the transaction history.
9. Handles invalid inputs and exceptions without crashing.
10. Demonstrates all the major Java OOP concepts.

The **priority of this project is the OOP concepts**, not IoT complexity. The RFID
reader, the barrier and the FASTag wallet are small classes that make the OOP
design easy to explain in a viva.

---

## 2. Features Completed

| # | Requirement | Where it is implemented |
|---|---|---|
| 1 | Vehicle registration with validation | `TollBooth.registerVehicle()`, menu option 1 |
| 2 | Vehicle types Car / Bike / Truck / Bus | `model/Car.java`, `Bike.java`, `Truck.java`, `Bus.java` |
| 3 | Automatic toll calculation | `Vehicle.calculateToll()` overridden in every child class |
| 4 | RFID identification simulation | `device/RFIDDevice.java` (`simulateScan`, `readRFID`, `validateRFID`) |
| 5 | Payment through Cash / UPI / FASTag | `payment/CashPayment.java`, `UPIPayment.java`, `FastagPayment.java` |
| 6 | Barrier opens and closes | `device/Barrier.java` + `TollBooth.openBarrier()/closeBarrier()` |
| 7 | Transaction details stored | `model/TollTransaction.java` + `ArrayList` inside `TollBooth` |
| 8 | Transaction history displayed | Menu option 6 (memory list **and** the saved file) |
| 9 | Invalid input never crashes the program | 3 custom exceptions + `try/catch/finally` in `TollBoothManager` |
| 10 | All 12 OOP concepts | See the [mapping table](#7-oop-concept-mapping-table) |
| 11 | Multithreading (many vehicles together) | `thread/VehicleProcessingThread.java`, menu option 9 |
| 12 | File handling | `service/TransactionFileManager.java` → `transactions.txt` |

---

## 3. Project Folder Structure

```
Toll-Booth-Manager/
│
├── tollbooth/
│   │
│   ├── model/                          <- DATA classes (the "nouns" of the project)
│   │   ├── Vehicle.java                <- ABSTRACT parent class (encapsulation)
│   │   ├── Car.java                    <- child class (inheritance + overriding)
│   │   ├── Bike.java                   <- child class (also overrides the overloaded method)
│   │   ├── Truck.java                  <- child class
│   │   ├── Bus.java                    <- child class
│   │   ├── TollRates.java              <- all toll rates and the currency format
│   │   └── TollTransaction.java        <- one toll record (auto id TXN1001, TXN1002 ...)
│   │
│   ├── payment/                        <- PAYMENT classes (interface + implementations)
│   │   ├── PaymentProcessor.java       <- INTERFACE (processPayment, printReceipt)
│   │   ├── CashPayment.java            <- implements PaymentProcessor
│   │   ├── UPIPayment.java             <- implements PaymentProcessor
│   │   └── FastagPayment.java          <- implements PaymentProcessor (RFID wallet)
│   │
│   ├── device/                         <- IoT HARDWARE simulation
│   │   ├── RFIDReader.java             <- INTERFACE (readRFID, validateRFID)
│   │   ├── RFIDDevice.java             <- implements RFIDReader (the scanner)
│   │   └── Barrier.java                <- the toll gate (open / close)
│   │
│   ├── exception/                      <- USER DEFINED EXCEPTIONS
│   │   ├── InvalidRFIDException.java
│   │   ├── InvalidPaymentException.java
│   │   └── InvalidVehicleException.java
│   │
│   ├── service/                        <- BUSINESS LOGIC
│   │   ├── TollBooth.java              <- the brain : HashMap + ArrayList + workflow
│   │   └── TransactionFileManager.java <- file handling (FileWriter / FileReader)
│   │
│   ├── thread/                         <- MULTITHREADING
│   │   └── VehicleProcessingThread.java <- extends Thread, one thread per vehicle
│   │
│   └── TollBoothManager.java           <- MAIN CLASS : menu + Scanner (user interface)
│
├── docs/                               <- documentation for the report and the viva
│   ├── OOP_CONCEPTS.md                  <- every concept explained with code references
│   ├── CLASS_EXPLANATIONS.md            <- explanation of every major class
│   ├── SAMPLE_INPUT_OUTPUT.md           <- complete sample input and output
│   └── VIVA_QUESTIONS.md                <- 40 viva questions with answers
│
├── compile-and-run.sh                  <- one command compile + run (Linux / Mac)
├── compile-and-run.bat                 <- one command compile + run (Windows)
├── README.md                           <- this file
└── transactions.txt                    <- created automatically at the first run
```

> The package name matches the folder name exactly:
> `package tollbooth.model;` is inside `tollbooth/model/`, and so on.

---

## 4. File by File Description (all 21 files)

### model package — the data of the project

| File | Purpose | OOP concepts |
|---|---|---|
| `model/Vehicle.java` | Abstract parent. Private fields `vehicleNumber`, `vehicleType`, `ownerName`, `rfidTag`, `totalTrips` with getters and setters. Holds the abstract `calculateToll()` and the two overloaded versions, plus `displayVehicleDetails()`. | Abstraction, Encapsulation, Overloading, Overriding, Constructor |
| `model/Car.java` | `extends Vehicle`; extra field `numberOfSeats`; toll `₹50`. | Inheritance, Overriding, Constructor overloading |
| `model/Bike.java` | `extends Vehicle`; extra field `engineCapacityCc`; toll `₹30`. Also overrides `calculateToll(boolean)` so a two wheeler never gets the FASTag discount. | Inheritance, Overriding (including an overloaded method) |
| `model/Truck.java` | `extends Vehicle`; extra field `numberOfAxles`; toll `₹150`. | Inheritance, Overriding |
| `model/Bus.java` | `extends Vehicle`; extra field `passengerCapacity`; toll `₹100`. | Inheritance, Overriding |
| `model/TollRates.java` | All rates (`BIKE_TOLL = 30`, `CAR_TOLL = 50`, `BUS_TOLL = 100`, `TRUCK_TOLL = 150`), the FASTag discount (10%), `applyDiscount()`, `applyFastagDiscount()`, `formatAmount()` and private constructor (utility class). | Encapsulation, Abstraction |
| `model/TollTransaction.java` | One toll record: `transactionId`, `vehicleNumber`, `vehicleType`, `tollAmount`, `paymentMode`, `dateTime`, `transactionStatus`; `displayTransaction()`, `generateReceipt()`, `toFileLine()`, `fromFileLine()`. Static counter generates `TXN1001`, `TXN1002` ... | Encapsulation, Constructor overloading, Static member, Overriding (`toString`) |

### payment package — the interface and its three implementations

| File | Purpose | OOP concepts |
|---|---|---|
| `payment/PaymentProcessor.java` | Interface with `processPayment(double)`, `printReceipt()`, `getPaymentMode()`. | Interface, Abstraction |
| `payment/CashPayment.java` | Cash counter; returns change; throws `InvalidPaymentException` when the cash is less than the toll. | Interface implementation, Exception handling |
| `payment/UPIPayment.java` | UPI payment with a UPI id and a reference number. | Interface implementation, Exception handling |
| `payment/FastagPayment.java` | FASTag wallet; deducts the toll automatically; throws when the balance is low; has `rechargeWallet()`. | Interface implementation, Encapsulation, Exception handling |

### device package — the IoT hardware simulation

| File | Purpose | OOP concepts |
|---|---|---|
| `device/RFIDReader.java` | Interface with `readRFID()` and `validateRFID(String)`. | Interface, Abstraction |
| `device/RFIDDevice.java` | The scanner: `simulateScan()` (detects a tag), `readRFID()`, `validateRFID()` (checks the `RFID####` format), `generateNewTag()`, `syncTagSerial()`. | Interface implementation, Encapsulation, Exception handling |
| `device/Barrier.java` | `openBarrier()`, `closeBarrier()`, private `open` flag, counts the openings. Uses `Thread.sleep()` to simulate the motor. | Encapsulation, Abstraction, Multithreading |

### exception package — user defined exceptions

| File | Purpose | OOP concepts |
|---|---|---|
| `exception/InvalidRFIDException.java` | Wrong format or unknown RFID tag. | Exception handling, Inheritance, Constructor overloading |
| `exception/InvalidPaymentException.java` | Payment failure (short cash, wrong UPI id, low FASTag balance). | Exception handling, Inheritance |
| `exception/InvalidVehicleException.java` | Registration problems (wrong number format, duplicate vehicle / RFID). | Exception handling, Inheritance |

All three extend `Exception` (checked exceptions), so the compiler forces the
programmer to handle them.

### service package — the business logic

| File | Purpose | OOP concepts |
|---|---|---|
| `service/TollBooth.java` | Fields `boothId`, `location`, `totalTransactions`, `totalCollection`, `HashMap<String, Vehicle> vehicleDatabase`, `ArrayList<TollTransaction> transactions`, an `RFIDDevice` and a `Barrier`. Methods: `registerVehicle()`, `searchVehicleByRFID()`, `displayAllVehicles()`, `calculateToll()` (3 overloads), `processVehicle()` (the 11 step workflow), `collectPayment()`, `recordTransaction()`, `openBarrier()`, `closeBarrier()`, `displayBoothDetails()`, `displayTransactionHistory()`. | Encapsulation, Collections, Polymorphism, Interface, Synchronization |
| `service/TransactionFileManager.java` | Writes every transaction to `transactions.txt` (`FileWriter` + `BufferedWriter`) and reads it back (`FileReader` + `BufferedReader`) with `try-with-resources` and `try/catch/finally`. | File handling, Abstraction, Exception handling, Collections |

### thread package and the main class

| File | Purpose | OOP concepts |
|---|---|---|
| `thread/VehicleProcessingThread.java` | `extends Thread`; one object processes one vehicle (arrives, pays, crosses the barrier). Uses `start()`, `run()`, `sleep()` and handles `InterruptedException`. | Multithreading, Inheritance, Overriding, Exception handling |
| `TollBoothManager.java` | Main class with `main()`, the 10 option menu, `Scanner` input and the `try/catch/finally` that keeps the program alive. Creates the objects (`new TollBooth()`, `new Car()`, `new CashPayment()` ...) and stores them in interface / parent references. | Object creation, Polymorphism, Interface, Exception handling, Collections |

---

## 5. How to Compile and Run

### Requirement

Only the **JDK** (Java Development Kit) is needed — Java 8 or above.
Check it with:

```bash
java -version
javac -version
```

### Windows (Command Prompt)

```bat
cd Toll-Booth-Manager
javac -d out tollbooth\TollBoothManager.java
java -cp out tollbooth.TollBoothManager
```

### Linux / macOS (Terminal)

```bash
cd Toll-Booth-Manager
javac -d out tollbooth/TollBoothManager.java
java -cp out tollbooth.TollBoothManager
```

`javac` automatically compiles all the other classes that `TollBoothManager`
uses, so one command is enough. To compile **every** file explicitly use:

```bash
javac -d out $(find tollbooth -name "*.java")         # Linux / macOS
javac -d out tollbooth\*.java tollbooth\*/*.java      # Windows
```

### One-command scripts included in this repository

```bash
./compile-and-run.sh          # Linux / macOS
compile-and-run.bat           # Windows (double click is enough)
```

### Notes

* The file `transactions.txt` is created in the folder from which you run
  `java`. Run the program from the project folder so that the file stays
  in the project.
* If the Indian Rupee symbol (₹) does not appear on your console, the program
  automatically prints `Rs.` instead. To force the Rupee symbol:
  * Windows: `chcp 65001` before running, then
    `java -Dfile.encoding=UTF-8 -cp out tollbooth.TollBoothManager`
  * Linux / macOS: `java -Dfile.encoding=UTF-8 -cp out tollbooth.TollBoothManager`
* Developed, compiled and run on **Java 17 (Temurin)**, compiled for the Java 8
  language level as well (`-source 8 -target 8`). The source uses only Java 8
  features (no `var`, no `String.repeat`, no lambdas/streams), so JDK 8 and
  above can compile and run the project.

---

## 6. Sample Input and Output

The complete transcript is in [`docs/SAMPLE_INPUT_OUTPUT.md`](docs/SAMPLE_INPUT_OUTPUT.md).
A short example (menu option 3 → Process Toll, paying with FASTag):

```text
========================================
        TOLL BOOTH VEHICLE ENTRY
========================================

Enter RFID : RFID1001
Vehicle found : TN01AB1234 (Car)

Select Payment Mode:
1. Cash
2. UPI
3. FASTag
Enter choice : 3
Enter the FASTag wallet balance (ENTER for ₹1000) : 1000

RFID SCANNER : tag detected -> RFID1001

RFID validated successfully.
---------------------------------------
RFID DETECTED
RFID           : RFID1001
Vehicle        : TN01AB1234
Type           : Car
Owner          : Hemachandran
---------------------------------------

Toll Amount    : ₹50
FASTag discount (10%) : -₹5
Amount Payable : ₹45
FASTag payment processing...
FASTag tag     : RFID1001
Wallet debited : ₹45
Balance left   : ₹955

Payment Successful!

========================================
          TRANSACTION RECEIPT
========================================
Transaction ID : TXN1001
Vehicle Number : TN01AB1234
Vehicle Type   : Car
Toll Amount    : ₹45
Payment Mode   : FASTag
Date & Time    : 06-10-2026 15:25:43
Status         : SUCCESS
========================================
Barrier Opening...
Barrier OPEN
Vehicle Passed Successfully.
Closing Barrier...
Barrier CLOSED
```

The same transaction is appended to `transactions.txt`:

```text
TXN1001;TN01AB1234;Car;45.00;FASTag;2026-10-06 15:25:43;SUCCESS
```

---

## 7. OOP Concept Mapping Table

| # | OOP Concept | Where it is used | File / Method |
|---|---|---|---|
| 1 | **Encapsulation** | Private data members + public getters / setters | `Vehicle.java` (`private String vehicleNumber`), `TollTransaction.java`, `Barrier.java` (`private boolean open`), `TollBooth.java` (private `HashMap`) |
| 2 | **Inheritance** | Child classes extend the parent | `Car`, `Bike`, `Truck`, `Bus` **extends** `Vehicle`; the 3 exceptions **extend** `Exception`; `VehicleProcessingThread` **extends** `Thread` |
| 3 | **Polymorphism** | Runtime: one reference, many objects | `Vehicle vehicle = new Car(); vehicle.calculateToll();` → `TollBooth.calculateToll(Vehicle)`; `PaymentProcessor payment = new FastagPayment(...)` |
| 4 | **Abstraction** | Abstract class + abstract method | `abstract class Vehicle` with `abstract double calculateToll()` |
| 5 | **Interface** | Contract with no body | `RFIDReader` (+ `RFIDDevice`), `PaymentProcessor` (+ `CashPayment`, `UPIPayment`, `FastagPayment`) |
| 6 | **Constructor** | Initialize objects, `super()` and `this()` | `Vehicle(...)`, `Car(...)`, `TollBooth(...)`, `TollTransaction(...)`, `CashPayment(...)` |
| 7 | **Method Overloading** | Same name, different parameters | `calculateToll()`, `calculateToll(boolean fastTag)`, `calculateToll(boolean fastTag, double discount)` in `Vehicle.java`; also `calculateToll(Vehicle)`, `calculateToll(Vehicle, boolean)`, `calculateToll(Vehicle, boolean, double)` in `TollBooth.java` |
| 8 | **Method Overriding** | Child replaces the parent method | `calculateToll()` in `Car`, `Bike`, `Truck`, `Bus`; `getExtraDetails()`; `toString()` in `Vehicle` and `TollTransaction`; `run()` in `VehicleProcessingThread` |
| 9 | **Exception Handling** | `try`, `catch`, `finally`, `throw`, `throws` | `TollBoothManager.start()` (try / catch / finally), `TollBooth.processVehicle()` (`throws InvalidRFIDException, InvalidPaymentException`), `RFIDDevice.simulateScan()` (`throw new InvalidRFIDException(...)`), `TransactionFileManager.countSavedTransactions()` (try / catch / finally) |
| 10 | **Collections** | `HashMap` and `ArrayList` | `HashMap<String, Vehicle> vehicleDatabase` (RFID → Vehicle), `ArrayList<TollTransaction> transactions`, `List<VehicleProcessingThread> threads` in option 9 |
| 11 | **File Handling** | Write and read a text file | `TransactionFileManager.java` with `FileWriter`, `BufferedWriter`, `FileReader`, `BufferedReader` → `transactions.txt` |
| 12 | **Multithreading** | Many vehicles at the same time | `VehicleProcessingThread extends Thread` with `start()`, `run()`, `sleep()`, `join()` and the `synchronized` methods of `TollBooth` |

### Where each concept can be shown in one line (viva ready)

```java
// Abstraction - an abstract class cannot be instantiated
Vehicle v;                       // OK  : a reference
// v = new Vehicle(...);         // ERROR: an abstract class has no object

// Inheritance - a Car IS-A Vehicle
Car car = new Car("TN01AB1234", "Hemachandran", "RFID1001", 5);

// Encapsulation - the data is private, only the getter is public
System.out.println(car.getVehicleNumber());     // TN01AB1234
// car.vehicleNumber = "XX";                     // ERROR: private field

// Runtime polymorphism - the parent reference calls the child method
Vehicle vehicle = new Truck("TN03EF9012", "Suresh Logistics", "RFID1003", 3);
System.out.println(vehicle.calculateToll());     // 150 (Truck version runs)

// Method overloading - same name, three parameter lists
vehicle.calculateToll();                         // 150
vehicle.calculateToll(true);                     // 135  (FASTag discount)

// Interface - the booth does not know which payment object it gets
PaymentProcessor payment = new UPIPayment("student@upi");
payment.processPayment(45.0);

// Custom exception - thrown, propagated with throws, caught in the menu
throw new InvalidRFIDException("No vehicle is registered", "RFID9999");
```

---

## 8. Toll Rates and How to Change Them

All the money rules are in **one** file: `tollbooth/model/TollRates.java`.

```java
public static final double BIKE_TOLL  = 30.0;    // two wheeler
public static final double CAR_TOLL   = 50.0;    // car / jeep / van
public static final double BUS_TOLL   = 100.0;   // bus
public static final double TRUCK_TOLL = 150.0;   // truck / lorry
public static final double FASTAG_DISCOUNT_PERCENT = 10.0;
```

To use another rate (for example Car ₹60), change `CAR_TOLL` to `60.0`,
compile again and the whole application (vehicle display, calculation, receipt,
file record) uses the new rate. Nothing else has to be edited — this is the
"easy to modify" requirement.

---

## 9. Transaction File Format

`transactions.txt` keeps one transaction per line with the `;` separator:

```text
# IoT Based Toll Booth Manager - transaction records
TXN1001;TN01AB1234;Car;45.00;FASTag;2026-10-06 15:25:43;SUCCESS
TXN1002;TN05IJ7890;Car;50.00;Cash;2026-10-06 15:25:45;SUCCESS
TXN1003;TN04GH3456;Bus;100.00;UPI;2026-10-06 15:25:46;SUCCESS
```

| Position | Value | Example |
|---|---|---|
| 1 | Transaction ID | `TXN1001` |
| 2 | Vehicle number | `TN01AB1234` |
| 3 | Vehicle type | `Car` |
| 4 | Toll amount | `45.00` |
| 5 | Payment mode | `FASTag` |
| 6 | Date and time | `2026-10-06 15:25:43` |
| 7 | Status | `SUCCESS` / `FAILED` |

Interesting points for the viva:

* The file is opened in **append mode** (`new FileWriter(FILE_NAME, true)`),
  so old records are never erased.
* On start up the program counts the saved records and continues the id
  counter (3 saved records → the next new transaction is `TXN1004`), so
  transaction ids never repeat.
* Lines starting with `#` (the header) and damaged lines are skipped while
  reading, so a hand edited file cannot crash the program.
* Failed payments are also saved, with the status `FAILED`, and they do not
  increase the total collection.

---

## 10. Exception Handling Summary

### The three custom exceptions

| Exception | Thrown when | Thrown from |
|---|---|---|
| `InvalidRFIDException` | tag is not in the `RFID####` format, or no vehicle is registered with that tag | `RFIDDevice.simulateScan()`, `TollBooth.searchVehicleByRFID()`, `TollBooth.registerVehicle()` |
| `InvalidPaymentException` | amount ≤ 0, cash given < toll, UPI id without `@`, FASTag balance too low | `CashPayment`, `UPIPayment`, `FastagPayment`, `TollBooth.collectPayment()` |
| `InvalidVehicleException` | registration number not in the form `TN01AB1234`, duplicate vehicle, RFID already used | `TollBooth.registerVehicle()` |

All of them are **checked** exceptions (`extends Exception`), which means the
compiler forces the programmer to use `throws` or `try/catch`. That is why an
invalid RFID or a failed payment can never be silently ignored.

### Keyword usage in this project

| Keyword | Where |
|---|---|
| `try` | `TollBoothManager.start()`, `TollBoothManager.processToll()`, `VehicleProcessingThread.run()`, `TransactionFileManager` (all file methods) |
| `catch` | the same places — different `catch` blocks for each custom exception, plus `NumberFormatException`, `IOException` and `InterruptedException` |
| `finally` | `TollBoothManager.start()` (closes the `Scanner`), `VehicleProcessingThread.run()` (thread sign-off message), `TransactionFileManager.countSavedTransactions()` (closes the `BufferedReader`) |
| `throw` | inside `RFIDDevice.simulateScan()`, the three payment classes and the vehicle number checks |
| `throws` | on `TollBooth.processVehicle()`, `TollBooth.registerVehicle()`, `TollBooth.searchVehicleByRFID()`, `PaymentProcessor.processPayment()`, `TollBoothManager.handleChoice()` |

Wrong input never stops the program:

* typing letters where a number is expected → `NumberFormatException` is caught
  and the question is asked again;
* an unknown RFID → the error is printed and the menu is shown again;
* a failed payment → a `FAILED` record is written and the barrier stays closed;
* a missing / damaged `transactions.txt` → the error is printed, no crash.

---

## 11. Collections Used

```java
// HashMap : the KEY is the RFID tag, the VALUE is the Vehicle object.
// Searching by RFID is almost instant, even with thousands of vehicles.
private final HashMap<String, Vehicle> vehicleDatabase;

// ArrayList : every transaction is added at the end, in the order it happened.
private final ArrayList<TollTransaction> transactions;
```

| Collection | Stored in | Used by |
|---|---|---|
| `HashMap<String, Vehicle>` | `TollBooth.vehicleDatabase` | `registerVehicle()`, `searchVehicleByRFID()`, `displayAllVehicles()` (with `entrySet()`), `getRegisteredVehicles()` |
| `ArrayList<TollTransaction>` | `TollBooth.transactions` | `recordTransaction()`, `displayTransactionHistory()` (with `get(index)`) |
| `ArrayList<Vehicle>` | `TollBoothManager.simulateMultipleVehicles()` | a copy of the registered vehicles for the threads |
| `ArrayList<VehicleProcessingThread>` | `TollBoothManager.simulateMultipleVehicles()` | the thread list that is started and joined |
| `List<TollTransaction>` | `TransactionFileManager.readAllTransactions()` | the transactions read back from the file |

---

## 12. Multithreading Explanation

Menu option **9. Simulate Multiple Vehicles** creates one thread per vehicle:

```java
VehicleProcessingThread thread = new VehicleProcessingThread(tollBooth, vehicle, fastTagUser);
thread.start();          // the JVM calls run() in a NEW thread
...
thread.join();           // the main thread waits for all the vehicle threads
```

Output of two vehicles arriving together:

```text
--------------------------------------------------
[Lane-1] Vehicle TN01AB1234 (Car) is being processed...
--------------------------------------------------
[Lane-1] Toll to be paid : ₹45 (FASTag discount applied)
...
[Lane-1] TN01AB1234 left the toll booth.
[Lane-1] Thread finished.

--------------------------------------------------
[Lane-2] Vehicle TN05IJ7890 (Car) is being processed...
--------------------------------------------------
[Lane-2] Toll to be paid : ₹50 (cash)
...
```

Thread safety: `TollBooth.processVehicle()` and `TollBooth.recordTransaction()`
are declared **`synchronized`**, so only one vehicle can update
`totalCollection`, `totalTransactions` and the barrier at a time. Without
synchronization, two threads adding money to `totalCollection` at the same
moment could lose an update (race condition). The threads also use
`Thread.sleep()` to simulate the arriving and leaving time of a vehicle.

---

## 13. Menu Options

```text
========================================
   IoT BASED TOLL BOOTH MANAGER SYSTEM
========================================

1. Register Vehicle
2. Display Vehicles
3. Process Toll
4. Calculate Toll
5. Make Payment
6. View Transaction History
7. Search Vehicle by RFID
8. Display Toll Booth Details
9. Simulate Multiple Vehicles
10. Exit

Enter your choice :
```

| Option | What it does | Demonstrates |
|---|---|---|
| 1 | Registers a new vehicle (type, number, owner, RFID – automatic or typed) after validating the data | Constructor overloading, exceptions, `HashMap.put` |
| 2 | Lists all the registered vehicles stored in the `HashMap` | Collections (`entrySet`) |
| 3 | The complete 11 step toll workflow: read RFID → validate → find vehicle → show details → calculate toll → choose payment mode → process payment → create transaction → save file → open barrier → close barrier | Every concept at one place |
| 4 | Shows all the overloaded `calculateToll()` results for the vehicle | Method overloading, polymorphism |
| 5 | Payment only (no barrier) — useful to show the payment interface alone | Interface, `printReceipt()` |
| 6 | Shows the history from the `ArrayList`, and optionally reads the saved file | Collections + file reading |
| 7 | Searches a vehicle with the RFID tag | `HashMap.get`, runtime polymorphism |
| 8 | Booth id, location, totals, vehicle count, barrier openings, RFID scans | Encapsulation of the booth data |
| 9 | Starts threads so several vehicles are processed together | Multithreading, `synchronized` |
| 10 | Shows the final summary and closes the application | `finally` block closes the `Scanner` |

Five sample vehicles are loaded automatically at start up, so the project can be
demonstrated immediately:

| RFID | Vehicle number | Type | Owner |
|---|---|---|---|
| RFID1001 | TN01AB1234 | Car | Hemachandran |
| RFID1002 | TN02CD5678 | Bike | Arun Kumar |
| RFID1003 | TN03EF9012 | Truck | Suresh Logistics |
| RFID1004 | TN04GH3456 | Bus | KPN Travels |
| RFID1005 | TN05IJ7890 | Car | Divya Bharathi |

---

## 14. Full Documentation and Viva Preparation

| Document | Content |
|---|---|
| [`docs/OOP_CONCEPTS.md`](docs/OOP_CONCEPTS.md) | Every OOP concept explained with the exact code from this project |
| [`docs/CLASS_EXPLANATIONS.md`](docs/CLASS_EXPLANATIONS.md) | Explanation of every major class, its data members and its methods |
| [`docs/SAMPLE_INPUT_OUTPUT.md`](docs/SAMPLE_INPUT_OUTPUT.md) | Complete sample input and the real output of a full session |
| [`docs/VIVA_QUESTIONS.md`](docs/VIVA_QUESTIONS.md) | 40 questions with short, exam ready answers |

---

## 15. Troubleshooting

| Problem | Reason | Solution |
|---|---|---|
| `javac: command not found` / `'javac' is not recognized` | JDK is not installed or not in the PATH | Install the JDK and add `<JDK>/bin` to the PATH |
| `Could not find or load main class tollbooth.TollBoothManager` | the `-cp` folder is wrong | run from the project folder with `java -cp out tollbooth.TollBoothManager` |
| `package tollbooth.model does not exist` | you compiled a single file from inside a sub folder | run `javac -d out tollbooth/TollBoothManager.java` from the project root |
| The Rupee symbol shows as `?` | the console cannot print Unicode | the program automatically uses `Rs.`; or run with `-Dfile.encoding=UTF-8` (Windows: also `chcp 65001`) |
| `transactions.txt` is missing | the program creates it at the first run, in the current folder | run the program from the project folder, or check the folder shown in option 8 |
| Duplicate transaction ids after a restart | an old file was copied with a different name | the counter resumes from the saved records in `transactions.txt` |

---

### Conclusion

The project applies Object Oriented Programming to a real IoT scenario:
an RFID reader detects the vehicle, the correct child object calculates its own
toll through runtime polymorphism, one of the three payment implementations
collects the money through a single interface, the barrier opens, and the
transaction is stored in an `ArrayList` and in a text file — while custom
exceptions and multithreading keep the system safe and realistic.
