/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;
import com.sqlapp.util.MessageDigests;
import com.sqlapp.util.YamlConverter;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;

/**
 * Migration pipeline definition: an optional transfer job that loads the
 * engine, the model YAML, and the transfer job that publishes model tables.
 * Paths are relative to the pipeline file. Connections are runtime inputs.
 *
 * @param formatVersion 1
 * @param load          transfer job from the source database to the engine, or
 *                      null when the engine is loaded otherwise
 * @param models        model YAML built inside the engine
 * @param publish       transfer job from the engine to the target database
 */
public record MigrationPipelineConfiguration(int formatVersion, String load, String models, String publish) {
	private static final long MAX_BYTES = 1_048_576;

	public MigrationPipelineConfiguration {
		if (formatVersion != 1 || blank(models) || blank(publish) || load != null && blank(load))
			throw new IllegalArgumentException(
					"Invalid migration pipeline. Require formatVersion: 1, models and publish; load is optional.");
	}

	/** Pipeline file text, its fingerprint and the parsed definition. */
	public record Snapshot(Path file, String text, String fingerprint, MigrationPipelineConfiguration configuration) {
		public Snapshot {
			file = file.toAbsolutePath().normalize();
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(fingerprint, "fingerprint");
			Objects.requireNonNull(configuration, "configuration");
		}

		public Path load() {
			return configuration.load() == null ? null : resolve(configuration.load());
		}

		public Path models() {
			return resolve(configuration.models());
		}

		public Path publish() {
			return resolve(configuration.publish());
		}

		private Path resolve(final String path) {
			return file.getParent().resolve(path).toAbsolutePath().normalize();
		}
	}

	public static Snapshot read(final Path file) throws IOException {
		final byte[] bytes = BoundedMigrationFile.read(file, MAX_BYTES, "maxMigrationPipelineBytes",
				"Migration pipeline");
		final String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
		final MigrationPipelineConfiguration configuration;
		try {
			configuration = new YamlConverter().getObjectMapper().rebuild()
					.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
							DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
							DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
					.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
					.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build()
					.readValue(text, MigrationPipelineConfiguration.class);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException(
					"Invalid migration pipeline. Require formatVersion: 1, models and publish; load is optional.");
		}
		if (configuration == null)
			throw new IllegalArgumentException("Migration pipeline is empty.");
		return new Snapshot(file, text, fingerprint(bytes), configuration);
	}

	static String fingerprint(final byte[] bytes) {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(bytes);
	}

	private static boolean blank(final String value) {
		return value == null || value.isBlank();
	}
}
