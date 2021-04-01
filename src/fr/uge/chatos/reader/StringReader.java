package fr.uge.chatos.reader;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * This class allows us to read a packet in this format :
 * int | String
 */
public class StringReader implements Reader<String> {
    private enum State {DONE, WAITING_SIZE, WAITING_CONTENT, ERROR}
    private static final int BUFFER_MAX_SIZE = 1024;
    private static final Charset charset = StandardCharsets.UTF_8;
    private final ByteBuffer internalBuffer = ByteBuffer.allocateDirect(BUFFER_MAX_SIZE);
    private final IntReader ir = new IntReader();
    private State currentState = State.WAITING_SIZE;
    private int size;
    private String content;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        // Get size
        if (currentState == State.WAITING_SIZE) {
            switch (ir.process(buffer)) {
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

        if (currentState != State.WAITING_CONTENT || size < 0 || size > BUFFER_MAX_SIZE) {
            return ProcessStatus.ERROR;
        }

        // Get Content
        buffer.flip();
        try {
            if (buffer.remaining() <= internalBuffer.remaining()) { // Si le buffer contient moins de cractères que notre buffer interne peut en contenir
                if (buffer.remaining() <= size) { // Si le buffer contient moins de caractères que l'on souhaite
                    internalBuffer.put(buffer);
                } else { // On récupère seulement la partie du buffer qui nous intéresse
                    extractSomeDataFromBuffer(buffer, size);
                }
            } else { // On remplie notre buffer interne des données du buffer
                extractSomeDataFromBuffer(buffer, internalBuffer.remaining());
            }
        } finally {
            buffer.compact();
        }
        // If we haven't recovered all the necessary data
        if (size > internalBuffer.position()) {
            return ProcessStatus.REFILL;
        }

        internalBuffer.flip();
        content = charset.decode(internalBuffer).toString();
        currentState = State.DONE;
        return ProcessStatus.DONE;
    }

    /**
     * Uses the limit to extract some data from the {@code buffer} and put it in {@code internalBuffer}.
     *
     * @param buffer The buffer from which the data is extracted.
     * @param numberToExtract The number of data to extract.
     */
    private void extractSomeDataFromBuffer(ByteBuffer buffer, int numberToExtract) {
        var oldLimit = buffer.limit();
        buffer.limit(buffer.position() + numberToExtract);
        internalBuffer.put(buffer);
        buffer.limit(oldLimit);
    }

    @Override
    public String get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return content;
    }

    @Override
    public void reset() {
        currentState = State.WAITING_SIZE;
        ir.reset();
        internalBuffer.clear();
        size = 0;
        content = "";
    }
}