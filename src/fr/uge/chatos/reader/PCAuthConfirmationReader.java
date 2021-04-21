package fr.uge.chatos.reader;

import fr.uge.chatos.packet.PCAuthConfirmation;

import java.nio.ByteBuffer;

/**
 * Pour le client
 */
public class PCAuthConfirmationReader implements Reader<PCAuthConfirmation> {
    private enum State {DONE, WAITING_ID, WAITING_CONFIRM, ERROR}
    private PCAuthConfirmation confirmation = new PCAuthConfirmation();
    private State currentState = State.WAITING_ID;
    private final LongReader longReader = new LongReader();
    private final ByteReader byteReader = new ByteReader();

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        if (currentState == State.WAITING_ID) {
            switch (longReader.process(buffer)) {
                case DONE:
                    confirmation.id = longReader.get();
                    longReader.reset();
                    currentState = State.WAITING_CONFIRM;
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                case ERROR:
                    currentState = State.ERROR;
                    return ProcessStatus.ERROR;
            }
        }

        if (currentState == State.WAITING_CONFIRM) {
            switch (byteReader.process(buffer)) {
                case DONE:
                    confirmation.confirm = byteReader.get();
                    byteReader.reset();
                    currentState = State.DONE;
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                case ERROR:
                    currentState = State.ERROR;
                    return ProcessStatus.ERROR;
            }
        }

        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return ProcessStatus.DONE;
    }

    @Override
    public PCAuthConfirmation get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return confirmation;
    }

    @Override
    public void reset() {
        currentState = State.WAITING_ID;
        longReader.reset();
        byteReader.reset();
        confirmation = new PCAuthConfirmation();
    }
}
