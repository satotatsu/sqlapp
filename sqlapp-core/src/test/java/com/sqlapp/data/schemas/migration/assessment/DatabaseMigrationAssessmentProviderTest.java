/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration.assessment;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.Schema;

class DatabaseMigrationAssessmentProviderTest {
	private static final MigrationAssessment EMPTY = new MigrationAssessment(List.of(), List.of());
	private static final DatabaseMigrationAssessmentProvider TARGET = new DatabaseMigrationAssessmentProvider() {
		public boolean supports(String source, String target, String version) {
			return "source".equals(source) && "target".equals(target) && "1".equals(version);
		}
		public String targetProduct() { return "target"; }
		public MigrationAssessment assess(MigrationAssessmentSource source, String version) { return EMPTY; }
	};
	private static final MigrationAssessmentSourceProvider SOURCE = new MigrationAssessmentSourceProvider() {
		public boolean supports(Path file) { return file.toString().endsWith(".fixture"); }
		public MigrationAssessmentSource load(Path file) {
			return new MigrationAssessmentSource(List.of(new Schema("s").setProductName("source")), EMPTY, false, false);
		}
	};

	@Test
	void resolvesOnlyAnExplicitUniqueSourceTargetVersionCombination() {
		assertSame(TARGET, DatabaseMigrationAssessmentProvider.resolve("source", "target", "1", List.of(TARGET)));
		assertThrows(IllegalArgumentException.class, () -> DatabaseMigrationAssessmentProvider.resolve("other", "target", "1", List.of(TARGET)));
		assertThrows(IllegalArgumentException.class, () -> DatabaseMigrationAssessmentProvider.resolve("source", "other", "1", List.of(TARGET)));
		assertThrows(IllegalArgumentException.class, () -> DatabaseMigrationAssessmentProvider.resolve("source", "target", "2", List.of(TARGET)));
		assertThrows(IllegalArgumentException.class, () -> DatabaseMigrationAssessmentProvider.resolve("source", "target", "1", List.of(TARGET, TARGET)));
		assertThrows(IllegalArgumentException.class, () -> DatabaseMigrationAssessmentProvider.resolve("source", " ", "1", List.of(TARGET)));
		assertThrows(IllegalArgumentException.class, () -> DatabaseMigrationAssessmentProvider.resolve("missing", "missing", "1"));
	}

	@Test
	void resolvesSourceWithoutFallbackOrAmbiguousSelection() {
		assertSame(SOURCE, MigrationAssessmentSourceProvider.resolve(Path.of("input.fixture"), List.of(SOURCE)));
		assertThrows(IllegalArgumentException.class, () -> MigrationAssessmentSourceProvider.resolve(Path.of("input.other"), List.of(SOURCE)));
		assertThrows(IllegalArgumentException.class, () -> MigrationAssessmentSourceProvider.resolve(Path.of("input.fixture"), List.of(SOURCE, SOURCE)));
		assertThrows(IllegalArgumentException.class, () -> MigrationAssessmentSourceProvider.resolve(Path.of("input.missing")));
	}

	@Test
	void snapshotsRequireAnUnambiguousProductAndPreserveSchemaSpecifics() {
		final var schema = new Schema("s").setProductName("source");
		schema.getSpecifics().put("source.nativeDetail", "value");
		final var schemas = new ArrayList<>(List.of(schema));
		final var snapshot = new MigrationAssessmentSource(schemas, EMPTY, false, false);
		schemas.clear();
		assertEquals("source", snapshot.sourceProduct());
		assertEquals("value", snapshot.schemas().getFirst().getSpecifics().get("source.nativeDetail", String.class));
		assertThrows(UnsupportedOperationException.class, () -> snapshot.schemas().clear());
		assertThrows(IllegalArgumentException.class, () -> new MigrationAssessmentSource(List.of(), EMPTY, false, false));
		assertThrows(IllegalArgumentException.class, () -> new MigrationAssessmentSource(List.of(new Schema("s")), EMPTY, false, false));
		assertThrows(IllegalArgumentException.class, () -> new MigrationAssessmentSource(List.of(schema,
				new Schema("other").setProductName("other")), EMPTY, false, false));
	}
}
