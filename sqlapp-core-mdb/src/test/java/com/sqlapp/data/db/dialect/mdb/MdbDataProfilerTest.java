/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.schemas.migration.assessment.MigrationDataProfile.*;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Severity;
import io.github.spannm.jackcess.*;

class MdbDataProfilerTest {
	@Test
	void handlesUnsignedBytesLargeIntegersGuidsBooleansAndExtendedDates() throws Exception {
		final Path file = directory.resolve("native.accdb");
		try (var database = DatabaseBuilder.create(Database.FileFormat.V2019, file.toFile())) {
			new TableBuilder("Native").addColumn(new ColumnBuilder("Byte", DataType.BYTE))
					.addColumn(new ColumnBuilder("Big", DataType.BIG_INT))
					.addColumn(new ColumnBuilder("Guid", DataType.GUID))
					.addColumn(new ColumnBuilder("Flag", DataType.BOOLEAN))
					.addColumn(new ColumnBuilder("Extended", DataType.EXT_DATE_TIME)).toTable(database).addRow(255,
							Long.MAX_VALUE, "{12345678-1234-1234-1234-1234567890AB}", true,
							LocalDateTime.of(2026, 1, 2, 3, 4, 5, 123456700));
		}
		final var table = provider.load(file, true).dataProfile().tables().getFirst();
		assertEquals(0, new BigDecimal("255").compareTo(column(table, "Byte").numeric().maximum()));
		assertEquals(0, BigDecimal.valueOf(Long.MAX_VALUE).compareTo(column(table, "Big").numeric().maximum()));
		assertNotNull(column(table, "Guid").text());
		assertEquals(Coverage.SCANNED, column(table, "Flag").coverage());
		assertEquals(7, column(table, "Extended").dateTime().maximumFractionalDigits());
	}

	@TempDir
	Path directory;
	private final MdbMigrationAssessmentSourceProvider provider = new MdbMigrationAssessmentSourceProvider();

	private ColumnProfile column(TableProfile table, String name) {
		return table.columns().stream().filter(c -> c.column().name().equals(name)).findFirst().orElseThrow();
	}

	@Test
	void profilesLocalRowsWithoutSamplesOrFollowingLinksAndPreservesInput() throws Exception {
		final Path file = directory.resolve("profile.accdb");
		try (var database = DatabaseBuilder.create(Database.FileFormat.V2010, file.toFile())) {
			final var table = new TableBuilder("顧客")
					.addColumn(new ColumnBuilder("Text", DataType.TEXT).withProperty(PropertyMap.ALLOW_ZERO_LEN_PROP,
							true))
					.addColumn(new ColumnBuilder("Amount", DataType.NUMERIC).withPrecision(12).withScale(4))
					.addColumn(new ColumnBuilder("Date", DataType.SHORT_DATE_TIME))
					.addColumn(new ColumnBuilder("AllNull", DataType.TEXT))
					.addColumn(new ColumnBuilder("Payload", DataType.OLE)).toTable(database);
			table.addRow(null, null, null, null, new byte[] { 1, 2, 3 });
			table.addRow("", new BigDecimal("-12.3400"), LocalDateTime.of(1600, 1, 2, 0, 0), null, null);
			table.addRow("あ😀", new BigDecimal("123.4500"), LocalDateTime.of(2026, 1, 2, 3, 4, 5, 123000000), null,
					null);
			// Existing invalid rows can survive a later metadata change; the profiler must
			// report them.
			table.getColumn("Text").getProperties().put(PropertyMap.REQUIRED_PROP, true);
			table.getColumn("Text").getProperties().save();
			database.createLinkedTable("External", directory.resolve("missing.accdb").toString(), "T");
		}
		final byte[] before = Files.readAllBytes(file);
		final var metadata = provider.load(file);
		assertFalse(metadata.dataScanned());
		assertNull(metadata.dataProfile());
		assertTrue(metadata.assessment().findings().stream()
				.anyMatch(finding -> finding.ruleId().equals("access.allow-zero-length")
						&& finding.object().name().equals("Text")));
		assertEquals(1, metadata.assessment().inventory().stream()
				.filter(item -> item.type().equals("accessAllowZeroLengthColumns")).findFirst().orElseThrow().count());
		final var result = provider.load(file, true);
		assertTrue(result.dataScanned());
		assertFalse(result.relationshipsCollected());
		assertEquals(1, result.dataProfile().tables().size());
		final var table = result.dataProfile().tables().getFirst();
		assertEquals(3, table.rowCount());
		final var text = column(table, "Text");
		assertEquals(1L, text.nullCount());
		assertEquals(1, text.text().emptyCount());
		assertEquals(2L, text.text().maximumCodePoints());
		assertEquals(3L, text.text().maximumUtf16Units());
		assertEquals(7L, text.text().maximumUtf8Bytes());
		final var number = column(table, "Amount").numeric();
		assertEquals(0, new BigDecimal("-12.34").compareTo(number.minimum()));
		assertEquals(0, new BigDecimal("123.45").compareTo(number.maximum()));
		assertEquals(3, number.maximumIntegerDigits());
		assertEquals(2, number.maximumScale());
		assertTrue(column(table, "Date").dateTime().minimum().startsWith("1600-01-02"));
		assertTrue(column(table, "Date").dateTime().maximum().startsWith("2026-01-02T03:04:05.123"));
		assertEquals(3L, column(table, "AllNull").nullCount());
		assertNull(column(table, "AllNull").text().maximumCodePoints());
		assertEquals(Coverage.UNSUPPORTED, column(table, "Payload").coverage());
		assertNull(column(table, "Payload").nullCount());
		assertTrue(result.assessment().findings().stream()
				.anyMatch(f -> f.ruleId().equals("access.data.required-null") && f.severity() == Severity.BLOCKER));
		assertFalse(
				result.assessment().findings().stream().anyMatch(f -> f.ruleId().equals("access.data-not-scanned")));
		assertTrue(
				result.assessment().findings().stream().anyMatch(f -> f.ruleId().equals("access.data-scan-coverage")));
		assertTrue(result.schemas().getFirst().getTables().getFirst().getRows().isEmpty());
		assertFalse(result.dataProfile().toString().contains("あ😀"));
		assertArrayEquals(before, Files.readAllBytes(file));
		assertEquals(result.dataProfile(), provider.load(file, true).dataProfile());
	}

