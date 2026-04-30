// MoneyLupe UI Kit — Screens C: Categories (Manage / Change / Add)
// Supports light + dark themes via theme prop.

// ── Theme tokens ─────────────────────────────────────────────────
const ML_THEMES = {
  light: {
    bg: "#F1F5F9", surface: "#FFFFFF", surfaceAlt: "#F8FAFC",
    border: "#E5E7EB", borderSoft: "#F1F5F9",
    text: "#0F172A", textMuted: "#475569", textFaint: "#94A3B8",
    accent: "#2563EB", accentBg: "#DBEAFE",
    headerBg: "#0F2A44", headerText: "#FFFFFF", headerMuted: "#94A3B8",
    rowBg: "#FFFFFF", rowBgSelected: "#EFF6FF",
    backdrop: "rgba(15,23,42,0.55)",
    sheetBg: "#FFFFFF",
    inputBg: "#F8FAFC", inputBorder: "#E5E7EB",
    chipBg: "#F1F5F9",
    danger: "#DC2626",
    success: "#16A34A", successBg: "#DCFCE7",
  },
  dark: {
    bg: "#0B1628", surface: "#15243A", surfaceAlt: "#1B2D47",
    border: "#243756", borderSoft: "#1B2D47",
    text: "#F1F5F9", textMuted: "#CBD5E1", textFaint: "#94A3B8",
    accent: "#60A5FA", accentBg: "#1E3A5F",
    headerBg: "#0B1628", headerText: "#F1F5F9", headerMuted: "#94A3B8",
    rowBg: "#15243A", rowBgSelected: "#1E3A5F",
    backdrop: "rgba(0,0,0,0.55)",
    sheetBg: "#1E2A3F",
    inputBg: "#15243A", inputBorder: "#324968",
    chipBg: "#1B2D47",
    danger: "#F87171",
    success: "#4ADE80", successBg: "#14532D",
  },
};
const useMLTheme = (mode = "light") => ML_THEMES[mode] || ML_THEMES.light;

// Themed phone — overrides MLPhone's hardcoded navy
function MLPhoneT({ children, width = 390, height = 800, label, theme = "light" }) {
  const t = useMLTheme(theme);
  return (
    <div data-screen-label={label} style={{
      width, height, borderRadius: 44, padding: 12, background: "#111",
      boxShadow: "0 30px 60px rgba(15,42,68,0.35), 0 0 0 1px rgba(0,0,0,0.5)",
      position: "relative", flex: "none",
    }}>
      <div style={{
        width: "100%", height: "100%", borderRadius: 34, overflow: "hidden",
        background: t.bg, position: "relative", display: "flex", flexDirection: "column",
      }}>{children}</div>
      <div style={{
        position: "absolute", top: 20, left: "50%", transform: "translateX(-50%)",
        width: 92, height: 28, borderRadius: 999, background: "#000", zIndex: 50,
      }}/>
    </div>
  );
}

// ── Manage Categories screen ─────────────────────────────────────
const ML_PREDEF_KEYS = [
  "Rent", "Transport", "Supermarket", "Restaurant", "Shopping", "Health",
  "Insurance", "Entertainment", "Subscriptions", "Investment", "Travel",
  "Salary", "Refund", "Transfer", "Education", "Taxes",
];
const ML_DISPLAY_NAME = { Rent: "Rent & Utilities" }; // app shows fuller name

