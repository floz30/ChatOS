package fr.uge.chatos.reader;

import fr.uge.chatos.packet.ConnectionConfirmation;
import fr.uge.chatos.packet.Packet;

import java.nio.ByteBuffer;

import static fr.uge.chatos.utils.OpCode.*;

public class ClientPacketReader implements Reader<Packet> {
    private enum State {DONE, WAITING_PACKET, ERROR}
    private final ByteReader byteReader = new ByteReader();
    private final ConnectionRequestReader connectionRequestReader = new ConnectionRequestReader();
    private final PublicMessageReader publicMessageReader = new PublicMessageReader();
    private final PrivateMessageReader privateMessageReader = new PrivateMessageReader();
    private final PCRequestReader PCRequestReader = new PCRequestReader();
    private final PCSocketsReader PCSocketsReader = new PCSocketsReader();
    private final PCAuthConfirmationReader pcar = new PCAuthConfirmationReader();
    private State currentState = State.WAITING_PACKET;
    private Packet packet;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        buffer.flip();
        if (!buffer.hasRemaining()) {
            buffer.compact();
            return ProcessStatus.REFILL;
        }
        var opCode = buffer.get();
        buffer.compact();

        var status = ProcessStatus.ERROR;
        switch (opCode) {
            case CONNECTION_ACCEPT -> {
                status = byteReader.process(buffer); // on utilise directement un ByteReader (+ simple)
                if (status == ProcessStatus.DONE) {
                    packet = new ConnectionConfirmation(byteReader.get());
                    connectionRequestReader.reset();
                    currentState = State.DONE;
                }
            }
            case GENERAL_RECEIVER -> {
                status = publicMessageReader.process(buffer);
                if (status == ProcessStatus.DONE) {
                    packet = publicMessageReader.get();
                    publicMessageReader.reset();
                    currentState = State.DONE;
                }
            }
            case PRIVATE_RECEIVER -> {
                status = privateMessageReader.process(buffer);
                if (status == ProcessStatus.DONE) {
                    packet = privateMessageReader.get();
                    privateMessageReader.reset();
                    currentState = State.DONE;
                }
            }
            case PRIVATE_CONNECTION_REQUEST_RECEIVER -> {
                status = PCRequestReader.process(buffer);
                if (status == ProcessStatus.DONE) {
                    packet = PCRequestReader.get();
                    PCRequestReader.reset();
                    currentState = State.DONE;
                }
            }
            case PRIVATE_CONNECTION_SOCKETS -> {
                status = PCSocketsReader.process(buffer);
                if (status == ProcessStatus.DONE) {
                    packet = PCSocketsReader.get();
                    PCSocketsReader.reset();
                    currentState = State.DONE;
                }
            }
            case PRIVATE_CONNECTION_CONFIRMATION -> {
                status = pcar.process(buffer);
                if (status == ProcessStatus.DONE) {
                    packet = pcar.get();
                    pcar.reset();
                    currentState = State.DONE;
                }
            } // TODO : ajouter les cas des paquets d'erreur
        }
        return status;
    }

    @Override
    public Packet get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return packet;
    }

    @Override
    public void reset() {
        currentState = State.WAITING_PACKET;
        packet = null; // à revoir
        connectionRequestReader.reset();
        publicMessageReader.reset();
        privateMessageReader.reset();
        PCRequestReader.reset();
        pcar.reset();
        PCSocketsReader.reset();
    }
}
