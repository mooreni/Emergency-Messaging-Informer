package bgu.spl.net.impl.stomp;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.srv.User;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {

    private int clientConnectionId;
    private ConnectionsImpl<String> connections;
    private ConnectionHandler<String> handler;
    private boolean shouldTerminate;

    private ConcurrentHashMap<Integer, String> subsIdsToChannels;//List of Id's and which channel they were for - used in Unsubscribe
    private ConcurrentHashMap<String, Integer> channelsToSubsIds;//List of channels and which Id is subscribed to them - used in send
    
    public StompMessagingProtocolImpl() {
        this.clientConnectionId = -1;
        this.connections = null;
        shouldTerminate=false;
        this.subsIdsToChannels = new ConcurrentHashMap<>();
        this.channelsToSubsIds = new ConcurrentHashMap<>();
    }
    
    @Override
    public void start(int connectionId, ConnectionsImpl<String> connections) {
        this.clientConnectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(String message) {
        StompFrame frame = new StompFrame(message);

        //Connect Frame from client
        if(frame.getCommand().compareTo("CONNECT")==0){
            processConnect(frame);
        }
        else if(frame.getCommand().compareTo("SEND")==0){
            processSend(frame);
        }

        else if(frame.getCommand().compareTo("SUBSCRIBE")==0){
            processSubscribe(frame);
        }

        else if(frame.getCommand().compareTo("UNSUBSCRIBE")==0){
            processUnsubscribe(frame);
        }

        else if(frame.getCommand().compareTo("DISCONNECT")==0){
            processDisconnect(frame);
        }

    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }


    //Process a connect message
    private void processConnect(StompFrame frame){
        String userName = "";
        String passcode = "";
        List<String[]> headers = frame.getHeaders();
        int receipt = -1;
        if(frame.getReceiptId().compareTo("")!=0){
            receipt = Integer.parseInt(frame.getReceiptId());
        }

        //Error Checks
        if(headers.size()!=4){
            handler.send(StompFrame.createErrorFrame("", 
                            "Not enough or too many headers.", receipt, 
                            frame.toString()).toString());
            terminate();
            try{
                handler.close(); // Closes the clientSocket
            }catch(IOException e){
                System.out.println("socket is closed.");
            };                    
            return;
        }
        else{
            for(String[] headerName : headers){
                //If version isnt 1.2 - send an error message and disconnect
                if(headerName[0].compareTo("accept-version")==0 && headerName[1].compareTo("1.2")!=0){
                    handler.send(StompFrame.createErrorFrame("wrong accept-version",
                                                                                     "Accept-version should be 1.2.", receipt,
                                                                                    frame.toString()).toString());
                    terminate();
                    try{
                        handler.close(); // Closes the clientSocket
                    }catch(IOException e){
                        System.out.println("socket is closed.");
                    };                    
                    return;
                }
                //If host isnt stomp.cs.bgu.ac.il - send an error message and disconnect
                else if(headerName[0].compareTo("host")==0 && headerName[1].compareTo("stomp.cs.bgu.ac.il")!=0){
                    handler.send(StompFrame.createErrorFrame("wrong host",
                                                                "Host should be 'stomp.cs.bgu.ac.il'.", receipt,
                                                                                    frame.toString()).toString());
                    terminate();
                    try{
                        handler.close(); // Closes the clientSocket
                    }catch(IOException e){
                        System.out.println("socket is closed.");
                    };                    
                    return;
                }
                else if(headerName[0].compareTo("login")==0){
                    if(headerName[1].compareTo("")==0){
                        handler.send(StompFrame.createErrorFrame("missing login information",
                                                                             "You must insert username and passcode in order to login to the system.",
                                                                             receipt, frame.toString()).toString());
                        terminate();
                        try{
                            handler.close(); // Closes the clientSocket
                        }catch(IOException e){
                            System.out.println("socket is closed.");
                        };
                        return;
                    }
                    else{
                        userName = headerName[1];
                    }
                }
                else if(headerName[0].compareTo("passcode")==0){
                    if(headerName[1].compareTo("")==0){
                        handler.send(StompFrame.createErrorFrame("missing login information",
                                                                             "You must insert username and passcode in order to login to the system.",
                                                                             receipt, frame.toString()).toString());
                        terminate();
                        try{
                            handler.close(); // Closes the clientSocket
                        }catch(IOException e){
                            System.out.println("socket is closed.");
                        };                    
                        return;
                    }
                    else{
                        passcode = headerName[1];
                    }
                }
            }
        }


        User user = new User(userName, passcode, clientConnectionId);
        user.setUserConnection(true);

        //If the username doesnt exist already:
        if(connections.tryAddUser(userName,user)){
            handler.setUsername(userName);
            //Adds the client to the connected clients map
            connections.addConnection(handler);
            //Sends CONNECTED frame
            connections.send(clientConnectionId, StompFrame.createConnectedFrame().toString());
        }
        //If the username exists:
        else{
            //Tries connecting an existing user
            String s = connections.tryConnectExistingUser(userName, passcode);

            //check if user is already connected - error
            if(s.compareTo("alreadyConnected")==0){
                handler.send(StompFrame.createErrorFrame("User already logged in",
                "", receipt, frame.toString()).toString());
                connections.disconnect(clientConnectionId);
                return;
            }            
            //If the user isnt connected: but the password doesnt match - error
            else if(s.compareTo("wrongPass")==0){
                handler.send(StompFrame.createErrorFrame("Wrong password",
                                                                             "",
                                                                             receipt, frame.toString()).toString());
                connections.disconnect(clientConnectionId);
                return;
            }
            //If the user isnt connected: and the password is correct - connect
            else{
                handler.setUsername(userName);
                //Adds the client to the connected clients map
                connections.addConnection(handler);
                //Sends CONNECTED frame
                connections.send(clientConnectionId, StompFrame.createConnectedFrame().toString());
                return;
            }
        }

    }

    private void processSend(StompFrame frame){
        //Errors
        if(!connections.isHandlerConnected(clientConnectionId) || !(frame.getHeaders().size()==1)){
            connections.send(clientConnectionId, StompFrame.createErrorFrame("malformed frame",
                                                                            "", -1, frame.toString()).toString());
            connections.disconnect(clientConnectionId);
            return;
        }
        String channel;
        if((frame.getHeaders().get(0))[1].startsWith("/")){
            channel = (frame.getHeaders().get(0))[1].substring(1);
        }
        else{
            channel = (frame.getHeaders().get(0))[1];
        }
        // If the client is not subscribed to the channel
        if(!connections.isSubscribed(channel, clientConnectionId)){
            connections.send(clientConnectionId, StompFrame.createErrorFrame("unvailable destination",
                                                                             "You are not subscribed to this channel.",
                                                                              -1, frame.toString()).toString());
            connections.disconnect(clientConnectionId);
            return;
        }

        connections.send(channel,(StompFrame.createMessageFrame(connections.getMessageId(), channel,frame.getBody().toString())).toString());

        // Send a MESSAGE frame to all the subscribers of the channel
        /*for(int recieverConnectionId : connections.getChannelsSubs().get(channel)){
            StompMessagingProtocolImpl protocolOfReciever = (StompMessagingProtocolImpl)connections.getConnectionHandlers()
                                                            .get(recieverConnectionId).getProtocol();
            int subscriptionId = protocolOfReciever.getChannelsToSubsIds().get(channel);
            StompFrame messageFrame = StompFrame.createMessageFrame(connections.getMessageId(), channel, 
                                                               subscriptionId ,frame.getBody().toString());
            connections.send(recieverConnectionId, messageFrame.toString());
            connections.incrementMessageIdCounter();                
        }*/
    }

    private void processSubscribe(StompFrame frame){
        List<String[]> headers = frame.getHeaders();
        String channel = "";
        int subscriptionId = -1;
        int receiptId = -1;
        for(String[] headerName : headers){
            if(headerName[0].compareTo("destination")==0){
                if(headerName[1].startsWith("/")){
                    channel = headerName[1].substring(1);
                }
                else{
                    channel = headerName[1];
                }
            }
            else if(headerName[0].compareTo("id")==0){
                subscriptionId = Integer.parseInt(headerName[1]);
            }
            else if(headerName[0].compareTo("receipt")==0){
                receiptId = Integer.parseInt(headerName[1]);
            }
        }
        // Errors or missing input
        if(channel.equals("")){
            connections.send(clientConnectionId, StompFrame.createErrorFrame("malformed subscription request",
                                                                             "You must mention a topic to subscribe to.",
                                                                              receiptId, frame.toString()).toString());
            connections.disconnect(clientConnectionId);
            return;
        }

        // Add the client to the channel subscribers
        connections.addSubscribed(channel, clientConnectionId);
        // Save the subscription id
        addSubsIdsToChannels(subscriptionId, channel);
        addChannelsToSubsIds(channel, subscriptionId);
        // Send a receipt message
        StompFrame receiptFrame = StompFrame.createRecieptFrame(receiptId);
        connections.send(clientConnectionId, receiptFrame.toString());
    }

    private void processUnsubscribe(StompFrame frame){
        List<String[]> headers = frame.getHeaders();
        int subscriptionId = -1;
        int receiptId = -1;
        for(String[] headerName : headers){
            if(headerName[0].compareTo("id")==0){
                subscriptionId = Integer.parseInt(headerName[1]);
            }
            else if(headerName[0].compareTo("receipt")==0){
                receiptId = Integer.parseInt(headerName[1]);
            }
        }

        // Remove the client from the channel subscribers
        String channel = subsIdsToChannels.get(subscriptionId);
        connections.removeSubscribed(channel, clientConnectionId);

        // Remove the subscription id
        removeSubsIdsToChannels(subscriptionId);
        removeChannelsToSubsIds(channel);
        // Send a receipt message
        StompFrame receiptFrame = StompFrame.createRecieptFrame(receiptId);
        connections.send(clientConnectionId, receiptFrame.toString());
    }

    private void processDisconnect(StompFrame frame){
        List<String[]> headers = frame.getHeaders();
        int receiptId = Integer.parseInt(headers.get(0)[1]);
        // Send a receipt message
        StompFrame receiptFrame = StompFrame.createRecieptFrame(receiptId);
        connections.send(clientConnectionId, receiptFrame.toString());
        connections.disconnect(clientConnectionId);
    }

    public ConcurrentHashMap<Integer, String> getSubsIdsToChannels(){
        return subsIdsToChannels;
    }

    public void addSubsIdsToChannels(int subscriptionId, String channel){
        subsIdsToChannels.put(subscriptionId, channel);
    }

    public void removeSubsIdsToChannels(int subscriptionId){
        subsIdsToChannels.remove(subscriptionId);
    }

    public ConcurrentHashMap<String, Integer> getChannelsToSubsIds(){
        return channelsToSubsIds;
    }

    public void addChannelsToSubsIds(String channel, int subscriptionId){
        channelsToSubsIds.put(channel, subscriptionId);
    }

    public void removeChannelsToSubsIds(String channel){
        channelsToSubsIds.remove(channel);
    }


    public void setHandler(ConnectionHandler<String> handler){
        this.handler=handler;
    }

    public void terminate(){
        shouldTerminate = true;
    }

    public int getChannelId(String channel){
        int tmp = channelsToSubsIds.get(channel);
        return tmp;
    }

}
