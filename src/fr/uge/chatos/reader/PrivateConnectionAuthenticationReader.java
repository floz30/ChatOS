package fr.uge.chatos.reader;

import fr.uge.chatos.packet.PrivateConnectionConfirmation;

import java.nio.ByteBuffer;

public class PrivateConnectionAuthenticationReader implements Reader<PrivateConnectionConfirmation> {
    private enum State {DONE, WAITING_ID, WAITING_PSEUDO, ERROR}
    private PrivateConnectionConfirmation confirmation = new PrivateConnectionConfirmation();
    private State currentState = State.WAITING_ID;
    private final StringReader stringReader =  new StringReader();
    private final LongReader longReader = new LongReader();

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        if (currentState == State.WAITING_ID) {
            switch (longReader.process(buffer)) {
                case DONE:
                    confirmation.id = longReader.get();
                    currentState = State.WAITING_PSEUDO;
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                case ERROR:
                    currentState = State.ERROR;
                    return ProcessStatus.ERROR;
            }
        }

        if (currentState != State.WAITING_PSEUDO) {
            return ProcessStatus.ERROR;
        }

        switch (stringReader.process(buffer)) {
            case DONE:
                confirmation.login = stringReader.get();
                currentState = State.DONE;
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
    public PrivateConnectionConfirmation get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return confirmation;
    }

    @Override
    public void reset() {
        currentState = State.WAITING_ID;
        stringReader.reset();
        longReader.reset();
        confirmation = new PrivateConnectionConfirmation();
    }
}
