package bgu.spl.net.srv;

public class User {

    private String username;
    private String passcode;
    private int clientId;
    private boolean connected;

    public User(){
        this.username = "";
        this.passcode = "";
        this.clientId = -1;
        this.connected=false;
    }

    public User(String username, String passcode, int clientId){
        this.username = username;
        this.passcode = passcode;
        this.clientId = clientId;
        this.connected = false;
    }

    public String getUsername(){
        return username;
    }

    public String getPasscode(){
        return passcode;
    }

    public int getClientId(){
        return clientId;
    }

    public boolean isConnected(){
        return connected;
    }

    public void setUserConnection(boolean isConnected){
        this.connected = isConnected;
    }
}
