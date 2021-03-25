package fr.uge.chatos.reader;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StringReader implements Reader<String> {
    private enum State {DONE, WAITING_SIZE, WAITING_CONTENT, ERROR}
    private final ByteBuffer internalBuffer = ByteBuffer.allocateDirect(1024);
    private final IntReader ir = new IntReader();
    private final Charset charset = StandardCharsets.UTF_8;
    private State currentState = State.WAITING_SIZE;
    private int size;
    private String value;

    @Override
    public ProcessStatus processData(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        // Get size
        if (currentState == State.WAITING_SIZE) {
            switch (ir.processData(buffer)) {
                case DONE:
                    size = ir.get();
                    currentState = State.WAITING_CONTENT;
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                case ERROR:
                    currentState = State.ERROR;
                    return ProcessStatus.ERROR;
            }
        }

        if (currentState != State.WAITING_CONTENT) {
            return ProcessStatus.ERROR;
        }

        // Get Content
        buffer.flip();
        try {
            var oldLimit = buffer.limit();
            buffer.limit(size);
            internalBuffer.put(buffer);
            buffer.limit(oldLimit);
//            if (buffer.remaining() <= internalBuffer.remaining()) {
//                internalBuffer.put(buffer);
//            } else {
//                var oldLimit = buffer.limit();
//                buffer.limit(buffer.position() + internalBuffer.remaining());
//                internalBuffer.put(buffer);
//                buffer.limit(oldLimit);
//            }
        } finally {
            buffer.compact();
        }
        // If we haven't recovered all the necessary data
        if (size > internalBuffer.position()) {
            return ProcessStatus.REFILL;
        }
        internalBuffer.flip();
//        var oldLimit = internalBuffer.limit();
//        internalBuffer.limit(size);
        value = charset.decode(internalBuffer).toString();
        //internalBuffer.limit(oldLimit);

        currentState = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public String get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        currentState = State.WAITING_SIZE;
        ir.reset();
        internalBuffer.clear();
        size = 0;
        value = "";
    }
}
