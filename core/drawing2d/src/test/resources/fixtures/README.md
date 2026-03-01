# Golden Test Fixtures

These JSON files are **golden fixtures** - they NEVER change once
committed. They protect against accidental serialization changes.

## Valid Fixtures

- `valid_simple.json` - Minimal valid drawing (1 entity)
- `valid_complex.json` - All entity types + all annotation types
- `valid_all_features.json` - All Drawing2D features (layers, metadata, sync)
- `valid_large.json` - Multiple entities (large drawing baseline)
- `valid_empty.json` - Minimal valid drawing with no entities

## Invalid Fixtures

- `invalid_negative_radius.json` - Circle with negative radius
- `invalid_broken_reference.json` - Annotation referencing non-existent entity
- `invalid_blank_id.json` - Entity with blank ID
- `invalid_nan_coordinate.json` - Coordinate with NaN string
- `invalid_bad_schema.json` - Wrong schema version (999)
- `invalid_missing_required.json` - Entity missing required field

## Usage

Golden fixtures are loaded by `GoldenFixtureTest.kt`.

**IMPORTANT:** Once committed, these files should NEVER be modified.
If serialization format changes, create NEW fixtures with different names.
