package fr.uge.chatos.reader;

import fr.uge.chatos.packet.PrivateMessage;

import java.nio.ByteBuffer;

public class PrivateMessageReader implements Reader<PrivateMessage> {
    private enum State {DONE, WAITING_SENDER, WAITING_RECIPIENT, WAITING_CONTENT, ERROR}
    private final StringReader stringReader = new StringReader();
    private PrivateMessage message = new PrivateMessage();
    private State currentState = State.WAITING_SENDER;


    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        while (currentState != State.DONE) {
            var status = stringReader.process(buffer);
            if (status == ProcessStatus.DONE) {
                if (currentState == State.WAITING_SENDER) {
                    message.sender = stringReader.get();
                    stringReader.reset();
                    currentState = State.WAITING_RECIPIENT;
                } else if (currentState == State.WAITING_RECIPIENT) {
                    message.recipient = stringReader.get();
                    stringReader.reset();
                    currentState = State.WAITING_CONTENT;
                } else if (currentState == State.WAITING_CONTENT) {
                    message.content = stringReader.get();
                    stringReader.reset();
                    currentState = State.DONE;
                }
            } else {
                return status;
            }
        }
        return ProcessStatus.DONE;
    }

    @Override
    public PrivateMessage get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return message;
    }

    @Override
    public void reset() {
        currentState = State.WAITING_SENDER;
        message = new PrivateMessage();
        stringReader.reset();
    }
}
