/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.bulk.*;

class TransferGeneratedKeyChecksTest {
	@ParameterizedTest
	@CsvSource({ "true,INT,true", "false,SERIAL,true", "false,INT,false" })
	void selectedSchemaIdentifiesOnlyGeneratedColumnsAndUsesResolvedTargetNames(boolean identity, DataType type,
			boolean expected) {
		final var source = new Table("CUSTOMERS").setSchemaName("SOURCE");
		final var key = new Column("ID").setDataType(type).setIdentity(identity).setNotNull(true);
		source.getColumns().add(key);
		source.setPrimaryKey(key);
		final var target = source.clone().setCatalogName("DEST").setSchemaName("ARCHIVE");
		final var task = BulkMigrationJobTask.builder().taskId("customers").sourceTable(source).targetTable(target)
				.options(ChunkedBulkMigrationOption.builder().migrationId("customers").mode(BulkMigrationMode.INSERT)
						.resume(false).build())
				.build();
		final var checks = TransferGeneratedKeyChecks.required(BulkMigrationJobPlanner.plan(List.of(task)));
		assertEquals(expected ? 1 : 0, checks.size());
		if (expected) {
			assertTrue(checks.getFirst().contains("Target 'DEST.ARCHIVE.CUSTOMERS'"));
			assertTrue(checks.getFirst().contains("column 'ID'"));
			assertTrue(checks.getFirst().contains("not checked or synchronized"));
		}
	}
}
