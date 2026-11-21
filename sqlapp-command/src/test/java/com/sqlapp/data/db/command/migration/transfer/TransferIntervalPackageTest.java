/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.FileFormat;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.interval.IntervalValue;

class TransferIntervalPackageTest {
	@ParameterizedTest
	@EnumSource(FileFormat.class)
	void intervalArrayPackagesPreserveBoundsNanosAndNullsThroughCreateImportAndVerify(FileFormat format)
			throws Exception {
		var source = database();
		var target = database();
		try (var from = source.getConnection();
				var to = target.getConnection();
				var statement = from.createStatement()) {
			statement
					.execute("CREATE TABLE ITEMS(ID INT PRIMARY KEY,D INTERVAL DAY(9) TO SECOND(9) ARRAY[3] ARRAY[2])");
			statement.execute(
					"INSERT INTO ITEMS VALUES(1,ARRAY[ARRAY[INTERVAL '1' DAY,NULL,INTERVAL '-0.000000001' SECOND(9,9)],"
							+ "ARRAY[INTERVAL '24' HOUR,INTERVAL '0' SECOND,NULL]])");
			statement.execute("INSERT INTO ITEMS VALUES(2,NULL)");
			var job = Files.writeString(directory.resolve("job.yaml"),
					"formatVersion: 1\nsource: from\ntarget: to\nsourceSchema: PUBLIC\ntargetSchema: PUBLIC\n"
							+ "tables: [ITEMS]\nschemaMode: CREATE_MISSING\ndataMode: FILE\n"
							+ "files: {directory: packages, format: " + format
							+ (format == FileFormat.JSONL ? "" : ", nullValue: __NULL__") + "}\n");
			var saved = new TransferFileExportService().export(job, Map.of("from", source));
			var service = new TransferFileImportService();
			var review = service.review(saved.file(), saved.fingerprint(), Map.of("to", target));
			assertTrue(
					review.schemaPreparation().statements().stream()
							.anyMatch(sql -> sql.sql().contains("ARRAY[3] ARRAY[2]")),
					() -> review.schemaPreparation().statements().toString());
			var outcome = service.execute(review, Map.of("to", target), directory);
			assertEquals(TransferJobExecutionService.Phase.COMPLETE, outcome.phase());
			assertTrue(outcome.verification().values().stream().allMatch(result -> result.isMatch()));
			var reader = DialectResolver.getInstance().getDialect(to).getCatalogReader().getSchemaReader()
					.getTableReader();
			reader.setSchemaName("PUBLIC");
			reader.setObjectName("ITEMS");
			var column = reader.getAllFull(to).getFirst().getColumns().get("D");
			assertEquals(2, column.getArrayDimension());
			assertEquals("3,2", column.getSpecifics().get(com.sqlapp.data.db.datatype.IntervalArrays.DECLARED_BOUNDS));
			var handler = DialectResolver.getInstance().getDialect(to).getDbDataTypes()
					.getDbTypeStrict(column.getDataType()).getJdbcTypeHandler();
			try (var query = to.createStatement(); var rows = query.executeQuery("SELECT D FROM ITEMS ORDER BY ID")) {
				assertTrue(rows.next());
				assertArrayEquals(
						new Object[] {
								new Object[] { IntervalValue.parse("PT86400S"), null,
										IntervalValue.parse("PT-0.000000001S") },
								new Object[] { IntervalValue.parse("PT86400S"), IntervalValue.ZERO, null } },
						(Object[]) handler.getObject(rows, 1));
				assertTrue(rows.next());
				assertNull(handler.getObject(rows, 1));
			}
		}
	}

	@TempDir
	Path directory;

	private JdbcDataSource database() {
		var source = new JdbcDataSource();
		source.setURL("jdbc:h2:mem:" + UUID.randomUUID());
		source.setUser("sa");
		return source;
	}

