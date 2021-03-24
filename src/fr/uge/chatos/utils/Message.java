package fr.uge.chatos.utils;

import java.util.Objects;

public class Message {
    private String login;
    private String message;

    public void setLogin(String login) {
        this.login = Objects.requireNonNull(login);
    }

    public void setMessage(String message) {
        this.message = Objects.requireNonNull(message);
    }

    public String getLogin() {
        return login;
    }

    public String getMessage() {
        return message;
    }
}
