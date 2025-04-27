package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.impl.stomp.StompFrame;
import bgu.spl.net.impl.stomp.StompMessagingProtocolImpl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NonBlockingConnectionHandler implements ConnectionHandler<String> {

    private static final int BUFFER_ALLOCATION_SIZE = 1 << 13; //8k
    private static final ConcurrentLinkedQueue<ByteBuffer> BUFFER_POOL = new ConcurrentLinkedQueue<>();

    private final StompMessagingProtocol<String> protocol;
    private final MessageEncoderDecoder<String> encdec;
    private final Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();
    private final SocketChannel chan;
    private final Reactor reactor;

    // Added Fields:
    private volatile boolean connected; //Is there a connected user
    private String username; //The username of the connected user
    private ConnectionsImpl<String> connections;
    private int clientConnectionId;
    private boolean protocolStarted;


    public NonBlockingConnectionHandler(
            MessageEncoderDecoder<String> reader,
            StompMessagingProtocol<String> protocol,
            SocketChannel chan,
            Reactor reactor) {
        this.chan = chan;
        this.encdec = reader;
        this.protocol = protocol;
        this.reactor = reactor;
        //Added:
        connected = true; // Meaning the clientSocket connected to the server
        this.username = "";
        protocolStarted = false;

    }

    public Runnable continueRead() {
        if(!protocolStarted){
            protocolStarted = true;
            protocol.start(clientConnectionId, connections);
            protocol.setHandler(this);
        }
        ByteBuffer buf = leaseBuffer();

        boolean success = false;
        try {
            success = chan.read(buf) != -1;
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        if (success) {
            buf.flip();
            return () -> {
                try {
                    while (buf.hasRemaining()) {
                        String nextMessage = encdec.decodeNextByte(buf.get());
                        if (nextMessage != null) {
                            protocol.process(nextMessage);
                            /*T response = protocol.process(nextMessage);
                            if (response != null) {
                                writeQueue.add(ByteBuffer.wrap(encdec.encode(response)));
                                reactor.updateInterestedOps(chan, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                            }*/
                        }
                    }
                } finally {
                    releaseBuffer(buf);
                }
            };
        } else {
            releaseBuffer(buf);
            close();
            return null;
        }

    }

    public void close() {
        try {
            connected = false;
            chan.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public boolean isClosed() {
        return !chan.isOpen();
    }

    public void continueWrite() {
        while (!writeQueue.isEmpty()) {
            try {
                ByteBuffer top = writeQueue.peek();
                chan.write(top);
                if (top.hasRemaining()) {
                    return;
                } else {
                    writeQueue.remove();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                close();
            }
        }

        if (writeQueue.isEmpty()) {
            if (protocol.shouldTerminate()) close();
            else reactor.updateInterestedOps(chan, SelectionKey.OP_READ);
        }
    }

    private static ByteBuffer leaseBuffer() {
        ByteBuffer buff = BUFFER_POOL.poll();
        if (buff == null) {
            return ByteBuffer.allocateDirect(BUFFER_ALLOCATION_SIZE);
        }

        buff.clear();
        return buff;
    }

    private static void releaseBuffer(ByteBuffer buff) {
        BUFFER_POOL.add(buff);
    }

    @Override
    public void send(String msg) {
        StompFrame frame = new StompFrame(msg);
        if(frame.isMessageFrame()){
            String subscriptionId = "";
            for(String[] pair : frame.getHeaders()){
                if(pair[0].compareTo("destination")==0){
                    int tmp = ((StompMessagingProtocolImpl)protocol).getChannelId(pair[1]);
                    subscriptionId = String.valueOf(tmp);
                }
            }
            frame.addHeader(new String[]{"subscription",subscriptionId});
        }

        writeQueue.add(ByteBuffer.wrap(encdec.encode(frame.toString())));
        reactor.updateInterestedOps(chan, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }



    //Added Methods
    public StompMessagingProtocol<String> getProtocol(){
        return protocol;
    }

    public String getUsername(){
        return username;
    }

    public int getConnectionId(){
        return clientConnectionId;
    }

    public boolean isConnected(){
        return connected;
    }

    public void setConnection(boolean flag){
        connected=flag;
    }
    public void setUsername(String username){
        this.username = username;
    }


    public void setClientConnectionId(int clientConnectionId) {
        this.clientConnectionId = clientConnectionId;
    }

    public void setConnections(ConnectionsImpl<String> connections){
        this.connections = connections;
    }
}
