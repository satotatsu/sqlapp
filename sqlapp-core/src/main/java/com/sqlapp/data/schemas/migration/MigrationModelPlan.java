/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.schemas.migration.MigrationModelReferences.Kind;
import com.sqlapp.data.schemas.migration.MigrationModelReferences.Reference;

/**
 * Validated, dependency-ordered set of models.
 * <p>
 * Dependencies come only from {@code ref} comments in the model SQL. Models
 * keep their declaration order wherever the dependencies allow it, so a plan
 * built from the same definitions is always executed in the same order.
 * </p>
 */
public final class MigrationModelPlan {
	private final List<MigrationModel> models;
	private final Map<String, MigrationModel> modelsByName;
	private final Map<String, MigrationModelSource> sources;
	private final Map<String, List<String>> dependencies;
	private final Map<String, List<String>> sourceDependencies;
	private final Map<String, String> fingerprints;
	private final String fingerprint;

	private MigrationModelPlan(final List<MigrationModel> ordered, final Map<String, MigrationModelSource> sources,
			final Map<String, List<String>> dependencies, final Map<String, List<String>> sourceDependencies) {
		this.models = List.copyOf(ordered);
		final Map<String, MigrationModel> byName = new LinkedHashMap<>();
		ordered.forEach(model -> byName.put(model.name(), model));
		this.modelsByName = java.util.Collections.unmodifiableMap(byName);
		this.sources = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(sources));
		this.dependencies = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(dependencies));
		this.sourceDependencies = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(sourceDependencies));
		final Map<String, String> nodeFingerprints = new LinkedHashMap<>();
		final StringBuilder all = new StringBuilder();
		for (final MigrationModel model : ordered) {
			final String value = fingerprint(model, sourceDependencies.get(model.name()), sources);
			nodeFingerprints.put(model.name(), value);
			all.append(model.name()).append('\n').append(value).append('\n')
					.append(dependencies.get(model.name())).append('\n');
		}
		this.fingerprints = java.util.Collections.unmodifiableMap(nodeFingerprints);
		this.fingerprint = sha256(all.toString());
	}

	/**
	 * Validates names and references and orders the models by dependency.
	 *
	 * @throws IllegalArgumentException for duplicate names, unknown references or
	 *                                  circular dependencies
	 */
	public static MigrationModelPlan of(final Collection<MigrationModel> models,
			final Collection<MigrationModelSource> sources) {
		Objects.requireNonNull(models, "models");
		final Map<String, MigrationModelSource> sourceMap = new LinkedHashMap<>();
		for (final MigrationModelSource source : sources == null ? List.<MigrationModelSource>of() : sources) {
			if (sourceMap.putIfAbsent(key(source.name()), source) != null) {
				throw new IllegalArgumentException("Duplicate source name: " + source.name());
			}
		}
		final Map<String, MigrationModel> modelMap = new LinkedHashMap<>();
		for (final MigrationModel model : models) {
			if (modelMap.putIfAbsent(key(model.name()), model) != null) {
				throw new IllegalArgumentException("Duplicate model name: " + model.name());
			}
			if (sourceMap.containsKey(key(model.name()))) {
				throw new IllegalArgumentException("A model and a source share the name: " + model.name());
			}
		}
		final Map<String, List<String>> dependencies = new LinkedHashMap<>();
		final Map<String, List<String>> sourceDependencies = new LinkedHashMap<>();
		for (final MigrationModel model : modelMap.values()) {
			final List<String> refs = new ArrayList<>();
			final List<String> sourceRefs = new ArrayList<>();
			for (final Reference reference : MigrationModelReferences.find(model.sql())) {
				if (reference.kind() == Kind.REF) {
					final MigrationModel target = modelMap.get(key(reference.name()));
					if (target == null) {
						throw new IllegalArgumentException(
								"Model " + model.name() + " references an unknown model: " + reference.name()
										+ (sourceMap.containsKey(key(reference.name()))
												? " (it is a source; use source('" + reference.name() + "'))"
												: ""));
					}
					if (target == model) {
						throw new IllegalArgumentException("Model references itself: " + model.name());
					}
					if (!refs.contains(target.name())) {
						refs.add(target.name());
					}
				} else {
					final MigrationModelSource target = sourceMap.get(key(reference.name()));
					if (target == null) {
						throw new IllegalArgumentException(
								"Model " + model.name() + " references an unknown source: " + reference.name()
										+ (modelMap.containsKey(key(reference.name()))
												? " (it is a model; use ref('" + reference.name() + "'))"
												: ""));
					}
					if (!sourceRefs.contains(target.name())) {
						sourceRefs.add(target.name());
					}
				}
			}
			dependencies.put(model.name(), List.copyOf(refs));
			sourceDependencies.put(model.name(), List.copyOf(sourceRefs));
		}
		final Map<String, MigrationModelSource> sourcesByName = new LinkedHashMap<>();
		sourceMap.values().forEach(source -> sourcesByName.put(source.name(), source));
		return new MigrationModelPlan(order(modelMap.values(), dependencies), sourcesByName, dependencies,
				sourceDependencies);
	}

	private static List<MigrationModel> order(final Collection<MigrationModel> models,
			final Map<String, List<String>> dependencies) {
		final List<MigrationModel> ordered = new ArrayList<>(models.size());
		final Set<String> done = new LinkedHashSet<>();
		final List<MigrationModel> remaining = new ArrayList<>(models);
		while (!remaining.isEmpty()) {
			boolean progressed = false;
			for (final var iterator = remaining.iterator(); iterator.hasNext();) {
				final MigrationModel model = iterator.next();
				if (done.containsAll(dependencies.get(model.name()))) {
					ordered.add(model);
					done.add(model.name());
					iterator.remove();
					progressed = true;
				}
			}
			if (!progressed) {
				throw new IllegalArgumentException("Circular model dependency: " + cycle(remaining, dependencies));
			}
		}
		return ordered;
	}

	private static String cycle(final List<MigrationModel> remaining, final Map<String, List<String>> dependencies) {
		final Set<String> names = new LinkedHashSet<>();
		remaining.forEach(model -> names.add(model.name()));
		final List<String> path = new ArrayList<>();
		String current = remaining.getFirst().name();
		while (!path.contains(current)) {
			path.add(current);
			current = dependencies.get(current).stream().filter(names::contains).findFirst().orElseThrow();
		}
		final List<String> loop = new ArrayList<>(path.subList(path.indexOf(current), path.size()));
		loop.add(current);
		return String.join(" -> ", loop);
	}

	/** Models in execution order. */
	public List<MigrationModel> models() {
		return models;
	}

	public MigrationModel model(final String name) {
		final MigrationModel model = modelsByName.get(name);
		if (model != null) {
			return model;
		}
		return models.stream().filter(candidate -> candidate.name().equalsIgnoreCase(name)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown model: " + name));
	}

	public Map<String, MigrationModelSource> sources() {
		return sources;
	}

	public MigrationModelSource source(final String name) {
		final MigrationModelSource source = sources.get(name);
		if (source != null) {
			return source;
		}
		return sources.values().stream().filter(candidate -> candidate.name().equalsIgnoreCase(name)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown source: " + name));
	}

	/** Model names directly referenced by the model. */
	public List<String> dependencies(final String model) {
		return dependencies.get(model(model).name());
	}

	/** Source names directly referenced by the model. */
	public List<String> sourceDependencies(final String model) {
		return sourceDependencies.get(model(model).name());
	}

	/** Fingerprint of every model definition, its sources and its dependencies. */
	public String fingerprint() {
		return fingerprint;
	}

	/**
	 * State artifact for selective execution. A node changes when its SQL,
	 * materialization, parameters, schema or one of its source definitions
	 * changes.
	 */
	public MigrationNodeManifest nodeManifest() {
		final Map<String, MigrationNodeManifest.Node> nodes = new LinkedHashMap<>();
		for (final MigrationModel model : models) {
			nodes.put(model.name(), new MigrationNodeManifest.Node(model.name(), fingerprints.get(model.name()),
					dependencies.get(model.name())));
		}
		return new MigrationNodeManifest(MigrationNodeManifest.CURRENT_VERSION, fingerprint, nodes);
	}

	/** Names of added or modified models and all of their downstream models. */
	public Set<String> modifiedAndDownstream(final MigrationNodeManifest previous) {
		return MigrationNodeStateSelector.modifiedAndDownstream(previous, nodeManifest()).selected();
	}

	/**
	 * Returns the selected models in execution order. Unselected upstream models
	 * are expected to exist already in the execution engine.
	 */
	public List<MigrationModel> select(final Set<String> names) {
		Objects.requireNonNull(names, "names");
		final Set<String> resolved = new LinkedHashSet<>();
		names.forEach(name -> resolved.add(model(name).name()));
		return models.stream().filter(model -> resolved.contains(model.name())).toList();
	}

	private static String fingerprint(final MigrationModel model, final List<String> sourceNames,
			final Map<String, MigrationModelSource> sources) {
		final StringBuilder value = new StringBuilder();
		value.append("name=").append(model.name()).append('\n');
		value.append("schema=").append(model.schemaName()).append('\n');
		value.append("materialization=").append(model.materialization()).append('\n');
		value.append("parameters=").append(model.parameters()).append('\n');
		for (final String name : sourceNames) {
			value.append("source=").append(sources.get(name)).append('\n');
		}
		value.append("sql=").append(model.sql().replace("\r\n", "\n"));
		return sha256(value.toString());
	}

	private static String sha256(final String value) {
		try {
			return "sha256:" + HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String key(final String name) {
		return name.toLowerCase(java.util.Locale.ROOT);
	}
}
