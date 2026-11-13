/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.datasource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import com.sqlapp.util.SimpleBeanUtils;
import com.sqlapp.util.YamlConverter;
import com.zaxxer.hikari.HikariConfig;

import tools.jackson.core.type.TypeReference;

/**
 * Reads data source settings files shared by the desktop application and the
 * Gradle plugin.
 * <p>
 * A file holds one connection as HikariCP settings ({@code jdbcUrl},
 * {@code username}, {@code driverClassName}, pool settings …) in
 * {@code .properties}, {@code .xml} (Java properties XML), {@code .json},
 * {@code .yaml} or {@code .yml} format. Several files are applied in order and
 * a later file overrides earlier values key by key, so a shared definition can
 * be combined with a separate file holding only the password.
 * <p>
 * The password can be given directly as {@code password}, or indirectly as
 * {@code passwordEnv}: the name of an environment variable that holds it.
 * {@code dataSource.*} and {@code dataSourceProperties.*} keys (or nested maps)
 * become JDBC driver properties.
 */
public final class DataSourceConfigFiles {
	/** Setting naming the environment variable that holds the password. */
	public static final String PASSWORD_ENV = "passwordEnv";
	public static final String PASSWORD = "password";
	public static final String JDBC_URL = "jdbcUrl";
	public static final String USERNAME = "username";
	public static final String DRIVER_CLASS_NAME = "driverClassName";
	/** Supported file extensions, without the dot. */
	public static final List<String> EXTENSIONS = List.of("yaml", "yml", "properties", "json", "xml");

	private DataSourceConfigFiles() {
	}

	/**
	 * Reads and merges settings files in order; later files override earlier
	 * values. Nested maps are flattened to dotted keys.
	 *
	 * @throws IllegalArgumentException when a file is missing, is a directory, has
	 *                                  an unsupported extension or cannot be parsed
	 */
	public static Map<String, Object> read(final Collection<Path> files) {
		final var settings = new LinkedHashMap<String, Object>();
		for (final Path file : files) {
			for (final var entry : readFile(file).entrySet())
				put(settings, entry.getKey(), entry.getValue());
		}
		return settings;
	}

	public static Map<String, Object> read(final Path... files) {
		return read(List.of(files));
	}

	/** Value of a setting, ignoring case, or null. */
	public static String get(final Map<String, Object> settings, final String name) {
		final Object value = settings.get(find(settings, name));
		return value == null ? null : value.toString();
	}

	/**
	 * The password: from the environment variable named by {@code passwordEnv} when
	 * that setting is present, otherwise the {@code password} setting, or null.
	 *
	 * @throws IllegalArgumentException when {@code passwordEnv} names an unset
	 *                                  variable
	 */
	public static String password(final Map<String, Object> settings, final Function<String, String> environment) {
		final String variable = get(settings, PASSWORD_ENV);
		if (variable != null && !variable.isBlank()) {
			final String value = environment.apply(variable.trim());
			if (value == null)
				throw new IllegalArgumentException(
						"Environment variable " + variable.trim() + " named by passwordEnv is not set.");
			return value;
		}
		return get(settings, PASSWORD);
	}

	/**
	 * Applies the settings to a HikariCP configuration, resolving the password.
	 * Setting names HikariCP does not know are skipped, as earlier versions did,
	 * and returned so the caller can report them.
	 *
	 * @return unknown setting names, in file order
	 * @throws IllegalArgumentException for an unset password environment variable
	 */
	public static List<String> applyTo(final HikariConfig config, final Map<String, Object> settings,
			final Function<String, String> environment) {
		final var unknown = new ArrayList<String>();
		for (final var entry : settings.entrySet()) {
			final String key = entry.getKey();
			final String lower = key.toLowerCase(Locale.ROOT);
			final Object value = entry.getValue();
			if (lower.equals(PASSWORD_ENV.toLowerCase(Locale.ROOT)) || lower.equals(PASSWORD))
				continue;
			if (value == null)
				continue;
			if (lower.startsWith("datasource.") || lower.startsWith("datasourceproperties.")) {
				config.addDataSourceProperty(key.substring(key.indexOf('.') + 1), value);
			} else if (lower.startsWith("healthcheckproperties.")) {
				config.addHealthCheckProperty(key.substring(key.indexOf('.') + 1), value.toString());
			} else if (!SimpleBeanUtils.setValueCI(config, key, value)) {
				unknown.add(key);
			}
		}
		final String password = password(settings, environment);
		if (password != null)
			config.setPassword(password);
		return List.copyOf(unknown);
	}

