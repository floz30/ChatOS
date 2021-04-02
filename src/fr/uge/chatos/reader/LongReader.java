package fr.uge.chatos.reader;

import java.nio.ByteBuffer;

public class LongReader implements Reader<Long> {
    private enum State { DONE, WAITING, ERROR };
    private final ByteBuffer internalBuffer = ByteBuffer.allocateDirect(Long.BYTES);
    private State currentState = State.WAITING;
    private long value;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
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
        value = internalBuffer.getLong();
        return ProcessStatus.DONE;
    }

    @Override
    public Long get() {
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
