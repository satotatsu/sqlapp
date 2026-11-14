/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.driver;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the runtime JARs of an artifact from its POMs: parent POMs,
 * properties, dependency management (including imported BOMs), compile and
 * runtime scopes, optional dependencies and exclusions, with Maven's
 * nearest-wins rule. Profiles and version ranges are not supported.
 */
public final class MavenDependencyResolver {
	/** Most JARs one driver may need. */
	static final int MAX_ARTIFACTS = 200;
	private static final int MAX_POMS = 500;
	private static final int MAX_PARENTS = 20;
	private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
	private final MavenRepositoryClient client;
	private final Map<String, Effective> effective = new HashMap<>();
	private final Map<String, MavenPom> raw = new HashMap<>();

	public MavenDependencyResolver(final MavenRepositoryClient client) {
		this.client = Objects.requireNonNull(client, "client");
	}

	/** An interpolated POM with its inheritance and imports applied. */
	private record Effective(String groupId, String artifactId, String version, String packaging,
			Map<String, String> properties, Map<String, MavenPom.Dependency> management,
			List<MavenPom.Dependency> dependencies) {
	}

	private record Node(MavenCoordinate coordinate, int depth, Set<String> exclusions, String path) {
	}

	/**
	 * @param root     the driver artifact with its version
	 * @param progress receives one line per POM read; may be null
	 * @return the root JAR (unless the root is a {@code pom}) and its runtime
	 *         dependencies, nearest first
	 */
	public List<MavenCoordinate> resolve(final MavenCoordinate root, final Consumer<String> progress)
			throws IOException {
		if (root.version() == null)
			throw new IllegalArgumentException("Choose a version for " + root.module());
		final Consumer<String> log = progress == null ? line -> {
		} : progress;
		final Effective top = effective(root, log, 0);
		final var result = new LinkedHashMap<String, MavenCoordinate>();
		if (!"pom".equals(top.packaging()))
			result.put(root.key(), root);
		final var seen = new HashSet<String>();
		seen.add(root.key());
		final var queue = new ArrayDeque<Node>();
		queue.add(new Node(root, 0, Set.of(), root.toString()));
		while (!queue.isEmpty()) {
			final Node node = queue.removeFirst();
			final Effective pom = node.depth() == 0 ? top : effective(node.coordinate(), log, 0);
			for (final MavenPom.Dependency declared : pom.dependencies()) {
				MavenPom.Dependency dependency = declared;
				if (node.depth() > 0) {
					// The root's dependency management also governs transitive versions.
					final var managed = top.management().get(dependency.key());
					if (managed != null)
						dependency = dependency.with(dependency.groupId(), dependency.artifactId(),
								managed.version() != null ? managed.version() : dependency.version(), dependency.type(),
								dependency.classifier(), managed.scope() != null ? managed.scope() : dependency.scope(),
								dependency.optional(), dependency.exclusions());
				}
				final String scope = dependency.scope() == null ? "compile" : dependency.scope();
				if (!scope.equals("compile") && !scope.equals("runtime"))
					continue;
				if ("true".equalsIgnoreCase(dependency.optional()))
					continue;
				final String type = dependency.type() == null ? "jar" : dependency.type();
				if (!type.equals("jar") && !type.equals("bundle"))
					continue;
				if (excluded(node.exclusions(), dependency.groupId(), dependency.artifactId()))
					continue;
				final String where = node.path() + " -> " + dependency.groupId() + ":" + dependency.artifactId();
				final String version = version(dependency.version(), where);
				final MavenCoordinate coordinate;
				try {
					coordinate = new MavenCoordinate(dependency.groupId(), dependency.artifactId(), version,
							dependency.classifier(), "jar");
				} catch (final IllegalArgumentException invalid) {
					throw new IOException("Unsupported dependency " + where + ": " + invalid.getMessage());
				}
				if (!seen.add(coordinate.key()))
					continue;
				result.put(coordinate.key(), coordinate);
				if (result.size() > MAX_ARTIFACTS)
					throw new IOException("More than " + MAX_ARTIFACTS + " dependencies for " + root
							+ ". Check the coordinate, or download the JARs and load them as local files.");
				final var exclusions = new HashSet<>(node.exclusions());
				exclusions.addAll(dependency.exclusions());
				queue.add(new Node(coordinate, node.depth() + 1, Set.copyOf(exclusions), where));
			}
		}
		return List.copyOf(result.values());
	}

	private static String version(final String version, final String where) throws IOException {
		if (version == null || version.contains("${"))
			throw new IOException("Unable to determine the version of " + where
					+ (version == null ? "" : " (" + version + ")") + ".");
		if (version.startsWith("[") && version.endsWith("]") && !version.contains(","))
			return version.substring(1, version.length() - 1).trim();
		if (version.startsWith("[") || version.startsWith("("))
			throw new IOException("Version ranges are not supported: " + where + " " + version
					+ ". Download that JAR separately and load it as a local file.");
		return version;
	}

	private static boolean excluded(final Set<String> exclusions, final String groupId, final String artifactId) {
		for (final String exclusion : exclusions) {
			final String[] parts = exclusion.split(":", 2);
			if ((parts[0].equals("*") || parts[0].equals(groupId))
					&& (parts.length < 2 || parts[1].equals("*") || parts[1].equals(artifactId)))
				return true;
		}
		return false;
	}

