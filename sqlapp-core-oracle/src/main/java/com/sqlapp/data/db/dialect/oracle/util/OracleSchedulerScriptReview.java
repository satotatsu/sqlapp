package com.sqlapp.data.db.dialect.oracle.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.sqlapp.data.schemas.Event;

/**
 * Collects native Scheduler scripts for manual review. This is not an
 * executable restoration plan: native scripts may enable objects or change
 * session state. Scripts retain their original owners and must not be blindly
 * replayed.
 */
public final class OracleSchedulerScriptReview {

	public enum Kind {
		PROGRAM, SCHEDULE, JOB
	}

	/**
	 * Immutable source script and its catalog identity; SQL text is not rewritten.
	 */
	public record Script(Kind kind, String owner, String name, String definition) {
	}

	/**
	 * Full source model snapshot, including argument values and dependency defaults
	 * omitted by native scripts.
	 */
	public record JobMetadata(String owner, String name, String xml) {
	}

	private final List<Script> scripts;
	private final List<JobMetadata> jobs;

	private OracleSchedulerScriptReview(List<Script> scripts, List<JobMetadata> jobs) {
		this.scripts = List.copyOf(scripts);
		this.jobs = List.copyOf(jobs);
	}

	/**
	 * Collects disabled jobs and their available, disabled Program dependencies.
	 * Shared dependencies are emitted once, before jobs. No SQL is executed, no
	 * target catalog is inspected, and completeness of external dependencies such
	 * as procedures, job classes and credentials is not asserted.
	 *
	 * @throws IllegalArgumentException for missing scripts, conflicting snapshots,
	 *                                  duplicate jobs, enabled objects or
	 *                                  unresolved dependency identities
	 */
	public static OracleSchedulerScriptReview collect(Collection<Event> events) {
		if (events == null || events.isEmpty())
			throw new IllegalArgumentException("Select at least one disabled Oracle Scheduler job");
		Map<List<String>, Script> objects = new LinkedHashMap<>();
		List<JobMetadata> jobs = new ArrayList<>();
		for (Event event : events) {
			if (event == null || event.isEnable())
				throw new IllegalArgumentException("Scheduler script review requires disabled jobs");
			String owner = required(event.getSchemaName(), "job owner");
			String name = required(event.getName(), "job name");
			String prefix = "oracle.scheduler.";
			requireSnapshot(event, prefix, owner, name);
			String definition = event.getSpecifics().get(prefix + "definition");
			add(objects, new Script(Kind.JOB, owner, name, required(definition, "job definition")), false);
			try {
				jobs.add(new JobMetadata(owner, name, event.asXml()));
			} catch (javax.xml.stream.XMLStreamException error) {
				throw new IllegalStateException("Cannot snapshot Oracle Scheduler metadata: " + owner + "." + name,
						error);
			}
			for (Kind kind : List.of(Kind.PROGRAM, Kind.SCHEDULE)) {
				String dependencyName = event.getSpecifics().get(prefix + kind + "_NAME");
				String dependencyOwner = event.getSpecifics().get(prefix + kind + "_OWNER");
				if (dependencyName == null && dependencyOwner == null)
					continue;
				required(dependencyName, kind + " name");
				required(dependencyOwner, kind + " owner");
				String dependencyPrefix = prefix + kind.name().toLowerCase(java.util.Locale.ROOT) + ".";
				if (!"AVAILABLE".equals(event.getSpecifics().get(dependencyPrefix + "status")))
					throw new IllegalArgumentException("Scheduler dependency is unavailable: " + kind + " "
							+ dependencyOwner + "." + dependencyName);
				if (kind == Kind.PROGRAM && !"FALSE".equals(event.getSpecifics().get(dependencyPrefix + "ENABLED")))
					throw new IllegalArgumentException(
							"Scheduler script review requires disabled Programs: " + dependencyName);
				requireSnapshot(event, dependencyPrefix, dependencyOwner, dependencyName);
				add(objects,
						new Script(kind, dependencyOwner, dependencyName, required(
								event.getSpecifics().get(dependencyPrefix + "definition"), kind + " definition")),
						true);
			}
		}
		List<Script> ordered = new ArrayList<>();
		for (Kind kind : Kind.values())
			objects.values().stream().filter(script -> script.kind() == kind).forEach(ordered::add);
		return new OracleSchedulerScriptReview(ordered, jobs);
	}

	private static void requireSnapshot(Event event, String prefix, String owner, String name) {
		if (!"AVAILABLE".equals(event.getSpecifics().get(prefix + "definitionStatus"))
				|| !"SOURCE_OWNER".equals(event.getSpecifics().get(prefix + "definitionContext"))
				|| !owner.equals(event.getSpecifics().get(prefix + "definitionOwner"))
				|| !name.equals(event.getSpecifics().get(prefix + "definitionName")))
			throw new IllegalArgumentException(
					"Refresh full Oracle Scheduler metadata before script review: " + prefix);
	}

	private static void add(Map<List<String>, Script> objects, Script script, boolean shared) {
		Script previous = objects.putIfAbsent(List.of(script.kind().name(), script.owner(), script.name()), script);
		if (previous != null && (!shared || !previous.equals(script)))
			throw new IllegalArgumentException("Duplicate or conflicting Scheduler script: " + script.kind() + " "
					+ script.owner() + "." + script.name());
	}

	private static String required(String value, String field) {
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("Missing Oracle Scheduler " + field);
		return value;
	}

	/**
	 * Scripts for inspection only; availability flags are not a safety attestation.
	 */
	public List<Script> scripts() {
		return scripts;
	}

	/**
	 * Source snapshots must accompany native scripts during review; base scripts
	 * can omit arguments.
	 */
	public List<JobMetadata> jobs() {
		return jobs;
	}
}
