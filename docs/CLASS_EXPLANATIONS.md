# Explanation of Every Major Class

This document explains each class of the project in the order of the package
structure: what it does, its data members, its important methods and the OOP
concepts it demonstrates.

---

## Package `tollbooth.model` — the data of the project

### 1. `Vehicle` (abstract parent class)

**Purpose** — the blueprint of every vehicle of the toll plaza. It holds the
information that is common to all vehicles and declares the method that every
vehicle must implement.

**Data members (all private → encapsulation)**

| Field | Type | Example |
|---|---|---|
| `vehicleNumber` | `String` | `TN01AB1234` |
| `vehicleType` | `String` | `Car` |
| `ownerName` | `String` | `Hemachandran` |
| `rfidTag` | `String` | `RFID1001` |
| `totalTrips` | `int` | `3` |

**Methods**

| Method | Meaning |
|---|---|
| `Vehicle(...)` (protected constructor) | initialises the four common values; `protected` because only a child class may call it |
| `abstract double calculateToll()` | the contract : every child gives its own rate |
| `double calculateToll(boolean fastTag)` | overloaded version with the FASTag discount |
| `double calculateToll(boolean fastTag, double discountPercent)` | overloaded version with any discount |
| `void displayVehicleDetails()` | prints the common details + `getExtraDetails()` |
| `String getExtraDetails()` | hook that children override (seats / cc / axles / capacity) |
| `void recordTrip()` | increases `totalTrips` after a successful crossing |
| getters / setters | controlled access to the private fields; the setters reject empty text and the vehicle number is stored in capital letters |
| `String toString()` | readable text instead of `tollbooth.model.Car@1b6d3586` |

**Concepts** — abstraction (abstract class and abstract method), encapsulation
(private fields, validating setters), overloading (three `calculateToll`
methods), overriding (children replace them), constructor.

---

### 2. `Car`, `3. Bike`, `4. Truck`, `5. Bus` (child classes)

**Purpose** — each one adds its own data and its own toll.

| Class | Extra field | Default | Toll | Extra detail shown on screen |
|---|---|---|---|---|
| `Car` | `numberOfSeats` | 5 | ₹50 | Number of Seats |
| `Bike` | `engineCapacityCc` | 150 | ₹30 | Engine Capacity (and no FASTag discount) |
| `Truck` | `numberOfAxles` | 2 | ₹150 | Number of Axles |
| `Bus` | `passengerCapacity` | 45 | ₹100 | Passenger Capacity |

**Common structure of every child class**

```java
public class Car extends Vehicle {
    private static final int DEFAULT_SEATS = 5;
    private int numberOfSeats;                                  // its own data

    public Car(String vehicleNumber, String ownerName, String rfidTag, int numberOfSeats) {
        super(vehicleNumber, "Car", ownerName, rfidTag);         // INHERITANCE
        setNumberOfSeats(numberOfSeats);
    }

    public Car(String vehicleNumber, String ownerName, String rfidTag) {
        this(vehicleNumber, ownerName, rfidTag, DEFAULT_SEATS);  // CONSTRUCTOR OVERLOADING
    }

    @Override
    public double calculateToll() {                              // METHOD OVERRIDING
        return TollRates.CAR_TOLL;
    }

    @Override
    public String getExtraDetails() {
        return "Number of Seats: " + numberOfSeats;
    }
}
```

`Bike` deserves a special mention: it **overrides the overloaded method**
`calculateToll(boolean fastTag)` and simply returns the normal bike toll, because
a two wheeler does not carry a FASTag. It proves that overriding works for every
version of an overloaded method, and it can be seen on the screen in menu
option 4 (all the values stay ₹30 for a Bike).

**Concepts** — inheritance, constructor overloading, `super()` and `this()`
chaining, method overriding, runtime polymorphism.

---

### 6. `TollRates` (utility class)

**Purpose** — one single place for every money rule of the project, so a rate
can be changed in one line.

