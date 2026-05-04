#!/usr/bin/env python3
"""Train a first transaction-category ML baseline.

Input CSV should contain at least:
- Description
- ExpectedCategory

Optional but recommended:
- Counterparty
- Amount
- Currency
- Account

The model is intentionally simple: TF-IDF + LogisticRegression.
It is meant for local evaluation and later app integration, not as a blind
replacement for rules/user overrides.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

import joblib
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, accuracy_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline


BANK_NOISE_WORDS = {
    "lastschrift", "basislastschrift", "einzug", "ueberweisung", "überweisung",
    "sepa", "svwz", "mref", "eref", "kref", "mandat", "referenz", "iban", "bic",
    "konto", "girokonto", "kontoauszug", "buchungstag", "valuta", "wertstellung",
    "betrag", "saldo", "neuer", "alter", "kunden", "information", "seite", "nummer",
    "nr", "xxxx", "arn", "visa", "mastercard", "girocard", "debitk", "kaufumsatz",
    "zahlung", "bezahlung", "rechnung", "abbuchung", "gutschrift", "entgelt",
    "gebühr", "gebuehr", "kundennummer", "kartenzahlung", "sagt", "danke",
    "paypal", "europe", "cie", "pp", "uhr", "kurs", "de", "nrxxxx",
    "januar", "februar", "maerz", "märz", "april", "mai", "juni", "juli", "august",
    "september", "oktober", "november", "dezember",
}

LEGAL_SUFFIXES = {
    "gmbh", "ag", "kg", "ohg", "ug", "se", "inc", "ltd", "llc", "bv", "ab", "as",
    "sarl", "sca", "co", "et",
}

CATEGORY_CANONICAL = {
    "rent & utilities": "RENT",
    "rent": "RENT",
    "housing": "RENT",
    "transport": "TRANSPORT",
    "supermarket": "SUPERMARKET",
    "groceries": "SUPERMARKET",
    "restaurant": "RESTAURANT",
    "shopping": "SHOPPING",
    "health": "HEALTH",
    "insurance": "INSURANCE",
    "entertainment": "ENTERTAINMENT",
    "subscriptions": "SUBSCRIPTIONS",
    "subscription": "SUBSCRIPTIONS",
    "investment": "INVESTMENT",
    "travel": "TRAVEL",
    "salary": "SALARY",
    "refund": "REFUND",
    "transfer": "TRANSFER",
    "education": "EDUCATION",
    "taxes": "TAXES",
    "tax": "TAXES",
    "other": "OTHER",
}

DOMAIN_HINTS: list[tuple[str, list[str]]] = [
    ("hint_transport", ["flix", "db vertrieb", "deutsche bahn", "bvg", "bahn", "logpay"]),
    ("hint_supermarket", ["flaschenpost", "rewe", "lidl", "aldi", "edeka", "kaufland", "netto", "penny", "alnatura", "bio company"]),
    ("hint_restaurant", ["backerei", "baeckerei", "coffee", "gastro", "food", "drei koche", "sironi", "burger", "pizza", "sushi", "takeaway"]),
    ("hint_subscription", ["apple services", "youtube premium", "google one", "spotify", "netflix"]),
    ("hint_travel", ["hotel", "booking", "airbnb", "flight", "flug", "lufthansa", "easyjet", "ryanair"]),
    ("hint_transfer", ["atm", "geldautomat", "abhebung", "auszahlung", "money transfer"]),
]

PAYPAL_PATTERNS = [
    re.compile(r"i\s*hr\s+einkauf\s+b\s*ei\s+(.+?)(?:\s+mandat:|\s+referenz:|/abbuchung|\s+awv-meldepflicht|\s+mref\+|\s+eref\+|$)", re.I),
    re.compile(r"/\.?\s*([^/]+?),\s*i\s*hr\s+einkauf", re.I),
    re.compile(r"svwz\+.*?\.\s*([^,]+?),\s*i\s*hr\s+einkauf", re.I),
]
VISA_PATTERN = re.compile(r"\bvisa\s+(.+?)(?:\s+nr\s+xxxx|\s+kaufumsatz|$)", re.I)
CARD_SLASH_PATTERN = re.compile(r"(?:debitkartenzahlung|kartenzahlung|lastschrift\s+aus\s+kartenzahlung)?\s*([A-ZÄÖÜ0-9 .&'\-]+?)//[A-ZÄÖÜa-zäöüß .\-]+/DE", re.I)


def normalize_broken_words(text: str) -> str:
    replacements = [
        (r"(?i)se\s+rvices", "services"),
        (r"(?i)servi\s+ces", "services"),
        (r"(?i)vertr\s+ieb", "vertrieb"),
        (r"(?i)vertri\s+eb", "vertrieb"),
        (r"(?i)koc\s+he", "koche"),
        (r"(?i)otr\s+ium", "otrium"),
        (r"(?i)al\s+ipay", "alipay"),
        (r"(?i)b\s+ei", "bei"),
        (r"(?i)i\s+hr", "ihr"),
    ]
    for pattern, repl in replacements:
        text = re.sub(pattern, repl, text)
    return text


def extract_effective_merchant(text: str) -> str | None:
    lower = text.lower()
    if "paypal" in lower:
        for pattern in PAYPAL_PATTERNS:
            match = pattern.search(text)
            if match:
                return cleanup_text(match.group(1), keep_noise=False)
    match = VISA_PATTERN.search(text)
    if match:
        return cleanup_text(match.group(1), keep_noise=False)
    match = CARD_SLASH_PATTERN.search(text)
    if match:
        return cleanup_text(match.group(1), keep_noise=False)
    return None


def cleanup_text(text: object, keep_noise: bool = False) -> str:
    if text is None or pd.isna(text):
        return ""
    text = normalize_broken_words(str(text)).lower()
    text = text.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    text = re.sub(r"arn\w+", " ", text, flags=re.I)
    text = re.sub(r"\b\w*gkka\w*\b", " ", text, flags=re.I)
    text = re.sub(r"\b\d{1,2}\.\d{1,2}(?:\.\d{2,4})?\b", " ", text)
    text = re.sub(r"\b\d+[,.]\d+\b", " ", text)
    text = re.sub(r"\b\d{3,}\b", " ", text)
    text = re.sub(r"[^a-z0-9 ]+", " ", text)
    words = [w for w in re.split(r"\s+", text) if w]
    if not keep_noise:
        words = [w for w in words if w not in BANK_NOISE_WORDS and w not in LEGAL_SUFFIXES and len(w) > 1]
    return " ".join(words).strip()


def domain_hints(text: str) -> list[str]:
    lowered = text.lower()
    hints: list[str] = []
    for hint, patterns in DOMAIN_HINTS:
        if any(pattern in lowered for pattern in patterns):
            hints.append(hint)
    return hints


def canonical_category(value: object) -> str:
    raw = str(value).strip()
    key = raw.lower().strip()
    return CATEGORY_CANONICAL.get(key, raw.upper().replace(" ", "_"))


def build_features(df: pd.DataFrame) -> pd.Series:
    descriptions = df.get("Description", pd.Series([""] * len(df))).fillna("").astype(str)
    counterparties = df.get("Counterparty", pd.Series([""] * len(df))).fillna("").astype(str)
    amounts = pd.to_numeric(df.get("Amount", pd.Series([0] * len(df))), errors="coerce").fillna(0.0)

    rows: list[str] = []
    for desc, counterparty, amount in zip(descriptions, counterparties, amounts):
        combined = f"{desc} {counterparty}"
        merchant = extract_effective_merchant(combined)
        clean_desc = cleanup_text(combined, keep_noise=False)
        hint_text = " ".join(domain_hints(" ".join([merchant or "", clean_desc])))
        amount_sign = "expense" if amount < 0 else "income" if amount > 0 else "zero"
        amount_bucket = "large" if abs(amount) >= 100 else "medium" if abs(amount) >= 20 else "small"
        # Repeat extracted merchant because it is usually the strongest feature.
        rows.append(" ".join(part for part in [merchant, merchant, hint_text, clean_desc, amount_sign, amount_bucket] if part))
    return pd.Series(rows)


def load_dataset(path: Path) -> pd.DataFrame:
    df = pd.read_csv(path)
    if "ExpectedCategory" not in df.columns:
        raise ValueError(f"CSV must contain ExpectedCategory. Columns: {df.columns.tolist()}")
    if "Description" not in df.columns:
        raise ValueError(f"CSV must contain Description. Columns: {df.columns.tolist()}")
    df = df.copy()
    df["label"] = df["ExpectedCategory"].apply(canonical_category)
    df["feature_text"] = build_features(df)
    df = df[(df["label"] != "") & (df["feature_text"] != "")]
    return df


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("csv", type=Path, help="Corrected CSV with ExpectedCategory")
    parser.add_argument("--output-dir", type=Path, default=Path("ml/model"))
    parser.add_argument("--test-size", type=float, default=0.2)
    parser.add_argument("--random-state", type=int, default=42)
    args = parser.parse_args()

    df = load_dataset(args.csv)
    print(f"Rows: {len(df)}")
    print("\nCategory distribution:")
    print(df["label"].value_counts())

    min_count = df["label"].value_counts().min()
    stratify = df["label"] if min_count >= 2 else None

    x_train, x_test, y_train, y_test = train_test_split(
        df["feature_text"],
        df["label"],
        test_size=args.test_size,
        random_state=args.random_state,
        stratify=stratify,
    )

    model = Pipeline([
        ("tfidf", TfidfVectorizer(ngram_range=(1, 3), min_df=1, max_features=12000, sublinear_tf=True)),
        ("clf", LogisticRegression(max_iter=3000, class_weight="balanced", C=2.0)),
    ])
    model.fit(x_train, y_train)

    y_pred = model.predict(x_test)
    proba = model.predict_proba(x_test)
    confidence = proba.max(axis=1)

    print("\nML only accuracy:", round(accuracy_score(y_test, y_pred), 4))
    print("\nClassification report:")
    print(classification_report(y_test, y_pred, zero_division=0))

    print("\nConfidence thresholds:")
    for threshold in [0.50, 0.60, 0.70, 0.75, 0.80, 0.90]:
        mask = confidence >= threshold
        if mask.sum() == 0:
            print(f">= {threshold:.2f}: coverage 0.00%, accuracy n/a")
            continue
        acc = accuracy_score(y_test[mask], y_pred[mask])
        print(f">= {threshold:.2f}: coverage {mask.mean():.2%}, accuracy {acc:.2%}, rows {int(mask.sum())}")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, args.output_dir / "transaction_category_model.joblib")
    labels = sorted(df["label"].unique().tolist())
    (args.output_dir / "labels.json").write_text(json.dumps(labels, indent=2), encoding="utf-8")
    print(f"\nSaved model to {args.output_dir / 'transaction_category_model.joblib'}")


if __name__ == "__main__":
    main()
