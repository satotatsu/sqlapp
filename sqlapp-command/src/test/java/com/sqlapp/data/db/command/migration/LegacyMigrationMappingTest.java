/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnAction;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnDefinition;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnPair;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.Diagnostic;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.DiagnosticSeverity;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.GeneratedKey;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.GeneratedKeyGenerationType;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.IndexedSourceColumn;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.RelationshipMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TableMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TransformationRecord;
import com.sqlapp.exceptions.CommandException;

class LegacyMigrationMappingTest {

	@TempDir
	File temporaryDirectory;

	@Test
	void testYamlRoundTripAndValidation() throws Exception {
		LegacyMigrationMapping mapping = initializedMapping();
		mapping.getMigration().setId("sample-v1");
		mapping.getSource().setSchemaFingerprint("source");
		mapping.getTarget().setSchemaFingerprint("target");
		TableMapping parent = table("table-parent", "PARENT");
		GeneratedKey generatedKey = new GeneratedKey();
		generatedKey.setColumn("ID");
		generatedKey.setDataType("BIGINT");
		generatedKey.setGenerationType(GeneratedKeyGenerationType.SEQUENCE);
		generatedKey.setSequence("PARENT_SEQ");
		parent.getKeys().setGeneratedKey(generatedKey);
		parent.getKeys().getTargetPrimaryKey().add("ID");
		ColumnMapping generatedColumn = new ColumnMapping();
		generatedColumn.setTarget("ID");
		generatedColumn.setAction(ColumnAction.GENERATE);
		generatedColumn.getConversion().put("type", "SEQUENCE");
		ColumnDefinition generatedDefinition = new ColumnDefinition();
		generatedDefinition.setDataType("BIGINT");
		generatedColumn.setTargetDefinition(generatedDefinition);
		parent.getColumns().add(generatedColumn);
		TableMapping child = table("table-child", "CHILD");
		child.setParent(new LegacyMigrationMapping.ParentMapping());
		child.getParent().setMappingId(parent.getId());
		mapping.getTables().add(parent);
		mapping.getTables().add(child);
		RelationshipMapping relationship = new RelationshipMapping();
		relationship.setId("rel-parent-child");
		relationship.setParentMappingId(parent.getId());
		relationship.setChildMappingId(child.getId());
		relationship.setParentIdPropagation(true);
		mapping.getRelationships().add(relationship);
		mapping.getTransformations().add(transformation(10, "Normalize", "source", "target"));
		Diagnostic warning = new Diagnostic();
		warning.setCode("REVIEW_COLUMN");
		warning.setSeverity(DiagnosticSeverity.WARNING);
		warning.setTableMappingId(parent.getId());
		warning.setAction("REVIEW");
		warning.setMessage("Review the generated column mapping.");
		mapping.getDiagnostics().getWarnings().add(warning);
		mapping.getStatistics().setWarningCount(1);

		File yaml = new File(temporaryDirectory, "mapping.yaml");
		LegacyMigrationMappingIO io = new LegacyMigrationMappingIO();
		io.write(yaml, mapping);
		LegacyMigrationMapping restored = io.read(yaml);

		new LegacyMigrationMappingValidator().validate(restored);
		assertEquals("sample-v1", restored.getMigration().getId());
		assertEquals(2, restored.getTables().size());
		assertTrue(restored.getRelationships().getFirst().isParentIdPropagation());
		assertEquals(LegacyMigrationMapping.TransformationStatus.SUCCESS,
				restored.getTransformations().getFirst().getStatus());
		final String serialized = Files.readString(yaml.toPath());
		assertTrue(serialized.contains("status: \"SUCCESS\"") || serialized.contains("status: SUCCESS"));
		assertTrue(
				serialized.contains("generationType: \"SEQUENCE\"") || serialized.contains("generationType: SEQUENCE"));
		assertTrue(serialized.contains("severity: \"WARNING\"") || serialized.contains("severity: WARNING"));
		File unknownStatus = new File(temporaryDirectory, "unknown-status.yaml");
		Files.writeString(unknownStatus.toPath(), serialized.replace("SUCCESS", "UNKNOWN"));
		assertThrows(CommandException.class, () -> io.read(unknownStatus));
		File unknownGenerationType = new File(temporaryDirectory, "unknown-generation-type.yaml");
		Files.writeString(unknownGenerationType.toPath(), serialized.replace("SEQUENCE", "UNKNOWN"));
		assertThrows(CommandException.class, () -> io.read(unknownGenerationType));
		File unknownSeverity = new File(temporaryDirectory, "unknown-severity.yaml");
		Files.writeString(unknownSeverity.toPath(), serialized.replace("WARNING", "UNKNOWN"));
		assertThrows(CommandException.class, () -> io.read(unknownSeverity));
	}