```java
public static final double BIKE_TOLL  = 30.0;
public static final double CAR_TOLL   = 50.0;
public static final double BUS_TOLL   = 100.0;
public static final double TRUCK_TOLL = 150.0;
public static final double FASTAG_DISCOUNT_PERCENT = 10.0;
public static final String CURRENCY = "\u20B9";      // the Rupee symbol
```

| Method | Meaning |
|---|---|
| `applyDiscount(amount, percent)` | amount after discount, rounded to 2 decimals |
| `applyFastagDiscount(amount)` | shortcut for the 10% FASTag discount |
| `roundToTwoDecimals(value)` | money rounding helper |
| `formatAmount(amount)` | prints `₹50` for a whole amount and `₹142.50` otherwise |
| `displayRateCard()` | prints the rate card shown at start up |
| `private TollRates()` | private constructor : nobody can create an object of this class |

The `CURRENCY` value is chosen at run time: if the console of the computer
cannot print the Rupee symbol (many Windows command prompts cannot), the program
automatically uses `Rs.` so the output never shows a broken character.

**Concepts** — encapsulation (private constructor, constants), abstraction (the
rest of the project only calls `formatAmount` / `applyDiscount`).

---

### 7. `TollTransaction`

**Purpose** — one complete toll record, exactly the fields asked in the project
requirement.

| Field | Type | Example |
|---|---|---|
| `transactionId` | `String` (final) | `TXN1001` |
| `vehicleNumber` | `String` (final) | `TN01AB1234` |
| `vehicleType` | `String` (final) | `Car` |
| `tollAmount` | `double` (final) | `45.0` |
| `paymentMode` | `String` (final) | `FASTag` |
| `dateTime` | `LocalDateTime` (final) | `2026-10-06T15:25:43` |
| `transactionStatus` | `String` | `SUCCESS` / `FAILED` |

**Important methods**

| Method | Meaning |
|---|---|
| three constructors | normal, with status, and the full one used by the file reader |
| `private static String nextTransactionId()` | `"TXN" + (++transactionCounter)` → `TXN1001`, `TXN1002` ... |
| `static void resumeCounterFrom(int)` | continues the ids from the records already saved in the file |
| `displayTransaction()` | one compact line for the history report |
| `generateReceipt()` | the receipt block printed after a successful payment |
| `toFileLine()` | one line for `transactions.txt` |
| `static TollTransaction fromFileLine(String)` | builds the object back from a saved line (returns `null` for a damaged line) |
| `getFormattedDateTime()` | `dd-MM-yyyy HH:mm:ss` for the screen |
| `toString()` | overridden for readable debug output |

`transactionCounter` is **static**, so all the objects of this class share one
counter and two transactions can never get the same id.

**Concepts** — encapsulation (final fields, getters, validated status setter),
static members, constructor overloading, method overriding.

---

## Package `tollbooth.payment` — the payment system

### 8. `PaymentProcessor` (interface)

```java
public interface PaymentProcessor {
    boolean processPayment(double amount) throws InvalidPaymentException;
    void printReceipt();
    String getPaymentMode();
}
```

It is the contract of every payment mode. `TollBooth` works only with this
interface, therefore a new mode can be added later without changing `TollBooth`.

### 9. `CashPayment`

| Member | Meaning |
|---|---|
| `cashTendered` | cash given by the driver |
| `changeReturned` | balance returned |
| `paymentDone` | did the payment succeed ? |
| `processPayment(amount)` | fails with `InvalidPaymentException` when the cash is less than the toll; otherwise computes the change and returns `true` |
| `printReceipt()` | cash slip (mode, paid, change, status) |
| `getPaymentMode()` | `"Cash"` |

### 10. `UPIPayment`

Takes a UPI id in the constructor, validates it (it must contain `@` and must
not start or end with `@`), creates a reference number such as `UPIREF481920`
and returns `true`.

### 11. `FastagPayment`

The IoT payment : the RFID tag of the vehicle is linked to a prepaid wallet.

