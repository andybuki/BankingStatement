// MoneyLupe UI Kit — Screens: Spending, Merchants, Settings

// ── Donut chart (SVG) ────────────────────────────────────────────
function MLDonut({ data, size = 180, thickness = 24 }) {
  const r = (size - thickness) / 2;
  const c = 2 * Math.PI * r;
  const total = data.reduce((s, d) => s + d.value, 0);
  let acc = 0;
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ transform: "rotate(-90deg)" }}>
      <circle cx={size/2} cy={size/2} r={r} fill="none" stroke="#F1F5F9" strokeWidth={thickness}/>
      {data.map((d, i) => {
        const frac = d.value / total;
        const dash = frac * c;
        const off  = -acc * c;
        acc += frac;
        return (
          <circle key={i} cx={size/2} cy={size/2} r={r} fill="none"
            stroke={d.color} strokeWidth={thickness} strokeLinecap="butt"
            strokeDasharray={`${dash} ${c - dash}`} strokeDashoffset={off}/>
        );
      })}
    </svg>
  );
}

// ── Spending screen ─────────────────────────────────────────────
function MLSpendingScreen({ onTab }) {
  const data = [
    { cat: "Rent",         value: 1180, color: ML_CATEGORIES.Rent.color },
    { cat: "Restaurant",   value: 142,  color: ML_CATEGORIES.Restaurant.color },
    { cat: "Supermarket",  value: 128,  color: ML_CATEGORIES.Supermarket.color },
    { cat: "Transport",    value: 92,   color: ML_CATEGORIES.Transport.color },
    { cat: "Subscriptions",value: 47,   color: ML_CATEGORIES.Subscriptions.color },
    { cat: "Health",       value: 44,   color: ML_CATEGORIES.Health.color },
  ];
  const total = data.reduce((s, d) => s + d.value, 0);
  return (
    <>
      <MLStatusBar dark/>
      <MLHeader title="Spending Overview" compact right={<MLHeaderIcon><MLIcon.download size={20}/></MLHeaderIcon>}/>
      <MLScroll>
        <div style={{ padding: "12px 16px 24px", display: "flex", flexDirection: "column", gap: 14 }}>

          <MLPeriodPills value="Month"/>

          <MLCard floating>
            <div style={{ display: "flex", gap: 16, alignItems: "center" }}>
              <div style={{ position: "relative", width: 180, height: 180, flex: "none" }}>
                <MLDonut data={data} size={180} thickness={22}/>
                <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center" }}>
                  <div style={{ fontSize: 10, letterSpacing: ".06em", textTransform: "uppercase", color: "#94A3B8", fontWeight: 600 }}>Total</div>
                  <div style={{ fontFamily: "Roboto Mono", fontWeight: 600, fontSize: 22, color: "#0F172A", marginTop: 2 }}>€{total.toLocaleString()}</div>
                  <div style={{ fontSize: 11, color: "#475569", marginTop: 2 }}>Apr 2026</div>
                </div>
              </div>
              <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 6, minWidth: 0 }}>
                {data.slice(0, 6).map(d => (
                  <div key={d.cat} style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 12 }}>
                    <div style={{ width: 10, height: 10, borderRadius: 3, background: d.color, flex: "none" }}/>
                    <span style={{ color: "#0F172A", flex: 1, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{d.cat}</span>
                    <span style={{ fontFamily: "Roboto Mono", color: "#475569", fontWeight: 500 }}>{Math.round(d.value / total * 100)}%</span>
                  </div>
                ))}
              </div>
            </div>
          </MLCard>

          <MLSectionTitle right="All">Spending by category</MLSectionTitle>
          <MLCard padding={0}>
            {data.map((d, i) => (
              <MLCatDetailRow key={d.cat} category={d.cat} amount={d.value} pct={Math.round(d.value / total * 100)} last={i === data.length - 1}/>
            ))}
          </MLCard>

          <MLSectionTitle>Monthly summary</MLSectionTitle>
          <MLCard>
            <MLBarChart/>
          </MLCard>

        </div>
      </MLScroll>
      <MLTabBar active="spending" onChange={onTab}/>
    </>
  );
}

function MLCatDetailRow({ category, amount, pct, last }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px", borderTop: last ? "1px solid #F1F5F9" : "1px solid #F1F5F9", borderTopWidth: 1 }}>
      <MLCatAvatar category={category}/>
      <div style={{ flex: 1 }}>
        <div style={{ display: "flex", justifyContent: "space-between" }}>
          <span style={{ fontSize: 14, fontWeight: 600, color: "#0F172A" }}>{category}</span>
          <span style={{ fontFamily: "Roboto Mono", fontSize: 14, fontWeight: 500, color: "#0F172A" }}>€{amount}</span>
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", marginTop: 2 }}>
          <span style={{ fontSize: 11, color: "#94A3B8" }}>{pct}% of spending</span>
          <MLIcon.chevronRight size={16}/>
        </div>
      </div>
    </div>
  );
}

