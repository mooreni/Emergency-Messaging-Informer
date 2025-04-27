#include <vector>
#include <string>
#include "../include/StompProtocol.h"
#include "event.cpp"
#include "../include/StompFrame.h"
#include "../include/StompProtocol.h"
#include <mutex>


StompProtocol::StompProtocol(std::mutex& Mutex): subscriptionIDs(0), recieptIDs(0), username(""), attempedUsername(""), framesToSend(), eventsReported(),
                                 receipts(), subsToChannels(), channelsToSubs(), isSocketOpen(false), protocolMutex(Mutex)
{
}

void StompProtocol::processKeyboardInput(const vector<string>& parsedInput)
{
    if(parsedInput[0].compare("login")==0){
        processLogin(parsedInput);
    }
    else if(parsedInput[0].compare("logout")==0){
        processLogout();
    }
    else if(parsedInput[0].compare("summary")==0){
        processSummary(parsedInput);
    }
    else if(parsedInput[0].compare("report")==0){
        processReport(parsedInput[1]);
    }
    else if(parsedInput[0].compare("join")==0){
        processJoin(parsedInput[1]);
    }
    else if(parsedInput[0].compare("exit")==0){
        processExit(parsedInput[1]);
    }
}

void StompProtocol::processLogin(const vector<string>& parsedInput)
{  
    //If the handler isnt connected, try to connect to it
    if(getAttemptedUsername()==""){
        string ip("");
        string port("");

        size_t colonPos = parsedInput[1].find(':');
        if (colonPos != std::string::npos) {
            // Split the string
            ip = parsedInput[1].substr(0, colonPos);
            port = parsedInput[1].substr(colonPos + 1);
        }

        setAttemptedUsername(parsedInput[2]);

        //Handling the possible results will be in the error processing
        framesToSend.push_back(StompFrame::createConnectFrame(parsedInput[2], parsedInput[3]).toString());
    }
    //If already connected, print
    else{
        std::cout << "The client is already logged in, log out before trying again" << std::endl;
    }
}

void StompProtocol::processLogout()
{
    if(getAttemptedUsername()!=""){
        receipts.emplace(recieptIDs, "logout");
        framesToSend.push_back(StompFrame::createDisconnectFrame(recieptIDs).toString());
        recieptIDs++;
    }
    else{
        std::cout << "The client isnt logged in" << std::endl;
    }
}

void StompProtocol::processSummary(const vector<string>& parsedInput)
{ 
    vector<Event> eventsToSummarize;
    auto it = eventsReported.find(parsedInput[1]);
    if (it != eventsReported.end()) { 
        for (const auto& item : it->second) {
            if (item.get_eventOwnerUser() == parsedInput[2]) { // Check if the string matches the user name
                eventsToSummarize.push_back(item);
            }
        }
    }

    writeEventsToFile(eventsToSummarize, parsedInput[3]);
}

void StompProtocol::processReport(const string& file)
{
    names_and_events channelNameAndEvents = parseEventsFile(file);
    for(Event event: channelNameAndEvents.events){
        //Updates who sent the event
        event.setEventOwnerUser(username);
        //Adds the event to the reported events map
        //addReportedEvent(channelNameAndEvents.channel_name, event);
        //Creates a send frame and adds it to the framesToSend vector
        StompFrame sendFrame = StompFrame::createSendFrame(channelNameAndEvents.channel_name, event.toString());
        framesToSend.push_back(sendFrame.toString());
    }
    

}

void StompProtocol::processExit(const string& channel)
{
    receipts.emplace(recieptIDs, "unsub"+channel);
    framesToSend.push_back(StompFrame::createUnsubscribeFrame(channelsToSubs[channel], recieptIDs).toString());
    recieptIDs++;
}

void StompProtocol::processJoin(const string& channel)
{   
    if(channelsToSubs.count(channel)){
        cout << "You are already subcribed to this channel.";
    } else{
        subsToChannels.emplace(subscriptionIDs, channel);
        channelsToSubs.emplace(channel, subscriptionIDs);
        receipts.emplace(recieptIDs, "sub"+channel);
        framesToSend.push_back(StompFrame::createSubscribeFrame(channel, subscriptionIDs, recieptIDs).toString());
        subscriptionIDs++;
        recieptIDs++;
    }
}


//=======================================================================
//Processing received frames
void StompProtocol::handleFrame(const string &recievedFrame)
{
    StompFrame frame(recievedFrame);
    if(frame.getCommand()=="CONNECTED"){
        handleCONNECTED();
    }
    else if(frame.getCommand()=="RECEIPT"){
        handleRECEIPT(frame);
    }
    else if(frame.getCommand()=="MESSAGE"){
        handleMESSAGE(frame);
    }
    else if(frame.getCommand()=="ERROR"){
        handleERROR(frame);
    }
}

