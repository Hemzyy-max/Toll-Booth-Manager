# OOP Concepts Used in the Project

Every concept below is shown with the **real code** of this project, followed by a
one line explanation that can be spoken in the viva.

---

## 1. Encapsulation

**Definition** — binding the data (variables) and the code (methods) together in
a class, and hiding the data by making the variables `private`.

**Where** — `tollbooth/model/Vehicle.java`

```java
// ENCAPSULATION : private data members. Outside classes cannot touch these
// variables directly, so a Vehicle object can never be put into an invalid
// state by mistake.
private String vehicleNumber;
private String vehicleType;
private String ownerName;
private String rfidTag;
private int totalTrips;

public String getVehicleNumber() {
    return vehicleNumber;                       // getter : read only access
}

public void setVehicleNumber(String vehicleNumber) {
    // the setter validates the value before storing it
    this.vehicleNumber = requireText(vehicleNumber, "Vehicle number").toUpperCase();
}
```

Encapsulation is used in almost every class:

| Class | Private data | Protection |
|---|---|---|
| `Vehicle` | `vehicleNumber`, `vehicleType`, `ownerName`, `rfidTag`, `totalTrips` | getters + validating setters |
| `TollTransaction` | all 7 fields, the amount and the date are `final` | getters only, `setTransactionStatus()` accepts only `SUCCESS` / `FAILED` |
| `Barrier` | `private boolean open` | only `openBarrier()` / `closeBarrier()` can change it |
| `FastagPayment` | `private double walletBalance` | only `processPayment()` / `rechargeWallet()` change it |
| `TollBooth` | the `HashMap`, the `ArrayList`, both totals | `getRegisteredVehicles()` returns a **copy** of the values, so nobody can change the internal map |

**One line for the viva** — "The data is private and can be used only through
public getters and setters, so the object always keeps a valid state. This is
called data hiding or encapsulation."

---

## 2. Inheritance

**Definition** — one class acquires the fields and methods of another class with
the keyword `extends` (an IS-A relationship).

**Where** — `Car`, `Bike`, `Truck`, `Bus` **extends** `Vehicle`

```java
public class Car extends Vehicle {

    public Car(String vehicleNumber, String ownerName, String rfidTag, int numberOfSeats) {
        super(vehicleNumber, "Car", ownerName, rfidTag);   // parent constructor
        setNumberOfSeats(numberOfSeats);
    }

    @Override
    public double calculateToll() {
        return TollRates.CAR_TOLL;                          // 50 rupees
    }
}
```

A `Car` object can use `getOwnerName()`, `displayVehicleDetails()`,
`recordTrip()` ... even though these methods are written only once in `Vehicle`.

Other inheritance in the project:

| Child | Parent | What is inherited |
|---|---|---|
| `Car`, `Bike`, `Truck`, `Bus` | `Vehicle` | the vehicle data and the common methods |
| `RFIDDevice` | `RFIDReader` (interface) | the method contract (`implements`) |
| `CashPayment`, `UPIPayment`, `FastagPayment` | `PaymentProcessor` (interface) | the method contract (`implements`) |
| `InvalidRFIDException`, `InvalidPaymentException`, `InvalidVehicleException` | `Exception` | `getMessage()`, `printStackTrace()` etc. |
| `VehicleProcessingThread` | `Thread` | `start()`, `sleep()`, `join()`, `getName()` |

**One line for the viva** — "Car, Bike, Truck and Bus inherit the common
properties of Vehicle, so the common code is written only once and reused.
This is code reusability."

---

## 3. Polymorphism

**Definition** — the same method call behaves differently depending on the
object. Java shows it in two forms.

### 3.1 Runtime polymorphism (method overriding)

**Where** — `Vehicle.calculateToll()` and its four overrides

```java
public abstract class Vehicle {
    public abstract double calculateToll();       // the contract
}

public class Car   extends Vehicle { @Override public double calculateToll() { return 50.0;  } }
public class Bike  extends Vehicle { @Override public double calculateToll() { return 30.0;  } }
public class Truck extends Vehicle { @Override public double calculateToll() { return 150.0; } }
public class Bus   extends Vehicle { @Override public double calculateToll() { return 100.0; } }
```

```java
// TollBooth.java
public double calculateToll(Vehicle vehicle) {
    return vehicle.calculateToll();   // which code runs is decided at RUN TIME
}
```

```java
Vehicle vehicle;

vehicle = new Car("TN01AB1234", "Hemachandran", "RFID1001", 5);
System.out.println(vehicle.calculateToll());     // 50  -> Car version

vehicle = new Truck("TN03EF9012", "Suresh Logistics", "RFID1003", 3);
System.out.println(vehicle.calculateToll());     // 150 -> Truck version
```

