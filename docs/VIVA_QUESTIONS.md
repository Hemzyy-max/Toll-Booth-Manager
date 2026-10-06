# Viva Questions and Answers

40 questions with short, exam-ready answers. The project file name is given in
brackets so the examiner's question can be answered with a real example.

---

## A. Basic OOP questions

**1. What is Object Oriented Programming?**
A programming style in which a program is built from objects. An object is a
combination of **data** (fields) and **behaviour** (methods). OOP has four main
pillars: encapsulation, inheritance, polymorphism and abstraction.

**2. What is a class and what is an object?**
A class is a blueprint (for example `Vehicle`), an object is a real instance of
it created with `new` (for example `new Car("TN01AB1234", ...)`). One class can
create any number of objects.

**3. Explain encapsulation with an example from your project.**
`Vehicle.java` keeps `vehicleNumber`, `ownerName`, `rfidTag` as **private**
fields and gives public getters and setters. So no other class can change the
data directly and the object always stays valid. This is also called data
hiding.

**4. Explain inheritance with an example.**
`class Car extends Vehicle` — a Car IS-A Vehicle, so it automatically gets
`vehicleNumber`, `getOwnerName()`, `displayVehicleDetails()` without rewriting
them. `Car`, `Bike`, `Truck` and `Bus` inherit from the abstract `Vehicle`.

**5. What is polymorphism? Which types of polymorphism are in your project?**
Polymorphism means "one name, many forms".
* **Compile time (static)** — method overloading: `calculateToll()`,
  `calculateToll(boolean)`, `calculateToll(boolean, double)`.
* **Runtime (dynamic)** — method overriding: `Car`, `Bike`, `Truck` and `Bus`
  override `calculateToll()`, so `Vehicle vehicle = new Truck(...)` charges ₹150.

**6. What is dynamic method dispatch?**
When a parent reference holds a child object, the JVM calls the version of the
method that belongs to the **real object**, not to the reference type. This is
how `TollBooth.calculateToll(Vehicle vehicle)` charges ₹50 for a Car and ₹150
for a Truck with the same line of code.

**7. What is abstraction? How is it achieved in Java and in your project?**
Abstraction means showing only the essential behaviour and hiding the
implementation. In Java it is achieved with an **abstract class** and with
**interfaces**. In the project `abstract class Vehicle` declares
`abstract double calculateToll();` without a body; the child classes decide the
actual rate.

**8. What is an interface? How is it different from an abstract class?**

| Point | Interface | Abstract class |
|---|---|---|
| Methods | all abstract (Java 8 also allows default) | abstract + concrete |
| Variables | public static final only | any type |
| Constructor | not allowed | allowed |
| Inheritance | a class can implement many interfaces | a class extends only one class |
| Used in project | `RFIDReader`, `PaymentProcessor` | `Vehicle` |

**9. Why did you use an interface for the payment instead of a class?**
Because the toll booth should not know **how** the money is paid. `TollBooth`
only knows `PaymentProcessor`, so Cash, UPI and FASTag can be added or replaced
without changing the booth. This is loose coupling, and it also allows a new
payment mode later (`CardPayment implements PaymentProcessor`) with zero changes
in the booth.

**10. What is a constructor and what is its use?**
A constructor has the same name as the class, has no return type and is called
automatically when an object is created with `new`. It initialises the object.
Example: `protected Vehicle(String vehicleNumber, String vehicleType, String ownerName, String rfidTag)`.

**11. What is constructor overloading?**
Writing two or more constructors with different parameter lists in the same
class. `Car(vehicleNumber, ownerName, rfidTag, numberOfSeats)` and
`Car(vehicleNumber, ownerName, rfidTag)` — the second one calls the first with
`this(...)`.

**12. What is the difference between `this()` and `super()`?**
`this(...)` calls another constructor of the **same** class.
`super(...)` calls the constructor of the **parent** class and must be the first
statement of the child constructor.

**13. What is method overriding? What rules must be followed?**
Replacing the parent method in the child class with the same name, same
parameter list and the same (or covariant) return type. The access modifier
cannot be more restrictive, and `final` / `static` / `private` methods cannot be
overridden. `@Override` is written above it so the compiler checks it.

**14. Difference between overloading and overriding?**

| Factor | Overloading | Overriding |
|---|---|---|
| Class | same class | parent + child |
| Parameters | different | same |
| Binding | compile time | run time |
| Also called | static polymorphism | dynamic polymorphism |
| Example | `calculateToll()`, `calculateToll(true)` | `Car.calculateToll()` over `Vehicle.calculateToll()` |

**15. Can we override a static method?**
No. Static methods belong to the class, not to the object. They can be hidden,
but not overridden, and they are resolved at compile time.

**16. Why is `Vehicle` abstract? Can you create its object?**
`Vehicle` is abstract because a "general vehicle" has no fixed toll rate — only
a Car, Bike, Truck or Bus has a rate. `new Vehicle(...)` gives a compile-time
error; only the child objects can be created.

