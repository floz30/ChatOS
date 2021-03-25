package fr.uge.chatos.utils;

import java.util.Objects;

public class Message {
    private String login;
    private String content;

    public Message() {

    }

    public Message(String login, String content) {
        this.login = Objects.requireNonNull(login);
        this.content = Objects.requireNonNull(content);
    }

    public void setLogin(String login) {
        this.login = Objects.requireNonNull(login);
    }

    public void setContent(String content) {
        this.content = Objects.requireNonNull(content);
    }

    public String getLogin() {
        return login;
    }

    public String getContent() {
        return content;
    }
}
