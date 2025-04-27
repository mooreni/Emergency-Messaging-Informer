#pragma once

#include <vector>
#include <algorithm>
#include <string>
#include "../include/ConnectionHandler.h"
#include "../include/event.h"
#include "../include/StompFrame.h"
#include <mutex>
using namespace std;

// TODO: implement the STOMP protocol
class StompProtocol
{
    private:
        int subscriptionIDs;
        int recieptIDs;
        string username; //username the client is connected to, "" if isnt conencted (probably should be synchronized)
        string attempedUsername;    //The username we are tring to connect to, or "" if not connected (probably should be synchronized)
        vector<string> framesToSend;
        map<string, vector<Event>> eventsReported;
        map<int, string> receipts; //receipt id and the command (or frame?) it was sent with
        map<int, string> subsToChannels; //SubId and the channel it represents
        map<string, int> channelsToSubs;
        bool isSocketOpen;
        std::mutex& protocolMutex; 

        
    public:
        StompProtocol(std::mutex& Mutex);

        // Handling keyboard input
        void processKeyboardInput(const vector<string>& parsedInput);
        void processLogin(const vector<string>& parsedInput);
        void processLogout();
        void processSummary(const vector<string>& parsedInput);
        void processReport(const string& file);
        void processExit(const string& channel);
        void processJoin(const string& channel);

        // Handling frames arrived from the server
        void handleFrame(const string& recievedFrame);
        void handleCONNECTED();
        void handleRECEIPT(const StompFrame& frame);
        void handleMESSAGE(const StompFrame& frame);
        void handleERROR(const StompFrame& frame);

        vector<string>& getFramesToSend();

        //Synchronized getters and setters - check how to sync later
        string getAttemptedUsername();
        void setAttemptedUsername(string attempedUser);
        bool getIsSocketOpen();
        void setIsSocketOpen(bool flag);

        //Functions used for summary
        void writeEventsToFile(std::vector<Event>& events, const std::string& filePath);
        void sortEvents(std::vector<Event>& events);

        //Used for process
        void addReportedEvent(const std::string& channel, const Event& newEvent);
};
