package fr.uge.chatos.reader;

import fr.uge.chatos.packet.PrivateConnectionRequest;

import java.nio.ByteBuffer;

public class PrivateConnectionRequestReader implements Reader<PrivateConnectionRequest> {
    private enum State {DONE, WAITING_RECIPIENT, ERROR}
    private final StringReader stringReader = new StringReader();
    private State currentState = State.WAITING_RECIPIENT;
    private PrivateConnectionRequest request = new PrivateConnectionRequest();

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        switch (stringReader.process(buffer)) {
            case DONE:
                request.recipient = stringReader.get();
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
    public PrivateConnectionRequest get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return request;
    }

    @Override
    public void reset() {
        currentState = State.WAITING_RECIPIENT;
        request = new PrivateConnectionRequest();
        stringReader.reset();
    }
}
