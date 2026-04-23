// MoneyLupe UI Kit — Screens: Home & Activity

// ── Sample data ─────────────────────────────────────────────────
const ML_SAMPLE_TX = [
  { id: 1,  merchant: "REWE SAGT DANKE",       cat: "Supermarket",  date: "22 Apr",  amt: -48.27 },
  { id: 2,  merchant: "ACME GmbH Gehalt",      cat: "Salary",       date: "01 Apr",  amt:  4820.00 },
  { id: 3,  merchant: "BVG Fahrkarte",         cat: "Transport",    date: "20 Apr",  amt: -9.20 },
  { id: 4,  merchant: "Spotify AB",            cat: "Subscriptions",date: "18 Apr",  amt: -10.99 },
  { id: 5,  merchant: "dm-drogerie markt",     cat: "Health",       date: "17 Apr",  amt: -24.80 },
  { id: 6,  merchant: "Lieferando",            cat: "Restaurant",   date: "16 Apr",  amt: -32.40 },
  { id: 7,  merchant: "Deutsche Bahn",         cat: "Travel",       date: "14 Apr",  amt: -89.50 },
  { id: 8,  merchant: "Apple Music",           cat: "Subscriptions",date: "12 Apr",  amt: -5.99 },
  { id: 9,  merchant: "Miete Wohnung",         cat: "Rent",         date: "01 Apr",  amt: -1180.00 },
  { id: 10, merchant: "Rossmann",              cat: "Health",       date: "10 Apr",  amt: -18.75 },
];

// ── Home screen ─────────────────────────────────────────────────
function MLHomeScreen({ onTab }) {
  return (
    <>
      <MLStatusBar dark/>
      <MLHeader title="MoneyLupe" incomeLabel="+€4,820.00" expenseLabel="−€1,342.18" onSearch/>
      <MLScroll>
        <div style={{ padding: "14px 16px 24px", display: "flex", flexDirection: "column", gap: 14 }}>

          <MLCard floating>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
              <div>
                <div style={{ fontSize: 11, letterSpacing: ".06em", fontWeight: 600, textTransform: "uppercase", color: "#94A3B8" }}>Net this month</div>
                <div style={{ fontFamily: "Roboto Mono, ui-monospace", fontWeight: 600, fontSize: 34, color: "#0F172A", marginTop: 4, letterSpacing: "-0.015em", lineHeight: 1.05 }}>+€3,477.82</div>
                <div style={{ fontSize: 12, color: "#475569", marginTop: 4 }}>Across 3 accounts · 47 transactions</div>
              </div>
              <div style={{ padding: "4px 10px", borderRadius: 999, background: "#DCFCE7", color: "#16A34A", fontSize: 11, fontWeight: 600 }}>↑ 12%</div>
            </div>
            <div style={{ marginTop: 14, borderRadius: 999, overflow: "hidden", height: 6, background: "#F1F5F9", display: "flex" }}>
              <div style={{ width: "78%", background: "#16A34A" }}/>
              <div style={{ width: "22%", background: "#DC2626" }}/>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", marginTop: 6, fontSize: 11, color: "#64748B" }}>
              <span>Income €4,820.00</span>
              <span>Expenses €1,342.18</span>
            </div>
          </MLCard>

          {/* Quick actions */}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
            <MLQuickCard tint="#2563EB" label="Import statement" subtitle="PDF · CSV" icon="upload"/>
            <MLQuickCard tint="#16A34A" label="Accounts" subtitle="3 connected" icon="wallet"/>
          </div>

          <div>
            <MLSectionTitle right="See all">Recent activity</MLSectionTitle>
            <MLCard padding={0}>
              {ML_SAMPLE_TX.slice(0, 4).map((t, i) => <MLTxRow key={t.id} tx={t} first={i === 0}/>)}
            </MLCard>
          </div>

          <div>
            <MLSectionTitle right="View breakdown">Top categories</MLSectionTitle>
            <MLCard>
              <MLCatBar category="Rent"        amount={1180} pct={66}/>
              <MLCatBar category="Restaurant"  amount={142}  pct={8}/>
              <MLCatBar category="Supermarket" amount={128}  pct={7}/>
              <MLCatBar category="Transport"   amount={92}   pct={5} last/>
            </MLCard>
          </div>

          {/* tip card */}
          <MLCard style={{ background: "#F0F9FF", borderLeft: "3px solid #2563EB" }}>
            <div style={{ display: "flex", gap: 10 }}>
              <div style={{ flex: "none", marginTop: 2, color: "#2563EB" }}><MLIcon.sparkle size={18}/></div>
              <div>
                <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>You spent 18% less on restaurants</div>
                <div style={{ fontSize: 12, color: "#475569", marginTop: 2, lineHeight: 1.45 }}>Compared to last month. Keep it up — that's €31 saved.</div>
              </div>
            </div>
          </MLCard>

        </div>
      </MLScroll>
      <MLTabBar active="home" onChange={onTab}/>
    </>
  );
}

