/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.IntervalPrecision;
import com.sqlapp.data.db.datatype.IntervalTypeHandler;
import com.sqlapp.data.db.datatype.MetaType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.*;
import com.sqlapp.util.iterator.IteratorCloseUtils;

/** One resolved interval projection shared by preflight, copy and verification. */
final class TransferIntervalProjection {
	static final String SNAPSHOT = "sqlapp.transfer.intervalProjection.v1";
	private record Field(String source, String target, DataType type, String typeName, Long length,
			Integer scale, int dimensions, Map<String, String> specifics) {}
	private record Snapshot(String product, int major, int minor, List<Field> fields, List<String> warnings,
			BulkInsertMethod insertMethod) {}
	private record Binding(Column source, Column destination, IntervalTypeHandler handler) {}

	static boolean captured(BulkMigrationJobPlan plan) {
		return plan.getTasks().stream().anyMatch(task -> task.getEffectiveSourceTable().getSpecifics().containsKey(SNAPSHOT));
	}

	static BulkMigrationJobPlan restoreChecked(BulkMigrationJobPlan raw, Connection target) throws Exception {
		var restored = restore(raw);
		var actual = new java.util.TreeSet<String>();
		var dialect = TransferJobPreparationService.dialect(target);
		for (int i = 0; i < raw.getTasks().size(); i++) {
			var original = raw.getTasks().get(i);
			var projected = restored.getTasks().get(i);
			if (!original.getEffectiveSourceTable().getSpecifics().containsKey(SNAPSHOT)) continue;
			var bindings = new ArrayList<Binding>();
			for (var column : original.getEffectiveSourceTable().getColumns()) {
				if (!interval(column)) continue;
				var destination = projected.getEffectiveTargetTable().getColumns().get(original.getTargetColumnName(column.getName()));
				bindings.add(new Binding(column, destination, handler(dialect, destination)));
			}
			actual.addAll(preflight(original, bindings, target));
		}
		var expected = warnings(restored).stream().filter(text -> !text.startsWith("[INTERVAL_PRECISION_MAPPING]"))
				.collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
		if (!actual.equals(expected)) throw invalid("Interval conversion warnings changed. Review the transfer again.");
		return restored;
	}

	/** Pins replayable destination semantics inside the already fingerprinted Schema artifact. */
	static boolean capture(com.sqlapp.data.schemas.Schema schema, BulkMigrationJobPlan plan, Connection target)
			throws SQLException {
		if (!(plan.getLifecycle() instanceof Report)) return false;
		var metadata = target.getMetaData();
		boolean first = true;
		for (var task : plan.getTasks()) {
			var fields = new ArrayList<Field>();
			for (var source : task.getEffectiveSourceTable().getColumns()) {
				if (!interval(source)) continue;
				var destination = task.getEffectiveTargetTable().getColumns().get(task.getTargetColumnName(source.getName()));
				fields.add(new Field(source.getName(), destination.getName(), destination.getDataType(),
						destination.getDataTypeName(), destination.getLength(), destination.getScale(),
						destination.getArrayDimension(), new LinkedHashMap<>(destination.getSpecifics())));
			}
			if (!fields.isEmpty()) {
				schema.getTables().get(task.getEffectiveSourceTable().getName()).getSpecifics()
					.put(SNAPSHOT, new com.sqlapp.util.JsonConverter().toJsonString(new Snapshot(
							metadata.getDatabaseProductName(), metadata.getDatabaseMajorVersion(), metadata.getDatabaseMinorVersion(),
							List.copyOf(fields), first ? warnings(plan) : List.of(), task.getOptions().getBulkOption().getInsertMethod())));
				first = false; // Store the job warning list once, not once per table.
			}
		}
		return true;
	}

