/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.util.JsonConverter;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;

/** Explicit, portable connection settings; never accepts a password field. */
final class ConnectionProfileService {
	private static final int MAX_BYTES = 65_536;
	private static final Set<String> FIELDS = Set.of("formatVersion", "jdbcUrl", "username", "driverClass");
	private static final Pattern SECRET = Pattern.compile(
			"(?i)(password|passwd|pwd|token|secret|credential|api[_-]?key)\\s*[=:]|//[^/?#]*@|jdbc:oracle:(thin|oci[^:]*):[^@]*/[^@]*@");

	record Profile(int formatVersion, String jdbcUrl, String username, String driverClass) {
		Profile {
			if (formatVersion != 1) {
				throw new IllegalArgumentException(
						"Unsupported connection profile version. Expected formatVersion: 1.");
			}
			jdbcUrl = clean(jdbcUrl);
			username = clean(username);
			driverClass = clean(driverClass);
			new PlanningJdbc.Spec(jdbcUrl, username, driverClass);
			String decoded = jdbcUrl;
			for (int index = 0; index < 4; index++) {
				if (SECRET.matcher(decoded).find()) {
					throw new IllegalArgumentException(
							"Remove embedded credentials from the JDBC URL before using a profile.");
				}
				String next;
				try {
					next = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
				} catch (final IllegalArgumentException exception) {
					throw new IllegalArgumentException("Profile JDBC URL contains invalid percent encoding.");
				}
				if (next.equals(decoded))
					break;
				decoded = next;
				if (index == 3) {
					throw new IllegalArgumentException("Profile JDBC URL contains excessive nested encoding.");
				}
			}
		}

		private static String clean(final String text) {
			final String value = text == null ? "" : text.trim();
			if (value.chars().anyMatch(Character::isISOControl)) {
				throw new IllegalArgumentException("Connection profile fields must not contain control characters.");
			}
			return value;
		}
	}

	Profile read(final Path file) throws IOException {
		final byte[] bytes;
		try (var input = Files.newInputStream(file)) {
			bytes = input.readNBytes(MAX_BYTES + 1);
		}
		if (bytes.length > MAX_BYTES) {
			throw new IllegalArgumentException("Connection profile exceeds 64 KiB.");
		}
		final String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
		final Map<?, ?> values;
		try {
			final var mapper = new JsonConverter().getObjectMapper().rebuild()
					.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
					.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
			values = mapper.readValue(text, Map.class);
		} catch (final RuntimeException exception) {
			// Parser errors can include file contents. Do not echo them into the UI.
			throw new IllegalArgumentException("Invalid connection profile JSON.");
		}
		if (values == null || !FIELDS.containsAll(values.keySet()) || !(values.get("formatVersion") instanceof Integer)
				|| !(values.get("jdbcUrl") instanceof String)
				|| values.containsKey("username") && !(values.get("username") instanceof String)
				|| values.containsKey("driverClass") && !(values.get("driverClass") instanceof String)) {
			throw new IllegalArgumentException(
					"Profile requires formatVersion and jdbcUrl; only username and driverClass are optional.");
		}
		return new Profile((Integer) values.get("formatVersion"), (String) values.get("jdbcUrl"),
				(String) values.get("username"), (String) values.get("driverClass"));
	}

	void write(final Profile profile, final Path file) throws IOException {
		if (file == null || file.getFileName() == null
				|| !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
			throw new IllegalArgumentException("Choose a .json file for the connection profile.");
		}
		final var converter = new JsonConverter();
		converter.setIndentOutput(true);
		final String text = converter.toJsonString(java.util.Objects.requireNonNull(profile, "profile"));
		if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
			throw new IllegalArgumentException("Connection profile exceeds 64 KiB.");
		}
		AtomicMigrationFile.write(file, temporary -> {
			Files.writeString(temporary, text, StandardCharsets.UTF_8);
			if (!profile.equals(read(temporary))) {
				throw new IOException("Saved profile differs from the selected connection settings.");
			}
		});
	}
}