	/**
	 * Writes one connection as YAML, replacing the file atomically. Keys are
	 * written in the given order; a short comment explains how to supply the
	 * password.
	 */
	public static void writeYaml(final Path file, final Map<String, Object> settings) throws IOException {
		final String extension = extension(file);
		if (!extension.equals("yaml") && !extension.equals("yml"))
			throw new IllegalArgumentException("Choose a .yaml or .yml file for data source settings.");
		final var text = new StringBuilder("# sqlapp data source settings (HikariCP names).\n")
				.append("# Password: add 'passwordEnv: VARIABLE_NAME', 'password: ...', or a second file.\n");
		text.append(new YamlConverter().toJsonString(new LinkedHashMap<>(settings)).replaceFirst("^---\\s*\\n", ""));
		com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile.write(file,
				temporary -> Files.writeString(temporary, text, java.nio.charset.StandardCharsets.UTF_8));
	}

	/** Whether the file name has a supported settings extension. */
	public static boolean isSupported(final Path file) {
		return EXTENSIONS.contains(extension(file));
	}

	private static Map<String, Object> readFile(final Path file) {
		if (file == null)
			throw new IllegalArgumentException("Data source settings file is not specified.");
		if (!Files.exists(file))
			throw new IllegalArgumentException("Data source settings file not found: " + file);
		if (Files.isDirectory(file))
			throw new IllegalArgumentException("Data source settings path is a directory: " + file);
		final String extension = extension(file);
		if (!EXTENSIONS.contains(extension))
			throw new IllegalArgumentException("Unsupported data source settings file: " + file
					+ ". Use .yaml, .yml, .properties, .json or .xml.");
		try {
			final var flat = new LinkedHashMap<String, Object>();
			switch (extension) {
			case "properties", "xml" -> {
				final var properties = new Properties();
				try (InputStream input = Files.newInputStream(file)) {
					if (extension.equals("xml"))
						properties.loadFromXML(input);
					else
						properties.load(input);
				}
				for (final String name : properties.stringPropertyNames())
					flat.put(name, properties.getProperty(name));
			}
			default -> {
				final Map<String, Object> tree = new YamlConverter().fromJsonString(file.toFile(),
						new TypeReference<Map<String, Object>>() {
						});
				if (tree != null)
					flatten("", tree, flat);
			}
			}
			return flat;
		} catch (final IOException | RuntimeException exception) {
			// Parser messages can contain file contents such as passwords; do not echo
			// them.
			throw new IllegalArgumentException("Unable to read data source settings file: " + file + " ("
					+ exception.getClass().getSimpleName() + ")");
		}
	}

	@SuppressWarnings("unchecked")
	private static void flatten(final String prefix, final Map<?, ?> tree, final Map<String, Object> flat) {
		for (final var entry : tree.entrySet()) {
			final String key = prefix + entry.getKey();
			if (entry.getValue() instanceof Map<?, ?> nested)
				flatten(key + ".", (Map<String, Object>) nested, flat);
			else
				flat.put(key, entry.getValue());
		}
	}

	/** Adds or overrides a setting, matching an existing key regardless of case. */
	private static void put(final Map<String, Object> settings, final String key, final Object value) {
		final String existing = find(settings, key);
		if (existing != null && !existing.equals(key))
			settings.remove(existing);
		settings.put(key, value);
	}

	private static String find(final Map<String, Object> settings, final String name) {
		for (final String key : settings.keySet())
			if (key.equalsIgnoreCase(name))
				return key;
		return null;
	}

	private static String extension(final Path file) {
		final var name = file.getFileName() == null ? "" : file.getFileName().toString();
		final int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}
}
