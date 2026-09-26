/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import java.nio.file.Path;
import java.util.List;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.migration.assessment.*;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.*;

/** Test-only providers prove the command has no source/target dialect assumptions. */
public final class TestMigrationAssessmentProviders {
	public static final class Source implements MigrationAssessmentSourceProvider {
		public boolean supports(Path file) { return file.toString().endsWith(".assessment-fixture"); }
		public MigrationAssessmentSource load(Path file) {
			final var schema = new Schema("fixture").setProductName("Fixture Source");
			schema.getSpecifics().put("fixture.nativeDetail", "preserved");
			return new MigrationAssessmentSource(List.of(schema), new MigrationAssessment(List.of(new Finding(
					"fixture.source-blocker", Severity.BLOCKER, Evidence.SCHEMA, null,
					"Source asset requires a mapping", "Supply a mapping", null)), List.of()), false, false);
		}
	}
	public static final class Target implements DatabaseMigrationAssessmentProvider {
		public boolean supports(String source, String target, String version) {
			return "Fixture Source".equals(source) && "fixture".equals(target) && "1".equals(version);
		}
		public String targetProduct() { return "Fixture Target"; }
		public MigrationAssessment assess(MigrationAssessmentSource source, String version) {
			final String detail = source.schemas().getFirst().getSpecifics().get("fixture.nativeDetail", String.class);
			return new MigrationAssessment(List.of(new Finding("fixture.target-review", Severity.REVIEW,
					Evidence.SCHEMA, null, detail, "Review target rules", null)), List.of());
		}
	}
}