**17. What does the `@Override` annotation do?**
It tells the compiler that the method is meant to override a parent method.
If the signature is wrong the compiler shows an error, which prevents a silent
bug.

---

## B. Questions about the project design

**18. Which class is the heart of your project and why?**
`TollBooth.java` (`tollbooth/service`). It stores the booth data, the `HashMap`
of vehicles and the `ArrayList` of transactions, calculates the toll and runs
the complete workflow `processVehicle()` from reading the RFID up to closing the
barrier.

**19. Name the collections used and why.**
* `HashMap<String, Vehicle>` — the RFID tag is the **key**, so searching a
  vehicle is very fast (`get()`), even with thousands of vehicles.
* `ArrayList<TollTransaction>` — transactions are always appended and read in
  order, and an ArrayList gives fast index based access.
Also `ArrayList` / `List` of vehicles and of threads in the manager class.

**20. Why HashMap and not ArrayList for the vehicles?**
Searching the vehicle of a tag happens on every entry. In a `HashMap` the
search is almost independent of the size (hashing), while in an `ArrayList` the
program would compare every vehicle one by one.

**21. Where is the IoT part of the project?**
`RFIDDevice` simulates the RFID antenna that detects the tag (`simulateScan`,
`readRFID`) and `FastagPayment` simulates the automatic RFID based wallet
payment. `Barrier` simulates the gate motor with `Thread.sleep()`.

**22. How does a vehicle get identified?**
The operator (or the thread) gives the tag → `RFIDDevice.simulateScan()` stores
and validates it → `RFIDDevice.readRFID()` returns it → `TollBooth.searchVehicleByRFID()` looks it up in the `HashMap` → the matching `Vehicle` object is returned.

**23. What is the use of `totalTrips` in `Vehicle`?**
It shows mutable object state : `recordTrip()` increases it after every
successful crossing, and it is displayed in option 2, in option 7 and in
`displayVehicleDetails()`.

**24. How many transactions did you store and where?**
In this session : in the `ArrayList<TollTransaction>` inside `TollBooth`, and
permanently in `transactions.txt` through `TransactionFileManager`. Failed
attempts are also saved with the status `FAILED`.

**25. Why is a `FAILED` transaction saved if no money was collected?**
Because the file should record the full activity of the booth (for auditing).
A failed record does not change `totalCollection` and no barrier is opened.

---

## C. Exception handling questions

**26. What are the exceptions created in your project?**
`InvalidRFIDException`, `InvalidPaymentException` and `InvalidVehicleException`
— all extend `Exception` (checked exceptions).

**27. Why did you make them checked exceptions?**
A checked exception forces the programmer to handle it with `try/catch` or to
declare it with `throws`. Therefore an invalid RFID, a failed payment or a wrong
vehicle number can never be ignored silently.

**28. What is the difference between `throw` and `throws`?**
`throw` is used inside a method to actually raise an exception
(`throw new InvalidRFIDException(...)`). `throws` is written in the method
signature to declare that the method may pass an exception to the caller
(`processVehicle(...) throws InvalidRFIDException, InvalidPaymentException`).

**29. When does a `finally` block run?**
Always — whether the `try` block completed normally, threw an exception, or even
after a `return`. It is used to release resources. In the project it closes the
`Scanner` (`TollBoothManager.start()`) and the `BufferedReader`
(`TransactionFileManager.countSavedTransactions()`).

**30. Difference between checked and unchecked exceptions?**
Checked (compile time) exceptions must be handled: `IOException`,
`SQLException`, `InterruptedException` and the three custom exceptions.
Unchecked (runtime) exceptions need not be declared: `ArithmeticException`,
`NumberFormatException`, `NullPointerException`,
`ArrayIndexOutOfBoundsException`, `IllegalArgumentException`.

**31. What happens if the user types letters instead of a menu number?**
`Integer.parseInt()` throws a `NumberFormatException`, the `catch` block prints
`"abc" is not a valid number. Try again.` and the menu asks again — the program
does not crash.

**32. What is the use of `getMessage()`?**
It returns the message passed to the constructor of the exception
(`super(message)`), which is exactly what the program prints in the error
blocks such as `[RFID ERROR] ...`.

---

## D. File handling questions

**33. Which classes did you use for file handling?**
`FileWriter` + `BufferedWriter` for writing and `FileReader` +
`BufferedReader` for reading, inside `TransactionFileManager`. `File` is used to
check existence and to get the absolute path.

**34. Why `BufferedWriter` when you already have `FileWriter`?**
`FileWriter` writes character by character to the disk. `BufferedWriter` collects
them in a memory buffer and writes in one go, which is much faster for many
records.

**35. Why is `new FileWriter(FILE_NAME, true)` used with `true`?**
The second parameter means **append mode**, so the new transaction is added at
the end of the file and the old records are not erased.