function MLManageCategoriesScreen({ theme = "light", customs = [], onAdd, onTab }) {
  const t = useMLTheme(theme);
  return (
    <>
      <MLStatusBar dark={theme === "dark" || theme === "navy"}/>
      {/* Sub-page header (back + title + Add) */}
      <div style={{ background: t.headerBg, color: t.headerText, padding: "6px 12px 14px", flex: "none" }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", minHeight: 40 }}>
          <button style={{ background: "transparent", border: 0, color: t.headerText, padding: 8, cursor: "pointer", display: "flex", alignItems: "center", gap: 8 }}>
            <MLIcon.chevronLeft size={22}/>
            <span style={{ fontFamily: "Roboto, system-ui, sans-serif", fontSize: 18, fontWeight: 600 }}>Manage Categories</span>
          </button>
          <button onClick={onAdd} style={{
            background: theme === "light" ? "rgba(255,255,255,0.14)" : "rgba(255,255,255,0.08)",
            border: 0, color: t.headerText, padding: "6px 12px", borderRadius: 999,
            fontSize: 13, fontWeight: 600, cursor: "pointer", display: "flex", alignItems: "center", gap: 4,
          }}>
            <MLIcon.plus size={14}/> Add
          </button>
        </div>
      </div>

      <MLScroll style={{ background: t.bg }}>
        <div style={{ padding: "16px 16px 24px", display: "flex", flexDirection: "column", gap: 18 }}>

          {/* Custom Categories */}
          <div>
            <div style={{ padding: "0 4px 10px", fontSize: 14, fontWeight: 700, color: t.text, fontFamily: "Roboto, system-ui, sans-serif" }}>Custom Categories</div>
            {customs.length === 0 ? (
              <div style={{
                background: t.surface, borderRadius: 16, padding: "28px 16px", textAlign: "center",
                border: theme === "dark" ? `1px dashed ${t.border}` : "0",
              }}>
                <div style={{ fontSize: 14, color: t.textMuted, fontFamily: "Roboto, system-ui, sans-serif" }}>No custom categories yet</div>
                <div style={{ fontSize: 12, color: t.textFaint, marginTop: 4 }}>Create your first custom category</div>
              </div>
            ) : (
              <div style={{ background: t.surface, borderRadius: 16, overflow: "hidden" }}>
                {customs.map((c, i) => <MLManageRow key={c.name} item={c} first={i === 0} t={t} editable/>)}
              </div>
            )}
          </div>

          {/* Predefined Categories */}
          <div>
            <div style={{ padding: "0 4px 10px", fontSize: 14, fontWeight: 700, color: t.text, fontFamily: "Roboto, system-ui, sans-serif" }}>Predefined Categories</div>
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {ML_PREDEF_KEYS.map(k => (
                <div key={k} style={{ background: t.surface, borderRadius: 12, padding: "12px 14px", display: "flex", alignItems: "center", gap: 12 }}>
                  <MLCatAvatar category={k} size={36}/>
                  <div style={{ flex: 1, fontSize: 15, color: t.text, fontWeight: 500, fontFamily: "Roboto, system-ui, sans-serif" }}>{ML_DISPLAY_NAME[k] || k}</div>
                </div>
              ))}
            </div>
          </div>

        </div>
      </MLScroll>
      <MLTabBarT theme={theme} active="settings" onChange={onTab}/>
    </>
  );
}

function MLManageRow({ item, first, t, editable }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 14px", borderTop: first ? 0 : `1px solid ${t.borderSoft}` }}>
      <div style={{ width: 36, height: 36, borderRadius: 12, background: item.color + "33", color: item.color, display: "flex", alignItems: "center", justifyContent: "center", flex: "none" }}>
        {(MLIcon[item.icon] || MLIcon.dashed)({ size: 20 })}
      </div>
      <div style={{ flex: 1, fontSize: 15, color: t.text, fontWeight: 500 }}>{item.name}</div>
      {editable && <div style={{ color: t.textFaint }}><MLIcon.chevronRight size={18}/></div>}
    </div>
  );
}

