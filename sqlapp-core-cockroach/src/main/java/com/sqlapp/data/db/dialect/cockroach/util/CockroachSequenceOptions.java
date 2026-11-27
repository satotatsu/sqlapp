/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.util;

import java.math.BigInteger;
import java.util.Locale;
import java.util.regex.Pattern;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;

/** Options in a CREATE SEQUENCE definition. */
public final class CockroachSequenceOptions {
	private static final Pattern CACHE = Pattern
			.compile("(?i);|(?<![\\p{L}\\p{N}_$.])(?:(PER\\s+(?:SESSION|NODE))\\s+)?CACHE\\s+([0-9]+)\\b");
	private static final String IDENTIFIER = "(?:\"(?:[^\"]|\"\")+\"|[A-Za-z_][A-Za-z_0-9$]*)";
	private static final Pattern OWNERSHIP = Pattern.compile("(?i);|(?<![\\p{L}\\p{N}_$.])OWNED\\s+BY\\s+");
	private static final Pattern OWNER = Pattern.compile(
			"(?i)(NONE|(" + IDENTIFIER + ")\\s*\\.\\s*(" + IDENTIFIER + ")\\s*\\.\\s*(" + IDENTIFIER + "))\\s*;?\\s*$");

	private CockroachSequenceOptions() {
	}

	public record Cache(String keyword, Integer size) {
	}

	public record Ownership(String definitionWithoutOwner, String ownerSql) {
	}

	public static Cache cache(String definition) {
		return new SequenceTokenizer(definition).cache(definition);
	}

	public static Ownership ownership(String definition) {
		if (!definition.matches("(?is)^\\s*CREATE\\s+SEQUENCE\\s+.*"))
			return new Ownership(definition, null);
		return new SequenceTokenizer(definition).ownership(definition);
	}

	private static String quoteIdentifier(String identifier) {
		return identifier.startsWith("\"") ? identifier : CockroachPlacement.quote(identifier.toLowerCase(Locale.ROOT));
	}

	private static final class SequenceTokenizer extends SqlTokenizer {
		private SequenceTokenizer(String definition) {
			super(definition);
		}

		@Override
		protected boolean supportsNestedBlockComments() {
			return true;
		}

		private Cache cache(String definition) {
			var tokens = CACHE.matcher(definition);
			while (tokens.find()) {
				if (getTextMarker(tokens.start()) != null)
					continue;
				if (";".equals(tokens.group()))
					break;
				String scope = tokens.group(1);
				String keyword = scope == null ? "CACHE"
						: scope.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ") + " CACHE";
				// The shared cache size is Integer. Keep larger values in the native
				// definition.
				BigInteger size = new BigInteger(tokens.group(2));
				return new Cache(keyword, size.bitLength() <= 31 ? size.intValue() : null);
			}
			return new Cache("CACHE", 1);
		}

		private Ownership ownership(String definition) {
			var tokens = OWNERSHIP.matcher(definition);
			while (tokens.find()) {
				if (getTextMarker(tokens.start()) != null)
					continue;
				if (";".equals(tokens.group()))
					break;
				var owner = OWNER.matcher(definition).region(tokens.end(), definition.length());
				if (!owner.matches())
					throw new UnsupportedOperationException(
							"CockroachDB sequence ownership requires a trailing OWNED BY schema.table.column or NONE");
				String sql = owner.group(2) == null ? "NONE"
						: quoteIdentifier(owner.group(2)) + "." + quoteIdentifier(owner.group(3)) + "."
								+ quoteIdentifier(owner.group(4));
				return new Ownership(definition.substring(0, tokens.start()).stripTrailing(), sql);
			}
			String base = definition.stripTrailing();
			if (base.endsWith(";"))
				base = base.substring(0, base.length() - 1).stripTrailing();
			return new Ownership(base, null);
		}
	}
}