The compiler only knows the reference type (`Vehicle`). The **JVM** looks at the
real object inside the reference and calls the matching method — that is why it
is called *runtime* polymorphism (dynamic method dispatch).

The same thing happens with the interface:

```java
PaymentProcessor payment;
payment = new CashPayment(100);          // runtime polymorphism with an interface
payment.processPayment(45.0);
payment = new FastagPayment("RFID1001", 1000);
payment.processPayment(45.0);
```

### 3.2 Compile time polymorphism (method overloading)

See concept 7 below.

**One line for the viva** — "The parent reference `Vehicle vehicle` can hold any
child object, and the method of the real object runs. This is runtime
polymorphism or dynamic method dispatch."

---

## 4. Abstraction

**Definition** — showing only the essential behaviour and hiding the
implementation details. It is achieved with an **abstract class** and
**abstract methods**.

**Where** — `tollbooth/model/Vehicle.java`

```java
public abstract class Vehicle {                  // cannot write new Vehicle()

    public abstract double calculateToll();      // no body - children must provide it

    public void displayVehicleDetails() {         // concrete method : written once
        System.out.println("Vehicle Number : " + vehicleNumber);
        ...
        String extraDetails = getExtraDetails();  // asks the child class
        if (!extraDetails.isEmpty()) {
            System.out.println(extraDetails);
        }
    }

    public String getExtraDetails() {
        return "";                                // children override this hook
    }
}
```

Points for the viva:

* `new Vehicle()` is a **compile-time error** — an abstract class is only a
  blueprint.
* The first concrete (non-abstract) child must implement `calculateToll()`,
  otherwise that child must also be declared abstract.
* The design is called the **template method** pattern: the parent fixes the
  steps of `displayVehicleDetails()` and the children add their own details
  through `getExtraDetails()`.

Abstraction is also achieved with interfaces (`RFIDReader`, `PaymentProcessor`)
and with the utility class `TollRates`, which hides how the discount and the
currency format are calculated.

**One line for the viva** — "Vehicle is abstract: it says WHAT every vehicle
must do (calculateToll) but not HOW, and the child classes fill in the details."

---

## 5. Interface

**Definition** — a fully abstract type that only declares method signatures.
A class uses `implements` and must provide all the methods. It gives 100%
abstraction and loose coupling.

**Where** — `tollbooth/device/RFIDReader.java` and
`tollbooth/payment/PaymentProcessor.java`

```java
public interface RFIDReader {
    String readRFID();                      // public and abstract by default
    boolean validateRFID(String rfidTag);
}
```

```java
public interface PaymentProcessor {
    boolean processPayment(double amount) throws InvalidPaymentException;
    void printReceipt();
    String getPaymentMode();
}
```

Implementations:

```java
public class RFIDDevice    implements RFIDReader      { ... }
public class CashPayment   implements PaymentProcessor { ... }
public class UPIPayment    implements PaymentProcessor { ... }
public class FastagPayment implements PaymentProcessor { ... }
```

Why an interface is better than a class here:

* `TollBooth` only knows `PaymentProcessor`. Tomorrow a new mode
  (`CardPayment`, `NetBankingPayment`) can be added without touching
  `TollBooth` — **loose coupling**.
* One class can implement many interfaces, but it can extend only one class.
* The RFID hardware can be replaced by a real reader class
  (`implements RFIDReader`) and the rest of the project does not change.

**One line for the viva** — "An interface is a contract. Cash, UPI and FASTag
all implement PaymentProcessor, so the booth can collect the toll without
knowing which payment object it receives."

---

## 6. Constructor

**Definition** — a special method with the same name as the class, called
automatically when an object is created with `new`. It has no return type.

```java
// parameterised constructor of the parent (called by the children with super)
protected Vehicle(String vehicleNumber, String vehicleType, String ownerName, String rfidTag) {
    setVehicleNumber(vehicleNumber);
    setVehicleType(vehicleType);
    setOwnerName(ownerName);
    setRfidTag(rfidTag);
    this.totalTrips = 0;
}
```

```java
// child constructor : super() gives the common data to Vehicle
public Car(String vehicleNumber, String ownerName, String rfidTag, int numberOfSeats) {
    super(vehicleNumber, "Car", ownerName, rfidTag);
    setNumberOfSeats(numberOfSeats);
}

// constructor overloading + this() chaining
public Car(String vehicleNumber, String ownerName, String rfidTag) {
    this(vehicleNumber, ownerName, rfidTag, DEFAULT_SEATS);
}
```

