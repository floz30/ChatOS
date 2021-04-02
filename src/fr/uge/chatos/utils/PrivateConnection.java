package fr.uge.chatos.utils;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class PrivateConnection {
    public enum State {REQUEST, START_AUTHENTICATION, AUTHENTICATED, CLOSED}
    private final String applicant; // demandeur
    private final String recipient; // destinataire
    private State currentState;
    private long id;
    private int port;

    /**
     * Pour les clients
     *
     * @param recipient
     */
    public PrivateConnection(String recipient) {
        this.applicant = null;
        this.recipient = Objects.requireNonNull(recipient);
        currentState = State.REQUEST;
        id = -1;
    }

    public PrivateConnection(String firstRecipient, String secondRecipient, long id) {
        applicant = Objects.requireNonNull(firstRecipient);
        recipient = Objects.requireNonNull(secondRecipient);
        currentState = State.REQUEST;
        this.id = id;
    }

    /**
     * Pour le serveur
     *
     * @param firstRecipient
     * @param secondRecipient
     */
    public PrivateConnection(String firstRecipient, String secondRecipient) {
        applicant = Objects.requireNonNull(firstRecipient);
        recipient = Objects.requireNonNull(secondRecipient);
        currentState = State.REQUEST;
        this.id = ThreadLocalRandom.current().nextLong();
    }

    public State getCurrentState() {
        return currentState;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void updateState(State newState) {
        this.currentState = newState;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getApplicant() {
        return applicant;
    }

    public String getRecipient() {
        return recipient;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PrivateConnection that = (PrivateConnection) o;
        return id == that.id
                && applicant.equals(that.applicant)
                && recipient.equals(that.recipient);
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicant, recipient, id);
    }
}