	@Test
	void lowerExistingTargetPrecisionIsRejectedBeforeAnyRowWrite() throws Exception {
		var source = database();
		var target = database();
		try (var from = source.getConnection();
				var to = target.getConnection();
				var statement = from.createStatement();
				var destination = to.createStatement()) {
			statement.execute("CREATE TABLE ITEMS(ID INT PRIMARY KEY,D INTERVAL DAY(9) TO SECOND(9))");
			statement.execute("INSERT INTO ITEMS VALUES(1,INTERVAL '0 00:00:00.000000001' DAY TO SECOND(9))");
			destination.execute("CREATE TABLE ITEMS(ID INT PRIMARY KEY,D INTERVAL DAY(9) TO SECOND(6))");
			var job = Files.writeString(directory.resolve("job.yaml"),
					"formatVersion: 1\nsource: from\ntarget: to\nsourceSchema: PUBLIC\ntargetSchema: PUBLIC\n"
							+ "tables: [ITEMS]\nschemaMode: REQUIRE_EXISTING\ndataMode: FILE\nverify: false\n"
							+ "files: {directory: packages, format: CSV, nullValue: __NULL__}\n");
			var saved = new TransferFileExportService().export(job, Map.of("from", source));
			var failure = assertThrows(IllegalArgumentException.class, () -> new TransferFileImportService()
					.review(saved.file(), saved.fingerprint(), Map.of("to", target)));
			assertTrue(failure.getMessage().contains("INTERVAL_VALUE_NOT_REPRESENTABLE"), failure::getMessage);
			try (var rows = destination.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
				assertTrue(rows.next());
				assertEquals(0, rows.getInt(1));
			}
		}
	}

	@Test
	void calendarDayConversionIsReviewedAuditedAndVerifiedAsDestinationValue() throws Exception {
		var source = database();
		var target = database();
		try (var from = source.getConnection();
				var to = target.getConnection();
				var statement = from.createStatement()) {
			statement.execute("CREATE TABLE ITEMS(ID INT PRIMARY KEY,D INTERVAL DAY(9) TO SECOND(9))");
			statement.execute("INSERT INTO ITEMS VALUES(1,INTERVAL '1' DAY)");
			var job = Files.writeString(directory.resolve("job.yaml"),
					"formatVersion: 1\nsource: from\ntarget: to\nsourceSchema: PUBLIC\ntargetSchema: PUBLIC\n"
							+ "tables: [ITEMS]\nschemaMode: CREATE_MISSING\ndataMode: FILE\n"
							+ "files: {directory: packages, format: CSV, nullValue: __NULL__}\n");
			var saved = new TransferFileExportService().export(job, Map.of("from", source));
			var member = saved.manifest().tables().getFirst();
			var file = saved.file().getParent().resolve(member.file());
			String text = Files.readString(file);
			assertTrue(text.contains("PT86400S"));
			Files.writeString(file, text.replace("PT86400S", "P1DT-1H"));
			var changed = new TransferFilePackageIO.TableFile(member.taskId(), member.catalog(), member.schema(),
					member.table(), member.columns(), member.file(), member.rows(), Files.size(file),
					com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile.sha256(file, null,
							"maxDataBytes", "data"));
			Files.writeString(saved.file(),
					new com.sqlapp.util.JsonConverter()
							.toJsonString(new TransferFilePackageIO.Manifest(1, saved.manifest().jobFingerprint(),
									saved.manifest().schemaFingerprint(), saved.manifest().files(), List.of(changed))));
			var updated = new TransferFilePackageIO().read(saved.file(), null);
			var service = new TransferFileImportService();
			service.validatePackage(updated.file(), updated.fingerprint()); // Lossless file value is valid.
			var review = service.review(updated.file(), updated.fingerprint(), Map.of("to", target));
			assertTrue(review.schemaPreparation().warnings().stream().anyMatch(
					warning -> warning.contains("CALENDAR_DAYS_AS_24_HOURS") && warning.contains("1 row(s)")));
			var outcome = service.execute(review, Map.of("to", target), directory);
			assertEquals(TransferJobExecutionService.Phase.COMPLETE, outcome.phase());
			assertTrue(outcome.verification().values().stream().allMatch(result -> result.isMatch()));
			assertTrue(Files.readString(outcome.auditFile()).contains("CALENDAR_DAYS_AS_24_HOURS"));
			try (var result = to.createStatement(); var rows = result.executeQuery("SELECT D FROM ITEMS")) {
				assertTrue(rows.next());
				var handler = DialectResolver.getInstance().getDialect(to).getDbDataTypes()
						.getDbTypeStrict(com.sqlapp.data.db.datatype.DataType.INTERVAL_DAY_TO_SECOND)
						.getJdbcTypeHandler();
				assertEquals(IntervalValue.parse("PT82800S"), handler.getObject(rows, 1));
			}
		}
	}

