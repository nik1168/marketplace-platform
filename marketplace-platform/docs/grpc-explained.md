# gRPC — Explained from Scratch

## What Problem Does gRPC Solve?

When you have two separate applications (like our Order Service and Inventory Service), they need a way to talk to each other over the network. There are two popular approaches:

```mermaid
graph TB
    subgraph "Option A: REST (what most people know)"
        A1["Order Service"] -->|"HTTP request<br/>JSON text:<br/>{'productId': 'prod-1', 'quantity': 3}"| A2["Inventory Service"]
        A2 -->|"HTTP response<br/>JSON text:<br/>{'available': true, 'currentStock': 500}"| A1
    end

    subgraph "Option B: gRPC (what we use for internal calls)"
        B1["Order Service"] -->|"HTTP/2 request<br/>Binary data:<br/>0A 06 70 72 6F 64 2D 31 10 03"| B2["Inventory Service"]
        B2 -->|"HTTP/2 response<br/>Binary data:<br/>08 01 10 F4 03"| B1
    end

    style A1 fill:#e3f2fd
    style A2 fill:#e3f2fd
    style B1 fill:#e8f5e9
    style B2 fill:#e8f5e9
```

Both accomplish the same thing — one service asks another service a question and gets an answer. But gRPC does it **faster** because:

1. **Binary data** instead of text — computers process binary much faster than parsing JSON text
2. **HTTP/2** instead of HTTP/1.1 — can send multiple requests at once over a single connection
3. **Typed contract** — both sides agree in advance on exactly what data they'll exchange (no surprises)

---

## The Analogy: Phone Calls vs Letters

Think of the difference between REST and gRPC like this:

**REST is like writing letters in English:**
- Both sides agree to communicate in English (JSON format)
- You write a letter (HTTP request with JSON body)
- The mail carrier delivers it (HTTP/1.1)
- The recipient reads the English text, interprets it, and writes back
- Easy for humans to read, but slow to process

**gRPC is like a phone call with a prepared script:**
- Both sides have the same script in front of them (the `.proto` file)
- When you call, you say "Line 1, field 2 is 3" (binary-encoded)
- The other side knows exactly what "Line 1, field 2" means because they have the script
- Much faster because there's no interpretation needed — but harder for humans to read

```mermaid
graph LR
    subgraph "REST — The Letter"
        direction TB
        R1["📝 Write in English<br/>(Human-readable JSON)"]
        R2["📬 Send via mail carrier<br/>(HTTP/1.1)"]
        R3["📖 Recipient reads & interprets<br/>(Parse JSON to objects)"]
        R1 --> R2 --> R3
    end

    subgraph "gRPC — The Phone Call"
        direction TB
        G1["📋 Both have same script<br/>(Shared .proto file)"]
        G2["📞 Call directly<br/>(HTTP/2, binary)"]
        G3["💡 Instant understanding<br/>(No parsing needed)"]
        G1 --> G2 --> G3
    end

    style R1 fill:#e3f2fd
    style R2 fill:#e3f2fd
    style R3 fill:#e3f2fd
    style G1 fill:#e8f5e9
    style G2 fill:#e8f5e9
    style G3 fill:#e8f5e9
```

---

## The .proto File — The Shared Script

The foundation of gRPC is the `.proto` file. It's a contract that both services agree on — like a menu at a restaurant. It defines:
1. What **operations** (RPCs) are available — "what can I order?"
2. What **data** each operation expects and returns — "what comes with each dish?"

Here's our actual proto file (`proto/inventory.proto`), explained piece by piece:

### Defining the Service (the menu)

```protobuf
service InventoryService {
  rpc CheckStock (CheckStockRequest) returns (CheckStockResponse);
  rpc ReserveStock (ReserveStockRequest) returns (ReserveStockResponse);
}
```

This is like a restaurant menu with two items:
- **CheckStock** — "Do you have enough of this product?"
- **ReserveStock** — "Hold some stock for my order"

```mermaid
graph TB
    subgraph "The Menu (InventoryService)"
        M1["📋 CheckStock<br/>━━━━━━━━━━━━<br/>Input: product ID + quantity<br/>Output: yes/no + current stock<br/><br/>'Do you have 3 mice?'<br/>'Yes, 500 available'"]

        M2["📋 ReserveStock<br/>━━━━━━━━━━━━<br/>Input: product ID + quantity + order ID<br/>Output: success/failure<br/><br/>'Hold 3 mice for order #123'<br/>'Done, they're reserved!'"]
    end

    style M1 fill:#e3f2fd
    style M2 fill:#e8f5e9
```

### Defining the Messages (what goes back and forth)