	@Test
	void testRejectUnknownParent() {
		LegacyMigrationMapping mapping = new LegacyMigrationMapping();
		TableMapping child = table("table-child", "CHILD");
		child.setParent(new LegacyMigrationMapping.ParentMapping());
		child.getParent().setMappingId("missing");
		mapping.getTables().add(child);

		assertThrows(CommandException.class, () -> new LegacyMigrationMappingValidator().validate(mapping));
	}

	@Test
	void testRejectInconsistentGeneratedKeyMapping() {
		LegacyMigrationMapping mapping = initializedMapping();
		TableMapping table = generatedKeyTable();
		mapping.getTables().add(table);
		LegacyMigrationMappingValidator validator = new LegacyMigrationMappingValidator();
		validator.validate(mapping);

		table.getKeys().getGeneratedKey().setSequence(null);
		assertThrows(CommandException.class, () -> validator.validate(mapping));
		table.getKeys().getGeneratedKey().setSequence("ITEM_SEQ");
		table.getKeys().getTargetPrimaryKey().clear();
		assertThrows(CommandException.class, () -> validator.validate(mapping));
		table.getKeys().getTargetPrimaryKey().add("ID");
		table.getColumns().getFirst().getConversion().put("type", "IDENTITY");
		assertThrows(CommandException.class, () -> validator.validate(mapping));
		table.getColumns().getFirst().getConversion().put("type", "SEQUENCE");
		table.getColumns().getFirst().getTargetDefinition().setDataType("INTEGER");
		assertThrows(CommandException.class, () -> validator.validate(mapping));
		table.getColumns().getFirst().getTargetDefinition().setDataType("BIGINT");
		table.getKeys().getGeneratedKey().setGenerationType(GeneratedKeyGenerationType.IDENTITY);
		assertThrows(CommandException.class, () -> validator.validate(mapping));
		table.getKeys().getGeneratedKey().setSequence(null);
		table.getColumns().getFirst().getConversion().put("type", "IDENTITY");
		validator.validate(mapping);
	}

	@Test
	void testRejectInvalidDiagnosticsAndStatistics() {
		LegacyMigrationMapping mapping = initializedMapping();
		TableMapping table = table("table-item", "ITEM");
		mapping.getTables().add(table);
		Diagnostic warning = new Diagnostic();
		warning.setCode("REVIEW");
		warning.setSeverity(DiagnosticSeverity.WARNING);
		warning.setTableMappingId(table.getId());
		warning.setAction("REVIEW");
		warning.setMessage("Review item.");
		mapping.getDiagnostics().getWarnings().add(warning);
		mapping.getStatistics().setWarningCount(1);
		LegacyMigrationMappingValidator validator = new LegacyMigrationMappingValidator();
		validator.validate(mapping);

		warning.setTableMappingId("missing");
		assertThrows(CommandException.class, () -> validator.validate(mapping));
		warning.setTableMappingId(table.getId());
		warning.setSeverity(DiagnosticSeverity.ERROR);
		assertThrows(CommandException.class, () -> validator.validate(mapping));
		warning.setSeverity(DiagnosticSeverity.WARNING);
		mapping.getStatistics().setWarningCount(0);
		assertThrows(CommandException.class, () -> validator.validate(mapping));
	}

