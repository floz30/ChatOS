package fr.uge.chatos.reader;

import fr.uge.chatos.packet.PCSockets;

import java.nio.ByteBuffer;

/**
 * Pour client
 */
public class PCSocketsReader implements Reader<PCSockets> {
    private enum State {DONE, WAITING_OTHER_PSEUDO, WAITING_ID, WAITING_PORT, ERROR}
    private final StringReader stringReader = new StringReader();
    private final LongReader longReader = new LongReader();
    private final IntReader intReader = new IntReader();
    private PCSockets sockets = new PCSockets();
    private State currentState = State.WAITING_OTHER_PSEUDO;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        if (currentState == State.WAITING_OTHER_PSEUDO) {
            switch (stringReader.process(buffer)) {
                case DONE:
                    sockets.sender = stringReader.get();
                    currentState = State.WAITING_ID;
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                default:
                    currentState = State.ERROR;
                    return ProcessStatus.ERROR;
            }
        }

        if (currentState == State.WAITING_ID) {
            switch (longReader.process(buffer)) {
                case DONE:
                    sockets.id = longReader.get();
                    currentState = State.WAITING_PORT;
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                default:
                    currentState = State.ERROR;
                    return ProcessStatus.ERROR;
            }
        }

        if (currentState == State.WAITING_PORT) {
            switch (intReader.process(buffer)) {
                case DONE:
                    sockets.port = intReader.get();
                    currentState = State.DONE;
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                default:
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
    public PCSockets get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return sockets;
    }

    @Override
    public void reset() {
        currentState = State.WAITING_OTHER_PSEUDO;
        sockets = new PCSockets();
        stringReader.reset();
        intReader.reset();
        longReader.reset();
    }
}
