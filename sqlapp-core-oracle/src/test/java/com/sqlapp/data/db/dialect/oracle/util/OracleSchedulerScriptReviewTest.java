package com.sqlapp.data.db.dialect.oracle.util;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.Event;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.db.dialect.oracle.util.OracleSchedulerScriptReview.Kind;

class OracleSchedulerScriptReviewTest {
	private Event job(String name) {
		Event event = new Event(name).setSchemaName("APP").setEnable(false);
		event.getSpecifics().put("oracle.scheduler.definition",
				"BEGIN\n -- 日本語\n dbms_scheduler.create_job('" + name + "');\nEND;\n/");
		snapshot(event, "oracle.scheduler.", "APP", name);
		return event;
	}

	private void snapshot(Event event, String prefix, String owner, String name) {
		event.getSpecifics().put(prefix + "definitionStatus", "AVAILABLE");
		event.getSpecifics().put(prefix + "definitionContext", "SOURCE_OWNER");
		event.getSpecifics().put(prefix + "definitionOwner", owner);
		event.getSpecifics().put(prefix + "definitionName", name);
	}

	private void dependencies(Event event, String owner) {
		for (Kind kind : List.of(Kind.PROGRAM, Kind.SCHEDULE)) {
			String prefix = "oracle.scheduler." + kind.name().toLowerCase(java.util.Locale.ROOT) + ".";
			event.getSpecifics().put("oracle.scheduler." + kind + "_OWNER", owner);
			event.getSpecifics().put("oracle.scheduler." + kind + "_NAME", "SHARED");
			snapshot(event, prefix, owner, "SHARED");
			event.getSpecifics().put(prefix + "status", "AVAILABLE");
			event.getSpecifics().put(prefix + "ENABLED", "FALSE");
			event.getSpecifics().put(prefix + "definition",
					"BEGIN\n -- 'quoted' " + kind + " " + owner + "\nEND;\n/\n");
		}
	}

	@Test
	void collectsImmutableDependencyOrderAfterXmlRoundTrip() throws Exception {
		Event first = job("J1"), second = job("J2");
		dependencies(first, "OTHER");
		dependencies(second, "OTHER");
		Event restored = SchemaUtils.readXml(new java.io.StringReader(first.asXml()));
		var review = OracleSchedulerScriptReview.collect(List.of(restored, second));
		assertEquals(List.of(Kind.PROGRAM, Kind.SCHEDULE, Kind.JOB, Kind.JOB),
				review.scripts().stream().map(s -> s.kind()).toList());
		assertEquals("OTHER", review.scripts().getFirst().owner());
		assertEquals(first.getSpecifics().get("oracle.scheduler.program.definition"),
				review.scripts().getFirst().definition());
		assertEquals(first.getSpecifics().get("oracle.scheduler.definition"), review.scripts().get(2).definition());
		assertThrows(UnsupportedOperationException.class, () -> review.scripts().clear());
		first.setName("CHANGED");
		assertEquals("J1", review.scripts().get(2).name());
	}

	@Test
	void rejectsMissingOrStaleCapturesAndEnabledObjects() {
		assertThrows(IllegalArgumentException.class, () -> OracleSchedulerScriptReview.collect(List.of()));
		Event event = job("J1");
		event.setEnable(true);
		assertThrows(IllegalArgumentException.class, () -> OracleSchedulerScriptReview.collect(List.of(event)));
		event.setEnable(false).setName("RENAMED");
		assertThrows(IllegalArgumentException.class, () -> OracleSchedulerScriptReview.collect(List.of(event)));
		event.setName("J1").setSchemaName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> OracleSchedulerScriptReview.collect(List.of(event)));
		event.setSchemaName("APP");
		dependencies(event, "OTHER");
		event.getSpecifics().put("oracle.scheduler.program.ENABLED", "TRUE");
		assertThrows(IllegalArgumentException.class, () -> OracleSchedulerScriptReview.collect(List.of(event)));
		event.getSpecifics().put("oracle.scheduler.program.ENABLED", "FALSE");
		event.getSpecifics().put("oracle.scheduler.schedule.definitionStatus", "INACCESSIBLE");
		assertThrows(IllegalArgumentException.class, () -> OracleSchedulerScriptReview.collect(List.of(event)));
	}

	@Test
	void rejectsDuplicateJobsAndConflictingSharedDefinitions() {
		assertThrows(IllegalArgumentException.class,
				() -> OracleSchedulerScriptReview.collect(List.of(job("J1"), job("J1"))));
		Event first = job("J1"), second = job("J2");
		dependencies(first, "OTHER");
		dependencies(second, "OTHER");
		second.getSpecifics().put("oracle.scheduler.program.definition", "different script");
		assertThrows(IllegalArgumentException.class, () -> OracleSchedulerScriptReview.collect(List.of(first, second)));
	}

	@Test
	void rejectsIncompleteDefinitionsAndDependencyIdentities() {
		Event event = job("J1");
		event.getSpecifics().put("oracle.scheduler.definition", "");
		event.setDefinition("BEGIN legacy_native_script; END;");
		assertThrows(IllegalArgumentException.class, () -> OracleSchedulerScriptReview.collect(List.of(event)));
		Event dependencyJob = job("J2");
		dependencyJob.getSpecifics().put("oracle.scheduler.PROGRAM_NAME", "P");
		assertThrows(IllegalArgumentException.class, () -> OracleSchedulerScriptReview.collect(List.of(dependencyJob)));
		dependencies(dependencyJob, "OTHER");
		dependencyJob.getSpecifics().put("oracle.scheduler.program.definition", "  ");
		assertThrows(IllegalArgumentException.class, () -> OracleSchedulerScriptReview.collect(List.of(dependencyJob)));
	}

	@Test
	void keepsCaseSensitiveOwnersDistinctAndSupportsJobsWithoutDependencies() {
		assertEquals(1, OracleSchedulerScriptReview.collect(List.of(job("J1"))).scripts().size());
		Event first = job("J1"), second = job("J2");
		dependencies(first, "Other");
		dependencies(second, "OTHER");
		assertEquals(6, OracleSchedulerScriptReview.collect(List.of(first, second)).scripts().size());
	}

	@Test
	void retainsSupplementalArgumentsAndDefaultsInImmutableSourceSnapshots() throws Exception {
		Event event = job("J1");
		dependencies(event, "OTHER");
		event.getSpecifics().put("oracle.scheduler.argument.1.value", "  'quoted'\nvalue  ");
		event.getSpecifics().put("oracle.scheduler.program.argument.1.defaultValue", "default");
		var review = OracleSchedulerScriptReview.collect(List.of(event));
		event.getSpecifics().put("oracle.scheduler.argument.1.value", "changed");
		Event restored = SchemaUtils.readXml(new java.io.StringReader(review.jobs().getFirst().xml()));
		assertEquals("  'quoted'\nvalue  ", restored.getSpecifics().get("oracle.scheduler.argument.1.value"));
		assertEquals("default", restored.getSpecifics().get("oracle.scheduler.program.argument.1.defaultValue"));
		assertThrows(UnsupportedOperationException.class, () -> review.jobs().clear());
	}
}