void StompProtocol::handleCONNECTED()
{
    username = getAttemptedUsername();
    cerr << "Login successful" << endl;
}

void StompProtocol::handleRECEIPT(const StompFrame& frame)
{
    int receiptID = stoi(frame.getHeaders()[0].second);
    string receiptCommand = receipts.at(receiptID);
    if(receiptCommand=="logout"){
        username = "";
        setAttemptedUsername("");
        eventsReported.clear();
        subsToChannels.clear();
        channelsToSubs.clear();
        lock_guard<mutex> lock(protocolMutex);
        setIsSocketOpen(false);
        cerr << "Logout successful" << endl;
    }
    else if(receiptCommand.rfind("sub",0)==0){
        string channel = receiptCommand.substr(3);
        cout << "Joined channel " << channel << endl;
    }
    else if(receiptCommand.rfind("unsub",0)==0){
        string channel = receiptCommand.substr(5);
        int subID = channelsToSubs[channel];
        channelsToSubs.erase(channel);
        subsToChannels.erase(subID);
        cout << "Exited channel " << channel << endl;
    }
}

void StompProtocol::handleMESSAGE(const StompFrame& frame)
{
    int subID = -1;
    for(pair<string, string> header:frame.getHeaders()){
        if(header.first.compare("subscription")==0){
            subID = stoi(header.second);
            break;
        }
    }
    string channel = subsToChannels[subID];
    Event event(frame.getBody());
    addReportedEvent(channel, event);
}

void StompProtocol::handleERROR(const StompFrame& frame)
{
    //Error frames lead to closing the socket
    username = "";
    setAttemptedUsername("");
    eventsReported.clear();
    subsToChannels.clear();
    channelsToSubs.clear();
    lock_guard<mutex> lock(protocolMutex);
    setIsSocketOpen(false);;

    std::cout << frame.toString() << std::endl;
}


//==============================================
//Getters
vector<string>& StompProtocol::getFramesToSend()
{
    return framesToSend;
}

//These should be synchronized

string StompProtocol::getAttemptedUsername()
{
    return attempedUsername;
}

void StompProtocol::setAttemptedUsername(string attempedUser)
{
    attempedUsername = attempedUser;
}

bool StompProtocol::getIsSocketOpen()
{
    return isSocketOpen;
}

void StompProtocol::setIsSocketOpen(bool flag)
{
    isSocketOpen=flag;
}

//======================================================
//Used for summary
void StompProtocol::writeEventsToFile(std::vector<Event>& events, const std::string& filePath) {
    // Sort events before writing
    sortEvents(events);

    std::ofstream outFile(filePath, std::ios::trunc);

    if (!outFile.is_open()) {
        std::cerr << "Error opening file: " << filePath << std::endl;
        return;
    }

    if (events.empty()) {
        std::cerr << "No events to write!" << std::endl;
        return;
    }

    const std::string channelName = events[0].get_channel_name();
    outFile << "Channel " << channelName << "\n";

    // Stats section
    int totalReports = events.size();
    int activeReports = 0;

    for (const auto& event : events) {
        if (event.get_general_information().count("active") > 0 && 
            event.get_general_information().at("active") == "true") {
            activeReports++;
        }
    }

    outFile << "Stats :\n";
    outFile << "Total : " << totalReports << "\n";
    outFile << "active : " << activeReports << "\n";
    outFile << "forces arrival at scene : " << activeReports << "\n";

    // Event Reports section
    outFile << "\nEvent Reports :\n";

    for (size_t i = 0; i < events.size(); ++i) {
        outFile << "\nReport_" << (i + 1) << " :\n";
        outFile << events[i].toStringSummary();  // Format each event using toString
    }

    outFile.close();
    std::cout << "File written successfully to " << filePath << std::endl;
}

void StompProtocol::sortEvents(std::vector<Event>& events) {
    std::sort(events.begin(), events.end(), [](const Event& a, const Event& b) {
        if (a.get_date_time() != b.get_date_time()) {
            return a.get_date_time() < b.get_date_time();  // Sort by date_time
        }
        return a.get_name() < b.get_name();  // Sort by name lexicographically
    });
}

// Adding events to the reported events map
void StompProtocol::addReportedEvent(const std::string& channel, const Event& newEvent) {
    // Check if the channel already exists
    auto it = eventsReported.find(channel);

    if (it != eventsReported.end()) {
            // Channel exists, insert the event into its list
            it->second.push_back(newEvent);
    } else {
            // Channel does not exist, create the channel and add the event
            eventsReported[channel] = {newEvent};
    }
}