Each operation has a **request** (what you send) and a **response** (what you get back). These are called "messages" in proto language:

```protobuf
message CheckStockRequest {
  string product_id = 1;    // Which product to check
  int32 quantity = 2;        // How many you need
}

message CheckStockResponse {
  bool available = 1;        // true/false: is there enough?
  int32 current_stock = 2;   // How many are in stock right now
}
```

The numbers (`= 1`, `= 2`) are **field tags**, not values. They tell the binary encoder "field 1 is product_id, field 2 is quantity." This is how binary encoding works — instead of sending the field name as text, it just sends the number.

```mermaid
graph LR
    subgraph "JSON (REST) — sends field names as text"
        JSON["{'product_id': 'prod-1', 'quantity': 3}<br/>━━━━━━━━━━━━<br/>42 bytes of text<br/>Every character is a byte"]
    end

    subgraph "Protobuf (gRPC) — sends field numbers"
        PB["field-1: 'prod-1', field-2: 3<br/>━━━━━━━━━━━━<br/>12 bytes of binary<br/>~70% smaller!"]
    end

    style JSON fill:#fff3e0
    style PB fill:#c8e6c9
```

### Our Complete Proto File

Here's the full file with all four messages:

```mermaid
graph TB
    subgraph "proto/inventory.proto — The Complete Contract"
        subgraph "CheckStock Operation"
            CS_REQ["📤 CheckStockRequest<br/>━━━━━━━━━━━━<br/>product_id: string<br/>quantity: int32"]
            CS_RES["📥 CheckStockResponse<br/>━━━━━━━━━━━━<br/>available: bool<br/>current_stock: int32"]
            CS_REQ -->|"sends"| CS_RES
        end

        subgraph "ReserveStock Operation"
            RS_REQ["📤 ReserveStockRequest<br/>━━━━━━━━━━━━<br/>product_id: string<br/>quantity: int32<br/>order_id: string"]
            RS_RES["📥 ReserveStockResponse<br/>━━━━━━━━━━━━<br/>success: bool<br/>message: string"]
            RS_REQ -->|"sends"| RS_RES
        end
    end

    style CS_REQ fill:#e3f2fd
    style CS_RES fill:#e8f5e9
    style RS_REQ fill:#e3f2fd
    style RS_RES fill:#e8f5e9
```

---

## How Code Gets Generated from the Proto File

Here's something really cool about gRPC — you don't write the networking code yourself. The `.proto` file is fed into a **code generator** that creates Java classes automatically.

```mermaid
graph TB
    subgraph "What You Write (1 file)"
        PROTO["📄 inventory.proto<br/>━━━━━━━━━━━━<br/>33 lines of proto definition<br/>Written by a human"]
    end

    subgraph "What Gets Generated (many files)"
        GEN1["📄 CheckStockRequest.java<br/>Builder pattern, serialization,<br/>getters, equals, hashCode"]
        GEN2["📄 CheckStockResponse.java<br/>Same thing for the response"]
        GEN3["📄 ReserveStockRequest.java<br/>Same thing for reserve request"]
        GEN4["📄 ReserveStockResponse.java<br/>Same thing for reserve response"]
        GEN5["📄 InventoryServiceGrpc.java<br/>Client stub + server base class<br/>All networking code built in"]
    end

    PROTO -->|"protoc compiler<br/>(runs during Maven build)"| GEN1
    PROTO --> GEN2
    PROTO --> GEN3
    PROTO --> GEN4
    PROTO --> GEN5

    style PROTO fill:#fff3e0
    style GEN1 fill:#e8f5e9
    style GEN2 fill:#e8f5e9
    style GEN3 fill:#e8f5e9
    style GEN4 fill:#e8f5e9
    style GEN5 fill:#e8f5e9
```

This means:
- You **never** manually write request/response Java classes for gRPC
- You **never** manually write networking/serialization code
- If you change the proto file, the generated code updates automatically on the next build
- Both services generate code from the **same proto file**, so they can never get out of sync

---

## The Server Side — Inventory Service Answers the Phone

The Inventory Service implements the gRPC server — it's the one answering the "phone calls" from the Order Service.

### What the developer writes

The developer only writes the **business logic**. The generated code handles all the networking:

```mermaid
graph TB
    subgraph "Generated Code (you don't write this)"
        BASE["InventoryServiceGrpc.InventoryServiceImplBase<br/>━━━━━━━━━━━━━━━<br/>• Listens on port 9090<br/>• Accepts incoming gRPC connections<br/>• Deserializes binary → Java objects<br/>• Routes to the correct method<br/>• Serializes response → binary<br/>• Sends response back"]
    end

    subgraph "Your Code (you write this)"
        SERVER["InventoryGrpcServer<br/>extends InventoryServiceImplBase<br/>━━━━━━━━━━━━━━━<br/>• checkStock(): look up product, check availability<br/>• reserveStock(): update stock, record event"]
    end

    SERVER -->|"inherits from"| BASE

    style BASE fill:#e0e0e0
    style SERVER fill:#e8f5e9
```