**36. What is try-with-resources?**
A `try` block that declares the resource in its brackets:
`try (BufferedReader reader = new BufferedReader(new FileReader(FILE_NAME))) { ... }`.
Java closes the resource automatically, so no `finally` + `close()` is needed.
The project also shows the classic `try/catch/finally` + `close()` style in
`countSavedTransactions()` so both methods are demonstrated.

**37. How do you prevent duplicate transaction ids after restarting the program?**
The file is counted at start up and the static counter continues:
`TollTransaction.resumeCounterFrom(TransactionFileManager.countSavedTransactions());`
So with 3 saved records the next id is `TXN1004`.

---

## E. Multithreading questions

**38. How is multithreading used in your project?**
`VehicleProcessingThread extends Thread` — one thread handles one vehicle.
`TollBoothManager` starts several threads with `start()`, the JVM executes
`run()` in each thread, and `join()` makes the main thread wait for all of them.

**39. What is the difference between `start()` and `run()`?**
`run()` contains the work of the thread but calling it directly runs the code in
the **current** thread (no new thread is created). `start()` creates a new
thread of execution and then the JVM calls `run()` inside it.

**40. Why is `TollBooth.processVehicle()` declared `synchronized`?**
Because several vehicle threads call it at the same time. Inside, the code does
`totalCollection = totalCollection + amount`, which is really read-add-write.
Two threads doing it together can lose an update (race condition).
`synchronized` allows only one thread at a time inside the method, so the shared
data stays correct. The barrier also should serve only one vehicle at a time.

---

## F. Extra questions (frequently asked)

**41. Why is `transactionCounter` static in `TollTransaction`?**
A static field belongs to the class, not to an object. All the transaction
objects share one counter, therefore every transaction gets a unique id
(`TXN1001`, `TXN1002`, ...).

**42. What is the use of the `final` keyword in your code?**
`final` on a field means it can be assigned only once — `transactionId`,
`tollAmount`, `dateTime` and `paymentMode` of `TollTransaction` are `final`, so
a saved transaction can never be edited. `final` on a class method prevents
overriding, and on a variable it makes it a constant
(`public static final double CAR_TOLL = 50.0;`).

**43. Why does `TollRates` have a private constructor?**
It is a **utility class** that contains only constants and static methods, so
there is no reason to create its object. A private constructor makes
`new TollRates()` impossible.

**44. What does `toString()` do and why did you override it?**
`toString()` belongs to the `Object` class and is called automatically when an
object is printed. Overriding it in `Vehicle` and `TollTransaction` prints
useful text instead of something like `tollbooth.model.Car@1b6d3586`.

**45. Why does `Bike` override `calculateToll(boolean)`?**
To show that an **overloaded** method can also be overridden, and because a two
wheeler in this project has no FASTag, so the discount is never applied. In
option 4 all the values of a Bike stay ₹30.

**46. How can you add a new vehicle type (for example a Jeep)?**
Create `Jeep extends Vehicle`, provide the two constructors, override
`calculateToll()` and `getExtraDetails()`, add its rate in `TollRates`, and add
one `case` in `TollBoothManager.createVehicle()` and in the registration menu.
Nothing else changes — this shows the advantage of polymorphism.

**47. How can you add a new payment mode (for example a credit card)?**
Create `CardPayment implements PaymentProcessor` with the three methods, and add
one `case` in `createPaymentProcessor()`. `TollBooth` does not change at all.

**48. Where is the transaction receipt generated?**
`TollTransaction.generateReceipt()` builds the receipt as a `String` (with a
`StringBuilder`) and the caller prints it. Returning a `String` instead of
printing directly makes the method reusable for a printer or a file later.

**49. Why is `StringBuilder` used in the receipt?**
`String` is immutable, so every `+` operation creates a new object.
`StringBuilder` appends to the same buffer, which is faster and uses less
memory when many lines are joined.

**50. What would you improve if you had more time?**
A real MySQL/MySQLite table instead of the text file (with JDBC), a GUI with
Swing or JavaFX, a real serial port reader class implementing `RFIDReader`,
storing the wallet balance per RFID tag, and unit tests with JUnit.

---

## One minute summary for the viva (memorise this)

> "This is the IoT Based Toll Booth Manager System, a Core Java console
> application. A vehicle is detected by the simulated RFID reader, the tag is
> validated and searched in a `HashMap`, the matching `Vehicle` child object
> calculates its own toll through runtime polymorphism, the toll is collected
> through the `PaymentProcessor` interface (Cash, UPI or FASTag), a
> `TollTransaction` object is created with an automatic id, stored in an
> `ArrayList` and appended to `transactions.txt`, and the barrier opens and
> closes. Three custom exceptions keep the program safe from invalid input, and
> `VehicleProcessingThread` processes several vehicles at the same time with
> `synchronized` access to the shared totals."