// Themed tab bar variant (dark-aware)
function MLTabBarT({ active = "home", onChange = () => {}, theme = "light" }) {
  const t = useMLTheme(theme);
  const tabs = [
    { id: "home", label: "Home", icon: "home" },
    { id: "activity", label: "Activity", icon: "list" },
    { id: "spending", label: "Spending", icon: "pie" },
    { id: "merchants", label: "Merchants", icon: "store" },
    { id: "settings", label: "Settings", icon: "gear" },
  ];
  return (
    <div style={{
      background: t.surface, borderTop: `1px solid ${t.border}`,
      display: "flex", padding: "6px 4px 24px", flex: "none",
    }}>
      {tabs.map(tb => {
        const I = MLIcon[tb.icon];
        const a = tb.id === active;
        return (
          <button key={tb.id} onClick={() => onChange(tb.id)} style={{
            flex: 1, background: "transparent", border: 0, cursor: "pointer",
            display: "flex", flexDirection: "column", alignItems: "center", gap: 3,
            padding: "6px 2px", color: a ? t.accent : t.textFaint,
          }}>
            <I size={24}/>
            <span style={{ fontSize: 11, fontWeight: a ? 600 : 500, fontFamily: "Roboto, system-ui, sans-serif" }}>{tb.label}</span>
          </button>
        );
      })}
    </div>
  );
}

// ── Change Category — modal sheet over a backdrop ────────────────
function MLChangeCategorySheet({
  theme = "dark",
  selected = "Salary",
  onSelect = () => {},
  onNewCategory = () => {},
  onCancel = () => {},
  // tweaks:
  showSearch = false,         // improvement #1
  highlightContrast = "subtle", // 'subtle' | 'strong' (improvement #2)
}) {
  const t = useMLTheme(theme);
  const [query, setQuery] = React.useState("");
  const items = ML_PREDEF_KEYS.filter(k => !query || (ML_DISPLAY_NAME[k] || k).toLowerCase().includes(query.toLowerCase()));

  return (
    <div style={{
      position: "absolute", inset: 0, background: t.backdrop, zIndex: 30,
      display: "flex", alignItems: "center", justifyContent: "center", padding: "60px 24px 24px",
    }}>
      <div style={{
        width: "100%", maxHeight: "100%", background: t.sheetBg,
        borderRadius: 18, display: "flex", flexDirection: "column", overflow: "hidden",
        boxShadow: "0 20px 50px rgba(0,0,0,0.45)",
      }}>
        {/* Title */}
        <div style={{ padding: "18px 20px 12px", fontFamily: "Roboto, system-ui, sans-serif", fontSize: 20, fontWeight: 700, color: t.text }}>
          Change Category
        </div>

        {/* Search (improvement) */}
        {showSearch && (
          <div style={{ padding: "0 16px 8px" }}>
            <div style={{
              display: "flex", alignItems: "center", gap: 8,
              background: t.inputBg, border: `1px solid ${t.inputBorder}`,
              borderRadius: 10, padding: "8px 12px",
            }}>
              <span style={{ color: t.textFaint }}><MLIcon.search size={16}/></span>
              <input
                value={query} onChange={e => setQuery(e.target.value)}
                placeholder="Search categories…"
                style={{ flex: 1, background: "transparent", border: 0, outline: "none",
                  color: t.text, fontSize: 14, fontFamily: "Roboto, system-ui, sans-serif" }}
              />
            </div>
          </div>
        )}

        {/* List */}
        <div style={{ flex: 1, overflow: "auto", padding: "4px 12px 4px", display: "flex", flexDirection: "column", gap: 8 }}>
          {items.map(k => {
            const isSelected = k === selected;
            const c = ML_CATEGORIES[k];
            const tinted = highlightContrast === "strong";
            return (
              <button key={k} onClick={() => onSelect(k)} style={{
                display: "flex", alignItems: "center", gap: 12, padding: "10px 12px",
                borderRadius: 12, border: 0, cursor: "pointer", textAlign: "left",
                background: isSelected ? (tinted ? c.color + "22" : t.rowBgSelected) : "transparent",
                outline: isSelected ? `1px solid ${tinted ? c.color : t.accent}` : "0",
              }}>
                <MLCatAvatar category={k} size={32}/>
                <span style={{
                  flex: 1, fontFamily: "Roboto, system-ui, sans-serif",
                  fontSize: 15, fontWeight: isSelected ? 600 : 500,
                  color: isSelected ? (tinted ? c.color : t.accent) : t.text,
                }}>{ML_DISPLAY_NAME[k] || k}</span>
              </button>
            );
          })}
        </div>

        {/* Footer */}
        <div style={{
          display: "flex", justifyContent: "flex-end", gap: 8,
          padding: "12px 16px 16px", borderTop: `1px solid ${t.border}`,
        }}>
          <button onClick={onNewCategory} style={ml_textBtn(t.accent)}>New Category</button>
          <button onClick={onCancel} style={ml_textBtn(t.accent)}>Cancel</button>
        </div>
      </div>
    </div>
  );
}

