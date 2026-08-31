// API client for the auction service.
//
// Right now every call goes to MOCK data so the page works before the backend
// does. Switch USE_MOCK to false once your endpoints are live.
//
// TODO: implement the real calls below.

export const BASE = "http://localhost:8080/api/auction";

export const USE_MOCK = true;

export type AuctionStatus = {
  currentPrice: number;
  leadingBidder: string | null;
  reserveMet: boolean;
  closesAt: string;
};

export type BidResult = {
  accepted: boolean;
  reason: string;
  status: AuctionStatus;
};

// ---------------------------------------------------------------------------
// Mock backend. Deliberately simplistic: it tracks the highest maximum and
// uses it as the price. That is NOT how proxy bidding works — it is here only
// so the page renders and the form responds while you build the real thing.
// ---------------------------------------------------------------------------

const mock = {
  price: 250000,
  leader: null as string | null,
  closesAt: new Date(Date.now() + 3600_000).toISOString(),
};

function mockStatus(): AuctionStatus {
  return {
    currentPrice: mock.price,
    leadingBidder: mock.leader,
    reserveMet: mock.price >= 300000,
    closesAt: mock.closesAt,
  };
}

async function delay<T>(value: T, ms = 250): Promise<T> {
  await new Promise((r) => setTimeout(r, ms));
  return value;
}

// ---------------------------------------------------------------------------

export async function getStatus(): Promise<AuctionStatus> {
  if (USE_MOCK) return delay(mockStatus());

  // TODO: GET `${BASE}/status`
  const res = await fetch(`${BASE}/status`);
  if (!res.ok) throw new Error(`Status request failed (${res.status})`);
  return res.json();
}

export async function placeBid(
  bidderId: string,
  amount: number,
  requestId: string
): Promise<BidResult> {
  if (USE_MOCK) {
    if (amount <= mock.price) {
      return delay({ accepted: false, reason: "Below the current price", status: mockStatus() });
    }
    mock.price = amount;
    mock.leader = bidderId;
    return delay({ accepted: true, reason: "Accepted", status: mockStatus() });
  }

  // TODO: POST `${BASE}/bids`
  const res = await fetch(`${BASE}/bids`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ bidderId, amount, requestId }),
  });
  if (!res.ok) throw new Error(`Bid request failed (${res.status})`);
  return res.json();
}