	/** Rebuilds a pinned plan without reading source rows or consulting current target column definitions. */
	static BulkMigrationJobPlan restore(BulkMigrationJobPlan original) {
		var tasks = new ArrayList<BulkMigrationJobTask>();
		List<String> warnings = null;
		for (var task : original.getTasks()) {
			String text = task.getEffectiveSourceTable().getSpecifics().get(SNAPSHOT);
			if (text == null) { tasks.add(task); continue; }
			var saved = new com.sqlapp.util.JsonConverter().fromJsonString(text, Snapshot.class);
			if (warnings != null && !saved.warnings().isEmpty()) throw invalid("Duplicate captured interval warnings");
			if (warnings == null) warnings = List.copyOf(saved.warnings());
			var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance()
					.getDialect(saved.product(), saved.major(), saved.minor(), null);
			var output = task.getEffectiveTargetTable().clone();
			var bindings = new ArrayList<Binding>();
			for (var field : saved.fields()) {
				var source = task.getEffectiveSourceTable().getColumns().get(field.source());
				var destination = output.getColumns().get(field.target());
				if (source == null || destination == null || !field.target().equals(task.getTargetColumnName(field.source())))
					throw invalid("Invalid captured interval column mapping");
				destination.setDataType(field.type()).setDataTypeName(field.typeName()).setLength(field.length())
						.setScale(field.scale()).setArrayDimension(field.dimensions());
				destination.getSpecifics().clear(); destination.getSpecifics().putAll(field.specifics());
				var adapter = handler(dialect, destination);
				adapter.validateIntervalColumn(destination);
				bindings.add(new Binding(source, destination, adapter));
			}
			tasks.add(projectTask(task, output, bindings, saved.insertMethod()));
		}
		return warnings == null ? original : BulkMigrationJobPlanner.plan(original.getJobId(), tasks,
				new Report(original.getLifecycle(), warnings));
	}

	static BulkMigrationJobPlan prepare(Connection target, BulkMigrationJobPlan original, Dialect sourceDialect,
			TransferJobConfiguration.SchemaMode mode, boolean staged) throws Exception {
		if (original.getTasks().stream().noneMatch(task -> task.getEffectiveSourceTable().getColumns().stream()
				.anyMatch(TransferIntervalProjection::interval))) return original;
		var dialect = TransferJobPreparationService.dialect(target);
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader().getColumnReader();
		var first = original.getTasks().getFirst().getEffectiveTargetTable();
		reader.setCatalogName(first.getCatalogName());
		reader.setSchemaName(first.getSchemaName());
		var existing = reader.getAll(target);
		var tasks = new ArrayList<BulkMigrationJobTask>();
		var warnings = new ArrayList<String>();
		for (var task : original.getTasks()) {
			var input = task.getEffectiveSourceTable();
			var output = task.getEffectiveTargetTable().clone();
			var bindings = new ArrayList<Binding>();
			for (var column : input.getColumns()) {
				if (!interval(column)) continue;
				var planned = output.getColumns().get(task.getColumnMappings().getOrDefault(column.getName(), column.getName()));
				var actual = mode == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES ? null
						: existing(existing, planned);
				Column destination;
				if (actual != null) {
					destination = actual.clone();
				} else {
					destination = planned.clone();
					if (dialect.getDbDataTypes().getDbTypeStrict(destination.getDataType()) == null) {
						DataType group;
						String name = column.getDataType().name();
						if (dialect.getDbDataTypes().getDbTypeStrict(DataType.INTERVAL) != null) group = DataType.INTERVAL;
						else if (name.contains("YEAR") || name.contains("MONTH")) group = DataType.INTERVAL_YEAR_TO_MONTH;
						else if (column.getDataType() != DataType.INTERVAL) group = DataType.INTERVAL_DAY_TO_SECOND;
						else group = inferGroup(task, column);
						destination.setDataType(group).setDataTypeName(null);
					}
					var sourceType = sourceDialect.getDbDataTypes().getDbTypeStrict(column.getDataType());
					if (sourceType == null || !(sourceType.getJdbcTypeHandler() instanceof IntervalTypeHandler source))
						throw invalid("Source interval adapter has not been migrated: " + column.getName());
					var targetHandler = handler(dialect, destination);
					var precision = source.intervalPrecision(column);
					targetHandler.adaptIntervalColumn(destination, precision);
				}
				if (!interval(destination)) throw invalid("Target must retain interval type: " + column.getName());
				var targetHandler = handler(dialect, destination);
				targetHandler.validateIntervalColumn(destination);
				var sourceType = sourceDialect.getDbDataTypes().getDbTypeStrict(column.getDataType());
				if (sourceType != null && sourceType.getJdbcTypeHandler() instanceof IntervalTypeHandler source) {
					var from = source.intervalPrecision(column);
					var to = targetHandler.intervalPrecision(destination);
					if (!from.equals(to) || column.getDataType() != destination.getDataType()
							|| !source.intervalQualifier(column).equals(targetHandler.intervalQualifier(destination)))
						warnings.add("[INTERVAL_PRECISION_MAPPING] Task " + task.getTaskId() + " column "
								+ column.getName() + " -> " + destination.getName() + ": " + column.getDataType()
								+ " " + from + " (" + source.intervalQualifier(column) + ") -> " + destination.getDataType()
								+ " " + to + " (" + targetHandler.intervalQualifier(destination) + ")"
								+ "; every value checked exactly.");
				}
				copyType(destination, planned);
				bindings.add(new Binding(column, destination, targetHandler));
			}
			if (bindings.isEmpty()) { tasks.add(task); continue; }
			var taskWarnings = preflight(task, bindings, target);
			if(taskWarnings.stream().anyMatch(text->text.startsWith("[INTERVAL_NATIVE_BINDING]"))
					&& taskWarnings.stream().anyMatch(text->text.startsWith("[INTERVAL_JDBC_BINDING]")))
				throw invalid("Interval columns require incompatible transports; split this task by supported destination types");
			warnings.addAll(taskWarnings);
			tasks.add(projectTask(task, output, bindings,
					taskWarnings.stream().anyMatch(text -> text.startsWith("[INTERVAL_JDBC_BINDING]"))
							? BulkInsertMethod.JDBC_BATCH : taskWarnings.stream().anyMatch(text -> text.startsWith("[INTERVAL_NATIVE_BINDING]"))
								? BulkInsertMethod.NATIVE : null));
		}
		return BulkMigrationJobPlanner.plan(original.getJobId(), tasks,
				new Report(original.getLifecycle(), List.copyOf(warnings)));
	}

