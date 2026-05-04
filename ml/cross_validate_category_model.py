#!/usr/bin/env python3
"""Cross-validate the transaction-category ML baseline.

Use this before Android integration. Unlike evaluate_category_model.py, this
script never evaluates on rows used for training in the same fold.

It reports:
- out-of-fold ML accuracy
- thresholded coverage/accuracy
- most confident out-of-fold mistakes

This is the best first estimate of how useful ML fallback will be on unseen
transactions.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report
from sklearn.model_selection import StratifiedKFold
from sklearn.pipeline import Pipeline

from train_category_model import load_dataset


def build_model() -> Pipeline:
    return Pipeline([
        ("tfidf", TfidfVectorizer(
            ngram_range=(1, 3),
            min_df=1,
            max_features=12000,
            sublinear_tf=True,
        )),
        ("clf", LogisticRegression(
            max_iter=3000,
            class_weight="balanced",
            C=2.0,
        )),
    ])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("csv", type=Path, help="Corrected CSV with ExpectedCategory")
    parser.add_argument("--folds", type=int, default=5)
    parser.add_argument("--thresholds", type=float, nargs="*", default=[0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.75])
    parser.add_argument("--errors-out", type=Path, default=Path("ml/model/cv_errors.csv"))
    args = parser.parse_args()

    df = load_dataset(args.csv).reset_index(drop=True)
    y = df["label"].values
    x = df["feature_text"].values

    counts = df["label"].value_counts()
    min_class_count = int(counts.min())
    folds = min(args.folds, min_class_count)

    if folds < 2:
        raise ValueError(
            "Need at least 2 examples in every category for stratified cross-validation. "
            f"Smallest class has {min_class_count}."
        )

    skf = StratifiedKFold(n_splits=folds, shuffle=True, random_state=42)

    predictions = np.empty(len(df), dtype=object)
    confidence = np.zeros(len(df), dtype=float)

    for fold, (train_idx, test_idx) in enumerate(skf.split(x, y), start=1):
        model = build_model()
        model.fit(x[train_idx], y[train_idx])
        fold_pred = model.predict(x[test_idx])
        fold_proba = model.predict_proba(x[test_idx])
        predictions[test_idx] = fold_pred
        confidence[test_idx] = fold_proba.max(axis=1)
        print(f"Fold {fold}: accuracy {accuracy_score(y[test_idx], fold_pred):.2%}, rows {len(test_idx)}")

    df["MlPrediction"] = predictions
    df["MlConfidence"] = confidence

    print("\nOut-of-fold ML accuracy:", f"{accuracy_score(y, predictions):.2%}")
    print("\nOut-of-fold classification report:")
    print(classification_report(y, predictions, zero_division=0))

    print("\nOut-of-fold confidence thresholds:")
    for threshold in args.thresholds:
        mask = confidence >= threshold
        if mask.sum() == 0:
            print(f">= {threshold:.2f}: coverage 0.00%, accuracy n/a, rows 0")
            continue
        acc = accuracy_score(y[mask], predictions[mask])
        print(f">= {threshold:.2f}: coverage {mask.mean():.2%}, accuracy {acc:.2%}, rows {int(mask.sum())}")

    errors = df[df["label"] != df["MlPrediction"]].copy()
    errors = errors.sort_values("MlConfidence", ascending=False)
    args.errors_out.parent.mkdir(parents=True, exist_ok=True)
    columns = [c for c in [
        "Date", "Description", "Counterparty", "Amount", "ExpectedCategory",
        "label", "MlPrediction", "MlConfidence", "feature_text"
    ] if c in errors.columns]
    errors[columns].to_csv(args.errors_out, index=False)

    print(f"\nOut-of-fold errors: {len(errors)}")
    print(f"Saved out-of-fold mistakes to: {args.errors_out}")
    if not errors.empty:
        preview_cols = [c for c in ["Description", "label", "MlPrediction", "MlConfidence"] if c in errors.columns]
        print("\nMost confident out-of-fold mistakes:")
        print(errors[preview_cols].head(20).to_string(index=False))


if __name__ == "__main__":
    main()
