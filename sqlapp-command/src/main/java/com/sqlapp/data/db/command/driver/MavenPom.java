/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.driver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

/**
 * The parts of a POM needed to find runtime dependencies. Values are kept
 * uninterpolated; profiles are not read.
 */
record MavenPom(String groupId, String artifactId, String version, String packaging, Parent parent,
		Map<String, String> properties, List<Dependency> dependencyManagement, List<Dependency> dependencies) {

	record Parent(String groupId, String artifactId, String version) {
	}

	/**
	 * @param exclusions {@code groupId:artifactId} patterns; {@code *} matches any
	 *                   part
	 */
	record Dependency(String groupId, String artifactId, String version, String type, String classifier, String scope,
			String optional, List<String> exclusions) {

		Dependency with(final String newGroupId, final String newArtifactId, final String newVersion,
				final String newType, final String newClassifier, final String newScope, final String newOptional,
				final List<String> newExclusions) {
			return new Dependency(newGroupId, newArtifactId, newVersion, newType, newClassifier, newScope, newOptional,
					newExclusions);
		}

		/** Management key: module, type and classifier. */
		String key() {
			return groupId + ":" + artifactId + ":" + (type == null ? "jar" : type) + ":"
					+ (classifier == null ? "" : classifier);
		}
	}

	static MavenPom parse(final byte[] content, final String description) throws IOException {
		final Element project = MavenXml.parse(content, description);
		if (!"project".equals(MavenXml.localName(project)))
			throw new IOException("Not a POM: " + description);
		final Element parentElement = MavenXml.child(project, "parent");
		final Parent parent = parentElement == null ? null
				: new Parent(MavenXml.text(parentElement, "groupId"), MavenXml.text(parentElement, "artifactId"),
						MavenXml.text(parentElement, "version"));
		final var properties = new LinkedHashMap<String, String>();
		for (final Element property : MavenXml.children(MavenXml.child(project, "properties"), null))
			properties.put(MavenXml.localName(property), property.getTextContent().trim());
		final Element management = MavenXml.child(project, "dependencyManagement");
		return new MavenPom(MavenXml.text(project, "groupId"), MavenXml.text(project, "artifactId"),
				MavenXml.text(project, "version"), MavenXml.text(project, "packaging"), parent, properties,
				dependencies(MavenXml.child(management, "dependencies")),
				dependencies(MavenXml.child(project, "dependencies")));
	}

	private static List<Dependency> dependencies(final Element list) {
		final var result = new ArrayList<Dependency>();
		for (final Element dependency : MavenXml.children(list, "dependency")) {
			final var exclusions = new ArrayList<String>();
			for (final Element exclusion : MavenXml.children(MavenXml.child(dependency, "exclusions"), "exclusion"))
				exclusions.add(MavenXml.text(exclusion, "groupId") + ":" + MavenXml.text(exclusion, "artifactId"));
			result.add(new Dependency(MavenXml.text(dependency, "groupId"), MavenXml.text(dependency, "artifactId"),
					MavenXml.text(dependency, "version"), MavenXml.text(dependency, "type"),
					MavenXml.text(dependency, "classifier"), MavenXml.text(dependency, "scope"),
					MavenXml.text(dependency, "optional"), List.copyOf(exclusions)));
		}
		return List.copyOf(result);
	}
}
