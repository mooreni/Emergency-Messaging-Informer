#include "../include/StompFrame.h"
#include "StompFrame.h"


using namespace std; // Add this line to use the standard library types without std::

// Default constructor
StompFrame::StompFrame()
    : command(""), headers(), body(""), receiptId(""), isMessage(false) {
}

// Constructor that initializes the frame using a message string
StompFrame::StompFrame(const string &message)
    : command(""), headers(), body(""), receiptId(""), isMessage(false) {
    parseFrame(message);
}

// Parses the STOMP frame from the message string
void StompFrame::parseFrame(const string &message) {
    stringstream ss(message);
    string line;
    string eventDescription;

    // Read lines and split the message
    getline(ss, line);
    command = line; // The first line is the command

    if (command == "MESSAGE") {
        isMessage = true;
    }

    // Parse headers
    while (getline(ss, line) && !line.empty()) {
        size_t colonPos = line.find(":");
        if (colonPos != string::npos) {
            string key = line.substr(0, colonPos);
            string value = line.substr(colonPos + 1);
            if (key == "receipt-id") {
                receiptId = value;
            }
            headers.push_back({key, value});
        }
    }

    // Skip empty line after headers (if present)
    //getline(ss, line);

    // Parse the body until the ^@ delimiter
    while (getline(ss, line) && line != "\0") {
        eventDescription += line + "\n";
    }

    // Remove the last newline from the body (if present)
    if (!eventDescription.empty()) {
        body = eventDescription.substr(0, eventDescription.length() - 1);
    }
}


// Getter methods
string StompFrame::getCommand() const {
    return command;
}

const vector<pair<string, string>>& StompFrame::getHeaders() const {
    return headers;
}

string StompFrame::getBody() const {
    return body;
}

// Setter methods
void StompFrame::setCommand(const string &command) {
    this->command = command;
}

void StompFrame::addHeader(const string &key, const string &value) {
    headers.push_back({key, value});
}

void StompFrame::setBody(const string &body) {
    this->body = body;
}

void StompFrame::addToBody(const string &addToBody) {
    body += addToBody;
}



// Convert the object back to string
string StompFrame::toString() const {
    string ret = command + "\n";
    for (const auto &header : headers) {
        ret += header.first + ":" + header.second + "\n";
    }
    ret += "\n";

    if (!body.empty()) {
        ret += body + "\n";
    }

    ret += "\0";
    return ret;
}

StompFrame StompFrame::createSendFrame(const string &channel, const string &body) 
{
    StompFrame frame;
    frame.setCommand("SEND");
    frame.addHeader("destination",channel);
    frame.setBody(body);

    return frame;
}

StompFrame StompFrame::createConnectFrame(const string& username, const string& password) 
{
    StompFrame frame;
    frame.setCommand("CONNECT");
    frame.addHeader("accept-version","1.2");
    frame.addHeader("host","stomp.cs.bgu.ac.il");
    frame.addHeader("login",username);
    frame.addHeader("passcode",password);
    return frame;
}

StompFrame StompFrame::createDisconnectFrame(const int reciept)
{
    StompFrame frame;
    frame.setCommand("DISCONNECT");
    frame.addHeader("receipt",to_string(reciept));
    return frame;
}
StompFrame StompFrame::createSubscribeFrame(const string &channel, const int subID, const int reciept)
{
    StompFrame frame;
    frame.setCommand("SUBSCRIBE");
    frame.addHeader("destination", channel);
    frame.addHeader("id", to_string(subID));
    frame.addHeader("receipt", to_string(reciept));
    return frame;
}

StompFrame StompFrame::createUnsubscribeFrame(const int subID, const int reciept)
{
    StompFrame frame;
    frame.setCommand("UNSUBSCRIBE");
    frame.addHeader("id", to_string(subID));
    frame.addHeader("receipt", to_string(reciept));
    return frame;
}
