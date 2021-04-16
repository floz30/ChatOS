package fr.uge.chatos.utils;

public class OpCode {
    public static final byte ERROR = 99;
    public static final byte CONNECTION_REQUEST = 0;
    public static final byte CONNECTION_ACCEPT = 1;
    /**
     * Client to server.
     */
    public static final byte GENERAL_SENDER = 2; // client -> server
    public static final byte GENERAL_RECEIVER = 3; // server -> client
    public static final byte PRIVATE_SENDER = 4; // client -> server
    public static final byte PRIVATE_RECEIVER = 5; // server -> client
    public static final byte PRIVATE_CONNECTION_REQUEST_SENDER = 6;
    public static final byte PRIVATE_CONNECTION_REQUEST_RECEIVER = 7;
    public static final byte PRIVATE_CONNECTION_REPLY = 8;
    public static final byte PRIVATE_CONNECTION_SOCKETS = 9;
    public static final byte PRIVATE_CONNECTION_AUTHENTICATION = 10;
    public static final byte PRIVATE_CONNECTION_CONFIRMATION = 11;
}
