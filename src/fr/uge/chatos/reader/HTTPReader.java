package fr.uge.chatos.reader;

import fr.uge.chatos.utils.HTTPException;
import fr.uge.chatos.utils.HTTPHeader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HTTPReader implements Reader<ByteBuffer> {
    private static final Logger logger = Logger.getLogger(HTTPReader.class.getName());
    private enum State {DONE, WAITING_HEADER, WAITING_CONTENT, ERROR};
    private final ByteBuffer internalBuffer = ByteBuffer.allocateDirect(64);
    private final SocketChannel socket;
    private final Charset charset = StandardCharsets.US_ASCII;
    private State currentState = State.WAITING_HEADER;
    private ByteBuffer content;

    public HTTPReader(SocketChannel socket) {
        this.socket = Objects.requireNonNull(socket);
    }

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        // Get headers
        HTTPHeader headers = null;
        try {
            headers = readHeader();
        } catch (IOException e) {
            currentState = State.ERROR;
            logger.log(Level.INFO, "Error occurs in readHeader() from HTTPReader.", e);
            return ProcessStatus.ERROR;
        }

        switch (headers.getCode()) {
            case 200:
                //ok
                break;
        }

        // Get content
        try {
            content = readBytes(headers.getContentLength());
        } catch (HTTPException e) {
            currentState = State.ERROR;
            return ProcessStatus.ERROR;
        } catch (IOException e) {
            currentState = State.ERROR;
            logger.log(Level.WARNING, "I/O error occurs.", e);
            return ProcessStatus.ERROR;
        }

        return ProcessStatus.DONE;
    }

    @Override
    public ByteBuffer get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return content;
    }

    @Override
    public void reset() {
        content.clear();
        internalBuffer.clear();
    }

    /**
     *
     * @return The ASCII string terminated by CRLF without the CRLF
     * @throws HTTPException If the connection is closed before a line could be read
     * @throws IOException If an other I/O error occurs
     */
    private String readLineCRLF() throws IOException {
        var sb = new StringBuilder();
        boolean carriageCheck = false;
        while (true) {
            internalBuffer.flip();
            while (internalBuffer.hasRemaining()) {
                char c = (char) internalBuffer.get();
                sb.append(c);
                if (c == '\n' && carriageCheck) {
                    internalBuffer.compact();
                    return sb.substring(0, sb.length()-2);
                }
                carriageCheck = c == '\r';
            }
            internalBuffer.compact();
            if (socket.read(internalBuffer) == -1) {
                throw new HTTPException();
            }
        }
    }

    /**
     *
     * @return The HTTPHeader object corresponding to the header read.
     * @throws HTTPException If the connection is closed before a line could be read or
     *                       if the header is ill-formed
     * @throws IOException If an other I/O error occurs
     */
    private HTTPHeader readHeader() throws IOException {
        var firstLine = readLineCRLF();
        var fields = new HashMap<String, String>();
        String line;
        while ((line = readLineCRLF()).length() > 2) {
            var content = line.split(":", 2);
            fields.put(content[0], content[1]);
        }
        return HTTPHeader.create(firstLine, fields);
    }

    /**
     *
     * @param size Number of bytes to read
     * @return A ByteBuffer in write-mode containing size bytes read on the socket
     * @throws HTTPException If the connection is closed before a line could be read
     * @throws IOException If an other I/O error occurs
     */
    private ByteBuffer readBytes(int size) throws IOException {
        var buff = ByteBuffer.allocate(size);
        internalBuffer.flip();
        while (internalBuffer.hasRemaining() && buff.hasRemaining()) {
            buff.put(internalBuffer.get());
        }
        while (buff.hasRemaining()) {
            if (socket.read(buff) == -1) {
                throw new HTTPException();
            }
        }
        internalBuffer.compact();
        return buff;
    }
}
