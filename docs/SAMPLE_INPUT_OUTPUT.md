# Sample Input and Sample Output

This document contains the complete sample input (what the operator types) and
the real output of the application.
The text typed by the user is shown **after each prompt** in the same line, the
way it appears on a real terminal.

---

## 1. Sample input (one full demonstration session)

| Step | Typed input | Purpose |
|---|---|---|
| 1 | `3` | menu → Process Toll |
| 2 | `RFID1001` | the car of Hemachandran |
| 3 | `3` | pay with FASTag |
| 4 | `1000` | FASTag wallet balance |
| 5 | `4` | menu → Calculate Toll |
| 6 | `RFID1003` | the truck |
| 7 | *(Enter)* | default 5% discount |
| 8 | `2` | menu → Display Vehicles |
| 9 | `5` | menu → Make Payment |
| 10 | `RFID1004` | the bus |
| 11 | `2` | pay with UPI |
| 12 | *(Enter)* | default UPI id `student@upi` |
| 13 | `1` | menu → Register Vehicle |
| 14 | `2` | type = Bike |
| 15 | `TN06PQ3456` | number of the new bike |
| 16 | `Karthik Raja` | owner of the new bike |
| 17 | *(Enter)* | automatic RFID tag |
| 18 | `180` | engine capacity in cc |
| 19 | `7` | menu → Search Vehicle by RFID |
| 20 | `RFID1002` | the existing bike |
| 21 | `9` | menu → Simulate Multiple Vehicles |
| 22 | `3` | three vehicles arrive together |
| 23 | `6` | menu → View Transaction History |
| 24 | `y` | also open `transactions.txt` |
| 25 | `8` | menu → Display Toll Booth Details |
| 26 | `10` | Exit |

The same list as it is typed (one value per line) is kept in the file
explanation below, so a student can re-run the demonstration exactly.

---

## 2. Start up screen

```text
New transaction file created : /home/user/Toll-Booth-Manager/transactions.txt
========================================
   IoT BASED TOLL BOOTH MANAGER SYSTEM
========================================
 Booth Id : BOOTH-TN01
 Location : Chennai Bypass Toll Plaza
 (Console application - Core Java only)
========================================
---------------------------------------
            TOLL RATE CARD
---------------------------------------
Bike  (2 wheeler) : ₹30
Car   (4 wheeler) : ₹50
Bus               : ₹100
Truck / Lorry     : ₹150
FASTag discount   : 10%
---------------------------------------

Loading sample vehicles into the HashMap database...
Vehicle registered successfully.
RFID Tag       : RFID1001
Vehicle Number : TN01AB1234
Vehicle Type   : Car
Vehicle registered successfully.
RFID Tag       : RFID1002
Vehicle Number : TN02CD5678
Vehicle Type   : Bike
Vehicle registered successfully.
RFID Tag       : RFID1003
Vehicle Number : TN03EF9012
Vehicle Type   : Truck
Vehicle registered successfully.
RFID Tag       : RFID1004
Vehicle Number : TN04GH3456
Vehicle Type   : Bus
Vehicle registered successfully.
RFID Tag       : RFID1005
Vehicle Number : TN05IJ7890
Vehicle Type   : Car
5 sample vehicles are ready. Use RFID1001 ... RFID1005.
```

---

## 3. Main menu (option 3 → Process Toll, paid with FASTag)

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

Enter your choice : 3

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

Toll process finished for TN01AB1234.
```

The same record is appended to `transactions.txt`:

```text
TXN1001;TN01AB1234;Car;45.00;FASTag;2026-10-06 15:25:43;SUCCESS
```

---

## 4. Option 4 → Calculate Toll (shows method overloading)

```text
Enter your choice : 4

========================================
            TOLL CALCULATION
========================================

Enter RFID : RFID1003
---------------------------------------
Vehicle Number : TN03EF9012
Owner Name     : Suresh Logistics
Vehicle Type   : Truck
RFID Tag       : RFID1003
Toll Rate      : ₹150
Trips Completed: 0
Number of Axles : 3
---------------------------------------
Toll calculation for a Truck using the OVERLOADED methods :

1. calculateToll()                  -> ₹150
2. calculateToll(true)   [FASTag]   -> ₹135
3. calculateToll(false)  [no tag]   -> ₹150
Enter a discount % for calculateToll(true, discount) (ENTER for 5) :
4. calculateToll(true, 5.0)         -> ₹142.50

NOTE : the same method name calculateToll() is used with different
       parameter lists. The compiler picks the version to call :
       this is COMPILE TIME POLYMORPHISM (method overloading).
```

---

## 5. Option 5 → Make Payment (UPI, no barrier)

```text
Enter your choice : 5

========================================
      TOLL PAYMENT (payment only)
========================================

Enter RFID : RFID1004
Vehicle : TN04GH3456 | Normal toll : ₹100

Select Payment Mode:
1. Cash
2. UPI
3. FASTag
Enter choice : 2

