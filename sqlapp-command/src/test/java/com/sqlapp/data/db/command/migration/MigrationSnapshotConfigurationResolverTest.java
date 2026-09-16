/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;

class MigrationSnapshotConfigurationResolverTest {
	@TempDir Path directory;

	@Test
	void resolvesConciseYamlAgainstSchemaModel() throws Exception {
		final FileSet files = files("effectiveAt: 2026-09-16T00:00:00Z\n");
		final var resolved = new MigrationSnapshotConfigurationResolver().resolve(files.yaml().toFile());
		assertEquals("CUSTOMER", resolved.sourceTable().getName());
		assertEquals("CUSTOMER_HISTORY", resolved.targetTable().getName());
		assertEquals(java.time.Instant.parse("2026-09-16T00:00:00Z"), resolved.effectiveAt());
		assertEquals(10_000, resolved.batchSize());
		assertEquals(java.util.List.of("ID"), resolved.definition().keyColumns());
	}

	@Test
	void requiresStableEffectiveTimestamp() throws Exception {
		final FileSet files = files("");
		assertThrows(CommandException.class,
				() -> new MigrationSnapshotConfigurationResolver().resolve(files.yaml().toFile()));
	}

	@Test
	void rejectsUnknownColumnsBeforeOpeningDatabaseConnections() throws Exception {
		final FileSet files = files("trackedColumns: [UNKNOWN]\neffectiveAt: 2026-09-16T00:00:00Z\n", false);
		final var error = assertThrows(CommandException.class,
				() -> new MigrationSnapshotConfigurationResolver().resolve(files.yaml().toFile()));
		assertEquals("Unknown column 'UNKNOWN' in sourceTable: CUSTOMER", error.getMessage());
	}

	private FileSet files(final String tail) throws Exception {
		return files(tail, true);
	}

	private FileSet files(final String tail, final boolean includeTrackedColumns) throws Exception {
		final Schema schema = new Schema("PUBLIC");
		final Table source = new Table("CUSTOMER");
		source.getColumns().add(new Column("ID").setDataType(DataType.INT));
		source.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
		final Table target = new Table("CUSTOMER_HISTORY");
		target.getColumns().add(new Column("ID").setDataType(DataType.INT));
		target.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
		target.getColumns().add(new Column("VALID_FROM").setDataType(DataType.TIMESTAMP));
		target.getColumns().add(new Column("VALID_TO").setDataType(DataType.TIMESTAMP));
		target.getColumns().add(new Column("IS_CURRENT").setDataType(DataType.BOOLEAN));
		schema.getTables().add(source);
		schema.getTables().add(target);
		final Path xml = directory.resolve("schema.xml");
		schema.writeXml(xml.toFile());
		final Path yaml = directory.resolve("snapshot.yaml");
		Files.writeString(yaml, "schemaFile: schema.xml\nsourceTable: CUSTOMER\ntargetTable: CUSTOMER_HISTORY\n"
				+ "keyColumns: [ID]\n" + (includeTrackedColumns ? "trackedColumns: [NAME]\n" : "")
				+ "validFromColumn: VALID_FROM\n"
				+ "validToColumn: VALID_TO\ncurrentColumn: IS_CURRENT\n" + tail);
		return new FileSet(xml, yaml);
	}

	private record FileSet(Path xml, Path yaml) { }
}