	private static List<String> preflight(BulkMigrationJobTask task, List<Binding> bindings, Connection target) throws Exception {
		var counts = new java.util.TreeMap<String, Long>();
		for (var binding : bindings) {
			if (!binding.handler().supportsJdbcBinding(binding.destination())) {
				if(task.getOptions().getBulkOption().getInsertMethod()==BulkInsertMethod.JDBC_BATCH)
					throw invalid("[INTERVAL_NATIVE_BINDING] Column "+binding.source().getName()+" requires NATIVE transport; choose AUTO or NATIVE");
				counts.put("INTERVAL_NATIVE_BINDING] column "+binding.source().getName()+" -> "+binding.destination().getName(),0L);
			}
			if (binding.handler().requiresJdbcBinding(null, binding.destination(), target.getMetaData())) {
				if (task.getOptions().getBulkOption().getInsertMethod() == BulkInsertMethod.NATIVE)
					throw invalid("[INTERVAL_JDBC_BINDING] Column " + binding.source().getName() + " requires JDBC_BATCH for interval arrays");
				counts.put("INTERVAL_JDBC_BINDING] column " + binding.source().getName() + " -> " + binding.destination().getName(), 0L);
			}
		}
		try (var rows = new Rows(open(task, null), task.getEffectiveSourceTable(), bindings)) {
			while (rows.hasNext()) {
				var raw = rows.rawNext();
				for (var binding : bindings) {
				var value = raw.get(binding.source());
				try {
					for (String code : value == null ? java.util.List.<String>of() : binding.handler().prepareInterval(value, binding.destination(), target.getMetaData()).warnings())
						counts.merge(code + "] column " + binding.source().getName()
							+ " -> " + binding.destination().getName(), 1L, Math::addExact);
					if(!binding.handler().supportsJdbcBinding(binding.destination()))
						counts.merge("INTERVAL_NATIVE_BINDING] column "+binding.source().getName()+" -> "+binding.destination().getName(),1L,Math::addExact);
					if (binding.handler().requiresJdbcBinding(value, binding.destination(), target.getMetaData())) {
						if (task.getOptions().getBulkOption().getInsertMethod() == BulkInsertMethod.NATIVE)
							throw invalid("[INTERVAL_JDBC_BINDING] Column " + binding.source().getName()
									+ " requires JDBC_BATCH for exact interval binding; NATIVE text bulk transport cannot represent it.");
						counts.merge("INTERVAL_JDBC_BINDING] column " + binding.source().getName()
								+ " -> " + binding.destination().getName(), 1L, Math::addExact);
					}
				} catch (TransferSchemaPreparation.ValidationException failure) {
					throw failure;
				} catch (RuntimeException failure) {
					throw invalid("[INTERVAL_VALUE_NOT_REPRESENTABLE] Column " + binding.source().getName()
						+ " exceeds the target interval qualifier, range, precision or array support.");
				}
				}
			}
		}
		var warnings = new ArrayList<String>();
		counts.forEach((code, count) -> warnings.add("[" + code + ": " + count + " row(s), task " + task.getTaskId()));
		return warnings;
	}

