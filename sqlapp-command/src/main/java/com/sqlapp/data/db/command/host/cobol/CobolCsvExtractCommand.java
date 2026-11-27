/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.cobol;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.host.cobol.CobolCsvExtractConfiguration.RecordFormat;
import com.sqlapp.data.db.command.host.cobol.CobolCsvExtractConfiguration.RecordType;
import com.sqlapp.data.db.command.host.cobol.CobolCsvExtractConfiguration.UnknownRecord;
import com.sqlapp.data.db.command.migration.legacy.LegacyMigrationContractIO;
import com.sqlapp.data.db.command.migration.legacy.LegacyMigrationMappingIO;
import com.sqlapp.data.db.command.migration.legacy.LegacyMigrationMappingValidator;
import com.sqlapp.data.db.command.properties.OutputDirectoryProperty;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.DataSet;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.Field;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;
import com.sqlapp.util.eval.mvel.MvelCompiledEvaluator;
import com.sqlapp.util.eval.mvel.SqlappParserContextFactory;

import lombok.Getter;
import lombok.Setter;

/**
 * Reads a host data set (EBCDIC, zoned and packed decimal, binary) with the
 * storage layout recorded by {@link CobolSchemaImportCommand} and writes the
 * CSV files of a legacy migration contract, as a host extraction program would.
 * <p>
 * Records are framed by {@link RecordFormat}. With several record layouts in
 * one data set, each record's type is identified by the configured byte ranges,
 * MVEL expressions, or a {@link CobolRecordTypeResolver}. A record imported
 * with a parent record (header and detail) takes its parent key from the most
 * recent parent record and is numbered from 1 after each parent.
 * </p>
 */
@Getter
@Setter
public class CobolCsvExtractCommand extends AbstractCommand implements OutputDirectoryProperty {

	private File contractFile;

	/** Migration mapping; defaults to the contract's mappingFile. */
	private File mappingFile;

	private File configurationFile;

	/** Host data set transferred in binary. */
	private File dataFile;

	private File outputDirectory = new File("./");

	/** Records read per record type, available after the run. */
	private final Map<String, Long> recordCounts = new LinkedHashMap<>();

	/** CSV rows written per data set id, available after the run. */
	private final Map<String, Long> rowCounts = new LinkedHashMap<>();

