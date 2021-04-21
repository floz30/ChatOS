package fr.uge.chatos.reader;

import fr.uge.chatos.packet.ConnectionRequest;

import java.nio.ByteBuffer;

public class ConnectionRequestReader implements Reader<ConnectionRequest> {
    private enum State {DONE, WAITING_CONTENT, ERROR}
    private final StringReader stringReader = new StringReader();
    private State currentState = State.WAITING_CONTENT;
    private ConnectionRequest request = new ConnectionRequest();

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        switch (stringReader.process(buffer)) {
            case DONE:
                request.sender = stringReader.get();
                currentState = State.DONE;
                return ProcessStatus.DONE;
            case REFILL:
                return ProcessStatus.REFILL;
            default:
                currentState = State.ERROR;
                return ProcessStatus.ERROR;
        }
    }

    @Override
    public ConnectionRequest get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return request;
    }

    @Override
    public void reset() {
        currentState = State.WAITING_CONTENT;
        request = new ConnectionRequest();
        stringReader.reset();
    }
}
