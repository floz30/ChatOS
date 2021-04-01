package fr.uge.chatos.utils;

import java.io.IOException;
import java.io.Serial;

public class HTTPException extends IOException {
    @Serial
    private static final long serialVersionUID = -1810727803680020453L;

    public HTTPException() {
        super();
    }

    public HTTPException(String s) {
        super(s);
    }

    public static void ensure(boolean b, String string) throws HTTPException {
        if (!b) {
            throw new HTTPException(string);
        }
    }
}
