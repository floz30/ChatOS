package fr.uge.chatos.reader;

import fr.uge.chatos.packet.PCSockets;

import java.nio.ByteBuffer;

/**
 * Pour serveur
 */
public class PCReplyReader implements Reader<PCSockets> {
    private enum State {DONE, WAITING_RECIPIENT, WAITING_REPLY, ERROR}
    private final StringReader stringReader = new StringReader();
    private final ByteReader byteReader = new ByteReader();
    private State currentState = State.WAITING_RECIPIENT;
    private PCSockets sockets = new PCSockets();

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        if (currentState == State.WAITING_RECIPIENT) {
            switch (stringReader.process(buffer)) {
                case DONE:
                    sockets.sender = stringReader.get();
                    currentState = State.WAITING_REPLY;
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                default:
                    currentState = State.ERROR;
                    return ProcessStatus.ERROR;
            }
        }

        if (currentState != State.WAITING_REPLY) {
            return ProcessStatus.ERROR;
        }

        switch (byteReader.process(buffer)) {
            case DONE:
                sockets.reply = byteReader.get();
                currentState = State.DONE;
                break;
            case REFILL:
                return ProcessStatus.REFILL;
            default:
                currentState = State.ERROR;
                return ProcessStatus.ERROR;
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
        currentState = State.WAITING_RECIPIENT;
        stringReader.reset();
        byteReader.reset();
        sockets = new PCSockets();
    }
}
