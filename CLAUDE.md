# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 3.5.10 WebSocket chat server demo project demonstrating STOMP (Simple Text Oriented Messaging Protocol) over WebSocket for real-time messaging. The project uses Java 17 and Gradle as the build tool.

**Important Note**: The codebase contains extensive inline comments in Korean explaining architectural differences between pure WebSocket and STOMP approaches. The STOMP implementation is marked as Spring MVC-only and will not work in a WebFlux environment.

## Build and Run Commands

### Build the project
```bash
./gradlew build
```

### Run the application
```bash
./gradlew bootRun
```

The server runs on default port 8080. Access the demo UI at: `http://localhost:8080/fib-stomp.html`

### Run tests
```bash
./gradlew test
```

### Clean build artifacts
```bash
./gradlew clean
```

### Create executable JAR
```bash
./gradlew bootJar
```
The JAR will be located in `build/libs/chat_server-0.0.1-SNAPSHOT.jar`

## Architecture

### Package Structure

```
com.example.chat_server
├── ChatServerApplication.java          # Main Spring Boot application entry point
└── fib_stomp/
    ├── config/
    │   └── WebSocketStompConfig.java   # STOMP WebSocket configuration
    ├── controller/
    │   └── FibStompController.java     # STOMP message handler
    └── dto/
        ├── FibRequest.java             # Request DTO for STOMP messages
        └── FibResponse.java            # Response DTO for STOMP messages
```

### STOMP Architecture

The project implements a STOMP-based WebSocket messaging system with the following key components:

**WebSocketStompConfig** (config/WebSocketStompConfig.java:30)
- Registers STOMP endpoint `/ws-fib` with SockJS fallback support
- Configures message broker with prefixes:
  - `/topic` - for 1:N broadcast (pub/sub pattern)
  - `/queue` - for 1:1 messaging
  - `/app` - application destination prefix for client messages

**FibStompController** (controller/FibStompController.java:32)
- `@MessageMapping("calculate")` - Handles Fibonacci calculation requests sent to `/app/calculate`
- `@SendTo("/topic/fib/results")` - Broadcasts responses to all subscribers
- `@MessageMapping("clear")` - Handles cache clearing without broadcasting response

### Message Flow

1. Client connects to `/ws-fib` using SockJS
2. Client subscribes to `/topic/fib/results` to receive broadcast messages
3. Client sends calculation request to `/app/calculate` with JSON body `{index: 5}`
4. Server processes via `FibStompController.calculate()`
5. Server broadcasts `FibResponse` to all `/topic/fib/results` subscribers
6. All connected clients receive the result in real-time

### Key Technical Details

- **STOMP Protocol**: Uses STOMP over WebSocket for standardized message routing
- **SockJS Fallback**: Automatically falls back to HTTP long-polling if WebSocket is unavailable
- **Auto Serialization**: Spring automatically converts JSON to/from DTOs using Jackson
- **Broadcast Pattern**: Single message sent by one client reaches all subscribers
- **MVC Limitation**: The `@EnableWebSocketMessageBroker` annotation only works in Spring MVC (Servlet-based), not WebFlux

### Static Resources

Frontend demo: `src/main/resources/static/fib-stomp.html`
- Uses SockJS client library for connection management
- Uses STOMP.js for STOMP protocol handling
- Demonstrates connect/disconnect, subscribe, send, and real-time updates

## Dependencies

- `spring-boot-starter-websocket` - WebSocket and STOMP support
- `lombok` - Reduces boilerplate code (compile-time only)
- `spring-boot-starter-test` - Testing framework with JUnit 5

## Code Comments

The codebase contains detailed architectural documentation in Korean within the source files themselves. These comments explain:
- Differences between pure WebSocket and STOMP approaches
- Message flow comparisons
- When to choose REST vs STOMP
- Protocol-level details of STOMP messaging

When modifying the code, preserve these educational comments as they provide valuable context for understanding the architectural decisions.
