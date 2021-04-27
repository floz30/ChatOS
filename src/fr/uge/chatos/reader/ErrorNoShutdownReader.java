package fr.uge.chatos.reader;

import fr.uge.chatos.packet.ErrorNoShutdown;

import java.nio.ByteBuffer;

public class ErrorNoShutdownReader implements Reader<ErrorNoShutdown> {
    private enum State {DONE, WAITING_MESSAGE, ERROR}
    private State currentState = State.WAITING_MESSAGE;
    private ErrorNoShutdown errorNoShutdown = new ErrorNoShutdown();
    private final StringReader stringReader = new StringReader();

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        switch (stringReader.process(buffer)) {
            case DONE:
                errorNoShutdown.setMessage(stringReader.get());
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
    public ErrorNoShutdown get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return errorNoShutdown;
    }

    @Override
    public void reset() {
        currentState = State.WAITING_MESSAGE;
        errorNoShutdown = new ErrorNoShutdown();
        stringReader.reset();
    }
}