| Member | Meaning |
|---|---|
| `rfidTag` | the FASTag / RFID tag |
| `walletBalance` | money available, private |
| `processPayment(amount)` | deducts the amount, or throws when the balance is not enough |
| `rechargeWallet(amount)` | adds money to the wallet |
| `getPaymentMode()` | `"FASTag"` |

All three classes throw the **same** `InvalidPaymentException`, so the caller
needs only one `catch` block for any payment mode — that is the strength of
programming against an interface.

**Concepts** — interface implementation, polymorphism, encapsulation, exception
handling.

---

## Package `tollbooth.device` — the IoT hardware simulation

### 12. `RFIDReader` (interface)

```java
public interface RFIDReader {
    String readRFID();
    boolean validateRFID(String rfidTag);
}
```

### 13. `RFIDDevice`

The software form of the RFID antenna of the toll plaza.

| Member | Meaning |
|---|---|
| `RFID_PATTERN` | `^RFID\d{4}$` — a valid tag is `RFID` + exactly 4 digits |
| `tagSerialCounter` (static) | used by `generateNewTag()` → `RFID1006`, `RFID1007` ... |
| `lastScannedTag` | the tag currently detected by the antenna |
| `totalScans` | how many tags were read |
| `simulateScan(rfidTag)` | the antenna "detects" the tag; wrong format → `throw new InvalidRFIDException(...)` |
| `readRFID()` | returns the detected tag (interface method) |
| `validateRFID(rfidTag)` | format check (interface method) |
| `generateNewTag()` | creates the next free tag for a new registration |
| `syncTagSerial(rfidTag)` | keeps the counter above the tags typed by the user, so no duplicate tag is generated |

IoT behaviour: the device stores the tag, the program reads it, validates it and
then asks the `HashMap` for the matching vehicle — exactly the way a real
serial/USB RFID reader delivers a tag id to the computer.

### 14. `Barrier`

| Member | Meaning |
|---|---|
| `barrierId` | for example `BOOTH-TN01-BARRIER` |
| `private boolean open` | current hardware state (encapsulation) |
| `totalOpenings` | count of the openings |
| `openBarrier()` | prints `Barrier Opening...`, sleeps 400 ms (motor), sets `open = true`, prints `Barrier OPEN` |
| `closeBarrier()` | same in the other direction, prints `Barrier CLOSED` |
| `simulateMotorDelay()` | private helper with `Thread.sleep()`; handles `InterruptedException` and restores the interrupt flag |

**Concepts** — encapsulation, abstraction, exception handling and a small taste
of multithreading (`Thread.sleep`).

---

## Package `tollbooth.exception` — user defined exceptions

### 15–17. `InvalidRFIDException`, `InvalidPaymentException`, `InvalidVehicleException`

All three follow the same pattern:

```java
public class InvalidRFIDException extends Exception {

    private static final long serialVersionUID = 1L;
    private final String rfidTag;                       // extra information about the error

    public InvalidRFIDException(String message) {       // constructor 1
        super(message);
        this.rfidTag = "";
    }

    public InvalidRFIDException(String message, String rfidTag) {   // constructor 2
        super(message);
        this.rfidTag = rfidTag;
    }

    public String getRfidTag() {
        return rfidTag;
    }
}
```

| Exception | Extra information captured | Thrown by |
|---|---|---|
| `InvalidRFIDException` | the rejected tag | `RFIDDevice.simulateScan()`, `TollBooth.searchVehicleByRFID()`, `TollBooth.registerVehicle()` |
| `InvalidPaymentException` | the amount that failed | `CashPayment`, `UPIPayment`, `FastagPayment`, `TollBooth.collectPayment()` |
| `InvalidVehicleException` | the vehicle number | `TollBooth.registerVehicle()` |

**Concepts** — exception handling, inheritance from `Exception`, constructor
overloading, encapsulation of the error data.

---

## Package `tollbooth.service` — the business logic

### 18. `TollBooth` (the most important class)

**Purpose** — it coordinates the complete toll process. It is the class that
connects the model, the devices, the payments and the file.

**Data members (all private)**

