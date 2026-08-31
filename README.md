# Property Auction — Technical Exercise

The problem is in **[BRIEF.md](BRIEF.md)**. Read it after you've started the
setup below.

## First: start both installs, then read the brief

Open two terminals and run these now. They take a few minutes and you can read
while they work.

**Terminal 1 — backend** (Java 21 + Spring Boot)

```
cd api
mvn test
```

**Terminal 2 — frontend** (Next.js + TypeScript)

```
cd web
npm install
```

## Then

```
cd api && mvn spring-boot:run     # http://localhost:8080
cd web && npm run dev             # http://localhost:3000
```

Confirm the API is up: http://localhost:8080/api/auction/ping

## What's here

**`api/`** — where you'll be working.
- `AuctionController.java` — two stubbed endpoints
- `AuctionStore.java` — in-memory storage
- `Bid.java`, `BidRequest.java` — a starting point for the model, change freely
- `AuctionControllerTest.java` — one passing test, run with `mvn test`

Logging is already configured (SLF4J, timestamped console output).

**`web/`** — already built and working against mock data. No frontend code
required. At the end you'll set `USE_MOCK = false` in `app/api.ts` and demo
your API through it.

CORS is pre-configured for localhost:3000, so you won't need to fight it.

## Notes

- No database. In-memory storage is fine.
- **Use your normal AI coding assistant.** Expected, not discouraged.
- If anything here doesn't run, say so straight away rather than
  troubleshooting quietly — we have a backup and it won't count against you.