	@Override
	protected void doRun() {
		for (Object[] property : new Object[][] { { contractFile, "contractFile" },
				{ configurationFile, "configurationFile" }, { dataFile, "dataFile" } }) {
			if (!(property[0] instanceof File file) || !file.isFile()) {
				throw new CommandException(property[1] + " does not exist or is not a file: " + property[0]);
			}
		}
		execute(() -> {
			CobolCsvExtractConfiguration configuration = new YamlConverter().fromJsonString(configurationFile,
					CobolCsvExtractConfiguration.class);
			if (configuration.getFormatVersion() != 1) {
				throw new CommandException("Unsupported formatVersion: " + configuration.getFormatVersion());
			}
			LegacyMigrationContract contract = new LegacyMigrationContractIO().read(contractFile);
			File mapping = mappingFile(contract);
			Model model = new Model(new LegacyMigrationMappingIO().read(mapping), contract, configuration,
					HostTextDecoder.of(configuration.getEncoding(),
							configurationFile.getAbsoluteFile().getParentFile()));
			if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
				throw new CommandException("Failed to create output directory: " + outputDirectory);
			}
			model.extract();
			recordCounts.forEach((type, count) -> info("Records ", type, ": ", count));
			rowCounts.forEach((id, count) -> info("Rows ", id, ": ", count));
		});
	}

	private File mappingFile(LegacyMigrationContract contract) {
		File file = mappingFile;
		if (file == null) {
			if (contract.getMappingFile() == null) {
				throw new CommandException("The contract has no mappingFile; set mappingFile.");
			}
			file = new File(contract.getMappingFile());
			if (!file.isAbsolute()) {
				file = new File(contractFile.getAbsoluteFile().getParentFile(), contract.getMappingFile());
			}
		}
		if (!file.isFile()) {
			throw new CommandException("Migration mapping was not found: " + file);
		}
		if (contract.getMappingFingerprint() != null
				&& !contract.getMappingFingerprint().equals(new LegacyMigrationMappingValidator().fingerprint(file))) {
			throw new CommandException("The migration mapping changed after the contract was generated; regenerate the"
					+ " contract: " + file);
		}
		return file;
	}

	/** Storage layout of one mapped table. */
	private static final class TableModel {
		String path;
		String recordName;
		TableModel parent;
		/** OCCURS table: offset of the array in the parent element. */
		int occursOffset;
		int elementLength;
		Integer maximum;
		int minimum;
		String dependingOn;
		/** Record whose parent is another record (header and detail). */
		boolean recordSequence;
		String parentRecord;
		int recordLength;
		boolean variableLength;
		final List<TableModel> arrays = new ArrayList<>();
		final List<DataSetWriter> writers = new ArrayList<>();

		boolean isArray() {
			return parent != null && !recordSequence;
		}
	}

	private record FieldModel(TableModel table, CobolFieldDecoder.Layout layout) {
	}

	/** Value of one CSV column. */
	private record Column(String path, FieldModel field, TableModel indexOf) {
	}

	private final class DataSetWriter {
		final DataSet dataSet;
		final List<Column> columns = new ArrayList<>();
		Writer writer;
		long rows;

		DataSetWriter(DataSet dataSet) {
			this.dataSet = dataSet;
		}
	}

	/** Extraction state; one per run. */
	private final class Model {
		private final LegacyMigrationContract contract;
		private final CobolCsvExtractConfiguration configuration;
		private final HostTextDecoder text;
		private final CobolFieldDecoder decoder;
		private final Map<String, TableModel> tables = new LinkedHashMap<>();
		private final Map<String, FieldModel> fields = new HashMap<>();
		/** Record name (upper case) to its root table. */
		private final Map<String, TableModel> records = new LinkedHashMap<>();
		private final List<DataSetWriter> writers = new ArrayList<>();
		private final List<TypeRule> rules = new ArrayList<>();
		private final CobolRecordTypeResolver resolver;
		private final Map<String, byte[]> lastRecords = new HashMap<>();
		private final Map<String, Integer> lastRecordLengths = new HashMap<>();
		private final Map<String, Integer> sequences = new HashMap<>();
		private final Map<TableModel, Integer> indexes = new HashMap<>();
		private long recordNumber;
		private String currentRecord;

		private record TypeRule(RecordType type, MvelCompiledEvaluator expression) {
		}

		/** Identified record type; {@code skip} records are not written. */
		private record Resolved(String name, boolean skip) {
		}

		/** Control (trailer) records by upper-case name; their layout has no table. */
		private final Map<String, TableModel> controls = new LinkedHashMap<>();
		private final List<TrailerState> trailers = new ArrayList<>();

		/** Records counted and totals summed since the previous trailer of a check. */
		private final class TrailerState {
			final CobolCsvExtractConfiguration.TrailerCheck check;
			final java.util.Set<String> countOf = new java.util.HashSet<>();
			final List<BigDecimal> totals = new ArrayList<>();
			long count;
			boolean pending;
			boolean seen;

			TrailerState(CobolCsvExtractConfiguration.TrailerCheck check) {
				this.check = check;
				check.getCountOf().forEach(name -> countOf.add(name.toUpperCase(Locale.ROOT)));
				check.getSums().forEach(sum -> totals.add(BigDecimal.ZERO));
			}

			boolean counts(String record) {
				return countOf.isEmpty() ? !controls.containsKey(record) : countOf.contains(record);
			}
		}

		Model(LegacyMigrationMapping mapping, LegacyMigrationContract contract,
				CobolCsvExtractConfiguration configuration, HostTextDecoder text) {
			this.contract = contract;
			this.configuration = configuration;
			this.text = text;
			this.decoder = new CobolFieldDecoder(text, configuration);
			readMapping(mapping);
			readControlRecords(mapping);
			for (DataSet dataSet : contract.getDataSets()) {
				writers.add(writer(dataSet));
			}
			for (RecordType type : configuration.getRecordTypes()) {
				if (type.getRecord() == null) {
					throw new CommandException("recordTypes[].record is required.");
				}
				if (!type.isSkip() && !records.containsKey(type.getRecord().toUpperCase(Locale.ROOT))) {
					throw new CommandException("recordTypes names a record without data sets: " + type.getRecord()
							+ "; known records: " + records.keySet() + " (use skip: true for records not loaded)");
				}
				if (type.getWhen() == null && type.getExpression() == null && !type.isSkip()) {
					throw new CommandException("recordTypes " + type.getRecord() + " needs when or expression.");
				}
				MvelCompiledEvaluator expression = null;
				if (type.getExpression() != null) {
					try {
						expression = new MvelCompiledEvaluator(type.getExpression(),
								SqlappParserContextFactory.getInstance().getParserContext());
					} catch (RuntimeException e) {
						throw new CommandException(
								"Invalid recordTypes expression for " + type.getRecord() + ": " + e.getMessage(), e);
					}
				}
				rules.add(new TypeRule(type, expression));
			}
			for (var check : configuration.getTrailerChecks()) {
				trailers.add(trailerState(check));
			}
			this.resolver = resolver(configuration.getRecordTypeResolver());
			if (records.size() > 1 && rules.isEmpty() && resolver == null) {
				throw new CommandException("The contract has several records " + records.keySet()
						+ "; configure recordTypes or recordTypeResolver.");
			}
		}

		private CobolRecordTypeResolver resolver(String className) {
			if (className == null || className.isBlank()) {
				return null;
			}
			try {
				Class<?> type = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
				return (CobolRecordTypeResolver) type.getDeclaredConstructor().newInstance();
			} catch (ReflectiveOperationException | ClassCastException e) {
				throw new CommandException("recordTypeResolver must be a CobolRecordTypeResolver with a public"
						+ " no-argument constructor: " + className, e);
			}
		}

		private void readMapping(LegacyMigrationMapping mapping) {
			Map<String, TableModel> byId = new HashMap<>();
			for (var table : mapping.getTables()) {
				TableModel model = new TableModel();
				model.path = table.getSource().getPath();
				if (model.path == null) {
					throw new CommandException("Mapping table has no source path: " + table.getId());
				}
				Map<?, ?> occurrence = map(table.getDetails().get("occurrence"));
				Map<?, ?> storage = map(table.getDetails().get("storage"));
				if (Boolean.TRUE.equals(occurrence.get("recordSequence"))) {
					model.recordSequence = true;
					model.parentRecord = string(occurrence.get("parentRecord")).toUpperCase(Locale.ROOT);
				} else if (!occurrence.isEmpty()) {
					if (!(occurrence.get("offset") instanceof Number offset)
							|| !(occurrence.get("elementLength") instanceof Number elementLength)) {
						throw new CommandException("Mapping table has no OCCURS storage layout; import it with"
								+ " cobolSchemaImport: " + model.path);
					}
					model.occursOffset = offset.intValue();
					model.elementLength = elementLength.intValue();
					model.maximum = occurrence.get("maximum") instanceof Number maximum ? maximum.intValue() : null;
					model.minimum = occurrence.get("minimum") instanceof Number minimum ? minimum.intValue() : 0;
					model.dependingOn = string(occurrence.get("dependingOn"));
				}
				if (storage.get("recordLength") instanceof Number length) {
					model.recordLength = length.intValue();
					model.variableLength = Boolean.TRUE.equals(storage.get("variableLength"));
				}
				byId.put(table.getId(), model);
				tables.put(model.path.toUpperCase(Locale.ROOT), model);
			}
			for (var table : mapping.getTables()) {
				TableModel model = byId.get(table.getId());
				if (!occurrenceOf(table).isEmpty() && !model.recordSequence) {
					if (table.getParent() == null || byId.get(table.getParent().getMappingId()) == null) {
						throw new CommandException("OCCURS table has no parent table: " + model.path);
					}
					model.parent = byId.get(table.getParent().getMappingId());
					model.parent.arrays.add(model);
				}
			}
			for (TableModel model : byId.values()) {
				TableModel root = model;
				while (root.isArray()) {
					root = root.parent;
				}
				model.recordName = root.path.toUpperCase(Locale.ROOT);
				if (!model.isArray()) {
					if (model.recordLength == 0) {
						throw new CommandException("Mapping table has no storage layout; import it with"
								+ " cobolSchemaImport: " + model.path);
					}
					records.put(model.recordName, model);
				}
			}
			for (var table : mapping.getTables()) {
				TableModel model = byId.get(table.getId());
				for (var column : table.getColumns()) {
					Map<?, ?> storage = map(column.getConversion().get("storage"));
					if (column.getSourcePath() == null || storage.isEmpty()) {
						continue;
					}
					try {
						fields.put(column.getSourcePath().toUpperCase(Locale.ROOT),
								new FieldModel(model, CobolFieldDecoder.Layout.of(storage, column.getConversion())));
					} catch (IllegalArgumentException e) {
						throw new CommandException(
								"Invalid storage of " + column.getSourcePath() + ": " + e.getMessage());
					}
				}
			}
			for (TableModel model : records.values()) {
				if (model.recordSequence && !records.containsKey(model.parentRecord)) {
					throw new CommandException("Parent record is not mapped: " + model.parentRecord);
				}
			}
		}

		private DataSetWriter writer(DataSet dataSet) {
			TableModel table = tables.get(string(dataSet.getSourcePath()).toUpperCase(Locale.ROOT));
			if (table == null) {
				throw new CommandException("Data set source path is not in the mapping: " + dataSet.getId() + " ("
						+ dataSet.getSourcePath() + ")");
			}
			DataSetWriter writer = new DataSetWriter(dataSet);
			for (Field field : dataSet.getFields().stream().filter(Field::isExtracted).toList()) {
				if (!field.getIndexedSources().isEmpty()) {
					throw new CommandException("Numbered occurrence columns are not supported by COBOL extraction: "
							+ dataSet.getId() + "." + field.getStagingColumn());
				}
				String path = string(field.getSourcePath());
				if (field.isOccurrenceIndex() || path.endsWith(".$index")) {
					TableModel indexed = tables
							.get(path.substring(0, path.length() - ".$index".length()).toUpperCase(Locale.ROOT));
					if (indexed == null || !indexed.isArray() && !indexed.recordSequence) {
						throw new CommandException("Occurrence field does not name an OCCURS table or a record with a"
								+ " parent: " + dataSet.getId() + "." + field.getStagingColumn() + " (" + path + ")");
					}
					writer.columns.add(new Column(path, null, indexed));
					continue;
				}
				FieldModel model = fields.get(path.toUpperCase(Locale.ROOT));
				if (model == null) {
					throw new CommandException("Field has no storage layout in the mapping: " + dataSet.getId() + "."
							+ field.getStagingColumn() + " (" + path + ")");
				}
				writer.columns.add(new Column(path, model, null));
			}
			table.writers.add(writer);
			return writer;
		}

		void extract() throws IOException {
			Charset csvCharset = Charset.forName(contract.getCsv().getEncoding());
			try {
				for (DataSetWriter writer : writers) {
					File file = new File(outputDirectory, writer.dataSet.getFileName());
					writer.writer = new BufferedWriter(
							new OutputStreamWriter(Files.newOutputStream(file.toPath()), csvCharset), 1 << 16);
					if (contract.getCsv().isHeader()) {
						List<String> header = writer.dataSet.getFields().stream().filter(Field::isExtracted)
								.map(Field::getStagingColumn).toList();
						writeRecord(writer, new ArrayList<>(header));
					}
				}
				try (InputStream input = new BufferedInputStream(Files.newInputStream(dataFile.toPath()), 1 << 16)) {
					read(new RecordReader(input));
				}
			} finally {
				IOException failure = null;
				for (DataSetWriter writer : writers) {
					if (writer.writer != null) {
						try {
							writer.writer.close();
						} catch (IOException e) {
							failure = e;
						}
					}
				}
				if (failure != null) {
					throw failure;
				}
			}
			for (DataSetWriter writer : writers) {
				rowCounts.put(writer.dataSet.getId(), writer.rows);
			}
		}

		private void read(RecordReader reader) throws IOException {
			while (true) {
				int length = reader.next();
				if (length < 0) {
					break;
				}
				recordNumber++;
				currentRecord = null;
				byte[] bytes = reader.bytes();
				Resolved type = type(bytes, length);
				if (type == null) {
					recordCounts.merge("(unknown, skipped)", 1L, Long::sum);
					continue;
				}
				String name = type.name().toUpperCase(Locale.ROOT);
				if (type.skip()) {
					recordCounts.merge(type.name() + " (skipped)", 1L, Long::sum);
					TableModel control = controls.get(name);
					if (control != null) {
						currentRecord = control.recordName;
						lastRecords.put(name, java.util.Arrays.copyOf(bytes, length));
						lastRecordLengths.put(name, length);
					}
					trailers(name);
					continue;
				}
				recordCounts.merge(type.name(), 1L, Long::sum);
				TableModel root = records.get(name);
				if (root == null) {
					continue;
				}
				record(root, bytes, length);
				trailers(name);
			}
			if (recordNumber == 0) {
				info("The data set has no records: ", dataFile);
			}
			currentRecord = null;
			for (TrailerState trailer : trailers) {
				if (!trailer.check.isRequired()) {
					continue;
				}
				if (!trailer.seen) {
					throw failure("trailer " + trailer.check.getRecord() + " was not found");
				}
				if (trailer.pending) {
					throw failure(trailer.count + " counted records after the last " + trailer.check.getRecord()
							+ " are not covered by a trailer");
				}
			}
		}

		/** Counts and sums the record, then checks it if it is a trailer. */
		private void trailers(String record) {
			for (TrailerState trailer : trailers) {
				if (trailer.counts(record)) {
					trailer.count++;
					trailer.pending = true;
				}
				for (int i = 0; i < trailer.check.getSums().size(); i++) {
					String of = trailer.check.getSums().get(i).getOf();
					FieldModel field = fields.get(of.toUpperCase(Locale.ROOT));
					if (field.table().recordName.equals(record) && records.containsKey(record)) {
						if (decode(of, field) instanceof BigDecimal value) {
							trailer.totals.set(i, trailer.totals.get(i).add(value));
						}
						trailer.pending = true;
					}
				}
				if (trailer.check.getRecord().equalsIgnoreCase(record)) {
					check(trailer);
				}
			}
		}

		private void check(TrailerState trailer) {
			var check = trailer.check;
			if (check.getCount() != null) {
				Object expected = decode(check.getCount(), fields.get(check.getCount().toUpperCase(Locale.ROOT)));
				if (!(expected instanceof BigDecimal number)
						|| number.compareTo(BigDecimal.valueOf(trailer.count)) != 0) {
					throw failure("trailer check failed: " + check.getCount() + " = " + expected + ", but "
							+ trailer.count + " records were read since the previous " + check.getRecord());
				}
			}
			for (int i = 0; i < check.getSums().size(); i++) {
				var sum = check.getSums().get(i);
				Object expected = decode(sum.getField(), fields.get(sum.getField().toUpperCase(Locale.ROOT)));
				BigDecimal actual = trailer.totals.get(i);
				if (!(expected instanceof BigDecimal number) || number.compareTo(actual) != 0) {
					throw failure("trailer check failed: " + sum.getField() + " = " + expected + ", but the sum of "
							+ sum.getOf() + " is " + actual.toPlainString());
				}
				trailer.totals.set(i, BigDecimal.ZERO);
			}
			trailer.count = 0;
			trailer.pending = false;
			trailer.seen = true;
		}

		private TrailerState trailerState(CobolCsvExtractConfiguration.TrailerCheck check) {
			String record = check.getRecord() == null ? null : check.getRecord().toUpperCase(Locale.ROOT);
			if (record == null || !controls.containsKey(record)) {
				throw new CommandException("trailerChecks.record must be a control record (control: true in"
						+ " cobolSchemaImport): " + check.getRecord() + "; control records: " + controls.keySet());
			}
			if (configuration.getRecordTypes().stream()
					.noneMatch(type -> type.isSkip() && check.getRecord().equalsIgnoreCase(type.getRecord()))) {
				throw new CommandException(
						"trailerChecks needs a recordTypes entry with skip: true for " + check.getRecord());
			}
			if (check.getCount() == null && check.getSums().isEmpty()) {
				throw new CommandException("trailerChecks " + check.getRecord() + " needs count or sums.");
			}
			if (check.getCount() != null) {
				controlField(check.getCount(), record, "count");
			}
			for (String name : check.getCountOf()) {
				if (!records.containsKey(name.toUpperCase(Locale.ROOT))
						&& !controls.containsKey(name.toUpperCase(Locale.ROOT))) {
					throw new CommandException("trailerChecks countOf names an unknown record: " + name);
				}
			}
			for (var sum : check.getSums()) {
				controlField(sum.getField(), record, "sums.field");
				FieldModel of = sum.getOf() == null ? null : fields.get(sum.getOf().toUpperCase(Locale.ROOT));
				if (of == null || !records.containsKey(of.table().recordName) || of.table().isArray()
						|| !"NUMERIC".equals(of.layout().category())) {
					throw new CommandException("trailerChecks sums.of must be a numeric item of a loaded record,"
							+ " outside OCCURS: " + sum.getOf());
				}
			}
			return new TrailerState(check);
		}

		private void controlField(String path, String record, String name) {
			FieldModel field = fields.get(path.toUpperCase(Locale.ROOT));
			if (field == null || !field.table().recordName.equals(record)
					|| !"NUMERIC".equals(field.layout().category())) {
				throw new CommandException(
						"trailerChecks " + name + " must be a numeric item of " + record + ": " + path);
			}
		}

		private void readControlRecords(LegacyMigrationMapping mapping) {
			for (var entry : map(mapping.getOptions().get("cobolControlRecords")).entrySet()) {
				Map<?, ?> layout = map(entry.getValue());
				TableModel control = new TableModel();
				control.path = string(entry.getKey());
				control.recordName = control.path.toUpperCase(Locale.ROOT);
				control.recordLength = layout.get("recordLength") instanceof Number length ? length.intValue() : 0;
				controls.put(control.recordName, control);
				for (var field : map(layout.get("fields")).entrySet()) {
					Map<?, ?> value = map(field.getValue());
					try {
						fields.put(string(field.getKey()).toUpperCase(Locale.ROOT), new FieldModel(control,
								CobolFieldDecoder.Layout.of(map(value.get("storage")), map(value.get("conversion")))));
					} catch (IllegalArgumentException e) {
						throw new CommandException("Invalid storage of " + field.getKey() + ": " + e.getMessage());
					}
				}
			}
		}

		/** Record type, or null when an unknown record is skipped. */
		private Resolved type(byte[] bytes, int length) {
			if (rules.isEmpty() && resolver == null) {
				return new Resolved(records.keySet().iterator().next(), false);
			}
			CobolRecordView view = new CobolRecordView(bytes, length, text);
			for (TypeRule rule : rules) {
				if (matches(rule, view)) {
					return new Resolved(rule.type().getRecord(), rule.type().isSkip());
				}
			}
			if (resolver != null) {
				String name = resolver.resolve(view, recordNumber);
				if (name != null) {
					if (!records.containsKey(name.toUpperCase(Locale.ROOT))) {
						throw failure("recordTypeResolver returned an unknown record " + name);
					}
					return new Resolved(name, false);
				}
			}
			if (configuration.getUnknownRecord() == UnknownRecord.SKIP) {
				return null;
			}
			throw failure("record type is not identified (first bytes X'"
					+ CobolFieldDecoder.hex(bytes, 0, Math.min(length, 16)) + "')");
		}

		private boolean matches(TypeRule rule, CobolRecordView view) {
			var when = rule.type().getWhen();
			if (when != null) {
				if (when.getOffset() + when.getLength() > view.length()) {
					return false;
				}
				if (when.getHex() != null
						&& !when.getHex().equalsIgnoreCase(view.hex(when.getOffset(), when.getLength()))) {
					return false;
				}
				if (when.getText() != null && !when.getText().equals(view.text(when.getOffset(), when.getLength()))) {
					return false;
				}
				if (when.getHex() == null && when.getText() == null) {
					throw new CommandException("recordTypes " + rule.type().getRecord() + ".when needs text or hex.");
				}
			}
			if (rule.expression() != null) {
				Map<String, Object> variables = new HashMap<>();
				variables.put("record", view);
				variables.put("recordNumber", recordNumber);
				Object result;
				try {
					result = rule.expression().eval(variables);
				} catch (RuntimeException e) {
					throw failure(
							"recordTypes expression of " + rule.type().getRecord() + " failed: " + e.getMessage());
				}
				if (!(result instanceof Boolean matched)) {
					throw failure("recordTypes expression of " + rule.type().getRecord() + " did not return a boolean");
				}
				return matched;
			}
			return true;
		}

		private void record(TableModel root, byte[] bytes, int length) throws IOException {
			currentRecord = root.recordName;
			if (root.recordSequence) {
				if (!lastRecords.containsKey(root.parentRecord)) {
					throw failure(root.path + " record appears before its parent record " + root.parentRecord);
				}
				sequences.merge(root.recordName, 1, Integer::sum);
			}
			// A new parent record restarts the numbering of its child records.
			for (TableModel child : records.values()) {
				if (child.recordSequence && child.parentRecord.equals(root.recordName)) {
					sequences.remove(child.recordName);
				}
			}
			byte[] copy = java.util.Arrays.copyOf(bytes, length);
			lastRecords.put(root.recordName, copy);
			lastRecordLengths.put(root.recordName, length);
			rows(root);
		}

		private void rows(TableModel table) throws IOException {
			for (DataSetWriter writer : table.writers) {
				List<String> values = new ArrayList<>(writer.columns.size());
				for (Column column : writer.columns) {
					values.add(value(column));
				}
				writeRecord(writer, values);
				writer.rows++;
			}
			for (TableModel array : table.arrays) {
				int count = occurrences(array);
				for (int i = 1; i <= count; i++) {
					indexes.put(array, i);
					rows(array);
				}
				indexes.remove(array);
			}
		}

		private int occurrences(TableModel array) {
			if (array.dependingOn == null) {
				return array.maximum;
			}
			FieldModel counter = fields.get(array.dependingOn.toUpperCase(Locale.ROOT));
			if (counter == null) {
				throw new CommandException("OCCURS DEPENDING ON item has no storage layout: " + array.dependingOn);
			}
			Object value = decode(array.dependingOn, counter);
			if (!(value instanceof BigDecimal number)) {
				throw failure(array.dependingOn + " is NULL but counts " + array.path);
			}
			int count;
			try {
				count = number.intValueExact();
			} catch (ArithmeticException e) {
				throw failure(array.dependingOn + " is not an integer: " + number);
			}
			if (count < array.minimum || count > array.maximum) {
				throw failure(array.dependingOn + " = " + count + " is outside OCCURS " + array.minimum + " TO "
						+ array.maximum + " of " + array.path);
			}
			return count;
		}

		private String value(Column column) {
			if (column.indexOf() != null) {
				Integer index = column.indexOf().recordSequence ? sequences.get(column.indexOf().recordName)
						: indexes.get(column.indexOf());
				if (index == null) {
					throw failure("occurrence " + column.path() + " is not available here");
				}
				return Integer.toString(index);
			}
			Object value = decode(column.path(), column.field());
			if (value == null) {
				return null;
			}
			if (value instanceof BigDecimal number) {
				return number.toPlainString();
			}
			return value.toString();
		}

		private Object decode(String path, FieldModel field) {
			String recordName = field.table().recordName;
			byte[] bytes = lastRecords.get(recordName);
			Integer length = lastRecordLengths.get(recordName);
			if (bytes == null) {
				throw failure(path + " belongs to record " + recordName + ", which has not been read");
			}
			int offset = base(field.table(), path) + field.layout().offset();
			if (offset + field.layout().length() > length) {
				throw failure(path + " (bytes " + offset + "-" + (offset + field.layout().length() - 1)
						+ ") is beyond the end of the " + length + "-byte record");
			}
			try {
				return decoder.decode(field.layout(), bytes, offset);
			} catch (CobolFieldDecoder.DecodeException e) {
				throw failure(path + ": " + e.getMessage());
			}
		}

		private int base(TableModel table, String path) {
			if (!table.isArray()) {
				return 0;
			}
			Integer index = indexes.get(table);
			if (index == null) {
				throw failure(path + " is inside OCCURS " + table.path + " outside of its occurrence");
			}
			return base(table.parent, path) + table.occursOffset + (index - 1) * table.elementLength;
		}

		private CommandException failure(String message) {
			return new CommandException("Record " + recordNumber
					+ (currentRecord == null ? "" : " (" + currentRecord + ")") + ": " + message);
		}

		private void writeRecord(DataSetWriter writer, List<String> values) throws IOException {
			var csv = contract.getCsv();
			Writer out = writer.writer;
			for (int i = 0; i < values.size(); i++) {
				if (i > 0) {
					out.write(csv.getDelimiter());
				}
				String value = values.get(i);
				if (value == null) {
					out.write(csv.getNullValue());
					continue;
				}
				boolean quote = !csv.getQuote().isEmpty() && (value.contains(csv.getDelimiter())
						|| value.contains(csv.getQuote()) || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0
						|| value.equals(csv.getNullValue()) && !value.isEmpty());
				if (value.isEmpty() && csv.getNullValue().isEmpty() && !csv.getQuote().isEmpty()) {
					// An empty string must not read back as NULL.
					quote = true;
				}
				if (quote) {
					out.write(csv.getQuote());
					out.write(value.replace(csv.getQuote(), csv.getQuote() + csv.getQuote()));
					out.write(csv.getQuote());
				} else {
					out.write(value);
				}
			}
			out.write(switch (csv.getRecordSeparator()) {
			case CRLF -> "\r\n";
			default -> "\n";
			});
		}

		/** Frames records according to the configured record format. */
		private final class RecordReader {
			private final InputStream input;
			private byte[] buffer = new byte[1 << 15];
			private int blockRemaining;
			private final int fixedLength;

			RecordReader(InputStream input) {
				this.input = input;
				int length = 0;
				if (configuration.getRecordFormat() == RecordFormat.FIXED) {
					if (configuration.getRecordLength() != null) {
						length = configuration.getRecordLength();
					} else if (records.size() == 1) {
						TableModel only = records.values().iterator().next();
						if (only.variableLength) {
							throw new CommandException("recordLength is required for a FIXED data set whose record has"
									+ " OCCURS DEPENDING ON.");
						}
						length = only.recordLength;
					} else {
						throw new CommandException(
								"recordLength is required for a FIXED data set with several" + " record types.");
					}
					if (length <= 0) {
						throw new CommandException("recordLength must be positive: " + length);
					}
				}
				this.fixedLength = length;
			}

			byte[] bytes() {
				return buffer;
			}

			/**
			 * Reads the next record into {@link #bytes()}; returns its length or -1 at the
			 * end.
			 */
			int next() throws IOException {
				return switch (configuration.getRecordFormat()) {
				case FIXED -> fixed();
				case VARIABLE -> variable(false);
				case VARIABLE_BLOCKED -> variable(true);
				case COMPUTED -> computed();
				};
			}

			private int fixed() throws IOException {
				ensure(fixedLength);
				int read = input.readNBytes(buffer, 0, fixedLength);
				if (read == 0) {
					return -1;
				}
				if (read < fixedLength) {
					throw failureAt("the data set ends inside a record (" + read + " of " + fixedLength + " bytes);"
							+ " check recordLength and recordFormat");
				}
				return read;
			}

			private int variable(boolean blocked) throws IOException {
				if (blocked && blockRemaining == 0) {
					byte[] bdw = input.readNBytes(4);
					if (bdw.length == 0) {
						return -1;
					}
					if (bdw.length < 4) {
						throw failureAt("the data set ends inside a block descriptor word");
					}
					int blockLength = (bdw[0] & 0x80) != 0
							? (bdw[0] & 0x7F) << 24 | (bdw[1] & 0xFF) << 16 | (bdw[2] & 0xFF) << 8 | bdw[3] & 0xFF
							: (bdw[0] & 0xFF) << 8 | bdw[1] & 0xFF;
					if (blockLength < 8) {
						throw failureAt("invalid block descriptor word X'" + CobolFieldDecoder.hex(bdw, 0, 4) + "'");
					}
					blockRemaining = blockLength - 4;
				}
				byte[] rdw = input.readNBytes(4);
				if (rdw.length == 0 && !blocked) {
					return -1;
				}
				if (rdw.length < 4) {
					throw failureAt("the data set ends inside a record descriptor word");
				}
				int recordLength = (rdw[0] & 0xFF) << 8 | rdw[1] & 0xFF;
				if (rdw[2] != 0 || rdw[3] != 0) {
					throw failureAt("spanned records (RECFM=VS/VBS) are not supported (RDW X'"
							+ CobolFieldDecoder.hex(rdw, 0, 4) + "')");
				}
				if (recordLength < 4) {
					throw failureAt("invalid record descriptor word X'" + CobolFieldDecoder.hex(rdw, 0, 4)
							+ "'; check recordFormat (the RDW may have been removed in transfer)");
				}
				int length = recordLength - 4;
				if (blocked) {
					blockRemaining -= recordLength;
					if (blockRemaining < 0) {
						throw failureAt("record crosses its block boundary");
					}
				}
				ensure(length);
				if (input.readNBytes(buffer, 0, length) < length) {
					throw failureAt("the data set ends inside a record of " + length + " bytes");
				}
				return length;
			}

			/** RDW removed: the record length follows from the layout of its type. */
			private int computed() throws IOException {
				int maximum = java.util.stream.Stream.concat(records.values().stream(), controls.values().stream())
						.mapToInt(table -> table.recordLength).max().orElse(0);
				ensure(maximum);
				input.mark(maximum);
				int available = input.readNBytes(buffer, 0, maximum);
				input.reset();
				if (available == 0) {
					return -1;
				}
				recordNumber++;
				Resolved resolved;
				try {
					resolved = type(buffer, available);
				} finally {
					recordNumber--;
				}
				String type = resolved == null ? null : resolved.name().toUpperCase(Locale.ROOT);
				TableModel table = type == null ? null : resolved.skip() ? controls.get(type) : records.get(type);
				if (table == null) {
					throw failureAt("records of unknown types, or skipped records that are not control records,"
							+ " cannot be framed without an RDW");
				}
				int length = table.recordLength;
				if (table.variableLength) {
					length = variableLength(table, available);
				}
				if (available < length) {
					throw failureAt("the data set ends inside a " + length + "-byte " + type + " record");
				}
				input.readNBytes(buffer, 0, length);
				return length;
			}

			private int variableLength(TableModel table, int available) {
				TableModel array = table.arrays.stream().filter(child -> child.dependingOn != null).findFirst()
						.orElseThrow(() -> new CommandException("No OCCURS DEPENDING ON in " + table.path));
				byte[] saved = lastRecords.get(table.recordName);
				Integer savedLength = lastRecordLengths.get(table.recordName);
				lastRecords.put(table.recordName, buffer);
				lastRecordLengths.put(table.recordName, available);
				try {
					return array.occursOffset + occurrences(array) * array.elementLength;
				} finally {
					if (saved == null) {
						lastRecords.remove(table.recordName);
						lastRecordLengths.remove(table.recordName);
					} else {
						lastRecords.put(table.recordName, saved);
						lastRecordLengths.put(table.recordName, savedLength);
					}
				}
			}

			private void ensure(int length) {
				if (buffer.length < length) {
					buffer = new byte[Math.max(length, buffer.length * 2)];
				}
			}

			private CommandException failureAt(String message) {
				return new CommandException("Record " + (recordNumber + 1) + ": " + message);
			}
		}
	}

	private static Map<?, ?> map(Object value) {
		return value instanceof Map<?, ?> map ? map : Map.of();
	}

	private static Map<?, ?> occurrenceOf(LegacyMigrationMapping.TableMapping table) {
		return map(table.getDetails().get("occurrence"));
	}

	private static String string(Object value) {
		return value == null ? null : value.toString();
	}
}
