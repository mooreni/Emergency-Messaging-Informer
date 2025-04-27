#include <thread>
#include <string>
#include <mutex>
#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"
using namespace std;

//Main function is down


std::mutex protocolMutex;
std::mutex socketMutex;
std::condition_variable socketCondition;


vector<string> parseInput(const string& input) { // Parsing the input commands into a vector
    vector<string> tokens;
    istringstream stream(input);
    string token;

    // Extract each token separated by spaces
    while (stream >> token) {
        tokens.push_back(token);
    }

    return tokens;
}
	
//The main cycle of the keyboard thread 
void keyboardThreadTask(ConnectionHandler &connectionHandler,StompProtocol &protocol,const vector<string>& parsedLoginInput)
{
	bool firstLoginFlag(true);

	while (true) {// When do we stop the threads?
		string userInput;
		vector<string> parsedInput;
		if(!firstLoginFlag){
			// Waits for command input from the keyboard
			getline(cin, userInput);

			parsedInput = parseInput(userInput);
		}
		//If its the first login
		else{
			parsedInput = parsedLoginInput;
			firstLoginFlag=false;
		}
		lock_guard<mutex> lock(protocolMutex);

		// Process through the protocol
		protocol.processKeyboardInput(parsedInput);
		vector<string>& framesToSend = protocol.getFramesToSend();

		if(!protocol.getIsSocketOpen()){
			connectionHandler.close();
		}

		for(string frame : framesToSend){
			if(frame.substr(0,7)=="CONNECT"){
				if(!connectionHandler.connect()){
					cerr << "Could not connect to server" << endl;
					break;
				}
				else{
					protocol.setIsSocketOpen(true);
					socketCondition.notify_one();
				}
			}
			if(protocol.getIsSocketOpen()){
				// Sends using the handler
				if(!connectionHandler.sendLine(frame)){
					cout << "Failed to deliver data to the server.";
					break;
				};
			}
		}
		framesToSend.clear();
	}
	
}

//The main cycle of the socket thread - waits for messages from the server
void socketThreadTask(ConnectionHandler& connectionHandler,StompProtocol& protocol){
	while (true) {// When do we stop the threads?
		std::unique_lock<std::mutex> lock(socketMutex);
        // Wait until socket is open; this blocks if the socket is closed
        socketCondition.wait(lock, [&protocol]() { return protocol.getIsSocketOpen(); });
		string recievedFrame = "";
		// Waits for the server to send data
		if(!connectionHandler.getLine(recievedFrame)){
			cout << "Failed to recieve data from the server." << endl;
			break;
			// Some error handling
		}
		if(recievedFrame!=""){
			// Handles the frame recieved according to it's type
			protocol.handleFrame(recievedFrame);
		}
	}
}

int main(int argc, char *argv[]) {
	
	// Waits for a 'login' input from the keyboard
	cout << "Waiting for login information." << endl;
	string userInput;
    getline(cin, userInput);

    // Parse the input
    vector<string> parsedLoginInput = parseInput(userInput);

	size_t colonPos = parsedLoginInput[1].find(':'); // Find the position of the colon
	//Server connection details
	string host;
    short port;

    if (colonPos != std::string::npos) { // Check if a colon was found
        host = parsedLoginInput[1].substr(0, colonPos); // Substring before the colon
        port =  static_cast<short>(std::stoi(parsedLoginInput[1].substr(colonPos + 1)));
	}
	else{
		std::cout << "Illegal host and port" << endl;
    	return 0;
	}

	//Create handler, protocol 
	ConnectionHandler handler (host, port);
	StompProtocol protocol(protocolMutex);

	//Create the socket thread with its own function and starts the main thread function
    thread socketThread(socketThreadTask, ref(handler), ref(protocol));
	keyboardThreadTask(handler, protocol, parsedLoginInput);


	//Wait for both to finish
    socketThread.join();

	//Close the connection once the threads finished their tasks
    handler.close();
    std::cout << "Client shut down." << endl;

    return 0;
}