Other examples: `TollBooth(String boothId, String location)` creates the
`RFIDDevice`, the `Barrier`, the `HashMap` and the `ArrayList`;
`TollTransaction` has three constructors (normal, with status, and the full one
used while reading the file); `TollRates` has a **private** constructor so that
nobody can create its object (utility class).

**One line for the viva** — "A constructor initialises the object. `super()`
passes the common data to the parent constructor and `this()` calls another
constructor of the same class."

---

## 7. Method Overloading (compile time polymorphism)

**Definition** — the same method name with different parameter lists in the same
class. The compiler decides which version to call.

**Where** — `Vehicle.java`

```java
public abstract double calculateToll();                              // 1 parameter list

public double calculateToll(boolean fastTag) {                        // 2
    double baseToll = calculateToll();
    return fastTag ? TollRates.applyFastagDiscount(baseToll) : baseToll;
}

public double calculateToll(boolean fastTag, double discountPercent) { // 3
    double baseToll = calculateToll();
    return fastTag ? TollRates.applyDiscount(baseToll, discountPercent) : baseToll;
}
```

And in `TollBooth.java`:

```java
public double calculateToll(Vehicle vehicle)                                     // 1
public double calculateToll(Vehicle vehicle, boolean fastTag)                    // 2
public double calculateToll(Vehicle vehicle, boolean fastTag, double discount)   // 3
```

Screen output of menu option 4 (Truck):

```text
1. calculateToll()                  -> ₹150
2. calculateToll(true)   [FASTag]   -> ₹135
3. calculateToll(false)  [no tag]   -> ₹150
4. calculateToll(true, 5.0)         -> ₹142.50
```

Rules to remember for the viva:

* Overloading is decided by the **parameter list** (number, type, order) —
  never by the return type.
* Overloading happens in the **same class** (or through inheritance) and is
  resolved at **compile time**.
* The 3 exceptions also show constructor overloading
  (`InvalidRFIDException(String message)` and
  `InvalidRFIDException(String message, String rfidTag)`).

---

## 8. Method Overriding (runtime polymorphism)

**Definition** — a child class replaces a method of the parent class, with the
same name, same parameters and same return type. The child method should carry
the `@Override` annotation.

```java
public class Truck extends Vehicle {
    @Override
    public double calculateToll() {
        return TollRates.TRUCK_TOLL;
    }

    @Override
    public String getExtraDetails() {
        return "Number of Axles : " + numberOfAxles;
    }
}
```

Overriding in the whole project:

| Parent method | Overridden in | Result |
|---|---|---|
| `Vehicle.calculateToll()` | `Car`, `Bike`, `Truck`, `Bus` | ₹50 / ₹30 / ₹150 / ₹100 |
| `Vehicle.calculateToll(boolean)` | `Bike` | a two wheeler never gets the FASTag discount |
| `Vehicle.getExtraDetails()` | all four children | seats / cc / axles / capacity |
| `Object.toString()` | `Vehicle`, `TollTransaction` | readable text instead of a memory address |
| `Thread.run()` | `VehicleProcessingThread` | the work of one vehicle thread |

Overloading vs overriding (a favourite viva question):

| Point | Overloading | Overriding |
|---|---|---|
| Where | same class | parent and child class |
| Parameters | must be different | must be the same |
| Decided | compile time | run time |
| Also called | compile time polymorphism | runtime polymorphism |
| Keyword | not needed | `@Override` recommended |

---

## 9. Exception Handling

**Definition** — handling run time errors so that the application does not
crash, using `try`, `catch`, `finally`, `throw` and `throws`.

### Custom (user defined) checked exceptions

```java
public class InvalidRFIDException extends Exception {
    private final String rfidTag;                        // ENCAPSULATION of the error data

    public InvalidRFIDException(String message) {        // constructor 1
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

| Exception | Situation | Example message |
|---|---|---|
| `InvalidRFIDException` | wrong tag format or unknown tag | `No vehicle is registered with the RFID tag RFID9999` |
| `InvalidPaymentException` | cash < toll, low FASTag balance, wrong UPI id | `Cash given (₹20) is less than the toll amount (₹50). Shortage : ₹30` |
| `InvalidVehicleException` | wrong registration number, duplicate vehicle | `Registration number "BADNUMBER" is not in the valid format` |

### `throw` and `throws`

```java
// THROW : raise the error
public void simulateScan(String rfidTag) throws InvalidRFIDException {
    if (!validateRFID(rfidTag)) {
        throw new InvalidRFIDException("RFID tag format is not valid (expected format : RFID1001)", rfidTag);
    }
    ...
}

