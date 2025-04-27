package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.impl.stomp.StompFrame;
import bgu.spl.net.impl.stomp.StompMessagingProtocolImpl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class BlockingConnectionHandler implements Runnable, ConnectionHandler<String> {

    private final StompMessagingProtocol<String> protocol; //Can process a message
    private final MessageEncoderDecoder<String> encdec; //Can convert a message to and from bytes
    private final Socket sock; //The socket of the client we communicate with
    private BufferedInputStream in; 
    private BufferedOutputStream out;
    private volatile boolean connected; //Is there a connected user
    // Added Fields:
    private String username; //The username of the connected user
    private ConnectionsImpl<String> connections;
    private int clientConnectionId;

    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<String> reader, StompMessagingProtocol<String> protocol, ConnectionsImpl<String> connections) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
        this.connections = connections;
        this.clientConnectionId = -1;
        connected = true; // Meaning the clientSocket connected to the server
        this.username = "";
        
    }

    @Override
    public void run() {
        try (Socket sock = this.sock) { //just for automatic closing
            int read;

            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());
            protocol.start(clientConnectionId, connections);
            protocol.setHandler(this);

            while (!protocol.shouldTerminate() && connected && (read = in.read()) >= 0) {
                String nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    protocol.process(nextMessage);
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    @Override
    public void close() throws IOException {
        connected = false;
        sock.close();
    }

    @Override
    public void send(String msg) {
        try {
            StompFrame frame = new StompFrame(msg);
            if(frame.isMessageFrame()){
                String subscriptionId = "";
                for(String[] pair : frame.getHeaders()){
                    if(pair[0].compareTo("destination")==0){

                        subscriptionId = String.valueOf(((StompMessagingProtocolImpl)protocol).getChannelId(pair[1]));
                    }
                }
                frame.addHeader(new String[]{"subscription",subscriptionId});
            }

            out.write(encdec.encode(frame.toString()));
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Added Methods:
    public void setClientConnectionId(int clientConnectionId) {
        this.clientConnectionId = clientConnectionId;
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

    public String getUsername(){
        return username;
    }

    public void setUsername(String username){
        this.username = username;
    }

    public StompMessagingProtocol<String> getProtocol(){
        return protocol;
    }
}