```java
private final String boothId;                              // BOOTH-TN01
private String location;                                   // Chennai Bypass Toll Plaza
private int totalTransactions;                             // successful crossings
private double totalCollection;                            // money collected
private final HashMap<String, Vehicle> vehicleDatabase;    // RFID -> Vehicle
private final ArrayList<TollTransaction> transactions;     // history
private final RFIDDevice rfidDevice;                       // HAS-A relationship
private final Barrier barrier;                             // HAS-A relationship
```

**Methods**

| Method | What it does | Concept |
|---|---|---|
| `TollBooth(boothId, location)` | creates the device, the barrier, the collections; ensures `transactions.txt` exists and resumes the id counter | constructor |
| `registerVehicle(Vehicle)` | 4 business validations, then `vehicleDatabase.put(...)`; `throws InvalidVehicleException, InvalidRFIDException` | encapsulation, collections, exceptions |
| `searchVehicleByRFID(rfidTag)` | validates the format and does `vehicleDatabase.get(key)` | `HashMap.get`, exceptions |
| `displayAllVehicles()` | iterates the `HashMap` with `entrySet()` | collections |
| `displayTransactionHistory()` | iterates the `ArrayList` with `get(index)` | collections |
| `displayBoothDetails()` | booth id, location, counts, collection, barrier state, file path | encapsulation |
| `calculateToll(Vehicle)` | `return vehicle.calculateToll();` | **runtime polymorphism** |
| `calculateToll(Vehicle, boolean)` | FASTag version | overloading |
| `calculateToll(Vehicle, boolean, double)` | custom discount version | overloading |
| `processVehicle(rfid, payment, fastTagUser)` | the complete 11 step workflow, `synchronized` | everything together |
| `collectPayment(payment, amount)` | calls the interface method `processPayment()` | interface |
| `recordTransaction(vehicle, amount, mode, status)` | creates the object, adds it to the `ArrayList`, updates the totals, appends to the file; `synchronized` | collections + file + synchronization |
| `recordFailedTransaction(...)` | stores the attempt with status `FAILED`, no money added | exception handling |
| `openBarrier()`, `closeBarrier()` | delegate to the `Barrier` object | delegation |
| `getRegisteredVehicles()` | returns a **copy** of the vehicle list | encapsulation |

**The 11 steps inside `processVehicle()`**

| Step | Code |
|---|---|
| 1. Read RFID | `rfidDevice.simulateScan(rfidTag);` |
| 2. Validate RFID | `if (!rfidDevice.validateRFID(detectedTag)) throw new InvalidRFIDException(...);` |
| 3. Find the vehicle | `Vehicle vehicle = searchVehicleByRFID(detectedTag);` |
| 4. Display the details | the `RFID DETECTED` block + `vehicle.displayVehicleDetails()` |
| 5. Calculate the toll | `calculateToll(vehicle)` and the overloaded FASTag version |
| 6–7. Collect the payment | `collectPayment(payment, payableToll);` |
| 8. Create the transaction | `new TollTransaction(...)` (automatic id and date) |
| 9. Save the transaction | `recordTransaction(...)` → `ArrayList` + `transactions.txt` |
| 10. Open the barrier | `openBarrier();` then `Vehicle Passed Successfully.` |
| 11. Close the barrier | `closeBarrier();` |

### 19. `TransactionFileManager` (utility class)

| Method | Concept shown |
|---|---|
| `ensureFileExists()` | `FileWriter` + `BufferedWriter` with **try-with-resources** |
| `saveTransaction(transaction)` | append mode (`new FileWriter(FILE_NAME, true)`) |
| `readAllTransactions()` | `FileReader` + `BufferedReader`, `readLine()` loop, parsing with `fromFileLine()` |
| `countSavedTransactions()` | the **classic try / catch / finally** style with an explicit `close()` |
| `displaySavedTransactions()` | report of the saved records with the file path |
| `getFilePath()` | full path shown in option 8 |

