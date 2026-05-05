# Transaction Category ML Toolkit

This folder contains the first local ML workflow for transaction categorization.

The goal is not to replace rules. The target architecture is:

```text
user override
→ strong signal rules
→ merchant / keyword rules
→ ML fallback
→ OTHER if confidence is too low
```

## Input CSV format

Use an exported and manually corrected CSV with at least these columns:

```csv
Description,ExpectedCategory
```

Recommended columns:

```csv
Date,Description,Counterparty,ExpectedCategory,Amount,Currency,Account
```

`ExpectedCategory` should use app category names, for example:

```text
RENT
TRANSPORT
SUPERMARKET
RESTAURANT
SHOPPING
HEALTH
INSURANCE
ENTERTAINMENT
SUBSCRIPTIONS
INVESTMENT
TRAVEL
SALARY
REFUND
TRANSFER
EDUCATION
TAXES
OTHER
```

Human-friendly labels such as `Rent & Utilities`, `Supermarket`, `Transfer` are also normalized by the script.

## Setup

From the repository root:

```bash
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\\Scripts\\activate
pip install -r ml/requirements.txt
```

## Train

```bash
python ml/train_category_model.py path/to/transactions_corrected.csv
```

Outputs:

```text
ml/model/transaction_category_model.joblib
ml/model/labels.json
```

## Evaluate

```bash
python ml/evaluate_category_model.py path/to/transactions_corrected.csv --threshold 0.75
```

The script prints:

```text
ML-only accuracy
thresholded coverage
thresholded accuracy
classification report
most confident mistakes
```

It also writes:

```text
ml/model/ml_errors.csv
```

## How to interpret results

For app integration, the most important metric is not plain ML accuracy.

The important metric is:

```text
accuracy when confidence >= threshold
```

Example:

```text
threshold 0.75
coverage 35%
accuracy 90%
```

This means ML can safely auto-categorize around 35% of rule-unknown transactions, while the remaining 65% should stay `OTHER` or wait for user confirmation.

## Recommended app modes

```text
Safe:
  rules only, ML disabled

Balanced:
  rules + ML fallback if confidence >= 0.75

Experimental:
  rules + ML fallback if confidence >= 0.55
```

## Notes

The current model is intentionally simple:

```text
TF-IDF + LogisticRegression
```

Before Android integration, compare:

```text
rules only
ml only
rules + ml fallback
```

Only ship on-device ML if `rules + ml fallback` improves automatic accuracy and does not create too many confident mistakes.

## On-device export

After training, export the joblib pipeline to JSON for the Kotlin Multiplatform classifier:

```bash
python ml/export_for_app.py \
  --model ml/model/transaction_category_model.joblib \
  --output composeApp/src/commonMain/composeResources/files/category_model.json \
  --model-version v1
```

The exporter writes a flat JSON document containing the TF-IDF vocabulary and IDF weights, the LogisticRegression coefficient matrix and intercepts, and the n-gram / sublinear_tf settings. It refuses to export non-default sklearn settings (e.g. `norm != 'l2'`) because the on-device implementation only supports the defaults the training script uses.

To enable the model in the app:

- **Android** — the file is bundled automatically via Compose Resources and read from `assets/files/category_model.json` at startup.
- **iOS** — add `category_model.json` as a resource in the `iosApp` Xcode project (Build Phases → Copy Bundle Resources).

If the file is missing or fails to parse, the app silently falls back to `NoOpTransactionMlClassifier` so the UI's `Balanced` / `Experimental` modes degrade gracefully.

## Preprocessing parity

The Kotlin classifier reproduces `cleanup_text`, `extract_effective_merchant`, `domain_hints`, and `amount_features` from `train_category_model.py`. Any change to the Python preprocessor must be mirrored in `MlFeatureExtractor.kt` or predictions will silently drift. `MlFeatureExtractorTest` pins golden inputs and should fail loudly on drift.
