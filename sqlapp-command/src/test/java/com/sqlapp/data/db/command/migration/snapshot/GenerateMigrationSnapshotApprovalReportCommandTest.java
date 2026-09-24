/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.snapshot;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class GenerateMigrationSnapshotApprovalReportCommandTest {
	@TempDir
	Path directory;

	@Test
	void generatesApprovalWithoutDatabase() throws Exception {
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
		schema.writeXml(directory.resolve("schema.xml").toFile());
		final Path yaml = directory.resolve("snapshot.yaml");
		Files.writeString(yaml,
				"schemaFile: schema.xml\nsourceTable: CUSTOMER\ntargetTable: CUSTOMER_HISTORY\n"
						+ "keyColumns: [ID]\ntrackedColumns: [NAME]\nvalidFromColumn: VALID_FROM\n"
						+ "validToColumn: VALID_TO\ncurrentColumn: IS_CURRENT\n"
						+ "effectiveAt: 2026-09-16T00:00:00Z\napprovalReportFile: not-created-yet.json\n");
		final Path targetFile = directory.resolve("review/approval.json");
		final var command = new GenerateMigrationSnapshotApprovalReportCommand();
		command.setConfigurationFile(yaml.toFile());
		command.setTargetFile(targetFile.toFile());
		command.run();

		final var report = new MigrationSnapshotApprovalReportIO().read(targetFile);
		assertEquals("CUSTOMER", report.snapshotId());
		assertEquals(report, command.getReport());
	}
}
