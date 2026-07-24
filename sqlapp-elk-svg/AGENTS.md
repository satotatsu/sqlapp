# sqlapp-elk-svg development instructions

These instructions supplement the repository root `AGENTS.md`.

## Environment-dependent SVG output

- SVG layout depends on the operating system, installed fonts, Java runtime,
  and font metrics.
- Exact full-SVG output comparisons are intentionally disabled.
- Do not enable exact SVG snapshot comparisons without first eliminating or
  controlling those environment dependencies.
- Do not treat coordinate or text-width differences alone as regressions.
- Do not update expected SVG files merely to match output from the current
  development environment.

## Testing rules

Prefer environment-independent structural assertions, including:

- The output is valid XML and SVG.
- Expected tables and columns are present.
- Expected foreign-key edges are present.
- SVG element IDs and references are consistent.
- XML text and attributes are correctly escaped.
- Required numeric attributes contain valid finite numbers.
- The output does not contain `NaN` or infinite values.
- Self-referencing and composite foreign keys are not omitted.
- Drawing and name modes produce the expected structural differences.

Avoid assertions that depend on:

- Exact coordinates
- Exact element dimensions
- Exact text widths
- Exact path geometry
- Installed font names
- Full SVG string equality

## Layout changes

When changing layout behavior:

- Explain which layout properties are expected to change.
- Separate ELK graph construction tests from final SVG rendering tests.
- Add structural tests for the behavior being changed.
- Perform visual inspection when the change cannot be verified reliably
  through environment-independent assertions.
- Record the environment used for visual verification when fonts or text
  measurement affect the result.

## Existing disabled tests

Exact SVG comparison code may remain disabled when it documents expected
output, but do not re-enable it without an explicit task and a strategy for
controlling font metrics.