### How a checkStock call works

```mermaid
sequenceDiagram
    participant OS as Order Service<br/>(gRPC Client)
    participant NET as Generated Networking Code<br/>(invisible to developer)
    participant SRV as InventoryGrpcServer<br/>(your code)
    participant STK as InventoryStockService<br/>(business logic)
    participant DB as MongoDB

    OS->>NET: Binary data arrives on port 9090
    NET->>NET: Deserialize binary → CheckStockRequest object
    NET->>SRV: checkStock(request, responseObserver)

    Note over SRV: This is the only part<br/>the developer writes

    SRV->>STK: checkStock("prod-1", 3)
    STK->>DB: findByProductId("prod-1")
    DB-->>STK: Product(currentStock=500, reserved=2)
    STK->>STK: available = (500 - 2) >= 3? Yes!
    STK-->>SRV: true

    SRV->>SRV: Build CheckStockResponse<br/>(available=true, currentStock=498)

    SRV->>NET: responseObserver.onNext(response)
    SRV->>NET: responseObserver.onCompleted()

    NET->>NET: Serialize response → binary
    NET-->>OS: Binary response sent back
```

### The responseObserver pattern

You might have noticed the `responseObserver` — this is how gRPC sends responses. It's different from REST where you just `return` a value:

```mermaid
graph TB
    subgraph "REST way (familiar)"
        REST["@GetMapping('/stock')<br/>public StockResponse checkStock() {<br/>    ...<br/>    return response;  // Just return it!<br/>}"]
    end

    subgraph "gRPC way (observer pattern)"
        GRPC["public void checkStock(request, responseObserver) {<br/>    ...<br/>    responseObserver.onNext(response);  // Send the response<br/>    responseObserver.onCompleted();     // Say 'I'm done'<br/>}"]
    end

    Note["Why the extra step? Because gRPC supports<br/>STREAMING — you could send multiple responses<br/>over time. onCompleted() says 'that was the last one.'<br/><br/>In our case we only send one response,<br/>but the pattern supports advanced use cases."]

    style REST fill:#e3f2fd
    style GRPC fill:#e8f5e9
    style Note fill:#fff9c4,stroke:none
```

---

## The Client Side — Order Service Makes the Phone Call

The Order Service has a gRPC **client** that calls the Inventory Service. Again, the generated code handles all networking — the developer just calls methods:

```mermaid
graph TB
    subgraph "What the developer writes"
        CLIENT["InventoryGrpcClient.java<br/>━━━━━━━━━━━━━━━<br/>checkStock(productId, quantity):<br/>  Build request → call stub → return result<br/><br/>reserveStock(productId, quantity, orderId):<br/>  Build request → call stub → return result"]
    end

    subgraph "What happens behind the scenes"
        STUB["Generated InventoryServiceBlockingStub<br/>━━━━━━━━━━━━━━━<br/>• Maintains HTTP/2 connection to port 9090<br/>• Serializes request → binary<br/>• Sends over the network<br/>• Waits for response<br/>• Deserializes binary → response object<br/>• Returns to caller"]
    end

    CLIENT -->|"calls"| STUB

    style CLIENT fill:#e8f5e9
    style STUB fill:#e0e0e0
```

### How the client makes a call

```mermaid
sequenceDiagram
    participant SVC as OrderService<br/>(needs stock info)
    participant CLIENT as InventoryGrpcClient<br/>(your wrapper code)
    participant STUB as BlockingStub<br/>(generated code)
    participant NET as Network<br/>(HTTP/2)
    participant IS as Inventory Service<br/>(port 9090)

    SVC->>CLIENT: checkStock("prod-1", 3)

    CLIENT->>CLIENT: Build request:<br/>CheckStockRequest.newBuilder()<br/>.setProductId("prod-1")<br/>.setQuantity(3)<br/>.build()

    CLIENT->>STUB: inventoryStub.checkStock(request)
    STUB->>STUB: Serialize to binary
    STUB->>NET: Send binary over HTTP/2

    NET->>IS: Binary arrives at port 9090
    IS-->>NET: Binary response
    NET-->>STUB: Binary arrives back
    STUB->>STUB: Deserialize to CheckStockResponse
    STUB-->>CLIENT: response object

    CLIENT->>CLIENT: return response.getAvailable()
    CLIENT-->>SVC: true
```

