package fr.uge.chatos.reader;

import fr.uge.chatos.packet.PrivateConnectionSockets;

import java.nio.ByteBuffer;

public class PrivateConnectionReplyReader implements Reader<PrivateConnectionSockets> {
    private enum State {DONE, WAITING_RECIPIENT, WAITING_REPLY, ERROR}
    private final StringReader stringReader = new StringReader();
    private final ByteReader byteReader = new ByteReader();
    private State currentState = State.WAITING_RECIPIENT;
    private PrivateConnectionSockets sockets = new PrivateConnectionSockets();

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
    public PrivateConnectionSockets get() {
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
        sockets = new PrivateConnectionSockets();
    }
}