function MLQuickCard({ tint, label, subtitle, icon }) {
  const Ic = MLIcon[icon];
  return (
    <div style={{
      background: "#fff", borderRadius: 16, padding: 14,
      boxShadow: "0 1px 2px rgba(15,23,42,0.04)",
      display: "flex", flexDirection: "column", gap: 10,
    }}>
      <div style={{ width: 36, height: 36, borderRadius: 10, background: tint + "22", color: tint, display: "flex", alignItems: "center", justifyContent: "center" }}>
        <Ic size={20}/>
      </div>
      <div>
        <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{label}</div>
        <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 2 }}>{subtitle}</div>
      </div>
    </div>
  );
}

function MLTxRow({ tx, first }) {
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 12,
      padding: "12px 16px", borderTop: first ? "0" : "1px solid #F1F5F9",
    }}>
      <MLCatAvatar category={tx.cat}/>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 14, fontWeight: 600, color: "#0F172A", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{tx.merchant}</div>
        <div style={{ fontSize: 12, color: "#94A3B8", marginTop: 1 }}>{tx.cat} · {tx.date}</div>
      </div>
      <MLAmount value={tx.amt}/>
    </div>
  );
}

function MLCatBar({ category, amount, pct, last }) {
  const c = ML_CATEGORIES[category] || ML_CATEGORIES.Other;
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 0", borderBottom: last ? "0" : "1px solid #F1F5F9" }}>
      <MLCatAvatar category={category} size={28}/>
      <div style={{ flex: 1 }}>
        <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, color: "#0F172A", fontWeight: 500 }}>
          <span>{category}</span>
          <span style={{ fontFamily: "Roboto Mono", fontWeight: 500 }}>€{amount}</span>
        </div>
        <div style={{ height: 4, background: "#F1F5F9", borderRadius: 999, marginTop: 6, overflow: "hidden" }}>
          <div style={{ height: "100%", width: pct + "%", background: c.color, borderRadius: 999 }}/>
        </div>
      </div>
    </div>
  );
}

// ── Activity (transaction list) ─────────────────────────────────
function MLActivityScreen({ onTab }) {
  return (
    <>
      <MLStatusBar dark/>
      <div style={{ background: "#0F2A44", padding: "4px 16px 14px", color: "#fff" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", minHeight: 36 }}>
          <div style={{ fontFamily: "Roboto, system-ui", fontWeight: 700, fontSize: 20 }}>Activity</div>
          <div style={{ display: "flex", gap: 6, color: "#E5E7EB" }}>
            <MLHeaderIcon><MLIcon.filter size={20}/></MLHeaderIcon>
            <MLHeaderIcon><MLIcon.download size={20}/></MLHeaderIcon>
          </div>
        </div>
        {/* search */}
        <div style={{ marginTop: 10, display: "flex", alignItems: "center", gap: 8, background: "#1E3A5F", borderRadius: 12, padding: "10px 12px" }}>
          <div style={{ color: "#94A3B8" }}><MLIcon.search size={18}/></div>
          <div style={{ fontSize: 13, color: "#94A3B8" }}>Search transactions…</div>
        </div>
      </div>
      <MLScroll style={{ background: "#F1F5F9" }}>
        <div style={{ padding: "12px 16px 24px" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
            <MLPeriodPills value="Month"/>
            <span style={{ fontSize: 11, color: "#64748B" }}>47 items</span>
          </div>

          <MLDayGroup date="Today · 22 Apr" txs={ML_SAMPLE_TX.filter(t => t.date === "22 Apr" || t.date === "20 Apr").slice(0, 3)}/>
          <MLDayGroup date="Fri · 18 Apr"   txs={ML_SAMPLE_TX.slice(3, 6)}/>
          <MLDayGroup date="Mon · 14 Apr"   txs={ML_SAMPLE_TX.slice(6, 9)}/>
          <MLDayGroup date="Tue · 01 Apr"   txs={[ML_SAMPLE_TX[1], ML_SAMPLE_TX[8]]}/>
        </div>
      </MLScroll>
      <MLTabBar active="activity" onChange={onTab}/>
    </>
  );
}
function MLDayGroup({ date, txs }) {
  const total = txs.reduce((s, t) => s + t.amt, 0);
  return (
    <div style={{ marginBottom: 14 }}>
      <div style={{ display: "flex", justifyContent: "space-between", padding: "0 4px 6px", fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: ".06em", color: "#94A3B8" }}>
        <span>{date}</span>
        <span style={{ fontFamily: "Roboto Mono", letterSpacing: "0" }}>{total >= 0 ? "+" : "−"}€{Math.abs(total).toFixed(2)}</span>
      </div>
      <MLCard padding={0}>{txs.map((t, i) => <MLTxRow key={t.id} tx={t} first={i === 0}/>)}</MLCard>
    </div>
  );
}

Object.assign(window, { MLHomeScreen, MLActivityScreen, MLTxRow, MLCatBar, MLQuickCard, ML_SAMPLE_TX });