	private static BulkMigrationJobTask projectTask(BulkMigrationJobTask task, Table output, List<Binding> bindings,
			BulkInsertMethod insertMethod) {
		var input = task.getEffectiveSourceTable();
		var projected = input.clone();
		for (var binding : bindings) copyType(binding.destination(), projected.getColumns().get(binding.source().getName()));
		var builder = BulkMigrationJobTask.builder().taskId(task.getTaskId()).targetTable(output)
				.columnMappings(task.getColumnMappings()).requireEmptyTarget(task.isRequireEmptyTarget())
				.options(withInsertMethod(task.getOptions(), insertMethod)).checkpointStore(task.getCheckpointStore()).chunkListener(task.getChunkListener());
		if (task.isFullScan()) {
			projected.setRowIteratorHandler(collection -> new Rows(openUnchecked(task), projected, bindings));
			builder.sourceTable(projected).fullScan(true);
		} else {
			builder.keysetSource(new BulkMigrationKeysetSource() {
				@Override public Table getTable() { return projected; }
				@Override public List<String> getKeyColumnNames() { return task.getKeysetSource().getKeyColumnNames(); }
				@Override public String getConfigurationFingerprint() {
				return task.getKeysetSource().getConfigurationFingerprint() + ":interval-projection-v1";
				}
				@Override public Iterator<Row> iterator(String token) throws SQLException {
				return new Rows(open(task, token), projected, bindings);
				}
				@Override public String resumeToken(Row row) throws SQLException {
				return task.getKeysetSource().resumeToken(row);
				}
			});
		}
		return builder.build();
	}

	private static ChunkedBulkMigrationOption withInsertMethod(ChunkedBulkMigrationOption original, BulkInsertMethod method) {
		var bulk = original.getBulkOption();
		if (method == null || method == bulk.getInsertMethod()) return original;
		var selected = BulkOption.builder().batchSize(bulk.getBatchSize()).bulkCopyTimeout(bulk.getBulkCopyTimeout())
				.checkConstraints(bulk.isCheckConstraints()).fireTriggers(bulk.isFireTriggers()).keepIdentity(bulk.isKeepIdentity())
				.keepNulls(bulk.isKeepNulls()).tableLock(bulk.isTableLock()).useTransaction(bulk.isUseTransaction())
				.allowEncryptedValueModifications(bulk.isAllowEncryptedValueModifications()).insertMethod(method).build();
		return ChunkedBulkMigrationOption.builder().migrationId(original.getMigrationId()).chunkSize(original.getChunkSize())
				.mode(original.getMode()).incrementalStrategy(original.getIncrementalStrategy()).resume(original.isResume())
				.checkpointMode(original.getCheckpointMode()).checkpointTableName(original.getCheckpointTableName())
				.sourceFingerprint(original.getSourceFingerprint()).targetFingerprint(original.getTargetFingerprint())
				.bulkOption(selected).bulkUpsertOption(original.getBulkUpsertOption()).retryOption(original.getRetryOption()).build();
	}

	private static DataType inferGroup(BulkMigrationJobTask task, Column column) throws Exception {
		boolean[] units = new boolean[2];
		var iterator = open(task, null);
		Throwable failure = null;
		try {
			while (iterator.hasNext()) {
				Object raw = iterator.next().get(column);
				if (raw == null) continue;
				com.sqlapp.data.db.datatype.IntervalArrays.map(raw, column.getArrayDimension(), leaf -> {
					var value = new com.sqlapp.data.converter.IntervalValueConverter().convertObject(leaf);
					units[0] |= value.months() != 0;
					units[1] |= value.days() != 0 || !value.timePart().isZero();
					return value;
				});
			}
		} catch (Exception | Error problem) { failure = problem; throw problem; }
		finally { IteratorCloseUtils.close(failure, iterator); }
		if (units[0] == units[1]) throw invalid("Generic interval column " + column.getName()
				+ " cannot resolve one standard interval group. Prepare an explicit target interval column.");
		return units[0] ? DataType.INTERVAL_YEAR_TO_MONTH : DataType.INTERVAL_DAY_TO_SECOND;
	}

