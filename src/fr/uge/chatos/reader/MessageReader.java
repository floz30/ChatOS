package fr.uge.chatos.reader;

import fr.uge.chatos.utils.Message;

import java.nio.ByteBuffer;

/**
 * This class allows us to parse a packet in this format
 * int ; String ; int ; String
 * TODO: ajouter le byte qui correspond à l'opération
 * 
 * @author michel
 *
 */


public class MessageReader implements Reader<Message> {
    private enum State {DONE, WAITING_LOGIN, WAITING_MSG, ERROR}
    private final ByteReader br = new ByteReader();
    private final StringReader sr = new StringReader();
    private Message message = new Message();
    private State currentState = State.WAITING_LOGIN;

    
    private void treatOps(byte operation) {
    	var op = Byte.toUnsignedInt(operation);
    	//TODO
    	switch(op) {
    	case 99:
    	case 1:
    	}
    }
    
    private ProcessStatus processOperation(ByteBuffer buffer) {
    	if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }
    	
    	switch(br.processData(buffer)) {
    	case DONE:
    		treatOps(br.get());
    	}
    }
    
    @Override
    public ProcessStatus processData(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        // Get login
        if (currentState == State.WAITING_LOGIN) {
            switch (sr.processData(buffer)) {
                case DONE:
                    message.setLogin(sr.get());
                    currentState = State.WAITING_MSG;
                    sr.reset();
                    break;
                case REFILL:
                    return ProcessStatus.REFILL;
                case ERROR:
                    currentState = State.ERROR;
                    return ProcessStatus.ERROR;
            }
        }
        //on a fini de lire le login : tout état qui n'est pas DONE doit être un cas échéant.
        if (currentState != State.WAITING_MSG) {
            return ProcessStatus.ERROR;
        }

        // Get message content
        switch (sr.processData(buffer)) {
            case DONE:
                message.setContent(sr.get());
                currentState = State.DONE;
                sr.reset();
                break;
            case REFILL:
                return ProcessStatus.REFILL;
            case ERROR:
                currentState = State.ERROR;
                return ProcessStatus.ERROR;
        }
        return ProcessStatus.DONE;
    }

    @Override
    public Message get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return message;
    }

    @Override
    public void reset() {
        currentState = State.WAITING_LOGIN;
        message = new Message();
        sr.reset();
    }
}
