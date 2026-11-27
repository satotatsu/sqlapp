/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.driver;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A Maven artifact: {@code groupId:artifactId[:version[:classifier]]}. Parts
 * are restricted so that they can be used as repository and file path segments.
 *
 * @param groupId    group, e.g. {@code com.oracle.database.jdbc}
 * @param artifactId artifact, e.g. {@code ojdbc11}
 * @param version    version, or null when not chosen yet
 * @param classifier classifier, or null
 * @param extension  file extension, {@code jar} or {@code pom}
 */
public record MavenCoordinate(String groupId, String artifactId, String version, String classifier, String extension) {
	private static final Pattern PART = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.+\\-]{0,199}");

	public MavenCoordinate {
		groupId = require("groupId", groupId);
		artifactId = require("artifactId", artifactId);
		version = optional("version", version);
		classifier = optional("classifier", classifier);
		extension = extension == null || extension.isBlank() ? "jar" : require("extension", extension);
	}

	public MavenCoordinate(final String groupId, final String artifactId, final String version) {
		this(groupId, artifactId, version, null, "jar");
	}

	/**
	 * Parses {@code groupId:artifactId}, {@code groupId:artifactId:version} or
	 * {@code groupId:artifactId:version:classifier}.
	 */
	public static MavenCoordinate parse(final String text) {
		final String value = text == null ? "" : text.trim();
		final String[] parts = value.split(":", -1);
		if (parts.length < 2 || parts.length > 4)
			throw new IllegalArgumentException(
					"Enter a Maven coordinate as groupId:artifactId[:version[:classifier]]: " + value);
		return new MavenCoordinate(parts[0], parts[1], parts.length > 2 ? parts[2] : null,
				parts.length > 3 ? parts[3] : null, "jar");
	}

	private static String require(final String name, final String value) {
		final String trimmed = value == null ? "" : value.trim();
		if (!PART.matcher(trimmed).matches() || trimmed.contains(".."))
			throw new IllegalArgumentException("Invalid Maven " + name + ": " + (value == null ? "" : value));
		return trimmed;
	}

	private static String optional(final String name, final String value) {
		return value == null || value.isBlank() ? null : require(name, value);
	}

	public MavenCoordinate withVersion(final String newVersion) {
		return new MavenCoordinate(groupId, artifactId, newVersion, classifier, extension);
	}

	/** The POM of this artifact's version. */
	public MavenCoordinate pom() {
		return new MavenCoordinate(groupId, artifactId, version, null, "pom");
	}

	/** {@code groupId:artifactId}, without version. */
	public String module() {
		return groupId + ":" + artifactId;
	}

	/** Identity used for "nearest wins": module, classifier and extension. */
	String key() {
		return module() + ":" + Objects.toString(classifier, "") + ":" + extension;
	}

	/** Repository directory of the module, e.g. {@code com/oracle/x/ojdbc11/}. */
	public String moduleDirectory() {
		return groupId.replace('.', '/') + "/" + artifactId + "/";
	}

	/** Repository path, e.g. {@code com/x/a/1.0/a-1.0.jar}. */
	public String path() {
		if (version == null)
			throw new IllegalStateException("Version is not chosen: " + this);
		return moduleDirectory() + version + "/" + artifactId + "-" + version
				+ (classifier == null ? "" : "-" + classifier) + "." + extension;
	}

	@Override
	public String toString() {
		return module() + (version == null ? "" : ":" + version) + (classifier == null ? "" : ":" + classifier);
	}
}
