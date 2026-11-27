# Shared utility behavior

The utilities in `sqlapp-core` retain their existing public signatures and
serialization fields. The following rules apply to mutations and conversion:

- `RuntimeExec` reads standard output and standard error through EOF, including
  output produced after a pause. Output read failures are reported as exceptions.
- `DoubleKeyMap.clone()`, `TripleKeyMap.clone()` and `QuadKeyMap.clone()` copy
  their nested map structures. Updating or removing a mapping in the clone does
  not modify the original. Keys and stored values remain shared references.
- The `keySet()`, `values()` and `entrySet()` views of `CaseInsensitiveMap` and
  `CaseInsensitiveGetMap` remain backed and mutable. View removals and entry
  value updates also update case-insensitive lookup information.
- `CaseInsensitiveGetMap.put()` replaces the value for an identical key and
  returns its previous value. Differently cased spellings remain separate keys.
  Exact spelling wins during lookup; otherwise the first remaining spelling in
  insertion order supplies the value. Removing that spelling promotes the next.
- `BinaryUtils.toBinaryFromHex()` requires an even number of hexadecimal digits.
  Odd lengths throw `IllegalArgumentException` instead of discarding the last
  digit. Empty input remains valid and produces an empty byte array. This also
  applies to string conversion through `HexBinaryConverter`.

Callers that relied on ignored `put()` updates, shared clone structures, or
truncated odd-length hexadecimal strings must correct those inputs or assumptions.
