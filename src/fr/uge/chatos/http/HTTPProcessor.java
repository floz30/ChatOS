package fr.uge.chatos.http;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;

public class HTTPProcessor {
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    public static void processHTTP(ByteBuffer bb, String root) throws HTTPException {
        var reader = new HTTPReader(bb);
        var header = reader.readHeader();
        if (header.getCode() == 404) {
            System.out.println("ERROR 404 : no such file");
            return;
        }
        var ext = header.getContentType();
        var content = reader.readBytes(header.getContentLength()).flip();
        if(ext.equals("txt")) {
            System.out.println(UTF8.decode(content));
        } else {
            if (!root.endsWith("/")) {
                root += "/";
            }
            try (var s = new FileOutputStream(root+"README.md")){
                s.write(content.array());
            } catch (IOException e) {
                System.out.println("err");
            }
            
        }
    }
}