	private static Column existing(List<Column> columns, Column planned) {
		var exact = columns.stream().filter(value -> planned.getTableName().equals(value.getTableName())
				&& planned.getName().equals(value.getName())).toList();
		if (exact.size() == 1) return exact.getFirst();
		var folded = columns.stream().filter(value -> planned.getTableName().equalsIgnoreCase(value.getTableName())
				&& planned.getName().equalsIgnoreCase(value.getName())).toList();
		if (folded.size() > 1) throw invalid("Ambiguous target interval column: " + planned.getName());
		return folded.isEmpty() ? null : folded.getFirst();
	}

	private static IntervalTypeHandler handler(Dialect dialect, Column column) {
		var type = dialect.getDbDataTypes().getDbTypeStrict(column.getDataType());
		if (type == null || !(type.getJdbcTypeHandler() instanceof IntervalTypeHandler interval))
			throw invalid("Target interval adapter is unavailable: " + column.getName());
		return interval;
	}

	private static boolean interval(Column column) {
		return column.getDataType() != null && column.getDataType().getMetaTtype() == MetaType.INTERVAL;
	}

	static void copyType(Column from, Column to) {
		to.setDataType(from.getDataType()).setDataTypeName(from.getDataTypeName()).setLength(from.getLength())
				.setScale(from.getScale()).setArrayDimension(from.getArrayDimension());
		if (from.getDataTypeName() != null) to.getSpecifics().putAll(from.getSpecifics());
		String bounds = from.getSpecifics().get(com.sqlapp.data.db.datatype.IntervalArrays.DECLARED_BOUNDS);
		if (bounds == null) to.getSpecifics().remove(com.sqlapp.data.db.datatype.IntervalArrays.DECLARED_BOUNDS);
		else to.getSpecifics().put(com.sqlapp.data.db.datatype.IntervalArrays.DECLARED_BOUNDS, bounds);
	}

	private static Iterator<Row> open(BulkMigrationJobTask task, String token) throws SQLException {
		return task.isFullScan() ? task.getEffectiveSourceTable().getRows().iterator() : task.getKeysetSource().iterator(token);
	}
	private static Iterator<Row> openUnchecked(BulkMigrationJobTask task) {
		try { return open(task, null); }
		catch (SQLException exception) { throw new IllegalStateException(exception); }
	}
	private static TransferSchemaPreparation.ValidationException invalid(String message) {
		return new TransferSchemaPreparation.ValidationException(message);
	}

	private static final class Rows implements Iterator<Row>, AutoCloseable {
		private final Iterator<Row> source;
		private final Table table;
		private final List<Binding> bindings;
		Rows(Iterator<Row> source, Table table, List<Binding> bindings) {
			this.source = source; this.table = table; this.bindings = bindings;
		}
		@Override public boolean hasNext() { return source.hasNext(); }
		Row rawNext() { return source.next(); }
		@Override public Row next() {
			var raw = rawNext();
			var row = table.newRow();
			for (var column : table.getColumns()) row.put(column, raw.get(column.getName()));
			for (var binding : bindings) {
				var value = raw.get(binding.source());
				if (value != null) row.put(binding.source().getName(),
						binding.handler().prepareInterval(value, binding.destination()).value());
			}
			return row;
		}
		@Override public void close() { IteratorCloseUtils.close(null, source); }
	}

	static List<String> warnings(BulkMigrationJobPlan plan) {
		return plan.getLifecycle() instanceof Report report ? report.warnings() : List.of();
	}

	private record Report(BulkMigrationJobLifecycle delegate, List<String> warnings) implements BulkMigrationJobLifecycle {
		@Override public String getConfigurationFingerprint() {
			return delegate.getConfigurationFingerprint() + ":interval-projection-v1:" + warnings;
		}
		@Override public List<BulkMigrationJobOperation> plan(List<BulkMigrationJobTask> tasks) { return delegate.plan(tasks); }
		@Override public void validateBeforeExecution(BulkMigrationJobPlan plan) throws SQLException { delegate.validateBeforeExecution(plan); }
		@Override public void before(Connection connection, BulkMigrationJobPlan plan) throws SQLException { delegate.before(connection, plan); }
		@Override public void after(Connection connection, BulkMigrationJobPlan plan, BulkMigrationJobResult result) throws SQLException {
			delegate.after(connection, plan, result);
		}
		@Override public void restore(Connection connection, BulkMigrationJobPlan plan, Throwable failure) throws SQLException {
			delegate.restore(connection, plan, failure);
		}
	}
}
