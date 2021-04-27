package fr.uge.chatos.packet;

import fr.uge.chatos.http.HTTPException;
import fr.uge.chatos.http.HTTPHeader;
import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

/**
 * Represent a frame containing a HTTP Response.
 *
 */

public class HttpData implements Packet {
    private final HTTPHeader header;
    private final String body;
    private final byte[] contentBody;

    private HttpData(HTTPHeader header, String body, byte[] contentBody) {
        this.header = header;
        this.body = body;
        this.contentBody = Arrays.copyOf(contentBody, contentBody.length);
    }

    public static HttpData create(String response, Map<String, String> fields, String body, byte[] contentBody) throws HTTPException {
        var header = HTTPHeader.create(response, fields);
        return new HttpData(header, body, contentBody);
    }

    public HTTPHeader getHeader() {
        return header;
    }

    public String getBody() {
        return body;
    }

    public byte[] getContentBody() {
        return contentBody;
    }

    @Override
    public ByteBuffer asByteBuffer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}
