#ifndef STOMPFRAME_H
#define STOMPFRAME_H

#include <string>
#include <vector>
#include <sstream>
#include <iostream>

using namespace std; // Add this line to use the standard library types without std::

class StompFrame {
private:
    string command;
    vector<pair<string, string>> headers; // Stores pairs of header key and value
    string body;
    string receiptId; // Empty string if no receipt
    bool isMessage; // If the current frame is a message frame

public:
    // Constructors
    StompFrame();
    StompFrame(const string &message);

    // Method to parse the STOMP frame message
    void parseFrame(const string &message);
    void prepareForSummary();

    // Getter methods
    string getCommand() const;
    const vector<pair<string, string>>& getHeaders() const;
    string getBody() const;

    // Setter methods
    void setCommand(const string &command);
    void addHeader(const string &key, const string &value);
    void setBody(const string &body);
    void addToBody(const string &addToBody);

    // Convert the object back to string (for sending to server)
    string toString() const;

    static StompFrame createSendFrame(const string& channel, const string& body);
    static StompFrame createConnectFrame(const string& username, const string& password);
    static StompFrame createDisconnectFrame(const int receipt);
    static StompFrame createSubscribeFrame(const string& channel, const int subID, const int reciept);
    static StompFrame createUnsubscribeFrame(const int subID, const int reciept);


};

#endif // STOMPFRAME_H
