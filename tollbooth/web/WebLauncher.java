package tollbooth.web;

import java.io.IOException;

/**
 * ============================================================================
 *  FILE    : WebLauncher.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  The main class of the WEB application.
 *
 *      java -cp out tollbooth.web.WebLauncher
 *      java -cp out tollbooth.web.WebLauncher --port 3000 --no-simulator
 *
 *  The console project keeps its own main class (tollbooth.TollBoothManager), so
 *  both applications can be run from the same compiled folder :
 *
 *      java -cp out tollbooth.TollBoothManager     <- menu driven console
 *      java -cp out tollbooth.web.WebLauncher      <- real time web application
 *
 *  OPTIONS
 *      --port <number>   port to listen on (default 3000, or the PORT variable)
 *      --quiet           do not log every HTTP request
 *      --no-simulator    do not start the automatic vehicle simulator
 * ============================================================================
 */
public final class WebLauncher {

    private WebLauncher() {
    }

    public static void main(String[] args) {
        int port = TollBoothServer.resolvePort(args);
        boolean requestLogging = !hasFlag(args, "--quiet");
        boolean simulatorEnabled = !hasFlag(args, "--no-simulator");

        System.out.println("Starting the real time toll booth web application...");
        TollBoothServer server = new TollBoothServer(port, requestLogging, simulatorEnabled);
        try {
            server.start();
        } catch (IOException e) {
            System.out.println();
            System.out.println("The server could not start on port " + port + " : " + e.getMessage());
            System.out.println("Another program may already use that port. Try :");
            System.out.println("   java -cp out tollbooth.web.WebLauncher --port 8080");
            return;
        }

        // The main thread waits here : the HTTP server uses its own threads.
        // On Ctrl+C the shutdown hook of TollBoothServer stops everything.
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String argument : args) {
            if (flag.equalsIgnoreCase(argument)) {
                return true;
            }
        }
        return false;
    }
}
