package fr.uge.chatos.reader;

import fr.uge.chatos.packet.ErrorShutdown;

import java.nio.ByteBuffer;

public class ErrorShutdownReader implements Reader<ErrorShutdown> {
    private enum State {DONE, WAITING_MESSAGE, ERROR}
    private State currentState = State.WAITING_MESSAGE;
    private ErrorShutdown errorShutdown = new ErrorShutdown();
    private final StringReader stringReader = new StringReader();

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        switch (stringReader.process(buffer)) {
            case DONE:
                errorShutdown.setMessage(stringReader.get());
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
    public ErrorShutdown get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return errorShutdown;
    }

    @Override
    public void reset() {
        currentState = State.WAITING_MESSAGE;
        errorShutdown = new ErrorShutdown();
        stringReader.reset();
    }
}
