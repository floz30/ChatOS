package fr.uge.chatos.reader;

import fr.uge.chatos.http.HTTPException;
import fr.uge.chatos.packet.HttpData;

import java.nio.ByteBuffer;
import java.util.HashMap;

public class HttpDataReader implements Reader<HttpData> {
    private enum State {DONE, WAITING_FIRST_LINE, WAITING_HEADERS, WAITING_BODY, ERROR}
    private final HttpLineCRLFReader crlfReader = new HttpLineCRLFReader();
    private State currentState = State.WAITING_FIRST_LINE;
    private String firstLine;
    private HashMap<String, String> fields = new HashMap<>();
    private String body = "";
    private byte[] contentBody;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        if (currentState == State.WAITING_FIRST_LINE) {
            switch (crlfReader.process(buffer)) {
                case DONE:
                    firstLine = crlfReader.get();
                    crlfReader.reset();
                    currentState = State.WAITING_HEADERS;
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                case ERROR:
                    currentState = State.ERROR;
                    return ProcessStatus.ERROR;
            }
        }

        if (currentState == State.WAITING_HEADERS) {
            while (currentState != State.WAITING_BODY) {
                var status = crlfReader.process(buffer);
                if (status == ProcessStatus.DONE) {
                    var line = crlfReader.get();
                    if (line.length() <= 2) {
                        currentState = State.WAITING_BODY;
                    } else {
                        var content = line.split(":", 2);
                        fields.put(content[0], content[1]);
                    }
                    crlfReader.reset();
                } else {
                    return status;
                }
            }
        }

        if (currentState == State.WAITING_BODY) {
            var oldPos = buffer.position();
            var oldLim = buffer.limit();
            buffer.flip();
            contentBody = new byte[buffer.remaining()];
            buffer.get(contentBody);
            buffer.position(oldPos);
            buffer.limit(oldLim);
            while (currentState != State.DONE) {
                var status = crlfReader.process(buffer);
                if (status == ProcessStatus.DONE) {
                    var line = crlfReader.get();
                    if (line.length() <= 2) {
                        currentState = State.DONE;
                    } else {
                        body += line;
                    }
                    crlfReader.reset();
                } else {
                    return status;
                }
            }
        }

        return ProcessStatus.DONE;
    }

    @Override
    public HttpData get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        try {
            return HttpData.create(firstLine, fields, body, contentBody);
        } catch (HTTPException e) {
            return null;
        }

    }

    @Override
    public void reset() {
        currentState = State.WAITING_FIRST_LINE;
        firstLine = "";
        fields = new HashMap<>();
        body = "";
    }
}
