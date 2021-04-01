package fr.uge.chatos.utils;

import java.util.Objects;

public class Message {
    /**
     * Expeditor username
     */
    private String login;
    private String content;
    private boolean mp;

    public Message() {

    }

    public Message(String login, String content, boolean mp) {
        this.login = Objects.requireNonNull(login);
        this.content = Objects.requireNonNull(content);
        this.mp = mp;
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

    public boolean isMp() {
        return mp;
    }
}