	@Test
	void testRejectInvalidOccurrenceMapping() {
		LegacyMigrationMapping mapping = initializedMapping();
		TableMapping table = table("table-detail", "DETAIL");
		ColumnMapping occurrence = new ColumnMapping();
		occurrence.setTarget("ROW_NO");
		occurrence.setAction(ColumnAction.GENERATE);
		occurrence.getConversion().put("type", "OCCURRENCE_NUMBER");
		table.getColumns().add(occurrence);
		ColumnMapping repeated = new ColumnMapping();
		repeated.setTarget("VALUE");
		repeated.setAction(ColumnAction.SPLIT);
		IndexedSourceColumn source = new IndexedSourceColumn();
		source.setIndex(1);
		source.setColumn("VALUE_1");
		repeated.getSourceColumns().add(source);
		table.getColumns().add(repeated);
		var occurrenceDetails = new java.util.LinkedHashMap<String, Object>();
		occurrenceDetails.put("column", "ROW_NO");
		occurrenceDetails.put("maximum", 1);
		table.getDetails().put("occurrence", occurrenceDetails);
		mapping.getTables().add(table);
		LegacyMigrationMappingValidator validator = new LegacyMigrationMappingValidator();
		validator.validate(mapping);

		occurrenceDetails.put("maximum", 0);
		assertThrows(CommandException.class, () -> validator.validate(mapping));
		occurrenceDetails.put("maximum", 1);
		source.setIndex(2);
		assertThrows(CommandException.class, () -> validator.validate(mapping));
		source.setIndex(1);
		table.getDetails().clear();
		assertThrows(CommandException.class, () -> validator.validate(mapping));
	}

	@Test
	void testRejectInvalidTransformationHistory() {
		LegacyMigrationMapping mapping = mapping("source", "target");
		mapping.getTransformations().add(transformation(20, "Second", "middle", "target"));
		mapping.getTransformations().add(transformation(10, "First", "source", "middle"));
		LegacyMigrationMappingValidator validator = new LegacyMigrationMappingValidator();
		assertThrows(CommandException.class, () -> validator.validate(mapping));

		mapping.getTransformations().clear();
		mapping.getTransformations().add(transformation(10, "First", "wrong", "middle"));
		mapping.getTransformations().add(transformation(20, "Second", "middle", "target"));
		assertThrows(CommandException.class, () -> validator.validate(mapping));

		mapping.getTransformations().getFirst().setInputFingerprint("source");
		mapping.getTransformations().getLast().setInputFingerprint("other");
		assertThrows(CommandException.class, () -> validator.validate(mapping));

		mapping.getTransformations().getLast().setInputFingerprint("middle");
		validator.validate(mapping);
	}

	@Test
	void testSchemaFingerprintDetectsDifferentFile() throws Exception {
		File expected = new File(temporaryDirectory, "expected.xml");
		File different = new File(temporaryDirectory, "different.xml");
		Files.writeString(expected.toPath(), "<schema name=\"A\"/>");
		Files.writeString(different.toPath(), "<schema name=\"B\"/>");
		LegacyMigrationMapping mapping = new LegacyMigrationMapping();
		LegacyMigrationMappingValidator validator = new LegacyMigrationMappingValidator();
		mapping.getSource().setSchemaFingerprint(validator.fingerprint(expected));

		validator.validateSourceFingerprint(mapping, expected);
		assertThrows(CommandException.class, () -> validator.validateSourceFingerprint(mapping, different));
	}

