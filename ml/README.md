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