function MLBarChart() {
  const months = [
    { m: "Dec", inc: 4200, exp: 1420 },
    { m: "Jan", inc: 4200, exp: 1680 },
    { m: "Feb", inc: 4200, exp: 1520 },
    { m: "Mar", inc: 4800, exp: 1640 },
    { m: "Apr", inc: 4820, exp: 1342 },
  ];
  const max = Math.max(...months.map(m => Math.max(m.inc, m.exp)));
  return (
    <div style={{ display: "flex", alignItems: "flex-end", gap: 16, height: 140, padding: "6px 0 0" }}>
      {months.map(m => (
        <div key={m.m} style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: 6 }}>
          <div style={{ flex: 1, display: "flex", alignItems: "flex-end", gap: 4, width: "100%", justifyContent: "center" }}>
            <div style={{ width: 10, height: `${m.inc / max * 100}%`, background: "#16A34A", borderRadius: 3 }}/>
            <div style={{ width: 10, height: `${m.exp / max * 100}%`, background: "#DC2626", borderRadius: 3 }}/>
          </div>
          <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 600, textTransform: "uppercase", letterSpacing: ".06em" }}>{m.m}</div>
        </div>
      ))}
    </div>
  );
}

// ── Merchants screen ────────────────────────────────────────────
function MLMerchantsScreen({ onTab }) {
  const merchants = [
    { name: "Miete Wohnung",    cat: "Rent",         total: 1180.00, tx: 1,  trend: 0,    this: 1180, last: 1180 },
    { name: "REWE SAGT DANKE",  cat: "Supermarket",  total: 384.60,  tx: 14, trend: -8,   this: 128,  last: 139  },
    { name: "Deutsche Bahn",    cat: "Travel",       total: 267.00,  tx: 3,  trend: 42,   this: 89,   last: 63   },
    { name: "Spotify AB",       cat: "Subscriptions",total: 131.88,  tx: 12, trend: 0,    this: 11,   last: 11   },
    { name: "Lieferando",       cat: "Restaurant",   total: 98.70,   tx: 4,  trend: -22,  this: 32,   last: 41   },
    { name: "dm-drogerie",      cat: "Health",       total: 74.20,   tx: 5,  trend: 12,   this: 25,   last: 22   },
    { name: "BVG Fahrkarte",    cat: "Transport",    total: 46.00,   tx: 5,  trend: 0,    this: 9,    last: 9    },
  ];
  return (
    <>
      <MLStatusBar dark/>
      <MLHeader title="Merchants" compact right={<MLHeaderIcon><MLIcon.filter size={20}/></MLHeaderIcon>}/>
      <MLScroll>
        <div style={{ padding: "12px 16px 24px", display: "flex", flexDirection: "column", gap: 14 }}>

          <MLPeriodPills value="Month"/>

          <div style={{ display: "flex", justifyContent: "space-between", padding: "0 4px" }}>
            <div style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: ".06em", color: "#94A3B8" }}>Top merchants</div>
            <div style={{ fontSize: 11, color: "#94A3B8" }}>By spending</div>
          </div>

          <MLCard padding={0}>
            {merchants.map((m, i) => <MLMerchantRow key={m.name} m={m} first={i === 0} rank={i + 1}/>)}
          </MLCard>

        </div>
      </MLScroll>
      <MLTabBar active="merchants" onChange={onTab}/>
    </>
  );
}
function MLMerchantRow({ m, first, rank }) {
  const up = m.trend > 0, down = m.trend < 0;
  const trendColor = up ? "#DC2626" : down ? "#16A34A" : "#94A3B8";
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px", borderTop: first ? 0 : "1px solid #F1F5F9" }}>
      <div style={{ width: 22, fontSize: 11, fontFamily: "Roboto Mono", color: "#94A3B8", fontWeight: 600, textAlign: "right" }}>{rank}</div>
      <MLCatAvatar category={m.cat}/>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 14, fontWeight: 600, color: "#0F172A", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{m.name}</div>
        <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 1 }}>{m.cat} · {m.tx} tx</div>
      </div>
      <div style={{ textAlign: "right" }}>
        <div style={{ fontFamily: "Roboto Mono", fontSize: 14, fontWeight: 500, color: "#0F172A" }}>€{m.total.toFixed(2)}</div>
        <div style={{ fontSize: 11, fontWeight: 600, color: trendColor, marginTop: 1, fontFamily: "Roboto Mono" }}>
          {m.trend === 0 ? "—" : (up ? "↑" : "↓") + " " + Math.abs(m.trend) + "%"}
        </div>
      </div>
    </div>
  );
}

