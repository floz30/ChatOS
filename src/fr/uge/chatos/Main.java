package fr.uge.chatos;

import fr.uge.chatos.server.Server;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: Main publicPort privatePort");
            return;
        }

        int port, privatePort;
        try {
            port = Integer.parseInt(args[0]);
            privatePort = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Port number must be an Integer.");
            return;
        }

        // Start server
        new Server(port).launch();
    }
}
