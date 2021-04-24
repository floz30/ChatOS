package fr.uge.chatos.http;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class HTTPProcessor {
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    public static void processHTTP(ByteBuffer bb) throws HTTPException {
        var reader = new HTTPReader(bb);
        var header = reader.readHeader();
        System.out.println(header.getContentLength());
        if(header.getContentType().equals("txt")) {
            var content = reader.readBytes(header.getContentLength()).flip();
            System.out.println(UTF8.decode(content));
        } else {
            //TODO: sauvegarder le fichier dans root.
        }
    }
}