// ── Add Category modal ─────────────────────────────────────────
const ML_NEW_CAT_ICONS = ["bag", "store", "sparkle", "heart", "shield", "book", "cap", "plane", "wallet", "gear", "bell", "lock"];
const ML_NEW_CAT_COLORS = ["#EF4444", "#22C55E", "#0EA5E9", "#10B981", "#F59E0B", "#EC4899", "#8B5CF6", "#06B6D4", "#F97316", "#84CC16", "#6366F1"];

function MLAddCategorySheet({
  theme = "dark", onCancel = () => {}, onSave = () => {},
}) {
  const t = useMLTheme(theme);
  const [name, setName] = React.useState("");
  const [icon, setIcon] = React.useState("bag");
  const [color, setColor] = React.useState("#EF4444");
  const canSave = name.trim().length > 0;
  const Ic = MLIcon[icon] || MLIcon.dashed;

  return (
    <div style={{
      position: "absolute", inset: 0, background: t.backdrop, zIndex: 30,
      display: "flex", alignItems: "center", justifyContent: "center", padding: "60px 24px 24px",
    }}>
      <div style={{
        width: "100%", background: t.sheetBg, borderRadius: 18,
        display: "flex", flexDirection: "column", overflow: "hidden",
        boxShadow: "0 20px 50px rgba(0,0,0,0.45)",
      }}>
        <div style={{ padding: "18px 20px 14px", fontFamily: "Roboto, system-ui, sans-serif", fontSize: 20, fontWeight: 700, color: t.text }}>
          Add Category
        </div>

        <div style={{ padding: "0 16px 14px", display: "flex", flexDirection: "column", gap: 16 }}>
          {/* Name input */}
          <div style={{
            background: t.inputBg, border: `1px solid ${t.inputBorder}`,
            borderRadius: 12, padding: "14px 14px",
          }}>
            <input
              value={name} onChange={e => setName(e.target.value)}
              placeholder="Category Name"
              style={{
                width: "100%", background: "transparent", border: 0, outline: "none",
                color: t.text, fontSize: 16, fontFamily: "Roboto, system-ui, sans-serif",
              }}
            />
          </div>

          {/* Icon picker */}
          <div>
            <div style={{ fontSize: 13, fontWeight: 600, color: t.text, marginBottom: 8 }}>Icon</div>
            <div style={{ display: "flex", gap: 10, overflowX: "auto", paddingBottom: 4 }}>
              {ML_NEW_CAT_ICONS.map(k => {
                const KIcon = MLIcon[k];
                const sel = k === icon;
                return (
                  <button key={k} onClick={() => setIcon(k)} style={{
                    width: 40, height: 40, borderRadius: "50%", border: 0, cursor: "pointer", flex: "none",
                    background: sel ? color : t.chipBg, color: sel ? "#fff" : t.textMuted,
                    display: "flex", alignItems: "center", justifyContent: "center",
                    outline: sel ? `2px solid ${color}` : "0", outlineOffset: 2,
                  }}>
                    <KIcon size={18}/>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Color picker */}
          <div>
            <div style={{ fontSize: 13, fontWeight: 600, color: t.text, marginBottom: 8 }}>Color</div>
            <div style={{ display: "flex", gap: 10, overflowX: "auto", paddingBottom: 4 }}>
              {ML_NEW_CAT_COLORS.map(c => {
                const sel = c === color;
                return (
                  <button key={c} onClick={() => setColor(c)} style={{
                    width: 36, height: 36, borderRadius: "50%", border: 0, cursor: "pointer", flex: "none",
                    background: c, position: "relative",
                    boxShadow: sel ? `0 0 0 3px ${t.sheetBg}, 0 0 0 5px ${c}` : "none",
                  }}>
                    {sel && <span style={{
                      position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center",
                      color: "#fff",
                    }}><MLIcon.check size={18}/></span>}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Live preview */}
          <div style={{
            background: t.inputBg, border: `1px solid ${t.inputBorder}`,
            borderRadius: 12, padding: "12px 14px", display: "flex", alignItems: "center", gap: 12,
          }}>
            <div style={{ fontSize: 12, color: t.textFaint, fontWeight: 600, fontFamily: "Roboto, system-ui, sans-serif" }}>Preview:</div>
            <div style={{ width: 30, height: 30, borderRadius: "50%", background: color, color: "#fff", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <Ic size={16}/>
            </div>
            <div style={{ fontSize: 15, color: t.text, fontFamily: "Roboto, system-ui, sans-serif", fontWeight: 500 }}>
              {name.trim() || "Category Name"}
            </div>
          </div>
        </div>

        {/* Footer */}
        <div style={{
          display: "flex", justifyContent: "flex-end", gap: 8,
          padding: "12px 16px 16px", borderTop: `1px solid ${t.border}`,
        }}>
          <button onClick={onCancel} style={ml_textBtn(t.accent)}>Cancel</button>
          <button onClick={() => canSave && onSave({ name: name.trim(), icon, color })} disabled={!canSave} style={{
            ...ml_textBtn(canSave ? t.accent : t.textFaint),
            cursor: canSave ? "pointer" : "not-allowed",
            opacity: canSave ? 1 : 0.6,
          }}>Save</button>
        </div>
      </div>
    </div>
  );
}

function ml_textBtn(color) {
  return {
    background: "transparent", border: 0, padding: "8px 12px",
    color, fontSize: 14, fontWeight: 600, fontFamily: "Roboto, system-ui, sans-serif",
    cursor: "pointer", borderRadius: 8,
  };
}

// ── Tiny "Activity backdrop" used behind the Change Category modal
function MLActivityBackdrop({ theme = "dark" }) {
  const t = useMLTheme(theme);
  return (
    <>
      <MLStatusBar dark={theme === "dark"}/>
      <div style={{ background: t.headerBg, color: t.text, padding: "6px 16px 14px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", minHeight: 36, opacity: 0.5 }}>
          <span style={{ fontSize: 18, fontWeight: 700, fontFamily: "Roboto, system-ui, sans-serif" }}>Activity</span>
          <span style={{ color: t.textFaint }}><MLIcon.share size={20}/></span>
        </div>
      </div>
      <div style={{ flex: 1, background: t.bg, padding: 16, opacity: 0.4 }}>
        {/* dim pretend-rows */}
        {[...Array(6)].map((_, i) => (
          <div key={i} style={{ display: "flex", gap: 12, padding: "12px 0", borderBottom: `1px solid ${t.borderSoft}` }}>
            <div style={{ width: 32, height: 32, borderRadius: 10, background: t.surface }}/>
            <div style={{ flex: 1 }}>
              <div style={{ height: 10, background: t.surface, borderRadius: 4, width: "60%" }}/>
              <div style={{ height: 8, background: t.surface, borderRadius: 4, width: "40%", marginTop: 6, opacity: 0.6 }}/>
            </div>
            <div style={{ width: 60, height: 12, background: t.surface, borderRadius: 4 }}/>
          </div>
        ))}
      </div>
      <MLTabBarT theme={theme} active="activity"/>
    </>
  );
}

Object.assign(window, {
  ML_THEMES, useMLTheme, MLPhoneT, MLTabBarT,
  MLManageCategoriesScreen, MLChangeCategorySheet, MLAddCategorySheet,
  MLActivityBackdrop, ML_PREDEF_KEYS, ML_DISPLAY_NAME,
});
