"use client";

import { useCallback, useEffect, useState } from "react";
import { getStatus, placeBid, USE_MOCK, type AuctionStatus } from "./api";

const money = (n: number) => "$" + n.toLocaleString("en-US");

export default function Page() {
  const [status, setStatus] = useState<AuctionStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [bidderId, setBidderId] = useState("alice");
  const [amount, setAmount] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      setStatus(await getStatus());
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : "Could not load the auction");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onSubmit() {
    const value = Number(amount);
    if (!bidderId.trim() || !Number.isFinite(value) || value <= 0) {
      setMessage({ ok: false, text: "Enter a bidder ID and a positive amount." });
      return;
    }

    setSubmitting(true);
    setMessage(null);
    try {
      // A stable id per submission, so a retry is recognisable as the same bid.
      const requestId = crypto.randomUUID();
      const result = await placeBid(bidderId.trim(), value, requestId);
      setStatus(result.status);
      setMessage({ ok: result.accepted, text: result.reason });
      if (result.accepted) setAmount("");
    } catch (err) {
      setMessage({ ok: false, text: err instanceof Error ? err.message : "Bid failed" });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main>
      <h1>Property auction</h1>
      <p className="sub">123 Meridian Way — online auction</p>

      {USE_MOCK && (
        <p className="banner">
          Running against mock data. Set <code>USE_MOCK = false</code> in{" "}
          <code>app/api.ts</code> once your API is live.
        </p>
      )}

      <section className="panel">
        <h2>Current status</h2>

        {loading && <div className="skeleton" aria-label="Loading" />}

        {!loading && loadError && (
          <>
            <p className="msg bad">{loadError}</p>
            <button onClick={() => void refresh()}>Try again</button>
          </>
        )}

        {!loading && !loadError && status && (
          <>
            <p className="price">{money(status.currentPrice)}</p>
            <dl className="meta">
              <dt>Leading bidder</dt>
              <dd>{status.leadingBidder ?? "No bids yet"}</dd>

              <dt>Reserve</dt>
              <dd>
                <span className={"pill " + (status.reserveMet ? "met" : "unmet")}>
                  {status.reserveMet ? "Met" : "Not met"}
                </span>
              </dd>

              <dt>Closes</dt>
              <dd>{new Date(status.closesAt).toLocaleString()}</dd>
            </dl>
          </>
        )}
      </section>

      <section className="panel">
        <h2>Set your maximum</h2>
        <div className="row">
          <input
            value={bidderId}
            onChange={(e) => setBidderId(e.target.value)}
            placeholder="Bidder ID"
            aria-label="Bidder ID"
          />
          <input
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="Maximum amount"
            inputMode="numeric"
            aria-label="Maximum amount"
          />
          <button onClick={() => void onSubmit()} disabled={submitting}>
            {submitting ? "Submitting…" : "Submit"}
          </button>
        </div>

        {message && (
          <p className={"msg " + (message.ok ? "ok" : "bad")} role="status">
            {message.text}
          </p>
        )}
      </section>
    </main>
  );
}
