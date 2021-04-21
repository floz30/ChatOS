package fr.uge.chatos.utils;

/**
 * This class represents a byte operation code. At the beginning
 * of each {@link fr.uge.chatos.packet.Packet} there is a byte
 * which indicate his type. Depending on this byte, the process
 * will not be the same
 */
public class OpCode {
    /**
     * This code represents a server error.
     */
    public static final byte ERROR = 99;

    /**
     * This code represents a public connection request.
     * <p>
     *     From {@link fr.uge.chatos.client.Client} to {@link fr.uge.chatos.server.Server}.
     * </p>
     */
    public static final byte CONNECTION_REQUEST = 0;

    /**
     * This code represents a public connection confirmation.
     * <p>
     *     From {@link fr.uge.chatos.server.Server} to {@link fr.uge.chatos.client.Client}.
     * </p>
     */
    public static final byte CONNECTION_ACCEPT = 1;

    /**
     * This code represents a public message.
     * <p>
     *     From {@link fr.uge.chatos.client.Client} to {@link fr.uge.chatos.server.Server}.
     * </p>
     */
    public static final byte GENERAL_SENDER = 2;

    /**
     * This code represents a public message.
     * <p>
     *     From {@link fr.uge.chatos.server.Server} to {@link fr.uge.chatos.client.Client}.
     * </p>
     */
    public static final byte GENERAL_RECEIVER = 3;

    /**
     * This code represents a private message.
     * <p>
     *     From {@link fr.uge.chatos.client.Client} to {@link fr.uge.chatos.server.Server}.
     * </p>
     */
    public static final byte PRIVATE_SENDER = 4;

    /**
     * This code represents a private message.
     * <p>
     *     From {@link fr.uge.chatos.server.Server} to {@link fr.uge.chatos.client.Client}.
     * </p>
     */
    public static final byte PRIVATE_RECEIVER = 5;

    /**
     * This code represents a private connection request.
     * <p>
     *     From {@link fr.uge.chatos.client.Client} to {@link fr.uge.chatos.server.Server}.
     * </p>
     */
    public static final byte PRIVATE_CONNECTION_REQUEST_SENDER = 6;

    /**
     * This code represents a private connection request.
     * <p>
     *     From {@link fr.uge.chatos.server.Server} to {@link fr.uge.chatos.client.Client}.
     * </p>
     */
    public static final byte PRIVATE_CONNECTION_REQUEST_RECEIVER = 7;

    /**
     * This code represents a private connection reply.
     * <p>
     *     From {@link fr.uge.chatos.client.Client} to {@link fr.uge.chatos.server.Server}.
     * </p>
     */
    public static final byte PRIVATE_CONNECTION_REPLY = 8;

    /**
     * This code represents a private connection initialization.
     * <p>
     *     From {@link fr.uge.chatos.server.Server} to {@link fr.uge.chatos.client.Client}.
     * </p>
     */
    public static final byte PRIVATE_CONNECTION_SOCKETS = 9;

    /**
     * This code represents a private connection authentication.
     * <p>
     *     From {@link fr.uge.chatos.client.Client} to {@link fr.uge.chatos.server.Server}.
     * </p>
     */
    public static final byte PRIVATE_CONNECTION_AUTHENTICATION = 10;

    /**
     * This code represents a private connection confirmation.
     * <p>
     *     From {@link fr.uge.chatos.server.Server} to {@link fr.uge.chatos.client.Client}.
     * </p>
     */
    public static final byte PRIVATE_CONNECTION_CONFIRMATION = 11;
}
