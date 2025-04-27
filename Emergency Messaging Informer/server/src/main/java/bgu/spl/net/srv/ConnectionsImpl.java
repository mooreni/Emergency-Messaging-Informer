package bgu.spl.net.srv;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import bgu.spl.net.impl.stomp.StompMessagingProtocolImpl;


public class ConnectionsImpl<T> implements Connections<T> {

    private ConcurrentHashMap<Integer, ConnectionHandler<T>> connectedClients; //ConnectionIds and their matching handlers
    private ConcurrentHashMap<String, ConcurrentLinkedQueue<Integer>> channelsSubs; //Channel names and for each a queue of connectionIDs
    private ConcurrentHashMap<String, User> usersList; //Usernames and their matching password and isConnected
    private AtomicInteger messageId; //Unique message ids

    public ConnectionsImpl() {
        connectedClients = new ConcurrentHashMap<>();
        channelsSubs = new ConcurrentHashMap<>();
        usersList = new ConcurrentHashMap<>();
        messageId = new AtomicInteger(0);
    }

    //Send a message to a specific client
    @Override
    public boolean send(int connectionId, T msg) {
        //Which version is correct?
        ConnectionHandler<T> handler = connectedClients.get(connectionId);
        if(handler!=null){
            synchronized(handler){
                handler.send(msg);
                return true;
            }
        }
        return false;

        /*synchronized(connectedClients){
            ConnectionHandler<T> handler = connectedClients.get(connectionId);
            if(handler!=null){
                handler.send(msg);
                return true;
            }
            return false;
        }*/

    }

    //Send a message to all subscribers of a channel
    @Override
    public void send(String channel, T msg) {
        ConcurrentLinkedQueue<Integer> list = channelsSubs.get(channel);
        synchronized(list){
            for (Integer connectionId : list) {
                send(connectionId, msg);
            }
        }
    }

    @Override
    public void disconnect(int connectionId) {
        // Removes the user from all the channels it subscribed to
        for (ConcurrentLinkedQueue<Integer> queue : channelsSubs.values()) {
            queue.remove(connectionId);
        }

        // Removes the client from the connected map
        ConnectionHandler<T> handler = connectedClients.remove(connectionId);
        if(handler!=null){
            synchronized(handler){
                //Disconnecting User
                String username = handler.getUsername();
                User user = usersList.get(username);
                if (user != null) {
                    synchronized (user) {
                        user.setUserConnection(false); // Mark user as disconnected
                    }
                }

                // Stops the handler's run
                ((StompMessagingProtocolImpl)handler.getProtocol()).terminate();
    
                // Closes the socket from the server side
                try{
                    Thread.sleep(500);
                    handler.close(); // Closes the clientSocket
                }catch(IOException e){
                    System.out.println("socket is closed.");
                }
                catch(InterruptedException e){
                    return;
                };
            }
        }
    }


    public void addConnection(ConnectionHandler<T> handler) {
        //Doesnt need sync - connectedClients is concurrent
        connectedClients.put(handler.getConnectionId(), handler);
    }

    
    public boolean tryAddUser(String username, User u){
        //Adds the user if it doesnt exist
        User result = usersList.putIfAbsent(username, u);
        if(result==null){
            return true;
        }
        return false;
    }

    public String tryConnectExistingUser(String username, String passcode){
        User user = usersList.get(username); 
        synchronized(user){
            if(!user.isConnected()){
                if(user.getPasscode().compareTo(passcode)==0){
                    user.setUserConnection(true);
                    return "success";
                }
                else{
                    return "wrongPass";
                }
            }
            return "alreadyConnected";
        }
    }

    public boolean isSubscribed(String channel, int connectionId){
        //Channelsubs keys are the name of the channel - not sorted by connectionID
        ConcurrentLinkedQueue<Integer> queue = channelsSubs.get(channel);
        if (queue != null) {
            synchronized (queue) {
                return queue.contains(connectionId);
            }
        }
        return false;
    }

    public void addSubscribed(String channel, int connectionId){
        channelsSubs.computeIfAbsent(channel, k -> new ConcurrentLinkedQueue<>()).add(connectionId);
    }

    public void removeSubscribed(String channel, int connectionId){
        ConcurrentLinkedQueue<Integer> queue = channelsSubs.get(channel);
        if (queue != null) {
            queue.remove(connectionId);
        }
    }

    public void incrementMessageIdCounter() {
        int oldValue;
        int newValue;
        do {
            oldValue = messageId.get(); // Get the current value
            newValue = oldValue + 1; // Calculate the new value
        } while (!messageId.compareAndSet(oldValue, newValue)); // Compare and set
    }

    public int getMessageId() {
        return messageId.get();
    }

    public boolean isHandlerConnected(int connectionId){
        ConnectionHandler<T> handler = connectedClients.get(connectionId);
        if(handler!=null){
            synchronized(handler){
                return handler.isConnected();
            }
        }
        return false;
    }

}
