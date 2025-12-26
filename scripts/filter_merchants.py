#!/usr/bin/env python3
"""
Filter merchant database to remove entries already covered by keywords.
This significantly reduces database size and improves categorization speed.
"""

import csv
import sys
from pathlib import Path

# Keywords that make an entry redundant if present in the merchant name
REDUNDANT_KEYWORDS = {
    # Restaurants & Food
    'restaurant', 'bistro', 'cafe', 'coffee', 'pizzeria', 'burger',
    'döner', 'kebab', 'sushi', 'asia', 'china', 'steakhouse',

    # Supermarkets (keep specific brand names only)
    # Remove generic "supermarkt", "lebensmittel", etc.
    'supermarkt', 'lebensmittel', 'biomarkt',

    # Gas stations
    'tankstelle', 'gas station', 'fuel',

    # Generic retail
    'shop', 'store', 'boutique', 'laden',

    # Hotels & Travel
    'hotel', 'motel', 'hostel', 'pension', 'ferienwohnung',

    # Healthcare
    'apotheke', 'pharmacy', 'arzt', 'doctor', 'klinik', 'hospital',

    # Entertainment
    'kino', 'cinema', 'theater', 'club', 'disco', 'bar', 'pub',

    # Services
    'friseur', 'hairdresser', 'massage', 'fitness', 'gym',
}

# Brand names to KEEP even if they contain redundant keywords
# These are specific merchants worth keeping in the database
KEEP_BRANDS = {
    'lidl', 'aldi', 'rewe', 'edeka', 'penny', 'netto', 'kaufland',
    'dm', 'rossmann', 'müller',
    'mcdonald', 'burger king', 'subway', 'starbucks', 'kfc',
    'shell', 'aral', 'esso', 'total', 'jet',
    'ikea', 'mediamarkt', 'saturn', 'amazon',
    'sparkasse', 'volksbank', 'commerzbank', 'deutsche bank',
}

def normalize(text):
    """Normalize text for comparison."""
    return text.lower().strip()

def should_keep_entry(name, category_code):
    """Determine if merchant entry should be kept."""
    normalized = normalize(name)

    # Always keep brand names
    for brand in KEEP_BRANDS:
        if brand in normalized:
            return True

    # Remove if contains redundant keywords
    for keyword in REDUNDANT_KEYWORDS:
        if keyword in normalized:
            return False

    # Keep entries with specific, non-generic names
    # (e.g., "Luigi's" is specific even if category is restaurant)
    return True

def filter_merchants(input_file, output_file):
    """Filter merchant CSV file."""
    kept = 0
    removed = 0

    with open(input_file, 'r', encoding='utf-8') as f_in:
        reader = csv.reader(f_in)

        # Check if first line is header
        first_line = next(reader)
        has_header = not first_line[0] in ['sm', 'fd', 'bk', 'en', 'ft', 'md', 'el', 'be', 'cl', 'tr']

        with open(output_file, 'w', encoding='utf-8', newline='') as f_out:
            writer = csv.writer(f_out)

            # Write header if present
            if has_header:
                writer.writerow(first_line)
            else:
                # Process first line as data
                if len(first_line) >= 3:
                    if should_keep_entry(first_line[2], first_line[0]):
                        writer.writerow(first_line)
                        kept += 1
                    else:
                        removed += 1

            # Process remaining lines
            for row in reader:
                if len(row) < 3:
                    continue

                category_code = row[0]
                name = row[2]

                if should_keep_entry(name, category_code):
                    writer.writerow(row)
                    kept += 1
                else:
                    removed += 1

    return kept, removed

if __name__ == '__main__':
    if len(sys.argv) != 3:
        print("Usage: python filter_merchants.py <input.csv> <output.csv>")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2]

    print(f"Filtering {input_file}...")
    kept, removed = filter_merchants(input_file, output_file)

    total = kept + removed
    reduction = (removed / total * 100) if total > 0 else 0

    print(f"✅ Done!")
    print(f"   Kept:    {kept:,}")
    print(f"   Removed: {removed:,}")
    print(f"   Total:   {total:,}")
    print(f"   Reduction: {reduction:.1f}%")
    print(f"\nFiltered merchants saved to: {output_file}")