Enter the UPI id (ENTER for student@upi) : student@upi
UPI payment processing...
UPI id         : student@upi
Reference No   : UPIREF798506
Amount debited : ₹100

Payment Successful!
--------- UPI PAYMENT SLIP --------
Payment Mode : UPI
UPI Id       : student@upi
Reference No : UPIREF798506
Amount Paid  : ₹100
Status       : SUCCESS
-----------------------------------
Payment recorded as TXN1002 (barrier not used in this option).
```

---

## 6. Option 1 → Register Vehicle (new bike, automatic RFID)

```text
Enter your choice : 1

========================================
          VEHICLE REGISTRATION
========================================

Select the vehicle type :
1. Car   (₹50)
2. Bike  (₹30)
3. Truck (₹150)
4. Bus   (₹100)
Enter the type (1-4) : 2
Enter the vehicle number (example TN06KL2345) : TN06PQ3456
Enter the owner name : Karthik Raja
Press ENTER to get an automatic RFID tag, or type your own tag (example RFID1006).
Enter the RFID tag :
Automatic RFID tag generated : RFID1006
Engine capacity in cc (ENTER for the default value 150) : 180
Vehicle registered successfully.
RFID Tag       : RFID1006
Vehicle Number : TN06PQ3456
Vehicle Type   : Bike
```

Wrong data is rejected politely (the program keeps running):

```text
Enter the vehicle number (example TN06KL2345) : BADNUMBER
Enter the owner name : Test Owner

[VEHICLE ERROR] Registration number "BADNUMBER" is not in the valid format (example : TN01AB1234).
```

```text
[VEHICLE ERROR] RFID tag RFID1001 is already used by vehicle TN01AB1234.
```

---

## 7. Option 2 → Display Vehicles and option 7 → Search Vehicle by RFID

```text
Enter your choice : 2

========================================
        REGISTERED VEHICLES
========================================
1. TN01AB1234 | Car | Owner : Hemachandran | RFID : RFID1001 | Trips : 1
2. TN05IJ7890 | Car | Owner : Divya Bharathi | RFID : RFID1005 | Trips : 0
3. TN04GH3456 | Bus | Owner : KPN Travels | RFID : RFID1004 | Trips : 1
4. TN03EF9012 | Truck | Owner : Suresh Logistics | RFID : RFID1003 | Trips : 0
5. TN02CD5678 | Bike | Owner : Arun Kumar | RFID : RFID1002 | Trips : 0
6. TN06PQ3456 | Bike | Owner : Karthik Raja | RFID : RFID1006 | Trips : 0
----------------------------------------
Total registered vehicles : 6

Enter your choice : 7

========================================
         SEARCH VEHICLE BY RFID
========================================

Enter the RFID tag : RFID1002
Vehicle found in the HashMap database.
---------------------------------------
Vehicle Number : TN02CD5678
Owner Name     : Arun Kumar
Vehicle Type   : Bike
RFID Tag       : RFID1002
Toll Rate      : ₹30
Trips Completed: 0
Engine Capacity : 150 cc (no FASTag for two wheelers)
---------------------------------------
POLYMORPHISM : the same display method shows the extra details of a Bike only.
```

An unknown tag produces a clean error message:

```text
Enter the RFID tag : RFID9999

[RFID ERROR] No vehicle is registered with the RFID tag RFID9999. Please register the vehicle first.
             Rejected tag : RFID9999
The transaction was cancelled. The system is still running.
```

---

## 8. Option 9 → Simulate Multiple Vehicles (multithreading)

```text
Enter your choice : 9

========================================
   MULTIPLE VEHICLES AT THE SAME TIME
========================================

How many vehicles arrive together (1-4) : 3
Starting 3 vehicle processing threads...
(Each thread handles one vehicle. TollBooth.processVehicle() is
 synchronized, so the shared totals stay correct.)

--------------------------------------------------
[Lane-1] Vehicle TN01AB1234 (Car) is being processed...
--------------------------------------------------
[Lane-1] Toll to be paid : ₹45 (FASTag discount applied)

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
Transaction ID : TXN1003
Vehicle Number : TN01AB1234
Vehicle Type   : Car
Toll Amount    : ₹45
Payment Mode   : FASTag
Date & Time    : 06-10-2026 15:25:45
Status         : SUCCESS
========================================
Barrier Opening...
Barrier OPEN
Vehicle Passed Successfully.
Closing Barrier...
Barrier CLOSED
[Lane-1] TN01AB1234 left the toll booth.
[Lane-1] Thread finished.

--------------------------------------------------
[Lane-2] Vehicle TN05IJ7890 (Car) is being processed...
--------------------------------------------------
[Lane-2] Toll to be paid : ₹50 (cash)
...
[Lane-2] Thread finished.

--------------------------------------------------
[Lane-3] Vehicle TN04GH3456 (Bus) is being processed...
--------------------------------------------------
[Lane-3] Toll to be paid : ₹90 (FASTag discount applied)
...
[Lane-3] Thread finished.