// ── Settings screen ─────────────────────────────────────────────
function MLSettingsScreen({ onTab }) {
  return (
    <>
      <MLStatusBar dark/>
      <MLHeader title="Settings" compact/>
      <MLScroll>
        <div style={{ padding: "12px 16px 24px", display: "flex", flexDirection: "column", gap: 14 }}>

          <MLCard padding={0}>
            <MLSetRow icon="wallet" color="#16A34A" title="Accounts" subtitle="3 accounts · 47 statements" first chev/>
            <MLSetRow icon="dashed" color="#8B5CF6" title="Manage categories" subtitle="17 predefined · 2 custom" chev/>
            <MLSetRow icon="upload" color="#2563EB" title="Import statement" subtitle="PDF · CSV · Excel" chev/>
            <MLSetRow icon="download" color="#06B6D4" title="Export data" subtitle="CSV / PDF" chev/>
          </MLCard>

          <div style={{ padding: "0 4px", fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: ".06em", color: "#94A3B8" }}>Appearance</div>
          <MLCard padding={0}>
            <MLSetRow icon="sparkle" color="#F59E0B" title="Theme" subtitle="System" first right={<MLChip>System</MLChip>}/>
            <MLSetRow icon="bell" color="#EC4899" title="Reminders" subtitle="Nudge me monthly" right={<MLSwitch on/>}/>
          </MLCard>

          <div style={{ padding: "0 4px", fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: ".06em", color: "#94A3B8" }}>Security</div>
          <MLCard padding={0}>
            <MLSetRow icon="lock" color="#6366F1" title="App lock" subtitle="Require fingerprint or PIN" first right={<MLSwitch on/>}/>
          </MLCard>

          <div style={{ padding: "0 4px", fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: ".06em", color: "#94A3B8" }}>About</div>
          <MLCard padding={0}>
            <MLSetRow icon="share" color="#2563EB" title="Share MoneyLupe" subtitle="Tell a friend" first chev/>
            <MLSetRow icon="heart" color="#DC2626" title="Rate the app" subtitle="On the Play Store" chev/>
            <MLSetRow icon="file"  color="#64748B" title="Privacy policy" chev/>
          </MLCard>

          <div style={{ textAlign: "center", marginTop: 4 }}>
            <div style={{ fontSize: 11, color: "#94A3B8" }}>MoneyLupe · v2.4.0</div>
            <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 2 }}>moneylupe.info@gmail.com</div>
          </div>

        </div>
      </MLScroll>
      <MLTabBar active="settings" onChange={onTab}/>
    </>
  );
}

function MLSetRow({ icon, color, title, subtitle, first, chev, right }) {
  const Ic = MLIcon[icon] || MLIcon.dashed;
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "14px 16px", borderTop: first ? 0 : "1px solid #F1F5F9" }}>
      <div style={{ width: 36, height: 36, borderRadius: 10, background: color + "22", color, display: "flex", alignItems: "center", justifyContent: "center", flex: "none" }}>
        <Ic size={18}/>
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 14, fontWeight: 600, color: "#0F172A" }}>{title}</div>
        {subtitle && <div style={{ fontSize: 12, color: "#94A3B8", marginTop: 1 }}>{subtitle}</div>}
      </div>
      {right}
      {chev && <div style={{ color: "#CBD5E1" }}><MLIcon.chevronRight size={18}/></div>}
    </div>
  );
}
function MLChip({ children }) {
  return <div style={{ padding: "4px 10px", background: "#F1F5F9", borderRadius: 999, fontSize: 11, fontWeight: 600, color: "#475569" }}>{children}</div>;
}
function MLSwitch({ on }) {
  return (
    <div style={{ width: 42, height: 24, borderRadius: 999, background: on ? "#2563EB" : "#CBD5E1", position: "relative", transition: "background 200ms" }}>
      <div style={{ width: 20, height: 20, borderRadius: "50%", background: "#fff", position: "absolute", top: 2, left: on ? 20 : 2, boxShadow: "0 1px 3px rgba(0,0,0,0.15)", transition: "left 200ms" }}/>
    </div>
  );
}

Object.assign(window, {
  MLSpendingScreen, MLMerchantsScreen, MLSettingsScreen,
  MLDonut, MLBarChart, MLCatDetailRow, MLMerchantRow,
  MLSetRow, MLChip, MLSwitch,
});
