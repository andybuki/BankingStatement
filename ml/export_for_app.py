#!/usr/bin/env python3
"""Export the trained sklearn TF-IDF + LogisticRegression pipeline to JSON
for on-device inference inside the Kotlin Multiplatform app.

Usage:
    python ml/export_for_app.py \
        --model ml/model/transaction_category_model.joblib \
        --output composeApp/src/commonMain/composeResources/files/category_model.json

The exported JSON has the following shape:
    {
        "model_version":  "...",
        "labels":         ["RENT", "TRANSPORT", ...],   # n_classes
        "ngram_min":      1,
        "ngram_max":      3,
        "sublinear_tf":   true,
        "vocabulary":     {"netflix": 14, "rewe": 421, ...},
        "idf":            [3.21, 1.84, ...],            # vocab_size
        "intercepts":     [...],                        # n_classes
        "coefs":          [[...], [...]]                # n_classes x vocab_size
    }

The Kotlin side reproduces sklearn's default tokenizer (\\b\\w\\w+\\b),
1+log(tf), L2 normalization, and a linear softmax. The features going
into the vectorizer must already be cleaned by the same preprocessing
the training script applies — that logic is mirrored in Kotlin too.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import joblib


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--model",
        type=Path,
        default=Path("ml/model/transaction_category_model.joblib"),
        help="Path to the joblib pipeline produced by train_category_model.py",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(
            "composeApp/src/commonMain/composeResources/files/category_model.json"
        ),
        help="Where to write the JSON consumed by the on-device classifier",
    )
    parser.add_argument(
        "--model-version",
        type=str,
        default="v1",
        help="Free-form version string written into the JSON",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    pipeline = joblib.load(args.model)

    tfidf = pipeline.named_steps["tfidf"]
    clf = pipeline.named_steps["clf"]

    # Sanity checks: the Kotlin reader assumes the same hyperparameters.
    if tfidf.analyzer != "word":
        raise SystemExit(f"Unsupported analyzer={tfidf.analyzer!r}; expected 'word'.")
    if tfidf.lowercase is not True:
        raise SystemExit("Unsupported tfidf.lowercase=False; the Kotlin reader assumes lowercase=True.")
    if tfidf.norm != "l2":
        raise SystemExit(f"Unsupported norm={tfidf.norm!r}; expected 'l2'.")
    if tfidf.binary:
        raise SystemExit("Unsupported binary=True.")

    vocabulary = {token: int(index) for token, index in tfidf.vocabulary_.items()}
    idf = [float(value) for value in tfidf.idf_.tolist()]

    labels = [str(label) for label in clf.classes_.tolist()]
    coefs = [[float(value) for value in row] for row in clf.coef_.tolist()]
    intercepts = [float(value) for value in clf.intercept_.tolist()]

    # Binary LogisticRegression keeps a single coefficient row shaped (1, n_features)
    # representing the positive class. The Kotlin path only handles the multi-class
    # softmax form, so refuse to export until the dataset has at least 3 classes.
    if len(labels) < 3:
        raise SystemExit(
            f"Need >= 3 classes for the multi-class softmax exporter; got {len(labels)}."
        )

    payload = {
        "model_version": args.model_version,
        "labels": labels,
        "ngram_min": int(tfidf.ngram_range[0]),
        "ngram_max": int(tfidf.ngram_range[1]),
        "sublinear_tf": bool(tfidf.sublinear_tf),
        "vocabulary": vocabulary,
        "idf": idf,
        "intercepts": intercepts,
        "coefs": coefs,
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload), encoding="utf-8")

    size_kb = args.output.stat().st_size / 1024
    print(
        f"Wrote {args.output} (vocab={len(vocabulary)}, classes={len(labels)}, {size_kb:.1f} KB)"
    )


if __name__ == "__main__":
    main()
