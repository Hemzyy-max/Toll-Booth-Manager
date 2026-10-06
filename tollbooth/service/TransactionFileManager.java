package tollbooth.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import tollbooth.model.TollTransaction;

/**
 * ============================================================================
 *  FILE    : TransactionFileManager.java
 *  PACKAGE : tollbooth.service
 * ----------------------------------------------------------------------------
 *  FILE HANDLING of the project. Every successful (or failed) transaction is
 *  appended to a simple text file "transactions.txt" so that the data is still
 *  available after the program is closed.
 *
 *  One line of the file looks like this :
 *  TXN1001;TN01AB1234;Car;45.00;FASTag;2026-10-06 15:42:10;SUCCESS
 *
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. ABSTRACTION : TollBooth saves a transaction with ONE call -
 *                   saveTransaction() - and never bothers about FileWriter,
 *                   BufferedWriter, closing streams or IOException.
 *  2. ENCAPSULATION : the file name and the private constructor keep the class
 *                   as a pure utility class (nobody can create its object).
 *  3. EXCEPTION HANDLING : checked IOException is handled with
 *                   try / catch / finally and try-with-resources, so the
 *                   application never stops because of a file problem.
 *  4. COLLECTIONS : readAllTransactions() returns an ArrayList of
 *                   TollTransaction objects built from the file lines.
 *
 *  Classes used (as asked in the question paper) :
 *    writing -> FileWriter  + BufferedWriter
 *    reading -> FileReader  + BufferedReader
 * ============================================================================
 */
public final class TransactionFileManager {

    /** Name of the text file that stores the transaction records. */
    public static final String FILE_NAME = "transactions.txt";

    /** Private constructor : this class is used only through static methods. */
    private TransactionFileManager() {
        // no objects of this class are needed
    }

    /**
     * Creates an empty transactions.txt when the program runs for the first
     * time. FILE HANDLING : try-with-resources closes the writer automatically.
     *
     * @return true when the file exists after this call
     */
    public static boolean ensureFileExists() {
        File file = new File(FILE_NAME);
        if (file.exists()) {
            return true;
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write("# IoT Based Toll Booth Manager - transaction records");
            writer.newLine();
            System.out.println("New transaction file created : " + file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            System.out.println("File error while creating " + FILE_NAME + " : " + e.getMessage());
            return false;
        }
    }

    /**
     * Appends ONE transaction line at the end of transactions.txt.
     * The second parameter of FileWriter is "true", which means APPEND mode :
     * old records are never erased.
     *
     * @param transaction the transaction to save
     * @return true when the record was written
     */
    public static boolean saveTransaction(TollTransaction transaction) {
        if (transaction == null) {
            return false;
        }
        // try-with-resources : the writer is closed automatically, even if an
        // exception happens inside the block.
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME, true))) {
            writer.write(transaction.toFileLine());
            writer.newLine();
            return true;
        } catch (IOException e) {
            System.out.println("Could not save the transaction : " + e.getMessage());
            return false;
        }
    }

    /**
     * Reads the whole file and returns a list of TollTransaction objects.
     * Lines that start with "#" (the header) and damaged lines are skipped, so a
     * hand edited file can never crash the program.
     */
    public static List<TollTransaction> readAllTransactions() {
        List<TollTransaction> transactionList = new ArrayList<>();
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            return transactionList;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(FILE_NAME))) {
            String line;
            while ((line = reader.readLine()) != null) {      // read line by line
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                TollTransaction transaction = TollTransaction.fromFileLine(line);
                if (transaction != null) {
                    transactionList.add(transaction);
                }
            }
        } catch (IOException e) {
            System.out.println("Could not read " + FILE_NAME + " : " + e.getMessage());
        }
        return transactionList;
    }

    /**
     * Counts the saved records.
     * This method is written with the CLASSIC try / catch / finally style so
     * that both styles of file handling are shown in the project.
     *
     * @return number of valid transaction lines in the file
     */
    public static int countSavedTransactions() {
        int count = 0;
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            return 0;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty() && !line.startsWith("#")
                        && TollTransaction.fromFileLine(line) != null) {
                    count = count + 1;
                }
            }
        } catch (IOException e) {
            System.out.println("Could not count the records : " + e.getMessage());
        } finally {
            // finally ALWAYS runs : it is the right place to release a resource.
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                System.out.println("Could not close the file : " + e.getMessage());
            }
        }
        return count;
    }

    /**
     * Displays the content of transactions.txt in a report format.
     * This is the "view saved transaction history" option of the menu.
     */
    public static void displaySavedTransactions() {
        List<TollTransaction> savedTransactions = readAllTransactions();

        System.out.println();
        System.out.println("========================================");
        System.out.println("     SAVED HISTORY (transactions.txt)   ");
        System.out.println("========================================");
        System.out.println("File : " + getFilePath());

        if (savedTransactions.isEmpty()) {
            System.out.println("The file has no saved transaction yet.");
            System.out.println("========================================");
            return;
        }

        for (int index = 0; index < savedTransactions.size(); index++) {
            System.out.print((index + 1) + ". ");
            savedTransactions.get(index).displayTransaction();
        }
        System.out.println("----------------------------------------");
        System.out.println("Total records in file : " + savedTransactions.size());
        System.out.println("========================================");
    }

    /** @return the full path of the transaction file (for messages). */
    public static String getFilePath() {
        return new File(FILE_NAME).getAbsolutePath();
    }
}
