package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.Server;

public class StompServer {

    public static void main(String[] args) {

        if(args[1].compareTo("tpc")==0){
            Server.threadPerClient(
                Integer.parseInt(args[0]), //port
                () -> new StompMessagingProtocolImpl(), //protocol factory
                () -> new StompEncoderDecoder() //message encoder decoder factory
            ).serve();  
        }
        else if(args[1].compareTo("reactor")==0){
            Server.reactor(Runtime.getRuntime().availableProcessors(), 
                Integer.parseInt(args[0]), 
                () -> new StompMessagingProtocolImpl(), //protocol factory
                () -> new StompEncoderDecoder() //message encoder decoder factory)
                ).serve();
        }
        else{
            System.out.println("invalid server type");
        }
    }
}
/*
login 127.0.0.1:7777 meni films
login 127.0.0.1:7777 guy films

join fire_dept
join ambulance
report /root/SPL251-Assignment3/JsonExampleEvents/firefighters.json
report /root/SPL251-Assignment3/JsonExampleEvents/ambulance.json

summary fire_dept meni /root/SPL251-Assignment3/JsonExampleEvents/reportedFires.json
summary ambulance meni /root/SPL251-Assignment3/JsonExampleEvents/ambulanceCalls.json

nitzan's path:
report /workspaces/SPL251-Assignment3/SPL251-Assignment3/JsonExampleEvents/firefighters.json
*/