	@Test
	void testComposeTransformationSteps() {
		LegacyMigrationMapping existing = mapping("sha256:source", "sha256:middle");
		TableMapping original = table("table-original", "MIDDLE_TABLE");
		original.getSource().setTable("LEGACY_TABLE");
		ColumnMapping originalColumn = new ColumnMapping();
		originalColumn.setSource("LEGACY_COLUMN");
		originalColumn.setTarget("MIDDLE_COLUMN");
		original.getColumns().add(originalColumn);
		existing.getTables().add(original);
		existing.getTransformations().add(transformation(10, "First", "sha256:source", "sha256:middle"));

		LegacyMigrationMapping step = mapping("sha256:middle", "sha256:target");
		TableMapping transformed = table("table-middle", "FINAL_TABLE");
		transformed.getSource().setTable("MIDDLE_TABLE");
		ColumnMapping transformedColumn = new ColumnMapping();
		transformedColumn.setSource("MIDDLE_COLUMN");
		transformedColumn.setTarget("FINAL_COLUMN");
		transformedColumn.setAction(ColumnAction.CAST);
		transformed.getColumns().add(transformedColumn);
		step.getTables().add(transformed);
		step.getTransformations().add(transformation(10, "Second", "sha256:middle", "sha256:target"));

		LegacyMigrationMapping merged = new LegacyMigrationMappingMerger().merge(existing, step);

		assertEquals("sha256:source", merged.getSource().getSchemaFingerprint());
		assertEquals("sha256:target", merged.getTarget().getSchemaFingerprint());
		assertEquals("LEGACY_TABLE", merged.getTables().getFirst().getSource().getTable());
		assertEquals("FINAL_TABLE", merged.getTables().getFirst().getTarget().getTable());
		assertEquals("LEGACY_COLUMN", merged.getTables().getFirst().getColumns().getFirst().getSource());
		assertEquals("FINAL_COLUMN", merged.getTables().getFirst().getColumns().getFirst().getTarget());
		assertEquals(ColumnAction.CAST, merged.getTables().getFirst().getColumns().getFirst().getAction());
		assertEquals(20, merged.getTransformations().getLast().getSequence());
	}

	@Test
	void testMergePreservesParentWhenNextStepOmitsUnchangedHierarchy() {
		LegacyMigrationMapping existing = mapping("source", "middle");
		TableMapping parent = table("parent", "PARENT_MIDDLE");
		TableMapping child = table("child", "CHILD_MIDDLE");
		child.setParent(new LegacyMigrationMapping.ParentMapping());
		child.getParent().setMappingId(parent.getId());
		existing.getTables().addAll(java.util.List.of(parent, child));
		existing.getRelationships().add(relationship("parent-child", parent, child));
		existing.getTransformations().add(transformation(10, "First", "source", "middle"));

		LegacyMigrationMapping step = mapping("middle", "target");
		TableMapping nextParent = table("next-parent", "PARENT_TARGET");
		nextParent.getSource().setTable("PARENT_MIDDLE");
		TableMapping nextChild = table("next-child", "CHILD_TARGET");
		nextChild.getSource().setTable("CHILD_MIDDLE");
		step.getTables().addAll(java.util.List.of(nextParent, nextChild));
		step.getTransformations().add(transformation(10, "Second", "middle", "target"));

		LegacyMigrationMapping merged = new LegacyMigrationMappingMerger().merge(existing, step);

		TableMapping mergedChild = merged.getTables().stream()
				.filter(table -> "CHILD_TARGET".equals(table.getTarget().getTable())).findFirst().orElseThrow();
		assertEquals(parent.getId(), mergedChild.getParent().getMappingId());
		new LegacyMigrationMappingValidator().validate(merged);
	}

