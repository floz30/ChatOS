package fr.uge.chatos.reader;

import java.nio.ByteBuffer;

public class IntReader implements Reader<Integer> {
    private enum State { DONE, WAITING, ERROR };
    private final ByteBuffer internalBuffer = ByteBuffer.allocateDirect(Integer.BYTES);
    private State currentState = State.WAITING;
    private int value;

    @Override
    public ProcessStatus processData(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }
        buffer.flip();
        try {
            if (buffer.remaining() <= internalBuffer.remaining()){
                internalBuffer.put(buffer);
            } else {
                var oldLimit = buffer.limit();
                buffer.limit(internalBuffer.remaining());
                internalBuffer.put(buffer);
                buffer.limit(oldLimit);
            }
        } finally {
            buffer.compact();
        }
        if (internalBuffer.hasRemaining()){
            return ProcessStatus.REFILL;
        }
        currentState = State.DONE;
        internalBuffer.flip();
        value = internalBuffer.getInt();
        return ProcessStatus.DONE;
    }

    @Override
    public Integer get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        currentState = State.WAITING;
        internalBuffer.clear();
    }
}