`IOException` is always caught and reported, so a file problem never crashes the
application. `private TransactionFileManager()` prevents creating an object of
this utility class.

---

## Package `tollbooth.thread` — multithreading

### 20. `VehicleProcessingThread`

| Member | Meaning |
|---|---|
| `tollBooth` | the shared booth object |
| `vehicle` | the vehicle handled by this thread |
| `fastTagUser` | does this driver pay with FASTag ? |
| `VehicleProcessingThread(...)` | calls `super("Lane-" + (++threadCounter))` to name the thread |
| `run()` | the work : arrival message → `sleep(350)` → toll calculation → payment object → `tollBooth.processVehicle(...)` → `sleep(250)` → `finally` message |
| `synchronized (tollBooth)` | only one vehicle inside the booth at a time |

Handled exceptions inside the thread: `InterruptedException` (the thread was
interrupted while sleeping), `InvalidRFIDException` and `InvalidPaymentException`
(the vehicle is rejected, the other threads continue normally).

---

## The main class

### 21. `TollBoothManager`

**Purpose** — the user interface of the application. It shows the menu, reads
the input with `Scanner` and calls the `TollBooth` methods.

| Field | Meaning |
|---|---|
| `BOOTH_ID`, `BOOTH_LOCATION` | constants of the booth |
| `private final TollBooth tollBooth` | the object that holds all the data |
| `private final Scanner scanner` | keyboard input |
| `private boolean running` | keeps the menu loop alive until option 10 |

| Method | Menu option |
|---|---|
| `main(String[] args)` | creates the object and calls `start()` |
| `start()` | banner, sample data, menu loop with `try / catch / finally` |
| `loadSampleVehicles()` | registers RFID1001 … RFID1005 so the demo is ready |
| `displayMenu()` | the 10 option menu |
| `handleChoice(choice)` | `switch` to the right method, `throws` the custom exceptions |
| `registerVehicle()` | option 1 |
| `createVehicle(...)` | factory method that builds a `Car` / `Bike` / `Truck` / `Bus` |
| `processToll()` | option 3, the complete workflow |
| `calculateTollOnly()` | option 4, shows the overloaded methods |
| `makePayment()` | option 5, payment without the barrier |
| `viewTransactionHistory()` | option 6, memory list + saved file |
| `searchVehicleByRFID()` | option 7 |
| `simulateMultipleVehicles()` | option 9, starts the threads and `join()`s them |
| `exitApplication()` | option 10, final summary and stops the loop |
| `readLine()`, `readIntInRange()`, `readDouble()`, `parsePositiveInt()` | safe input helpers that never crash on a wrong input |

The class returns `Vehicle` and `PaymentProcessor` types from its helper
methods, but the real objects are `Car`, `Truck`, `CashPayment`, `UPIPayment` or
`FastagPayment` — the clearest example of **polymorphism** in the project.

---

## How the classes work together (one toll crossing)

```text
TollBoothManager (menu, Scanner)
   │  reads the RFID typed by the operator
   ▼
TollBooth.processVehicle(rfid, payment, fastTagUser)     [synchronized]
   │
   ├─► RFIDDevice.simulateScan() + validateRFID()   (device package, interface RFIDReader)
   │
   ├─► vehicleDatabase.get(rfid)  ─────────────►  Car / Bike / Truck / Bus  (model package)
   │                                                     │
   │                                             calculateToll()  ← runtime polymorphism
   │
   ├─► payment.processPayment(amount)  ───────►  CashPayment / UPIPayment / FastagPayment
   │                                             (payment package, interface PaymentProcessor)
   │
   ├─► new TollTransaction(...)  ─────────────►  model package (automatic TXN id)
   │
   ├─► transactions.add(...)  +  TransactionFileManager.saveTransaction(...)
   │        ArrayList                 transactions.txt
   │
   └─► Barrier.openBarrier() → Vehicle Passed Successfully → Barrier.closeBarrier()
```

The same `processVehicle()` method is used by the menu (option 3) and by every
thread of option 9, which is why the project needs only one implementation of
the toll workflow.