	@Test
	void testComposeHierarchyReferencesAcrossTransformationSteps() {
		LegacyMigrationMapping existing = mapping("source", "middle");
		TableMapping parent = table("parent", "PARENT_MIDDLE");
		TableMapping child = table("child", "CHILD_MIDDLE");
		child.setParent(new LegacyMigrationMapping.ParentMapping());
		child.getParent().setMappingId(parent.getId());
		child.getParent().getSourceReference().add(new ColumnPair("LEGACY_ID", "LEGACY_PARENT_ID"));
		child.getParent().getResolvedReference().add(new ColumnPair("MIDDLE_ID", "MIDDLE_PARENT_ID"));
		RelationshipMapping relationship = relationship("source-relationship", parent, child);
		relationship.getSourceKeys().add(new ColumnPair("LEGACY_ID", "LEGACY_PARENT_ID"));
		relationship.getTargetKeys().add(new ColumnPair("MIDDLE_ID", "MIDDLE_PARENT_ID"));
		existing.getTables().addAll(java.util.List.of(parent, child));
		existing.getRelationships().add(relationship);
		existing.getTransformations().add(transformation(10, "First", "source", "middle"));

		LegacyMigrationMapping step = mapping("middle", "target");
		TableMapping nextParent = table("next-parent", "PARENT_FINAL");
		nextParent.getSource().setTable("PARENT_MIDDLE");
		TableMapping nextChild = table("next-child", "CHILD_FINAL");
		nextChild.getSource().setTable("CHILD_MIDDLE");
		nextChild.setParent(new LegacyMigrationMapping.ParentMapping());
		nextChild.getParent().setMappingId(nextParent.getId());
		nextChild.getParent().getSourceReference().add(new ColumnPair("MIDDLE_ID", "MIDDLE_PARENT_ID"));
		nextChild.getParent().getResolvedReference().add(new ColumnPair("FINAL_ID", "FINAL_PARENT_ID"));
		RelationshipMapping nextRelationship = relationship("target-relationship", nextParent, nextChild);
		nextRelationship.getSourceKeys().add(new ColumnPair("MIDDLE_ID", "MIDDLE_PARENT_ID"));
		nextRelationship.getTargetKeys().add(new ColumnPair("FINAL_ID", "FINAL_PARENT_ID"));
		step.getTables().addAll(java.util.List.of(nextParent, nextChild));
		step.getRelationships().add(nextRelationship);
		step.getTransformations().add(transformation(10, "Second", "middle", "target"));

		LegacyMigrationMapping merged = new LegacyMigrationMappingMerger().merge(existing, step);

		TableMapping mergedChild = merged.getTables().stream()
				.filter(table -> "CHILD_FINAL".equals(table.getTarget().getTable())).findFirst().orElseThrow();
		assertEquals("LEGACY_ID", mergedChild.getParent().getSourceReference().getFirst().getParentColumn());
		assertEquals("FINAL_ID", mergedChild.getParent().getResolvedReference().getFirst().getParentColumn());
		RelationshipMapping mergedRelationship = merged.getRelationships().getFirst();
		assertEquals("LEGACY_ID", mergedRelationship.getSourceKeys().getFirst().getParentColumn());
		assertEquals("FINAL_ID", mergedRelationship.getTargetKeys().getFirst().getParentColumn());
		new LegacyMigrationMappingValidator().validate(merged);
	}

	@Test
	void testRejectDiscontinuousMapping() {
		LegacyMigrationMapping existing = mapping("sha256:source", "sha256:middle");
		LegacyMigrationMapping step = mapping("sha256:other", "sha256:target");

		assertThrows(CommandException.class, () -> new LegacyMigrationMappingMerger().merge(existing, step));
	}

	@Test
	void testAtomicReplacementLeavesNoTemporaryFile() {
		LegacyMigrationMapping mapping = initializedMapping();
		File yaml = new File(temporaryDirectory, "atomic.yaml");
		LegacyMigrationMappingIO io = new LegacyMigrationMappingIO();
		io.write(yaml, mapping);
		mapping.getMigration().setId("updated");
		io.write(yaml, mapping);

		assertEquals("updated", io.read(yaml).getMigration().getId());
		assertTrue(!new File(temporaryDirectory, "atomic.yaml.tmp").exists());
	}

