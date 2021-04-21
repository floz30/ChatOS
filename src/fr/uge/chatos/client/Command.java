package fr.uge.chatos.client;

import java.util.Objects;

/**
 *
 */
record Command(String recipient, String content, boolean isMessage) {

    /**
     * Extracts the command written by the client.
     *
     * @param message the line written by the client
     * @return a {@code Command} object
     */
    static Command extractCommand(String message) {
        Objects.requireNonNull(message);
        String recipient, content;
        boolean isMessage = true;
        if (message.startsWith("@") || message.startsWith("/")) { // connexion privée
            var elements = message.split(" ", 2);
            recipient = elements[0].substring(1);
            content = elements[1];
            if (elements[0].charAt(0) == '/') {
                isMessage = false;
            }
        } else { // message général
            recipient = null;
            content = message;
        }
        return new Command(recipient, content, isMessage);
    }
}
