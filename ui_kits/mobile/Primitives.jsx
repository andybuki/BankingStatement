// MoneyLupe UI Kit — Phone frame + shared primitives

// ── Phone frame (custom, matches app's navy header) ──────────────────
function MLPhone({ children, width = 390, height = 800, label }) {
  return (
    <div data-screen-label={label} style={{
      width, height, borderRadius: 44, padding: 12,
      background: "#111",
      boxShadow: "0 30px 60px rgba(15,42,68,0.35), 0 0 0 1px rgba(0,0,0,0.5)",
      position: "relative", flex: "none",
    }}>
      <div style={{
        width: "100%", height: "100%", borderRadius: 34, overflow: "hidden",
        background: "#0F2A44", position: "relative",
        display: "flex", flexDirection: "column",
      }}>
        {children}
      </div>
      {/* dynamic island */}
      <div style={{
        position: "absolute", top: 20, left: "50%", transform: "translateX(-50%)",
        width: 92, height: 28, borderRadius: 999, background: "#000", zIndex: 50,
      }}/>
    </div>
  );
}

// iOS-ish status bar that sits on whatever header background is passed in
function MLStatusBar({ dark = true }) {
  const c = dark ? "#fff" : "#0F172A";
  return (
    <div style={{
      height: 48, padding: "14px 28px 0", display: "flex",
      justifyContent: "space-between", alignItems: "center",
      fontFamily: "Roboto, system-ui", fontSize: 15, fontWeight: 600, color: c,
      flex: "none",
    }}>
      <span>9:41</span>
      <span style={{ width: 92 }}/>
      <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
        {/* signal */}
        <svg width="17" height="11" viewBox="0 0 17 11" fill={c}><rect x="0" y="7" width="3" height="4" rx=".5"/><rect x="5" y="5" width="3" height="6" rx=".5"/><rect x="10" y="2" width="3" height="9" rx=".5"/><rect x="15" y="0" width="3" height="11" rx=".5" opacity=".4"/></svg>
        {/* wifi */}
        <svg width="16" height="11" viewBox="0 0 16 11" fill={c}><path d="M8 2a10 10 0 0 1 7 2.8l-1.4 1.4A8 8 0 0 0 8 4 8 8 0 0 0 2.4 6.2L1 4.8A10 10 0 0 1 8 2Zm0 4a6 6 0 0 1 4.2 1.7L11 9A4 4 0 0 0 8 8a4 4 0 0 0-3 1L3.8 7.7A6 6 0 0 1 8 6Zm0 4a2 2 0 0 1 1.4.6L8 11 6.6 10.6A2 2 0 0 1 8 10Z"/></svg>
        {/* battery */}
        <svg width="25" height="11" viewBox="0 0 25 11"><rect x=".5" y=".5" width="22" height="10" rx="2.3" fill="none" stroke={c} strokeWidth="1" opacity=".5"/><rect x="23" y="3.5" width="1.5" height="4" rx=".5" fill={c} opacity=".5"/><rect x="2" y="2" width="18" height="7" rx="1.3" fill={c}/></svg>
      </div>
    </div>
  );
}

// ── Navy header used on most screens ────────────────────────────────
function MLHeader({ title = "MoneyLupe", incomeLabel, expenseLabel, right, onSearch, compact }) {
  return (
    <div style={{
      background: "#0F2A44", color: "#fff",
      padding: compact ? "2px 16px 14px" : "4px 16px 16px",
      flex: "none",
    }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", minHeight: 36 }}>
        <div style={{ fontFamily: "Roboto, system-ui, sans-serif", fontWeight: 700, fontSize: compact ? 18 : 22, letterSpacing: "-0.01em" }}>{title}</div>
        <div style={{ display: "flex", gap: 6, color: "#E5E7EB" }}>
          {onSearch && <MLHeaderIcon><MLIcon.search size={20}/></MLHeaderIcon>}
          {right}
        </div>
      </div>
      {(incomeLabel || expenseLabel) && (
        <div style={{ display: "flex", gap: 28, marginTop: 10 }}>
          {incomeLabel && <MLHeaderPair label="Income" value={incomeLabel} color="#34D399"/>}
          {expenseLabel && <MLHeaderPair label="Expenses" value={expenseLabel} color="#FCA5A5"/>}
        </div>
      )}
    </div>
  );
}
function MLHeaderIcon({ children }) {
  return <div style={{ width: 34, height: 34, display: "flex", alignItems: "center", justifyContent: "center", borderRadius: 10 }}>{children}</div>;
}
function MLHeaderPair({ label, value, color }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
      <div style={{ fontSize: 10, letterSpacing: ".06em", fontWeight: 600, textTransform: "uppercase", color: "#94A3B8" }}>{label}</div>
      <div style={{ fontFamily: "Roboto Mono, ui-monospace", fontSize: 16, fontWeight: 600, fontVariantNumeric: "tabular-nums", color }}>{value}</div>
    </div>
  );
}