### The "BlockingStub" — what does "blocking" mean?

```mermaid
graph TB
    subgraph "BlockingStub (what we use)"
        BS["inventoryStub.checkStock(request)<br/>━━━━━━━━━━━━━━━<br/>The thread WAITS here until<br/>the Inventory Service responds.<br/><br/>Simple to use — just like a<br/>regular method call.<br/><br/>Works great with virtual threads<br/>because waiting is 'free'."]
    end

    subgraph "AsyncStub (the alternative)"
        AS["inventoryStub.checkStock(request, callback)<br/>━━━━━━━━━━━━━━━<br/>The thread CONTINUES immediately.<br/>When the response arrives later,<br/>the callback function is called.<br/><br/>More complex code, but the thread<br/>is never blocked."]
    end

    Note["We chose BlockingStub because virtual threads<br/>make blocking almost free. The virtual thread<br/>'parks' during the wait, costing nearly nothing."]

    style BS fill:#c8e6c9
    style AS fill:#fff3e0
    style Note fill:#fff9c4,stroke:none
```

---

## The @GrpcClient Annotation — Spring Boot Magic

In our project, we use the `grpc-spring-boot-starter` library, which makes gRPC feel as easy as writing a REST controller. Here's the magic:

```mermaid
graph TB
    subgraph "Without Spring Boot (manual setup)"
        M1["1. Create a ManagedChannel<br/>(network connection to port 9090)"]
        M2["2. Configure TLS, timeouts, retries"]
        M3["3. Create the stub from the channel"]
        M4["4. Handle connection failures"]
        M5["5. Shut down channel when app stops"]
        M1 --> M2 --> M3 --> M4 --> M5
    end

    subgraph "With Spring Boot (what we do)"
        S1["@GrpcClient('inventory-service')<br/>private InventoryServiceBlockingStub stub;<br/><br/>That's it. Spring does the rest."]
    end

    subgraph "application.yml"
        Y1["grpc:<br/>  client:<br/>    inventory-service:<br/>      address: static://localhost:9090<br/>      negotiation-type: plaintext"]
    end

    S1 ---|"reads config from"| Y1

    style M1 fill:#ffcdd2
    style M2 fill:#ffcdd2
    style M3 fill:#ffcdd2
    style M4 fill:#ffcdd2
    style M5 fill:#ffcdd2
    style S1 fill:#c8e6c9
    style Y1 fill:#e3f2fd
```

The `@GrpcClient("inventory-service")` annotation tells Spring: "Look up the connection details for 'inventory-service' in the config file, create a channel, create a stub, and inject it here." All in one line.

Similarly on the server side, `@GrpcService` replaces pages of server setup code:

```mermaid
graph TB
    subgraph "Without Spring Boot"
        M1B["1. Create a gRPC Server"]
        M2B["2. Bind to port 9090"]
        M3B["3. Register your service implementation"]
        M4B["4. Start the server"]
        M5B["5. Add shutdown hooks"]
        M1B --> M2B --> M3B --> M4B --> M5B
    end

    subgraph "With Spring Boot"
        S1B["@GrpcService<br/>public class InventoryGrpcServer extends ...<br/><br/>That's it. Spring starts the server automatically."]
    end

    style M1B fill:#ffcdd2
    style M2B fill:#ffcdd2
    style M3B fill:#ffcdd2
    style M4B fill:#ffcdd2
    style M5B fill:#ffcdd2
    style S1B fill:#c8e6c9
```

---

## gRPC vs REST vs Kafka — When to Use Each

In our project, we use all three. Here's a simple decision guide:

```mermaid
graph TB
    START{"What kind of<br/>communication?"}

    START -->|"External client<br/>(browser, mobile app)"| REST
    START -->|"Internal service-to-service<br/>(need instant answer)"| GRPC
    START -->|"Internal service-to-service<br/>(can happen later)"| KAFKA

    REST["🌐 REST (HTTP/JSON)<br/>━━━━━━━━━━━━━━━<br/>• Human-readable<br/>• Easy to test with curl<br/>• Every developer knows it<br/>• Browsers support it natively<br/><br/>We use it for:<br/>• POST /api/orders<br/>• GET /api/products"]

    GRPC["☎️ gRPC (HTTP/2 + binary)<br/>━━━━━━━━━━━━━━━<br/>• Faster than REST<br/>• Typed contract (.proto)<br/>• No interpretation needed<br/>• Great for internal calls<br/><br/>We use it for:<br/>• CheckStock<br/>• ReserveStock"]

    KAFKA["📬 Kafka (async messages)<br/>━━━━━━━━━━━━━━━<br/>• Fire and forget<br/>• Survives service outages<br/>• No one waits<br/>• Great for events<br/><br/>We use it for:<br/>• OrderPlaced events<br/>• StockUpdated events"]

    style REST fill:#e3f2fd
    style GRPC fill:#e8f5e9
    style KAFKA fill:#fce4ec
```

