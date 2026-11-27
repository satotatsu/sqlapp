/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes passwords and JDBC URLs from diagnostics shown in the desktop UI.
 * <p>
 * Plain substring replacement corrupts unrelated text when a password is very
 * short ("a" would be removed from every word). Short secrets are therefore
 * only replaced where they are not part of a longer word, which still covers
 * the usual {@code password=a;} and quoted forms. URL-encoded spellings are
 * removed as well.
 */
final class SecretRedaction {
	static final String SECRET = "[redacted]";
	static final String URL = "[JDBC URL]";
	/** Secrets shorter than this are matched only at word boundaries. */
	static final int MIN_SUBSTRING_LENGTH = 4;

	private SecretRedaction() {
	}

	static String redact(final String text, final PlanningJdbc.Spec[] specs, final char[]... secrets) {
		if (text == null)
			return null;
		String result = text;
		if (specs != null) {
			for (final var spec : specs) {
				if (spec != null && spec.jdbcUrl() != null && !spec.jdbcUrl().isEmpty())
					result = result.replace(spec.jdbcUrl(), URL);
			}
		}
		if (secrets != null) {
			for (final char[] secret : secrets) {
				if (secret != null && secret.length > 0)
					result = redactSecret(result, new String(secret));
			}
		}
		return result;
	}

	static String redact(final String text, final PlanningJdbc.Spec spec, final char[] secret) {
		return redact(text, new PlanningJdbc.Spec[] { spec }, secret);
	}

	private static String redactSecret(final String text, final String secret) {
		final Set<String> forms = new LinkedHashSet<>();
		forms.add(secret);
		forms.add(URLEncoder.encode(secret, StandardCharsets.UTF_8));
		forms.add(URLEncoder.encode(secret, StandardCharsets.UTF_8).replace("+", "%20"));
		String result = text;
		for (final String form : forms) {
			if (form.length() >= MIN_SUBSTRING_LENGTH) {
				result = result.replace(form, SECRET);
			} else {
				result = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(form) + "(?![\\p{L}\\p{N}])")
						.matcher(result).replaceAll(Matcher.quoteReplacement(SECRET));
			}
		}
		return result;
	}
}