	@Test
	void countsRowsEvenWhenEveryColumnIsExcludedAndDistinguishesEmptyTables() throws Exception {
		final Path file = directory.resolve("binary.mdb");
		try (var database = DatabaseBuilder.create(Database.FileFormat.V2000, file.toFile())) {
			final var table = new TableBuilder("BinaryOnly").addColumn(new ColumnBuilder("B", DataType.OLE))
					.toTable(database);
			table.addRow(new byte[] { 1 });
			table.addRow(new Object[] { null });
			new TableBuilder("Empty").addColumn(new ColumnBuilder("N", DataType.LONG)).toTable(database);
		}
		final var profile = provider.load(file, true).dataProfile();
		final var binary = profile.tables().stream().filter(t -> t.table().name().equals("BinaryOnly")).findFirst()
				.orElseThrow();
		assertEquals(2, binary.rowCount());
		assertEquals(Coverage.UNSUPPORTED, binary.columns().getFirst().coverage());
		final var empty = profile.tables().stream().filter(t -> t.table().name().equals("Empty")).findFirst()
				.orElseThrow();
		assertEquals(0, empty.rowCount());
		assertEquals(0L, empty.columns().getFirst().nullCount());
		assertNull(empty.columns().getFirst().numeric().minimum());
	}

	@Test
	void excludesNonFiniteValuesFromExtremaAndReportsTheirCount() throws Exception {
		final Path file = directory.resolve("numbers.accdb");
		try (var database = DatabaseBuilder.create(Database.FileFormat.V2010, file.toFile())) {
			final var table = new TableBuilder("Numbers").addColumn(new ColumnBuilder("N", DataType.DOUBLE))
					.addColumn(new ColumnBuilder("Zero", DataType.NUMERIC).withPrecision(4).withScale(2))
					.toTable(database);
			table.addRow(Double.NaN, BigDecimal.ZERO);
			table.addRow(Double.POSITIVE_INFINITY, BigDecimal.ZERO);
			table.addRow(-1.25, BigDecimal.ZERO);
		}
		final var source = provider.load(file, true);
		final var table = source.dataProfile().tables().getFirst();
		final var number = column(table, "N").numeric();
		assertEquals(2, number.nonFiniteCount());
		assertEquals(0, new BigDecimal("-1.25").compareTo(number.minimum()));
		assertEquals(number.minimum(), number.maximum());
		assertEquals(0, column(table, "Zero").numeric().maximumIntegerDigits());
		assertTrue(source.assessment().findings().stream().anyMatch(f -> f.ruleId().equals("access.data.non-finite")));
	}
}