	@Test
	void testRejectInvalidMappingDuringRead() throws Exception {
		File file = new File(temporaryDirectory, "invalid-mapping.yaml");
		Files.writeString(file.toPath(), "format: wrong\nversion: 1\n");

		assertThrows(CommandException.class, () -> new LegacyMigrationMappingIO().read(file));
	}

	@Test
	void testRejectIncompleteOrInvalidMappingHeader() {
		LegacyMigrationMapping mapping = initializedMapping();
		LegacyMigrationMappingValidator validator = new LegacyMigrationMappingValidator();
		validator.validate(mapping);

		mapping.getMigration().setGeneratedAt("not-a-date");
		assertThrows(CommandException.class, () -> validator.validate(mapping));
		mapping.getMigration().setGeneratedAt("2026-01-01T00:00:00Z");
		mapping.getSource().setSchemaFile(null);
		assertThrows(CommandException.class, () -> validator.validate(mapping));
		mapping.getSource().setSchemaFile("source.xml");
		var definition = new LegacyMigrationMapping.DefinitionFile();
		definition.setPath("source.pli");
		mapping.getSource().getDefinitionFiles().add(definition);
		assertThrows(CommandException.class, () -> validator.validate(mapping));
		definition.setType("PLI");
		validator.validate(mapping);
	}

	@Test
	void testRejectCyclesMultipleParentsAndIncompleteReferences() {
		LegacyMigrationMapping cyclic = new LegacyMigrationMapping();
		TableMapping first = table("first", "FIRST");
		TableMapping second = table("second", "SECOND");
		first.setParent(new LegacyMigrationMapping.ParentMapping());
		first.getParent().setMappingId(second.getId());
		second.setParent(new LegacyMigrationMapping.ParentMapping());
		second.getParent().setMappingId(first.getId());
		cyclic.getTables().add(first);
		cyclic.getTables().add(second);
		assertThrows(CommandException.class, () -> new LegacyMigrationMappingValidator().validate(cyclic));

		LegacyMigrationMapping multipleParents = new LegacyMigrationMapping();
		TableMapping parent1 = table("parent1", "PARENT1");
		TableMapping parent2 = table("parent2", "PARENT2");
		TableMapping child = table("child", "CHILD");
		multipleParents.getTables().addAll(java.util.List.of(parent1, parent2, child));
		multipleParents.getRelationships().add(relationship("rel1", parent1, child));
		multipleParents.getRelationships().add(relationship("rel2", parent2, child));
		assertThrows(CommandException.class, () -> new LegacyMigrationMappingValidator().validate(multipleParents));

		LegacyMigrationMapping incompleteReference = new LegacyMigrationMapping();
		TableMapping referenced = table("referenced", "REFERENCED");
		ColumnMapping reference = new ColumnMapping();
		reference.setAction(ColumnAction.REFERENCE);
		reference.setTarget("PARENT_ID");
		referenced.getColumns().add(reference);
		incompleteReference.getTables().add(referenced);
		assertThrows(CommandException.class, () -> new LegacyMigrationMappingValidator().validate(incompleteReference));
	}

	@Test
	void testRejectOneSidedHierarchyDefinitions() {
		LegacyMigrationMapping missingRelationship = new LegacyMigrationMapping();
		TableMapping parent = table("parent", "PARENT");
		TableMapping child = table("child", "CHILD");
		child.setParent(new LegacyMigrationMapping.ParentMapping());
		child.getParent().setMappingId(parent.getId());
		missingRelationship.getTables().addAll(java.util.List.of(parent, child));
		LegacyMigrationMappingValidator validator = new LegacyMigrationMappingValidator();
		assertThrows(CommandException.class, () -> validator.validate(missingRelationship));

		LegacyMigrationMapping missingParent = new LegacyMigrationMapping();
		parent = table("parent", "PARENT");
		child = table("child", "CHILD");
		missingParent.getTables().addAll(java.util.List.of(parent, child));
		missingParent.getRelationships().add(relationship("parent-child", parent, child));
		assertThrows(CommandException.class, () -> validator.validate(missingParent));
	}