// ── Bottom navigation ───────────────────────────────────────────────
function MLTabBar({ active = "home", onChange = () => {} }) {
  const tabs = [
    { id: "home", label: "Home", icon: "home" },
    { id: "activity", label: "Activity", icon: "list" },
    { id: "spending", label: "Spending", icon: "pie" },
    { id: "merchants", label: "Merchants", icon: "store" },
    { id: "settings", label: "Settings", icon: "gear" },
  ];
  return (
    <div style={{
      background: "#fff", borderTop: "1px solid #E5E7EB",
      display: "flex", padding: "6px 4px 24px",
      flex: "none",
    }}>
      {tabs.map(t => {
        const I = MLIcon[t.icon];
        const a = t.id === active;
        return (
          <button key={t.id} onClick={() => onChange(t.id)} style={{
            flex: 1, background: "transparent", border: 0, cursor: "pointer",
            display: "flex", flexDirection: "column", alignItems: "center", gap: 3,
            padding: "6px 2px", color: a ? "#2563EB" : "#94A3B8",
          }}>
            <I size={24}/>
            <span style={{ fontSize: 11, fontWeight: a ? 600 : 500 }}>{t.label}</span>
          </button>
        );
      })}
    </div>
  );
}

// ── Scrollable content area ─────────────────────────────────────────
function MLScroll({ children, style }) {
  return (
    <div style={{
      flex: 1, overflow: "auto", background: "#F1F5F9",
      ...style,
    }}>{children}</div>
  );
}

// ── Card + rows ──────────────────────────────────────────────────────
function MLCard({ children, style, padding = 16, floating }) {
  return (
    <div style={{
      background: "#fff", borderRadius: 16, padding,
      boxShadow: floating ? "0 2px 8px rgba(15,23,42,0.06)" : "0 1px 2px rgba(15,23,42,0.04)",
      ...style,
    }}>{children}</div>
  );
}
function MLSectionTitle({ children, right }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", padding: "4px 4px 10px" }}>
      <div style={{ fontFamily: "Roboto, system-ui, sans-serif", fontSize: 16, fontWeight: 600, color: "#0F172A" }}>{children}</div>
      <div style={{ fontSize: 12, color: "#2563EB", fontWeight: 600 }}>{right}</div>
    </div>
  );
}

// ── Category avatar ─────────────────────────────────────────────────
function MLCatAvatar({ category = "Other", size = 36 }) {
  const c = ML_CATEGORIES[category] || ML_CATEGORIES.Other;
  const Ic = MLIcon[c.icon];
  return (
    <div style={{
      width: size, height: size, borderRadius: size / 3.2,
      background: c.color + "22", color: c.color,
      display: "flex", alignItems: "center", justifyContent: "center", flex: "none",
    }}>
      <Ic size={Math.round(size * 0.55)}/>
    </div>
  );
}

// ── Amount helper ──────────────────────────────────────────────────
function MLAmount({ value, size = 16, weight = 500 }) {
  const positive = value >= 0;
  const abs = Math.abs(value).toLocaleString("en-DE", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return (
    <span style={{
      fontFamily: "Roboto Mono, ui-monospace", fontSize: size, fontWeight: weight,
      fontVariantNumeric: "tabular-nums",
      color: positive ? "#16A34A" : "#DC2626",
      letterSpacing: "-0.01em",
    }}>{positive ? "+" : "−"}€{abs}</span>
  );
}

// ── Period pills ──────────────────────────────────────────────────
function MLPeriodPills({ value = "Month", options = ["Week", "Month", "Year", "All"], onChange = () => {} }) {
  return (
    <div style={{ display: "flex", gap: 6, padding: "0 4px" }}>
      {options.map(o => (
        <button key={o} onClick={() => onChange(o)} style={{
          padding: "6px 14px", borderRadius: 999, border: 0, cursor: "pointer",
          background: o === value ? "#2563EB" : "#fff",
          color: o === value ? "#fff" : "#475569",
          fontSize: 12, fontWeight: 600, fontFamily: "Roboto, system-ui, sans-serif",
          boxShadow: o === value ? "none" : "0 1px 2px rgba(15,23,42,0.04)",
        }}>{o}</button>
      ))}
    </div>
  );
}

Object.assign(window, {
  MLPhone, MLStatusBar, MLHeader, MLHeaderIcon, MLTabBar,
  MLScroll, MLCard, MLSectionTitle, MLCatAvatar, MLAmount, MLPeriodPills,
});