// THROWS : warn the caller that the method can fail
public synchronized TollTransaction processVehicle(String rfidTag, PaymentProcessor payment,
                                                   boolean fastTagUser)
        throws InvalidRFIDException, InvalidPaymentException { ... }
```

### `try` / `catch` / `finally`

```java
// TollBoothManager.start()
try {
    while (running) {
        displayMenu();
        int choice = readIntInRange("Enter your choice : ", 1, MENU_EXIT);
        try {
            handleChoice(choice);
        } catch (InvalidRFIDException e) {
            System.out.println("[RFID ERROR] " + e.getMessage());
        } catch (InvalidPaymentException e) {
            System.out.println("[PAYMENT ERROR] " + e.getMessage());
        } catch (NumberFormatException e) {          // wrong input typed by the user
            System.out.println("Please enter a number.");
        }
    }
} catch (NoSuchElementException e) {
    System.out.println("Input stream closed. Closing the toll booth system.");
} finally {
    scanner.close();                                 // finally always runs
}
```

Checked vs unchecked:

* `InvalidRFIDException`, `InvalidPaymentException`, `InvalidVehicleException`,
  `IOException`, `InterruptedException` are **checked** — the compiler forces
  `try/catch` or `throws`.
* `NumberFormatException`, `IllegalArgumentException`,
  `NoSuchElementException` are **unchecked** (children of `RuntimeException`) —
  they are caught as a safety net and to re-ask the question.

**One line for the viva** — "I created three custom checked exceptions so that
an invalid RFID, a failed payment or a wrong vehicle number can never crash the
program; the menu catches them and prints a clear message."

---

## 10. Collections

**Definition** — ready made classes from `java.util` that store groups of
objects with different performance.

```java
// TollBooth.java
/** HashMap : the RFID tag is the KEY -> searching is very fast. */
private final HashMap<String, Vehicle> vehicleDatabase;

/** ArrayList : keeps the transactions in the order they happened. */
private final ArrayList<TollTransaction> transactions;
```

```java
vehicleDatabase.put(rfidTag, vehicle);              // add a vehicle
Vehicle vehicle = vehicleDatabase.get(rfidTag);     // search a vehicle
vehicleDatabase.containsKey(rfidTag);               // duplicate check
vehicleDatabase.size();                             // number of vehicles

for (Map.Entry<String, Vehicle> entry : vehicleDatabase.entrySet()) {
    System.out.println(entry.getKey() + " -> " + entry.getValue().getVehicleNumber());
}

transactions.add(transaction);                      // add a record
transactions.get(0).displayTransaction();           // read by index
for (TollTransaction t : transactions) { ... }       // for-each loop
```

Also used: `List<VehicleProcessingThread> threads` (option 9),
`List<TollTransaction>` (the file reader), `Collection<Vehicle>`
(`getRegisteredVehicles()`).

**One line for the viva** — "HashMap is used for the vehicle database because
the RFID tag is a key and searching becomes very fast, and an ArrayList is used
for the transactions because we always append and then read them in order."

---

## 11. File Handling

**Definition** — storing data permanently in a file so that it is available
after the program is closed.

**Where** — `tollbooth/service/TransactionFileManager.java` → `transactions.txt`

```java
// WRITING with FileWriter + BufferedWriter (try-with-resources)
public static boolean saveTransaction(TollTransaction transaction) {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME, true))) {
        writer.write(transaction.toFileLine());
        writer.newLine();
        return true;
    } catch (IOException e) {
        System.out.println("Could not save the transaction : " + e.getMessage());
        return false;
    }
}
```

```java
// READING with FileReader + BufferedReader
try (BufferedReader reader = new BufferedReader(new FileReader(FILE_NAME))) {
    String line;
    while ((line = reader.readLine()) != null) {
        TollTransaction transaction = TollTransaction.fromFileLine(line);
        if (transaction != null) {
            transactionList.add(transaction);
        }
    }
}
```

The classic style with `finally` is also used in the same class:

```java
BufferedReader reader = null;
try {
    reader = new BufferedReader(new FileReader(file));
    ...
} catch (IOException e) {
    System.out.println("Could not count the records : " + e.getMessage());
} finally {
    try {
        if (reader != null) {
            reader.close();          // finally is the right place to release a resource
        }
    } catch (IOException e) {
        System.out.println("Could not close the file : " + e.getMessage());
    }
}
```

Saved line format:

```text
TXN1001;TN01AB1234;Car;45.00;FASTag;2026-10-06 15:25:43;SUCCESS
```

Extra feature — the transaction id counter continues from the file:

```java
TransactionFileManager.ensureFileExists();
TollTransaction.resumeCounterFrom(TransactionFileManager.countSavedTransactions());
```

**One line for the viva** — "Every transaction is appended to transactions.txt
with FileWriter and BufferedWriter, and option 6 reads the file back with
FileReader and BufferedReader to show the saved history."

---

## 12. Multithreading

**Definition** — running more than one task at the same time inside one program.
Each task is a thread.

**Where** — `tollbooth/thread/VehicleProcessingThread.java`

```java
public class VehicleProcessingThread extends Thread {

