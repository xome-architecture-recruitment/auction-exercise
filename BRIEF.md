# Proxy bidding

Technical exercise — Builder, Software Development Engineer

| | |
|---|---|
| **Build time** | 50 minutes, live with us |
| **Where** | `api/` — Java 21 + Spring Boot. Already runs. |
| **Storage** | In memory. No database. |
| **AI assistance** | Expected, not discouraged. Use whatever you normally use. |
| **Frontend** | Not a deliverable. You'll demo through it at the end. |

Keep this file open. The worked example below is here deliberately — refer
back to it.

---

## Context

Xome runs online auctions for residential properties. Bidders don't sit at
their screens for the whole auction, so we don't ask them for a bid — we ask
them for the **most they're willing to pay**. The system then bids on their
behalf, raising the price only as far as it needs to in order to keep them in
front.

This is the same mechanic used by most online auction platforms, and it is the
core of what you'll build.

## How proxy bidding works

A bidder submits a maximum. The *current price* is not that maximum — it's
whatever is needed to beat the next-highest maximum, plus one increment. A
bidder's maximum is never revealed to anyone else, and the price never goes
above the leader's maximum.

For this exercise the increment is a flat **$5,000**.

### Worked example — property starts at $250,000

| Event | Price after | Leader |
|---|---:|---|
| Alice sets a maximum of $300,000 | 250,000 | Alice |
| Bob sets a maximum of $280,000 | 285,000 | Alice |
| Carla sets a maximum of $310,000 | 305,000 | Carla |

Alice leads at the starting price because nobody is pushing her. Bob's maximum
pushes Alice up to one increment above it, but Bob still loses. Carla outbids
Alice, and the price lands one increment above Alice's maximum.

> The example above covers the ordinary case. It does not cover everything that
> can happen. Working out what else can happen, and deciding what should
> happen, is a large part of this exercise.

## Two more rules

**Reserve price.** The seller sets a reserve. Until the price reaches it, the
property will not sell even if there are bids. Watchers need to be able to tell
whether the reserve has been met — but the reserve amount itself is
confidential.

**Duplicate submissions.** Bids arrive from a mobile app on unreliable
connections, and the app retries on timeout. The same bid can therefore reach
you more than once. A retry must not have a different effect from the original
request.

## What to build

**`POST /api/auction/bids`** — accept a bidder's maximum. Accept or reject it,
and return something a client can act on.

**`GET /api/auction/status`** — the current state of the auction, as a watcher
is allowed to see it.

## What we've been told so far

- Starting price and closing time are already in `AuctionStore`. Add the
  reserve yourself.
- Bidders are identified by an ID that arrives with the request. No
  authentication needed.
- A hot property can draw a few hundred bidders in the final minutes.
- The business team wants to know at any time who is currently winning and what
  the current price is.
- Operations needs to reconstruct what happened after the fact when a bidder
  complains their bid didn't go through, or that the price moved when it
  shouldn't have.

> This spec is incomplete on purpose. Ask us what you need to know. Where we
> don't have an answer, make a call and tell us what you decided.

## What we'd like to see by the end

- A working bid flow.
- **Four tests** covering the cases you think matter most. Which four you
  choose is part of what interests us.
- Logging that would let someone reconstruct a disputed auction from the log
  alone.
- Your reasoning about the data model. The starting classes are a suggestion,
  not a constraint — change them.

## If you run short on time

1. Bid endpoint with correct price calculation
2. Tests
3. Logging
4. Reserve and duplicate handling

## At the end

Set `USE_MOCK = false` in `web/app/api.ts` and demo your API through the UI.
The client is already written. If its expectations don't match what you built,
that's worth talking about — it's not a mistake on your part.

## What we are not looking for

- Persistence, deployment, authentication, or frontend code.
- Completeness. A narrow slice done properly beats broad coverage.
- Perfect code. We'd rather see you notice what's imperfect.

---

Think out loud as you go. We're interested in how you decide, not only in what
you produce.
