package fr.uge.chatos.reader;

import fr.uge.chatos.utils.Message;

import java.nio.ByteBuffer;

public class MessageReader implements Reader<Message> {
    private enum State {DONE, WAITING_LOGIN, WAITING_MSG, ERROR}
    private final StringReader sr = new StringReader();
    private Message message = new Message();
    private State currentState = State.WAITING_LOGIN;

    @Override
    public ProcessStatus processData(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        // Get login
        if (currentState == State.WAITING_LOGIN) {
            switch (sr.processData(buffer)) {
                case DONE:
                    message.setLogin(sr.get());
                    currentState = State.WAITING_MSG;
                    sr.reset();
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                case ERROR:
                    currentState = State.ERROR;
                    return ProcessStatus.ERROR;
            }
        }
        //on a fini de lire le login : tout état qui n'est pas DONE doit être un cas échéant.
        if (currentState != State.WAITING_MSG) {
            return ProcessStatus.ERROR;
        }

        // Get message content
        switch (sr.processData(buffer)) {
            case DONE:
                message.setMessage(sr.get());
                currentState = State.DONE;
                sr.reset();
                break;
            case REFILL:
                return ProcessStatus.REFILL;
            case ERROR:
                currentState = State.ERROR;
                return ProcessStatus.ERROR;
        }
        return ProcessStatus.DONE;
    }

    @Override
    public Message get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return message;
    }

    @Override
    public void reset() {
        currentState = State.WAITING_LOGIN;
        message = new Message();
        sr.reset();
    }
}
