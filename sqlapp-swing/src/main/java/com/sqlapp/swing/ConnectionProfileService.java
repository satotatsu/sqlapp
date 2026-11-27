/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.sqlapp.data.db.command.datasource.DataSourceConfigFiles;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.util.JsonConverter;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;

/**
 * Loads and saves connection settings for the connection forms.
 * <p>
 * Data source settings files ({@code .yaml}, {@code .yml}, {@code .properties},
 * {@code .json}, {@code .xml}) are the same files the Gradle plugin reads with
 * {@code dataSource { properties ... }}. Their password, given directly or
 * through {@code passwordEnv}, is only placed in the form for this session;
 * saving never writes a password. Legacy JSON profiles ({@code formatVersion})
 * remain readable and never contain a password.
 */
final class ConnectionProfileService {
	/**
	 * A loaded connection: form values, an optional session password and the
	 * reusable settings file.
	 */
	record Loaded(Profile profile, char[] password, String note, Path settingsFile) {
		static Loaded profileOnly(final Profile profile) {
			return new Loaded(profile, null, "Loaded; enter password", null);
		}
	}

	private final java.util.function.Function<String, String> environment;

	ConnectionProfileService() {
		this(System::getenv);
	}

	ConnectionProfileService(final java.util.function.Function<String, String> environment) {
		this.environment = java.util.Objects.requireNonNull(environment);
	}

	/** Reads a data source settings file or a legacy JSON profile. */
	Loaded readAny(final Path file) throws IOException {
		if (isLegacyProfile(file))
			return Loaded.profileOnly(read(file));
		final var settings = DataSourceConfigFiles.read(file);
		final var profile = new Profile(1, DataSourceConfigFiles.get(settings, DataSourceConfigFiles.JDBC_URL),
				DataSourceConfigFiles.get(settings, DataSourceConfigFiles.USERNAME),
				DataSourceConfigFiles.get(settings, DataSourceConfigFiles.DRIVER_CLASS_NAME));
		final String variable = DataSourceConfigFiles.get(settings, DataSourceConfigFiles.PASSWORD_ENV);
		char[] secret = null;
		String note;
		try {
			final String value = DataSourceConfigFiles.password(settings, environment);
			if (value != null) {
				secret = value.toCharArray();
				note = variable != null ? "Loaded; password from " + variable.trim() : "Loaded; password from file";
			} else
				note = "Loaded; enter password";
		} catch (final IllegalArgumentException unset) {
			note = "Loaded; " + variable.trim() + " is not set, enter password";
		}
		return new Loaded(profile, secret, note, file.toAbsolutePath().normalize());
	}

	private static boolean isLegacyProfile(final Path file) {
		if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json") || !Files.isRegularFile(file))
			return false;
		try (var input = Files.newInputStream(file)) {
			final byte[] bytes = input.readNBytes(MAX_BYTES + 1);
			final Map<?, ?> values = new JsonConverter().getObjectMapper().readValue(bytes, Map.class);
			return values != null && values.containsKey("formatVersion");
		} catch (final IOException | RuntimeException notAProfile) {
			return false;
		}
	}

	private static void writeSettings(final Profile profile, final Path file) throws IOException {
		if ((profile.jdbcUrl() + profile.username() + profile.driverClass())
				.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
			throw new IllegalArgumentException("Connection profile exceeds 64 KiB.");
		final var settings = new LinkedHashMap<String, Object>();
		if (Files.exists(file))
			settings.putAll(DataSourceConfigFiles.read(file));
		set(settings, DataSourceConfigFiles.JDBC_URL, profile.jdbcUrl());
		set(settings, DataSourceConfigFiles.USERNAME, profile.username());
		set(settings, DataSourceConfigFiles.DRIVER_CLASS_NAME, profile.driverClass());
		DataSourceConfigFiles.writeYaml(file, settings);
		final var saved = DataSourceConfigFiles.read(file);
		if (!java.util.Objects.equals(DataSourceConfigFiles.get(saved, DataSourceConfigFiles.JDBC_URL),
				profile.jdbcUrl()))
			throw new IOException("Saved settings differ from the selected connection settings.");
	}

	/**
	 * Replaces a setting in place regardless of key case; a blank value removes it.
	 */
	private static void set(final LinkedHashMap<String, Object> settings, final String name, final String value) {
		final var copy = new LinkedHashMap<>(settings);
		settings.clear();
		boolean replaced = false;
		for (final var entry : copy.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(name)) {
				if (!replaced && value != null && !value.isBlank())
					settings.put(name, value);
				replaced = true;
			} else
				settings.put(entry.getKey(), entry.getValue());
		}
		if (!replaced && value != null && !value.isBlank())
			settings.put(name, value);
	}

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
		final String name = file == null || file.getFileName() == null ? ""
				: file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (name.endsWith(".yaml") || name.endsWith(".yml")) {
			writeSettings(java.util.Objects.requireNonNull(profile, "profile"), file);
			return;
		}
		if (!name.endsWith(".json")) {
			throw new IllegalArgumentException(
					"Choose a .yaml file for connection settings (or .json for a legacy profile).");
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