	private MavenPom raw(final MavenCoordinate coordinate, final Consumer<String> log) throws IOException {
		final String key = coordinate.toString();
		final MavenPom cached = raw.get(key);
		if (cached != null)
			return cached;
		if (raw.size() >= MAX_POMS)
			throw new IOException("More than " + MAX_POMS + " POM files are needed. Resolution stopped.");
		log.accept("Reading POM " + coordinate);
		final var download = client.download(coordinate.pom().path(), MavenRepositoryClient.MAX_TEXT_BYTES);
		if (download == null)
			throw new IOException("Not found in the Maven repositories: " + coordinate);
		final MavenPom pom = MavenPom.parse(download.content(), "POM of " + coordinate);
		raw.put(key, pom);
		return pom;
	}

	private Effective effective(final MavenCoordinate coordinate, final Consumer<String> log, final int depth)
			throws IOException {
		final String key = coordinate.module() + ":" + coordinate.version();
		final Effective cached = effective.get(key);
		if (cached != null)
			return cached;
		if (depth > MAX_PARENTS)
			throw new IOException("Too many parent or imported POMs from " + coordinate);
		final MavenPom pom = raw(coordinate, log);
		Effective parent = null;
		if (pom.parent() != null) {
			final var p = pom.parent();
			if (p.groupId() == null || p.artifactId() == null || p.version() == null)
				throw new IOException("Incomplete parent in the POM of " + coordinate);
			parent = effective(new MavenCoordinate(p.groupId(), p.artifactId(), p.version()), log, depth + 1);
		}
		final var properties = new LinkedHashMap<String, String>();
		if (parent != null)
			properties.putAll(parent.properties());
		properties.putAll(pom.properties());
		final String groupId = pom.groupId() != null ? pom.groupId()
				: parent != null ? parent.groupId() : coordinate.groupId();
		final String version = pom.version() != null ? pom.version()
				: parent != null ? parent.version() : coordinate.version();
		final String artifactId = pom.artifactId() != null ? pom.artifactId() : coordinate.artifactId();
		for (final String prefix : new String[] { "project.", "pom.", "" }) {
			properties.put(prefix + "groupId", groupId);
			properties.put(prefix + "artifactId", artifactId);
			properties.put(prefix + "version", version);
		}
		if (pom.parent() != null) {
			properties.put("project.parent.groupId", pom.parent().groupId());
			properties.put("project.parent.version", pom.parent().version());
			properties.put("parent.version", pom.parent().version());
		}
		final var management = new LinkedHashMap<String, MavenPom.Dependency>();
		if (parent != null)
			management.putAll(parent.management());
		final var imports = new ArrayList<MavenPom.Dependency>();
		for (final var declared : pom.dependencyManagement()) {
			final var dependency = interpolate(declared, properties);
			if ("import".equals(dependency.scope()) && "pom".equals(dependency.type()))
				imports.add(dependency);
			else
				management.put(dependency.key(), dependency);
		}
		for (final var imported : imports) {
			final String where = coordinate + " imports " + imported.groupId() + ":" + imported.artifactId();
			final Effective bom = effective(
					new MavenCoordinate(imported.groupId(), imported.artifactId(), version(imported.version(), where)),
					log, depth + 1);
			bom.management().forEach(management::putIfAbsent);
		}
		final var dependencies = new LinkedHashMap<String, MavenPom.Dependency>();
		if (parent != null)
			for (final var inherited : parent.dependencies())
				dependencies.put(inherited.key(), inherited);
		for (final var declared : pom.dependencies()) {
			var dependency = interpolate(declared, properties);
			final var managed = management.get(dependency.key());
			if (managed != null) {
				final var exclusions = new ArrayList<>(dependency.exclusions());
				exclusions.addAll(managed.exclusions());
				dependency = dependency.with(dependency.groupId(), dependency.artifactId(),
						dependency.version() != null ? dependency.version() : managed.version(), dependency.type(),
						dependency.classifier(), dependency.scope() != null ? dependency.scope() : managed.scope(),
						dependency.optional() != null ? dependency.optional() : managed.optional(),
						List.copyOf(exclusions));
			}
			dependencies.put(dependency.key(), dependency);
		}
		final var result = new Effective(groupId, artifactId, version,
				pom.packaging() == null ? "jar" : pom.packaging(), Map.copyOf(properties), Map.copyOf(management),
				List.copyOf(dependencies.values()));
		effective.put(key, result);
		return result;
	}

	private static MavenPom.Dependency interpolate(final MavenPom.Dependency dependency,
			final Map<String, String> properties) {
		final var exclusions = new ArrayList<String>();
		for (final String exclusion : dependency.exclusions())
			exclusions.add(interpolate(exclusion, properties));
		return dependency.with(interpolate(dependency.groupId(), properties),
				interpolate(dependency.artifactId(), properties), interpolate(dependency.version(), properties),
				interpolate(dependency.type(), properties), interpolate(dependency.classifier(), properties),
				interpolate(dependency.scope(), properties), interpolate(dependency.optional(), properties),
				List.copyOf(exclusions));
	}

	static String interpolate(final String value, final Map<String, String> properties) {
		if (value == null)
			return null;
		String current = value;
		for (int round = 0; round < 10 && current.contains("${"); round++) {
			final Matcher matcher = PROPERTY.matcher(current);
			final var text = new StringBuilder();
			boolean changed = false;
			while (matcher.find()) {
				final String replacement = properties.get(matcher.group(1));
				changed |= replacement != null;
				matcher.appendReplacement(text,
						Matcher.quoteReplacement(replacement == null ? matcher.group() : replacement));
			}
			matcher.appendTail(text);
			if (!changed)
				break;
			current = text.toString();
		}
		return current;
	}
}
