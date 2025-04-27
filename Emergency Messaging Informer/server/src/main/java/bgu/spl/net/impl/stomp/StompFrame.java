package bgu.spl.net.impl.stomp;

import java.util.LinkedList;
import java.util.List;

//Added file - represents a single frame of whatever type
public class StompFrame {
    private String command;
    private List<String[]> headers;
    private String body;
    private String receiptId; //"" empty string if no receipt
    private boolean isMessage; //If the current frame is a message frame

    public StompFrame(){
        headers= new LinkedList<>();
        receiptId = "";
        isMessage = false;
    }

    public StompFrame(String message){
        headers= new LinkedList<>();
        receiptId = "";
        isMessage = false;
        parseFrame(message);
    }

    public void parseFrame(String message){
        String[] lines = message.toString().split("\n"); // Split the message by newlines
        command = lines[0]; // The first line is the command
        if(command.compareTo("MESSAGE")==0){
            isMessage=true;
        }

        StringBuilder bodyBuilder = new StringBuilder();

        int i = 1;
        // Parse headers
        while (i < lines.length && !lines[i].isEmpty()) { // Empty line indicates end of headers
            String line = lines[i];
            if (line.contains(":")) {
                String[] headerParts = line.split(":", 2); // Split into key and value
                if(headerParts[0].compareTo("receipt-id")==0){
                    receiptId = headerParts[1];
                }
                headers.add(new String[]{headerParts[0].trim(), headerParts[1].trim()});
            }
            i++;
        }
        // Skip empty line (if present) after headers
        if (i < lines.length && lines[i].isEmpty()) {
            i++;
        }

        // Parse body until the ^@ delimiter
        while (i < lines.length && !lines[i].equals("^@")) {
            bodyBuilder.append(lines[i]).append("\n");
            i++;
        }

        // Remove the last newline from the body (if present)
        body = bodyBuilder.length() > 0
                ? bodyBuilder.substring(0, bodyBuilder.length() - 1)
                : null;
    }

    public String getCommand(){
        return command;
    }

    public List<String[]> getHeaders(){
        return headers;
    }

    public String getBody(){
        return body;
    }

    public void setCommand(String command){
        this.command=command;
    }
    public void addHeader(String[] header){
        headers.add(header);
    }
    public void setBody(String body){
        this.body=body;
    }

    public void addToBody(String addToBody){
        body = body+addToBody;
    }

    public String toString(){
        String ret="";
        ret = ret+command+"\n";
        for(String[] header : headers){
            ret = ret + header[0] + ":" + header[1] + "\n";
        }
        ret = ret + "\n";
        if(body!=null && body.compareTo("")!=0){
            ret = ret + body + "\n";
        }
        ret = ret+"^@";
        return ret;
    }

    public static StompFrame createRecieptFrame(int receiptId){
        StompFrame frame = new StompFrame("RECEIPT\nreceipt-id:"+receiptId+"\n\n");
        return frame;
    }

    public static StompFrame createMessageFrame(int messageId, String channel, String frameBody){
        StompFrame frame = new StompFrame();
        frame.setCommand("MESSAGE");
        frame.addHeader(new String[] {"message-id",String.valueOf(messageId)});
        frame.addHeader(new String[] {"destination",channel});
        frame.setBody(frameBody);
        return frame;
    }


    public static StompFrame createMessageFrame(int messageId, String channel, int subscriptionId, String frameBody){
        StompFrame frame = new StompFrame("MESSAGE\nsubscription:"+subscriptionId+"\nmessage-id:"
                                            +messageId+"\ndestination:/"+channel+"\n\n"+frameBody+"\n");
        return frame;
    }

    public static StompFrame createErrorFrame(String shortDescription, String detailedDescription, int receiptId, String malformedFrame){
        String reciept = "";
        if (receiptId!=-1) {
            reciept = "reciept-id:"+receiptId+"\n";
        }
        StompFrame frame = new StompFrame("ERROR\n"+reciept+"message:malformed frame recieved\n\nThe message:"
                                            +shortDescription+"\n----\n"+malformedFrame+"\n----\n"
                                            +detailedDescription+"\n");
        return frame;
    }

    public static StompFrame createConnectedFrame(){
        StompFrame frame = new StompFrame("CONNECTED\nversion:1.2\n\n");
        return frame;
    }

    public String getReceiptId(){
        return receiptId;
    }

    public boolean isMessageFrame(){
        return isMessage;
    }
}
