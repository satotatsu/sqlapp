/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.util.*;

/** Pure name authoring; persist resolved names in ordinary mapping YAML. */
public record TransferNameRules(NameCase letterCase, boolean replaceSpaces) {
	public enum NameCase {
		PRESERVE, UPPER, LOWER
	}

	public TransferNameRules {
		letterCase = letterCase == null ? NameCase.PRESERVE : letterCase;
	}

	/**
	 * Resolve one name; database collisions still require Schema checks and Review.
	 */
	public String apply(String name) {
		// Reuse the shared Unicode/name contract instead of inventing a naming grammar.
		TransferNameMappingIO.TableMapping.names(null, Map.of(Objects.requireNonNull(name, "name"), name));
		String resolved = name;
		if (replaceSpaces) {
			var text = new StringBuilder();
			name.codePoints().forEach(code -> text
					.appendCodePoint(Character.isWhitespace(code) || Character.isSpaceChar(code) ? '_' : code));
			resolved = text.toString();
		}
		resolved = switch (letterCase) {
		case PRESERVE -> resolved;
		case UPPER -> resolved.toUpperCase(Locale.ROOT);
		case LOWER -> resolved.toLowerCase(Locale.ROOT);
		};
		TransferNameMappingIO.TableMapping.names(null, Map.of(name, resolved));
		return resolved;
	}
}
