#!/usr/bin/env python3
"""Evaluate a trained category model on corrected transaction CSV data.

This script focuses on deployment-style metrics:
- ML-only accuracy
- thresholded ML fallback coverage/accuracy
- rows that should remain OTHER because confidence is too low
- most confident mistakes for manual inspection

Use after running train_category_model.py.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import joblib
import pandas as pd
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix

from train_category_model import load_dataset


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("csv", type=Path, help="Corrected CSV with ExpectedCategory")
    parser.add_argument("--model", type=Path, default=Path("ml/model/transaction_category_model.joblib"))
    parser.add_argument("--threshold", type=float, default=0.75)
    parser.add_argument("--errors-out", type=Path, default=Path("ml/model/ml_errors.csv"))
    args = parser.parse_args()

    df = load_dataset(args.csv)
    model = joblib.load(args.model)

    pred = model.predict(df["feature_text"])
    proba = model.predict_proba(df["feature_text"])
    confidence = proba.max(axis=1)

    df = df.copy()
    df["MlPrediction"] = pred
    df["MlConfidence"] = confidence
    df["MlAutoCategory"] = [p if c >= args.threshold else "OTHER" for p, c in zip(pred, confidence)]

    print(f"Rows: {len(df)}")
    print(f"Threshold: {args.threshold:.2f}")
    print("\nML-only accuracy:", round(accuracy_score(df["label"], df["MlPrediction"]), 4))

    auto_mask = df["MlConfidence"] >= args.threshold
    if auto_mask.any():
        print("Thresholded coverage:", f"{auto_mask.mean():.2%}")
        print("Thresholded accuracy:", f"{accuracy_score(df.loc[auto_mask, 'label'], df.loc[auto_mask, 'MlPrediction']):.2%}")
    else:
        print("Thresholded coverage: 0.00%")
        print("Thresholded accuracy: n/a")

    print("\nClassification report:")
    print(classification_report(df["label"], df["MlPrediction"], zero_division=0))

    errors = df[df["label"] != df["MlPrediction"]].copy()
    errors = errors.sort_values("MlConfidence", ascending=False)
    args.errors_out.parent.mkdir(parents=True, exist_ok=True)
    columns = [c for c in [
        "Date", "Description", "Counterparty", "Amount", "ExpectedCategory",
        "label", "MlPrediction", "MlConfidence", "feature_text"
    ] if c in errors.columns]
    errors[columns].to_csv(args.errors_out, index=False)

    print(f"\nErrors: {len(errors)}")
    print(f"Saved confident mistakes to: {args.errors_out}")

    print("\nMost confident mistakes:")
    preview_cols = [c for c in ["Description", "label", "MlPrediction", "MlConfidence"] if c in errors.columns]
    if not errors.empty:
        print(errors[preview_cols].head(20).to_string(index=False))


if __name__ == "__main__":
    main()
