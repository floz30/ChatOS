package fr.uge.chatos.reader;

import fr.uge.chatos.packet.HttpRequest;

import java.nio.ByteBuffer;
import java.util.HashMap;

public class HttpRequestReader implements Reader<HttpRequest> {
    private enum State {DONE, WAITING_FIRST_LINE, WAITING_HEADERS, ERROR}
    private final HttpLineCRLFReader crlfReader = new HttpLineCRLFReader();
    private State currentState = State.WAITING_FIRST_LINE;
    private String firstLine;
    private HashMap<String, String> fields = new HashMap<>();

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
            while (currentState != State.DONE) {
                var status = crlfReader.process(buffer);
                if (status == ProcessStatus.DONE) {
                    var line = crlfReader.get();
                    if (line.length() <= 2) {
                        currentState = State.DONE;
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

        return ProcessStatus.DONE;
    }


    @Override
    public HttpRequest get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return HttpRequest.create(firstLine, fields);
    }

    @Override
    public void reset() {
        currentState = State.WAITING_FIRST_LINE;
        firstLine = "";
        fields = new HashMap<>();
    }
}