All vehicle threads have finished.
  Thread Lane-1 -> state : TERMINATED
  Thread Lane-2 -> state : TERMINATED
  Thread Lane-3 -> state : TERMINATED
```

---

## 9. Option 6 → Transaction History and option 8 → Booth Details

```text
Enter your choice : 6

========================================
        TRANSACTION HISTORY
========================================
1. TXN1001 | 06-10-2026 15:25:43 | TN01AB1234 | Car | ₹45 | FASTag | SUCCESS
2. TXN1002 | 06-10-2026 15:25:44 | TN04GH3456 | Bus | ₹100 | UPI | SUCCESS
3. TXN1003 | 06-10-2026 15:25:45 | TN01AB1234 | Car | ₹45 | FASTag | SUCCESS
4. TXN1004 | 06-10-2026 15:25:46 | TN05IJ7890 | Car | ₹50 | Cash | SUCCESS
5. TXN1005 | 06-10-2026 15:25:47 | TN04GH3456 | Bus | ₹90 | FASTag | SUCCESS
----------------------------------------
Transactions this session : 5
Collection this session   : ₹330

Open the saved file history (transactions.txt) too? (y/n) : y

========================================
     SAVED HISTORY (transactions.txt)
========================================
File : /home/user/Toll-Booth-Manager/transactions.txt
1. TXN1001 | 06-10-2026 15:25:43 | TN01AB1234 | Car | ₹45 | FASTag | SUCCESS
2. TXN1002 | 06-10-2026 15:25:44 | TN04GH3456 | Bus | ₹100 | UPI | SUCCESS
3. TXN1003 | 06-10-2026 15:25:45 | TN01AB1234 | Car | ₹45 | FASTag | SUCCESS
4. TXN1004 | 06-10-2026 15:25:46 | TN05IJ7890 | Car | ₹50 | Cash | SUCCESS
5. TXN1005 | 06-10-2026 15:25:47 | TN04GH3456 | Bus | ₹90 | FASTag | SUCCESS
----------------------------------------
Total records in file : 5
========================================

Enter your choice : 8

========================================
          TOLL BOOTH DETAILS
========================================
Booth Id            : BOOTH-TN01
Location            : Chennai Bypass Toll Plaza
Registered vehicles : 6
Transactions        : 5
Total collection    : ₹330
Barrier openings    : 5
RFID scans          : 5
Barrier status      : CLOSED
Transaction file    : /home/user/Toll-Booth-Manager/transactions.txt
========================================
```

---

## 10. Option 10 → Exit

```text
Enter your choice : 10
Closing the toll booth...

========================================
          TOLL BOOTH DETAILS
========================================
Booth Id            : BOOTH-TN01
Location            : Chennai Bypass Toll Plaza
Registered vehicles : 6
Transactions        : 5
Total collection    : ₹330
Barrier openings    : 5
RFID scans          : 5
Barrier status      : CLOSED
Transaction file    : /home/user/Toll-Booth-Manager/transactions.txt
========================================

Thank you for using the IoT Based Toll Booth Manager System.
```

---

## 11. Error situations handled without a crash

| Wrong input | Message shown | Handled by |
|---|---|---|
| letters instead of a menu number (`abc`) | `"abc" is not a valid number. Try again.` | `NumberFormatException` in `readIntInRange()` |
| menu number outside 1–10 (`55`) | `Please enter a number between 1 and 10.` | range check |
| unknown tag (`RFID9999`) | `[RFID ERROR] No vehicle is registered with the RFID tag RFID9999...` | `InvalidRFIDException` |
| damaged tag (`RFID123`) | `[RFID ERROR] RFID tag format is wrong : "RFID123"` | `InvalidRFIDException` |
| cash less than the toll (`20` for `₹50`) | `[PAYMENT ERROR] Cash given (₹20) is less than the toll amount (₹50). Shortage : ₹30` | `InvalidPaymentException`, a `FAILED` record is written and the barrier stays **closed** |
| FASTag wallet too low | `[PAYMENT ERROR] FASTag wallet balance (₹100) is not enough for ₹150...` | `InvalidPaymentException` |
| UPI id without `@` (`test`) | `[PAYMENT ERROR] Invalid UPI id "test" (correct format : name@bank)` | `InvalidPaymentException` |
| wrong vehicle number (`BADNUMBER`) | `[VEHICLE ERROR] Registration number "BADNUMBER" is not in the valid format...` | `InvalidVehicleException` |
| same RFID registered twice | `[VEHICLE ERROR] RFID tag RFID1001 is already used by vehicle TN01AB1234.` | `InvalidVehicleException` |
| empty `transactions.txt` | `The file has no saved transaction yet.` | file reader |
| damaged line inside the file | the line is skipped silently, the rest of the history is shown | `TollTransaction.fromFileLine()` returns `null` |
