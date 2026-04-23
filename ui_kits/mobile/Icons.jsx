// MoneyLupe UI Kit — Icons (Lucide subset, inlined as small React components)
// Keeps stroke 1.75, size defaults to 20.

const ml_sv = (p, s = 20) => ({
  width: s, height: s, viewBox: "0 0 24 24", fill: "none",
  stroke: "currentColor", strokeWidth: 1.75, strokeLinecap: "round", strokeLinejoin: "round",
});

const MLIcon = {
  home:   (p = {}) => <svg {...ml_sv(p, p.size)}><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2h-4v-7h-6v7H5a2 2 0 0 1-2-2z"/></svg>,
  list:   (p = {}) => <svg {...ml_sv(p, p.size)}><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><circle cx="4" cy="6" r="1.2"/><circle cx="4" cy="12" r="1.2"/><circle cx="4" cy="18" r="1.2"/></svg>,
  pie:    (p = {}) => <svg {...ml_sv(p, p.size)}><path d="M21 15.9A10 10 0 1 1 8 2.8"/><path d="M22 12A10 10 0 0 0 12 2v10z"/></svg>,
  store:  (p = {}) => <svg {...ml_sv(p, p.size)}><path d="M3 9l1-5h16l1 5"/><path d="M4 9v11h16V9"/><path d="M9 22V12h6v10"/></svg>,
  gear:   (p = {}) => <svg {...ml_sv(p, p.size)}><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09c.73 0 1.37-.43 1.65-1.09z"/></svg>,
  upload: (p = {}) => <svg {...ml_sv(p, p.size)}><path d="M12 19V5M5 12l7-7 7 7"/></svg>,
  download:(p = {}) => <svg {...ml_sv(p, p.size)}><path d="M12 5v14M5 12l7 7 7-7"/></svg>,
  share:  (p = {}) => <svg {...ml_sv(p, p.size)}><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.6" y1="13.5" x2="15.4" y2="17.5"/><line x1="15.4" y1="6.5" x2="8.6" y2="10.5"/></svg>,
  search: (p = {}) => <svg {...ml_sv(p, p.size)}><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.5" y2="16.5"/></svg>,
  chevronRight:(p = {}) => <svg {...ml_sv(p, p.size)}><polyline points="9 18 15 12 9 6"/></svg>,
  chevronLeft:(p = {}) => <svg {...ml_sv(p, p.size)}><polyline points="15 18 9 12 15 6"/></svg>,
  plus:   (p = {}) => <svg {...ml_sv(p, p.size)}><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>,
  cart:   (p = {}) => <svg {...ml_sv(p, p.size)}><circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/><path d="M1 1h4l2.7 13.4a2 2 0 0 0 2 1.6h9.7a2 2 0 0 0 2-1.6L23 6H6"/></svg>,
  bus:    (p = {}) => <svg {...ml_sv(p, p.size)}><rect x="4" y="3" width="16" height="15" rx="2"/><path d="M4 11h16"/><path d="M8 18v2M16 18v2"/><circle cx="8" cy="15" r="1"/><circle cx="16" cy="15" r="1"/></svg>,
  utensils:(p = {}) => <svg {...ml_sv(p, p.size)}><path d="M3 2v7a2 2 0 0 0 2 2h0a2 2 0 0 0 2-2V2M5 11v11"/><path d="M15 2v20"/><path d="M15 12c0-3 2-6 5-6v16"/></svg>,
  bag:    (p = {}) => <svg {...ml_sv(p, p.size)}><path d="M4 7h16l-1 14H5z"/><path d="M8 7a4 4 0 0 1 8 0"/></svg>,
  heart:  (p = {}) => <svg {...ml_sv(p, p.size)}><path d="M3 12h3l2-5 4 10 2-5h7"/></svg>,
  plane:  (p = {}) => <svg {...ml_sv(p, p.size)}><path d="M17.8 19.2L16 11l3.5-3.5A2.12 2.12 0 0 0 16.5 4.5L13 8 4.8 6.2a.5.5 0 0 0-.5.8L9 12l-3 3H4l2 3 3 2 3-3v-2l4.2 4.7a.5.5 0 0 0 .8-.5z"/></svg>,
  wallet: (p = {}) => <svg {...ml_sv(p, p.size)}><path d="M21 12V7a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-1"/><path d="M18 12a2 2 0 0 0 0 4h4v-4z"/></svg>,
  repeat: (p = {}) => <svg {...ml_sv(p, p.size)}><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>,
  trending:(p = {}) => <svg {...ml_sv(p, p.size)}><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>,
  filter: (p = {}) => <svg {...ml_sv(p, p.size)}><line x1="4" y1="6" x2="20" y2="6"/><line x1="6" y1="12" x2="18" y2="12"/><line x1="9" y1="18" x2="15" y2="18"/></svg>,
  arrowLR:(p = {}) => <svg {...ml_sv(p, p.size)}><polyline points="17 1 21 5 17 9"/><path d="M3 5h18"/><polyline points="7 15 3 19 7 23"/><path d="M21 19H3"/></svg>,
  file:   (p = {}) => <svg {...ml_sv(p, p.size)}><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>,
  bell:   (p = {}) => <svg {...ml_sv(p, p.size)}><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>,
  lock:   (p = {}) => <svg {...ml_sv(p, p.size)}><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>,
  check:  (p = {}) => <svg {...ml_sv(p, p.size)}><polyline points="20 6 9 17 4 12"/></svg>,
  x:      (p = {}) => <svg {...ml_sv(p, p.size)}><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>,
  shield: (p = {}) => <svg {...ml_sv(p, p.size)}><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>,
  sparkle:(p = {}) => <svg {...ml_sv(p, p.size)}><path d="M12 3v4M12 17v4M3 12h4M17 12h4M5.6 5.6l2.8 2.8M15.6 15.6l2.8 2.8M5.6 18.4l2.8-2.8M15.6 8.4l2.8-2.8"/></svg>,
  book:   (p = {}) => <svg {...ml_sv(p, p.size)}><path d="M22 4v16l-6-2-4 2-4-2-6 2V4l6-2 4 2 4-2z"/><path d="M12 4v16"/></svg>,
  landmark:(p = {}) => <svg {...ml_sv(p, p.size)}><line x1="3" y1="22" x2="21" y2="22"/><line x1="6" y1="18" x2="6" y2="11"/><line x1="10" y1="18" x2="10" y2="11"/><line x1="14" y1="18" x2="14" y2="11"/><line x1="18" y1="18" x2="18" y2="11"/><polygon points="12 2 20 7 4 7"/></svg>,
  undo:   (p = {}) => <svg {...ml_sv(p, p.size)}><polyline points="9 14 4 9 9 4"/><path d="M20 20v-7a4 4 0 0 0-4-4H4"/></svg>,
  dashed: (p = {}) => <svg {...ml_sv(p, p.size)}><circle cx="12" cy="12" r="9" strokeDasharray="4 3"/></svg>,
  clap:   (p = {}) => <svg {...ml_sv(p, p.size)}><rect x="3" y="6" width="18" height="13" rx="2"/><path d="M3 10h18"/><path d="M7 6l2-3M17 6l-2-3"/></svg>,
  cap:    (p = {}) => <svg {...ml_sv(p, p.size)}><path d="M2 8l10-4 10 4-10 4z"/><path d="M6 10v5c0 2 3 3 6 3s6-1 6-3v-5"/></svg>,
};

// Category → (color var, icon)
const ML_CATEGORIES = {
  Rent:         { color: "#0EA5E9", icon: "home" },
  Transport:    { color: "#F59E0B", icon: "bus" },
  Supermarket:  { color: "#22C55E", icon: "cart" },
  Restaurant:   { color: "#EF4444", icon: "utensils" },
  Shopping:     { color: "#EC4899", icon: "bag" },
  Health:       { color: "#14B8A6", icon: "heart" },
  Insurance:    { color: "#6366F1", icon: "shield" },
  Entertainment:{ color: "#A855F7", icon: "clap" },
  Subscriptions:{ color: "#8B5CF6", icon: "repeat" },
  Investment:   { color: "#10B981", icon: "trending" },
  Travel:       { color: "#06B6D4", icon: "plane" },
  Salary:       { color: "#16A34A", icon: "wallet" },
  Refund:       { color: "#84CC16", icon: "undo" },
  Transfer:     { color: "#64748B", icon: "arrowLR" },
  Education:    { color: "#F97316", icon: "cap" },
  Taxes:        { color: "#71717A", icon: "landmark" },
  Other:        { color: "#94A3B8", icon: "dashed" },
};

Object.assign(window, { MLIcon, ML_CATEGORIES });
