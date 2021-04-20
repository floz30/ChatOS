package fr.uge.chatos.reader;

import fr.uge.chatos.packet.PublicMessage;

import java.nio.ByteBuffer;

public class PublicMessageReader implements Reader<PublicMessage> {
    private enum State {DONE, WAITING_SENDER, WAITING_CONTENT, ERROR}
    private final StringReader stringReader = new StringReader();
    private PublicMessage message = new PublicMessage();
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
    public PublicMessage get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return message;
    }

    @Override
    public void reset() {
        currentState = State.WAITING_SENDER;
        message = new PublicMessage();
        stringReader.reset();
    }
}
