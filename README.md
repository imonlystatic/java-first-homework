# Betting Service

## dependency list

| description | JDK class                                              |
|-------------|--------------------------------------------------------|
| HTTP Server | `com.sun.net.httpserver.HttpServer`                    |
| concurrent  | `java.util.concurrent.ConcurrentHashMap`               |
| thread pool | `java.util.concurrent.Executors.newCachedThreadPool()` |
| clean cache | `java.util.concurrent.ScheduledExecutorService`        |
| Session Key | `java.security.SecureRandom`                           |

## compile & run

**JDK 11+**

```bash
chmod +x build.shcd
./build.sh

java -jar betting-service.jar
# ✓ Betting server running on http://localhost:8001
```

## API example

```bash
# 1. get/create session（return the same in 10 minutes）
curl http://localhost:8001/1234/session
# → A3FZ9KQW

# 2. stake（sessionkey must be available）
curl -X POST "http://localhost:8001/888/stake?sessionkey=A3FZ9KQW" -d "4500"

# 3. get top 20 highest stake amount
curl http://localhost:8001/888/highstakes
# → 1234=4500,57453=1337
```

## core design

### concurrent

- all shared data stored in `ConcurrentHashMap` 
- save the highest stake in map `map.merge(customerId, stake, Math::max)` — atomic, do not need lock 
- handle HTTP request  by `newCachedThreadPool()`

### session 

- two maps design：`customerId→Session`（getOrCreate）+ `sessionKey→Session`（validate), time complexity:O(1)
- deamon thread clean expired session in 5 minutes, prevent leakage of memory 
- do not delete stake data after session expired

### project structure（6 files）

```
src/com/betting/
├── server/   BettingServerMain.java              ← main class, start server
├── handler/  Router.java            ← path + request handle
├── service/  SessionService.java    ← session lifecycle
│             StakeService.java      ← stake logic and top 20 sort
│             SessionKeyGenerator.java
└── model/    Session.java
```
