# BankingStatement Context

## Project Overview

**BankingStatement** is a Kotlin Multiplatform (Android + iOS) application for importing and analyzing bank statements locally on-device. The shared domain model centers around normalized transactions, which are parsed from multiple input types (notably PDF, CSV, and Excel) and then used for categorization, validation, storage, export, and reporting.

This repository is not just a parser library; it includes end-user app features such as account management, transaction lists, category management, spending overviews, and merchant trends.

## What the Project Actually Does

Primary capabilities implemented in the codebase:

- Import bank statements from **PDF, CSV, and Excel** file types.
- Parse heterogeneous statement formats using format-specific and bank-specific parser logic.
- Normalize extracted rows into a common `ParsedTransaction` model.
- Store data in a local database and run migrations.
- Categorize transactions using an offline keyword/merchant database.
- Validate statement consistency and support data export.
- Present analytics and trends in Compose-based UI screens.

## Core Data Model

The shared transaction abstraction is `ParsedTransaction`, with fields such as:

- `bookingDate`
- `valueDate` (optional)
- `amount`
- `currency`
- `balance` (optional)
- `description`
- optional counterparty/remittance/type metadata

This model is the normalization boundary between import/parsing and downstream processing.

## Input & Parsing

### Supported input types

The import layer currently recognizes:

- PDF
- CSV
- Excel (`.xls`, `.xlsx`)

### Parsing strategy

- **CSV parser** auto-detects delimiter and header variants, then maps rows into normalized transactions.
- **PDF processing** extracts text and routes to bank-specific parser logic.
- **Bank parser set** contains dedicated implementations for multiple institutions (especially German banks, plus selected international banks).

## Processing Pipeline (Conceptual)

```text
Input Files (PDF/CSV/Excel)
        |
        v
Type Detection + Parser Selection
        |
        v
Format/Bank-Specific Parsing
        |
        v
Normalized ParsedTransaction List
        |
        +--> Validation
        +--> Categorization
        +--> Local Storage
        +--> Export / Analytics / UI
```

## Design Principles (Repository-Aligned)

- **Deterministic parsing:** same file should yield stable normalized output.
- **Extensibility:** new banks/formats should be added as modular parser components.
- **Data integrity:** validation and tests cover edge cases and malformed input handling.
- **Privacy-first local processing:** statement data is processed and stored locally without required cloud backends.

## Typical Workflow

1. User imports a statement file.
2. App detects file type and chooses parser path.
3. Parser extracts and normalizes transactions.
4. Transactions are validated, categorized, and persisted.
5. User views analytics or exports processed data.

## Key Challenges

- Inconsistent bank export schemas and naming conventions.
- PDF text extraction quality and layout variance.
- Locale-specific date/amount formats.
- Avoiding duplicate or malformed transaction ingestion.

## Suggested Contribution Focus

When extending this project, prioritize:

- Parser modularity (new bank/format parsers as isolated components).
- Test coverage for real-world edge cases (dates, delimiters, currencies, malformed rows).
- Deterministic outputs for reproducibility.
- Preservation of local-first privacy behavior.

## Notes on Scope

This context intentionally avoids claiming support for formats not clearly present in the current code (e.g., OFX/MT940) and instead documents confirmed capabilities in this repository.
