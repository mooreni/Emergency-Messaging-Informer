package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.StompMessagingProtocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Supplier;



public abstract class BaseServer implements Server<String> {

    private final int port;
    private final Supplier<StompMessagingProtocol<String>> protocolFactory;
    private final Supplier<MessageEncoderDecoder<String>> encdecFactory;
    private ServerSocket sock;
    // Added Fields:
    private ConnectionsImpl<String> connectedClients;
    private int clientsCounter;
    
    
    public BaseServer(
            int port,
            Supplier<StompMessagingProtocol<String>> protocolFactory,
            Supplier<MessageEncoderDecoder<String>> encdecFactory) {

        this.port = port;
        this.protocolFactory = protocolFactory;
        this.encdecFactory = encdecFactory;
		this.sock = null;
        // Added:
        this.connectedClients = new ConnectionsImpl<>();
        this.clientsCounter = 0;
    }

    @Override
    public void serve() {

        try (ServerSocket serverSock = new ServerSocket(port)) {
			System.out.println("Server started");

            this.sock = serverSock; //just to be able to close

            //In a loop tries to accept more clients, create handlers for them, and allowing the handler to run
            while (!Thread.currentThread().isInterrupted()) {

                Socket clientSock = serverSock.accept();

                BlockingConnectionHandler handler = new BlockingConnectionHandler(
                        clientSock,
                        encdecFactory.get(),
                        protocolFactory.get(),
                        connectedClients
                );

                clientsCounter++;
                handler.setClientConnectionId(clientsCounter);

                execute(handler);
            }
        } catch (IOException ex) {
        }

        System.out.println("server closed!!!");
    }

    @Override
    public void close() throws IOException {
		if (sock != null)
			sock.close();
    }

    
    abstract void execute(BlockingConnectionHandler  handler);


}