	@Test
	void testRejectHierarchyKeyDisagreement() {
		LegacyMigrationMapping mapping = initializedMapping();
		TableMapping parent = table("parent", "PARENT");
		TableMapping child = table("child", "CHILD");
		child.setParent(new LegacyMigrationMapping.ParentMapping());
		child.getParent().setMappingId(parent.getId());
		child.getParent().getSourceReference().add(new ColumnPair("PARENT_ID", "PARENT_ID"));
		child.getParent().getResolvedReference().add(new ColumnPair("ID", "PARENT_ID"));
		RelationshipMapping relationship = relationship("parent-child", parent, child);
		relationship.getSourceKeys().add(new ColumnPair("PARENT_ID", "OTHER_ID"));
		relationship.getTargetKeys().add(new ColumnPair("ID", "PARENT_ID"));
		mapping.getTables().addAll(java.util.List.of(parent, child));
		mapping.getRelationships().add(relationship);
		LegacyMigrationMappingValidator validator = new LegacyMigrationMappingValidator();
		assertThrows(CommandException.class, () -> validator.validate(mapping));

		relationship.getSourceKeys().getFirst().setChildColumn("PARENT_ID");
		validator.validate(mapping);
	}

	private RelationshipMapping relationship(String id, TableMapping parent, TableMapping child) {
		RelationshipMapping relationship = new RelationshipMapping();
		relationship.setId(id);
		relationship.setParentMappingId(parent.getId());
		relationship.setChildMappingId(child.getId());
		return relationship;
	}

	private TableMapping table(String id, String name) {
		TableMapping table = new TableMapping();
		table.setId(id);
		table.getTarget().setTable(name);
		return table;
	}

	private TableMapping generatedKeyTable() {
		TableMapping table = table("table-item", "ITEM");
		table.getKeys().getTargetPrimaryKey().add("ID");
		GeneratedKey key = new GeneratedKey();
		key.setColumn("ID");
		key.setDataType("BIGINT");
		key.setGenerationType(GeneratedKeyGenerationType.SEQUENCE);
		key.setSequence("ITEM_SEQ");
		table.getKeys().setGeneratedKey(key);
		ColumnMapping column = new ColumnMapping();
		column.setTarget("ID");
		column.setAction(ColumnAction.GENERATE);
		column.getConversion().put("type", "SEQUENCE");
		ColumnDefinition definition = new ColumnDefinition();
		definition.setDataType("BIGINT");
		column.setTargetDefinition(definition);
		table.getColumns().add(column);
		return table;
	}

	private LegacyMigrationMapping mapping(String sourceFingerprint, String targetFingerprint) {
		LegacyMigrationMapping mapping = initializedMapping();
		mapping.getSource().setSchemaFingerprint(sourceFingerprint);
		mapping.getTarget().setSchemaFingerprint(targetFingerprint);
		return mapping;
	}

	private LegacyMigrationMapping initializedMapping() {
		LegacyMigrationMapping mapping = new LegacyMigrationMapping();
		mapping.getMigration().setId("test-migration");
		mapping.getMigration().setGeneratedAt("2026-01-01T00:00:00Z");
		mapping.getSource().setSchemaFile("source.xml");
		mapping.getSource().setSchemaFingerprint("source");
		mapping.getTarget().setSchemaFile("target.xml");
		mapping.getTarget().setSchemaFingerprint("target");
		return mapping;
	}

	private TransformationRecord transformation(int sequence, String command, String input, String output) {
		TransformationRecord record = new TransformationRecord();
		record.setSequence(sequence);
		record.setCommand(command);
		record.setStatus(LegacyMigrationMapping.TransformationStatus.SUCCESS);
		record.setInputFingerprint(input);
		record.setOutputFingerprint(output);
		return record;
	}
}
