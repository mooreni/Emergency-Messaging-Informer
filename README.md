
---

## Emergency Messaging Informer

```markdown
# Emergency Messaging Informer

STOMP 1.2–based emergency-reporting system with Java server (TPC & Reactor) and multithreaded C++ client.

## 🚀 Features
- **Java STOMP Server:**  
  - Supports both Thread-Per-Client and Reactor patterns  
  - Manages CONNECT/SUBSCRIBE/SEND/UNSUBSCRIBE/DISCONNECT  
- **C++ STOMP Client:**  
  - Separate stdin & socket threads  
  - JSON event file parsing for `report`  
  - Commands: `login`, `join`, `exit`, `report`, `summary`, `logout`

## 🛠 Tech Stack
- **Server:** Java 11, Maven  
- **Client:** C++ (C++11), Makefile, `std::thread`, `std::mutex`
