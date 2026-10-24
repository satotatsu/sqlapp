package com.sqlapp.data.db.command.generator;

import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.schemas.Table;

class GeneratorTableResolutionTest {
	private Table table(String catalog, String schema, String name) {
		return new Table(name).setSchemaName(schema).setCatalogName(catalog);
	}

	private TableGeneratorConfig config(String schema, String name) {
		var config = new TableGeneratorConfig();
		config.setSchemaName(schema);
		config.setName(name);
		return config;
	}

	@Test
	void resolvesSameNamedTablesAndMultipleConfigsWithoutMutatingInput() {
		var a = table("C", "A", "T");
		var b = table("C", "B", "T");
		var ca = config("A", "T");
		var cb = config("B", "T");
		var cb2 = config("B", "T");
		var input = Map.of("T", List.of(ca, cb, cb2));
		var result = new GenerateDataInsertCommand().resolveTableConfigs(List.of(a, b), input);
		assertEquals(List.of(ca), result.get(a));
		assertEquals(List.of(cb, cb2), result.get(b));
		assertEquals(3, input.get("T").size());
		assertEquals("T", ca.getName());
	}

	@Test
	void rejectsAmbiguousShortNamesAndListsCandidates() {
		var error = assertThrows(MultiTableFoundException.class,
				() -> new GenerateDataInsertCommand().resolveTableConfigs(
						List.of(table("C", "A", "T"), table("C", "B", "T")), Map.of("T", List.of(config(null, "T")))));
		assertTrue(error.getMessage().contains("schema=A"));
		assertTrue(error.getMessage().contains("schema=B"));
		assertTrue(error.getMessage().contains("schemaName"));
	}

	@Test
	void acceptsUniqueShortNamesAndUnambiguousCaseInsensitiveNames() {
		var t = table("C", "A", "T");
		var command = new GenerateDataInsertCommand();
		assertTrue(command.resolveTableConfigs(List.of(t), Map.of("T", List.of(config(null, "T")))).containsKey(t));
		assertTrue(command.resolveTableConfigs(List.of(t), Map.of("t", List.of(config("a", "t")))).containsKey(t));
	}

	@Test
	void preservesLiteralDotsAndExactIdentifierCase() {
		var upper = table("C", "A", "T.X");
		var lower = table("C", "A", "t.x");
		var result = new GenerateDataInsertCommand().resolveTableConfigs(List.of(upper, lower),
				Map.of("T.X", List.of(config("A", "T.X"))));
		assertTrue(result.containsKey(upper));
		assertFalse(result.containsKey(lower));
	}

	@Test
	void doesNotConflateCatalogIdentities() {
		assertThrows(MultiTableFoundException.class, () -> new GenerateDataInsertCommand().resolveTableConfigs(
				List.of(table("C1", "A", "T"), table("C2", "A", "T")), Map.of("T", List.of(config("A", "T")))));
	}

	@Test
	void usesSchemaDirectoriesAndRejectsConflictingOrMissingIdentity() {
		var a = table("C", "A", "T");
		var b = table("C", "B", "T");
		var command = new GenerateDataInsertCommand();
		command.setUseSchemaNameDirectory(true);
		var ca = config(null, "T");
		ca.setFile(new File("A/T.yaml"));
		var cb = config("B", "T");
		cb.setFile(new File("B/T.yaml"));
		var result = command.resolveTableConfigs(List.of(a, b), Map.of("T", List.of(ca, cb)));
		assertEquals(List.of(ca), result.get(a));
		assertEquals(List.of(cb), result.get(b));
		ca.setSchemaName("B");
		assertThrows(IllegalArgumentException.class,
				() -> command.resolveTableConfigs(List.of(a, b), Map.of("T", List.of(ca))));
		assertThrows(IllegalArgumentException.class,
				() -> command.resolveTableConfigs(List.of(a, b), Map.of("T", List.of(config(null, "T")))));
	}

	@Test
	void rejectsMissingTargetsButHonorsExplicitSelectionFilters() {
		var command = new GenerateDataInsertCommand();
		var t = table("C", "A", "T");
		assertThrows(TableNotFoundException.class,
				() -> command.resolveTableConfigs(List.of(t), Map.of("U", List.of(config("A", "U")))));
		assertThrows(TableNotFoundException.class,
				() -> command.resolveTableConfigs(List.of(t), Map.of("T", List.of(config("B", "T")))));
		command.setIncludeTables("T");
		assertTrue(command.resolveTableConfigs(List.of(t), Map.of("U", List.of(config("A", "U")))).isEmpty());
		command.setIncludeSchemas("A");
		assertTrue(command.resolveTableConfigs(List.of(t), Map.of("T", List.of(config("B", "T")))).isEmpty());
	}
}