	@ParameterizedTest
	@EnumSource(FileFormat.class)
	void exportsPreviewsCreatesTargetImportsAndVerifiesWithNineDigitPrecision(FileFormat format) throws Exception {
		var source = database();
		var target = database();
		try (var from = source.getConnection();
				var to = target.getConnection();
				var statement = from.createStatement()) {
			statement.execute(
					"CREATE TABLE ITEMS(ID INT PRIMARY KEY,Y INTERVAL YEAR(9) TO MONTH,D INTERVAL DAY(9) TO SECOND(9))");
			statement.execute("INSERT INTO ITEMS VALUES(1, INTERVAL '-999999999-11' YEAR(9) TO MONTH,"
					+ "INTERVAL '-999999999 23:59:59.123456789' DAY(9) TO SECOND(9))");
			statement.execute("INSERT INTO ITEMS VALUES(2,NULL,INTERVAL '-0 00:00:00.000000001' DAY TO SECOND(9))");
			statement.execute("INSERT INTO ITEMS VALUES(3,INTERVAL '0-0' YEAR TO MONTH,NULL)");
			var job = Files.writeString(directory.resolve("job.yaml"),
					"formatVersion: 1\nsource: from\ntarget: to\nsourceSchema: PUBLIC\ntargetSchema: PUBLIC\n"
							+ "tables: [ITEMS]\nschemaMode: CREATE_MISSING\ndataMode: FILE\nchunkSize: 1\n"
							+ "files: {directory: packages, format: " + format
							+ (format == FileFormat.JSONL ? "" : ", nullValue: __NULL__") + "}\n");
			var exported = new TransferFileExportService().export(job, Map.of("from", source));
			var member = exported.manifest().tables().getFirst();
			var text = Files.readString(exported.file().getParent().resolve(member.file()));
			assertTrue(text.contains("P-11999999999M"));
			assertTrue(text.contains("PT-86399999999999.123456789S"));
			assertTrue(text.contains("PT-0.000000001S"));
			var service = new TransferFileImportService();
			service.validatePackage(exported.file(), exported.fingerprint());
			var preview = service.preview(exported.file(), exported.fingerprint(), member.taskId());
			assertEquals("\"P-11999999999M\"", preview.rows().getFirst().get(1).text());
			assertTrue(preview.rows().get(1).get(1).sqlNull());
			var review = service.review(exported.file(), exported.fingerprint(), Map.of("to", target));
			var outcome = service.execute(review, Map.of("to", target), directory);
			assertEquals(TransferJobExecutionService.Phase.COMPLETE, outcome.phase());
			assertTrue(outcome.verification().values().stream().allMatch(result -> result.isMatch()));
			var dialect = DialectResolver.getInstance().getDialect(to);
			try (var result = to.createStatement(); var rows = result.executeQuery("SELECT * FROM ITEMS ORDER BY ID")) {
				var year = dialect.getDbDataTypes()
						.getDbTypeStrict(com.sqlapp.data.db.datatype.DataType.INTERVAL_YEAR_TO_MONTH)
						.getJdbcTypeHandler();
				var day = dialect.getDbDataTypes()
						.getDbTypeStrict(com.sqlapp.data.db.datatype.DataType.INTERVAL_DAY_TO_SECOND)
						.getJdbcTypeHandler();
				assertTrue(rows.next());
				assertEquals(IntervalValue.parse("P-11999999999M"), year.getObject(rows, "Y"));
				assertEquals(IntervalValue.parse("PT-86399999999999.123456789S"), day.getObject(rows, "D"));
				assertTrue(rows.next());
				assertNull(year.getObject(rows, "Y"));
				assertEquals(IntervalValue.parse("PT-0.000000001S"), day.getObject(rows, "D"));
				assertTrue(rows.next());
				assertEquals(IntervalValue.ZERO, year.getObject(rows, "Y"));
				assertNull(day.getObject(rows, "D"));
			}
		}
	}
}