    private final TollBooth tollBooth;
    private final Vehicle vehicle;
    private final boolean fastTagUser;

    public VehicleProcessingThread(TollBooth tollBooth, Vehicle vehicle, boolean fastTagUser) {
        super("Lane-" + (++threadCounter));       // Thread(String name)
        this.tollBooth = tollBooth;
        this.vehicle = vehicle;
        this.fastTagUser = fastTagUser;
    }

    @Override
    public void run() {                            // the work of the thread
        synchronized (tollBooth) {                 // one vehicle at a time
            try {
                Thread.sleep(350);                 // simulate the vehicle arriving
                double toll = tollBooth.calculateToll(vehicle, fastTagUser);
                PaymentProcessor payment = fastTagUser
                        ? new FastagPayment(vehicle.getRfidTag(), 1000.0)
                        : new CashPayment(toll + 100.0);
                tollBooth.processVehicle(vehicle.getRfidTag(), payment, fastTagUser);
                Thread.sleep(250);                 // vehicle leaves the lane
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (InvalidRFIDException | InvalidPaymentException e) {
                System.out.println("Problem : " + e.getMessage());
            } finally {
                System.out.println("[" + getName() + "] Thread finished.");
            }
        }
    }
}
```

```java
// TollBoothManager.simulateMultipleVehicles()
for (VehicleProcessingThread thread : threads) {
    thread.start();          // create the new thread and run run() inside it
}
for (VehicleProcessingThread thread : threads) {
    thread.join();           // the main thread waits for every vehicle
}
```

Synchronization in `TollBooth`:

```java
public synchronized TollTransaction processVehicle(...)     // only one thread inside
public synchronized TollTransaction recordTransaction(...)  // protected totals
```

Why `synchronized` is needed: `totalCollection = totalCollection + amount` is
three steps (read, add, write). If two threads do it at the same moment, one
update is lost (race condition). `synchronized` allows only one thread at a time
inside the method, so the totals stay correct.

Thread life cycle shown in the project: **NEW** (object created) →
**RUNNABLE** (`start()`) → **TIMED_WAITING** (`sleep(350)`) →
**TERMINATED** (end of `run()`). Option 9 prints the final state.

**One line for the viva** — "Each vehicle is handled by one thread, so several
vehicles can be processed at the same time. `TollBooth.processVehicle()` is
synchronized so that the shared total collection is not corrupted."

---

## Quick concept → file map

| Concept | Primary file | Secondary file |
|---|---|---|
| Encapsulation | `model/Vehicle.java` | `device/Barrier.java`, `payment/FastagPayment.java`, `service/TollBooth.java` |
| Inheritance | `model/Car.java` | `model/Bike.java`, `Truck.java`, `Bus.java`, `thread/VehicleProcessingThread.java` |
| Polymorphism | `model/Vehicle.java` | `service/TollBooth.java`, `TollBoothManager.java` |
| Abstraction | `model/Vehicle.java` | `model/TollRates.java` |
| Interface | `payment/PaymentProcessor.java` | `device/RFIDReader.java` and their implementations |
| Constructor | `model/Vehicle.java` | `model/TollTransaction.java`, `service/TollBooth.java` |
| Overloading | `model/Vehicle.java` (`calculateToll` x3) | `service/TollBooth.java`, the exceptions |
| Overriding | `model/Car.java` | all children, `thread/VehicleProcessingThread.java` |
| Exception handling | `exception/*.java` | `service/TollBooth.java`, `TollBoothManager.java` |
| Collections | `service/TollBooth.java` | `TollBoothManager.java`, `service/TransactionFileManager.java` |
| File handling | `service/TransactionFileManager.java` | `model/TollTransaction.java` (`toFileLine`, `fromFileLine`) |
| Multithreading | `thread/VehicleProcessingThread.java` | `service/TollBooth.java` (`synchronized`) |