### Real-world analogy

```mermaid
graph TB
    subgraph "Three Ways to Communicate"
        A["🌐 REST<br/>= Writing an email<br/><br/>Anyone can read it,<br/>easy to understand,<br/>works everywhere,<br/>but not the fastest"]

        B["☎️ gRPC<br/>= Making a phone call<br/><br/>Instant back-and-forth,<br/>both sides need the<br/>same 'language' (proto),<br/>very efficient"]

        C["📬 Kafka<br/>= Dropping a letter<br/>in a mailbox<br/><br/>You don't wait for a reply,<br/>the letter is safe even if<br/>the recipient is away,<br/>they'll read it when ready"]
    end

    style A fill:#e3f2fd
    style B fill:#e8f5e9
    style C fill:#fce4ec
```

---

## The Complete gRPC Flow in Our Project

Here's everything that happens when the Order Service checks stock via gRPC, from top to bottom:

```mermaid
graph TB
    subgraph "1. Developer wrote the contract once"
        PROTO["proto/inventory.proto<br/>Defines: CheckStock, ReserveStock"]
    end

    subgraph "2. Maven build generates code for both services"
        GEN_OS["Order Service gets:<br/>• CheckStockRequest.java<br/>• CheckStockResponse.java<br/>• InventoryServiceGrpc.java<br/>(contains BlockingStub)"]
        GEN_IS["Inventory Service gets:<br/>• Same request/response classes<br/>• InventoryServiceGrpc.java<br/>(contains ImplBase to extend)"]
    end

    subgraph "3. Inventory Service starts gRPC server"
        SRV["@GrpcService<br/>InventoryGrpcServer<br/>listens on port 9090"]
    end

    subgraph "4. Order Service creates gRPC client"
        CLI["@GrpcClient('inventory-service')<br/>connects to localhost:9090"]
    end

    subgraph "5. During order creation"
        CALL["OrderService.createOrder()<br/>calls inventoryClient.checkStock()<br/>━━━━━━━━━━━━━━━<br/>Request serialized → binary<br/>Sent over HTTP/2 to port 9090<br/>Server deserializes → runs logic<br/>Response serialized → binary<br/>Sent back over HTTP/2<br/>Client deserializes → returns result<br/>━━━━━━━━━━━━━━━<br/>All in ~5-10 milliseconds"]
    end

    PROTO --> GEN_OS
    PROTO --> GEN_IS
    GEN_IS --> SRV
    GEN_OS --> CLI
    CLI --> CALL
    SRV --> CALL

    style PROTO fill:#fff3e0
    style GEN_OS fill:#e0e0e0
    style GEN_IS fill:#e0e0e0
    style SRV fill:#e8f5e9
    style CLI fill:#e3f2fd
    style CALL fill:#c8e6c9
```

---

## Key Source Files

| File | What it is |
|------|-----------|
| `proto/inventory.proto` | The contract — defines operations and data types |
| `inventory-service/.../grpc/InventoryGrpcServer.java` | The server — answers gRPC calls with business logic |
| `order-service/.../grpc/InventoryGrpcClient.java` | The client — makes gRPC calls to the server |
| `order-service/src/main/resources/application.yml` (lines 34-38) | Client config — where to connect (`localhost:9090`) |
| `inventory-service/src/main/resources/application.yml` (lines 26-28) | Server config — which port to listen on (`9090`) |
| `order-service/pom.xml` (lines 126-143) | Maven plugin — generates Java code from the proto file |

---

## Summary — gRPC in One Picture

```mermaid
graph LR
    subgraph "The Story of a gRPC Call"
        A["📄 .proto file<br/>The shared contract"] --> B["⚙️ Code generator<br/>Creates Java classes"]
        B --> C["📋 Order Service<br/>Uses generated client<br/>to make calls"]
        B --> D["📦 Inventory Service<br/>Uses generated base class<br/>to handle calls"]
        C -->|"☎️ Binary over HTTP/2<br/>~5ms round trip"| D
    end

    style A fill:#fff3e0
    style B fill:#e0e0e0
    style C fill:#e3f2fd
    style D fill:#e8f5e9
```

**The key insight:** You write a 33-line `.proto` file, and the tooling generates all the networking, serialization, and boilerplate code for both services. You just write the business logic.
