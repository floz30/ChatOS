package fr.uge.chatos.reader;

import java.nio.ByteBuffer;

public class HttpLineCRLFReader implements Reader<String> {
    private enum State {DONE, WAITING_CRLF, ERROR}
    private static final int BUFFER_MAX_SIZE = 1024;
    private final ByteBuffer internalBuffer = ByteBuffer.allocateDirect(BUFFER_MAX_SIZE);
    private State currentState = State.WAITING_CRLF;
    private StringBuilder line = new StringBuilder();

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        buffer.flip();
        boolean carriageCheck = false;
        while (buffer.hasRemaining()) {
            char c = (char) buffer.get();
            line.append(c);
            if (c == '\n' && carriageCheck) {
                buffer.compact();
                currentState = State.DONE;
                return ProcessStatus.DONE;
            }
            carriageCheck = c == '\r';
        }

        buffer.compact();
        return ProcessStatus.REFILL;
    }

    @Override
    public String get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return line.substring(0, line.length()-2);
    }

    @Override
    public void reset() {
        currentState = State.WAITING_CRLF;
        internalBuffer.clear();
        line = new StringBuilder();
    }
}
