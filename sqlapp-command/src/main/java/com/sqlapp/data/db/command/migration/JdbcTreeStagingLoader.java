/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlanWrapper;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlanWrapper.JoinKeyWrapper;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlanWrapper.LoadDataSetWrapper;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlanWrapper.LoadFieldWrapper;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.sql.JdbcTreeDataCopySession;
import com.sqlapp.jdbc.sql.JdbcTreeDataCopySession.HoldCursorStrategy;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/**
 * Loads persistent staging tables into a relational hierarchy through
 * {@link JdbcTreeDataCopySession}.
 */
public class JdbcTreeStagingLoader {

	private static final String LOAD_STATUS_COLUMN = "SQLAPP_LOAD_STATUS";

	private final Connection connection;

	private final List<Table> tables;

	private final LegacyMigrationLoadPlanWrapper plan;

	private final Map<String, LoadDataSetWrapper> dataSets = new LinkedHashMap<>();

	private final Map<String, Table> targetTables = new LinkedHashMap<>();

	private final Map<String, Table> stagingTables = new LinkedHashMap<>();

	private final String identifierQuote;

	private final Dialect dialect;

	public JdbcTreeStagingLoader(Connection connection, DbCommonObject<?> schema, LegacyMigrationLoadPlan plan)
			throws SQLException {
		this(connection, SchemaUtils.toTables(schema), plan);
	}

	public JdbcTreeStagingLoader(Connection connection, List<Table> tables, LegacyMigrationLoadPlan plan)
			throws SQLException {
		this.connection = connection;
		this.tables = tables;
		this.plan = new LegacyMigrationLoadPlanWrapper(plan);
		this.dialect = DialectResolver.getInstance().getDialect(connection);
		this.tables.forEach(table -> table.setDialect(dialect));
		String quote = connection.getMetaData().getIdentifierQuoteString();
		this.identifierQuote = quote == null || quote.isBlank() ? "" : quote;
		initialize();
		initializeStagingTables();
	}

	/**
	 * Removes descendant rows orphaned by an earlier committed run, then loads
	 * every pending root hierarchy. Committed root staging rows are deleted in the
	 * same transaction as their target rows; their descendants are retained until
	 * the next invocation to avoid recursive deletes for every chunk. The
	 * connection must have auto-commit disabled.
	 *
	 * @return number of staged root rows processed
	 */
	public long load() throws SQLException {
		if (connection.getAutoCommit()) {
			throw new CommandException("JdbcTreeStagingLoader requires autoCommit=false.");
		}
		cleanupOrphanedStagingRows();
		long count = 0;
		for (LoadDataSetWrapper root : roots()) {
			count += loadRoot(root);
		}
		return count;
	}

	private long loadRoot(LoadDataSetWrapper root) throws SQLException {
		long loaded = 0;
		JdbcTreeDataSession reader = createReader(root);
		JdbcTreeDataSession writer = createWriter();
		try (JdbcTreeDataCopySession copySession = createCopySession(root, reader, writer)) {
			while (copySession.next(stagingTables.get(root.getId()))) {
				copyHierarchy(copySession, root);
				loaded++;
			}
		}
		return loaded;
	}

	private JdbcTreeDataCopySession createCopySession(LoadDataSetWrapper root, JdbcTreeDataSession reader,
			JdbcTreeDataSession writer) {
		JdbcTreeDataCopySession copySession = new JdbcTreeDataCopySession(reader, writer);
		copySession.setRootBatchSize(plan.getRootBatchSize());
		copySession.setCommitEveryRootBatches(plan.getCommitEveryRootBatches());
		copySession.setHoldCursorStrategy(HoldCursorStrategy.valueOf(plan.getRootCursorStrategy()));
		copySession.setDeleteSourceRowHandler(
				rows -> completeStagingRoots(root, rows.stream().map(row -> keyValues(root, row)).toList()));
		return copySession;
	}

	private JdbcTreeDataSession createReader(LoadDataSetWrapper root) throws SQLException {
		JdbcTreeDataSession reader = new JdbcTreeDataSession(connection, new ArrayList<>(stagingTables.values()));
		reader.setRootBatchSize(plan.getRootBatchSize());
		reader.setFetchSize(plan.getRootBatchSize());
		reader.setTableOperationMode(TableOperationMode.NONE);
		reader.select(stagingTables.get(root.getId()), selectSql(root, true));
		registerChildren(reader, root);
		return reader;
	}

	private void registerChildren(JdbcTreeDataSession reader, LoadDataSetWrapper parent) throws SQLException {
		for (LoadDataSetWrapper child : children(parent.getId())) {
			reader.select(stagingTables.get(child.getId()));
			registerChildren(reader, child);
		}
	}

	private void copyHierarchy(JdbcTreeDataCopySession copySession, LoadDataSetWrapper dataSet) throws SQLException {
		Row source = copySession.getRow(dataSet.getStagingTable());
		Row target = copySession.newRow(dataSet.getTargetTable());
		copyTargetValues(dataSet, source, target);
		for (LoadDataSetWrapper child : children(dataSet.getId())) {
			Table childTable = stagingTables.get(child.getId());
			while (copySession.next(childTable)) {
				copyHierarchy(copySession, child);
			}
		}
	}

	private JdbcTreeDataSession createWriter() {
		JdbcTreeDataSession session = new JdbcTreeDataSession(connection, new ArrayList<>(targetTables.values()));
		session.setTableOperationMode(TableOperationMode.valueOf(plan.getTableOperationMode()));
		return session;
	}

	private void completeStagingRoots(LoadDataSetWrapper root, List<Map<String, Object>> rootKeys) throws SQLException {
		Map<String, PreparedStatement> statements = new LinkedHashMap<>();
		try {
			for (Map<String, Object> rootKey : rootKeys) {
				Where where = keyWhere(root, rootKey);
				String sql = completeRootSql(root, where);
				PreparedStatement statement = statements.get(sql);
				if (statement == null) {
					statement = connection.prepareStatement(sql);
					statements.put(sql, statement);
				}
				setParameters(statement, where.parameters());
				statement.addBatch();
			}
			for (PreparedStatement statement : statements.values()) {
				statement.executeBatch();
			}
		} finally {
			for (PreparedStatement statement : statements.values()) {
				statement.close();
			}
		}
	}

	private String completeRootSql(LoadDataSetWrapper root, Where where) {
		if (plan.isDeleteCommittedRoots()) {
			return "DELETE FROM " + id(root.getInner().getStagingTable()) + where.sql();
		}
		return "UPDATE " + id(root.getInner().getStagingTable()) + " SET " + id(LOAD_STATUS_COLUMN) + "='LOADED', "
				+ id("SQLAPP_LOADED_AT") + "=CURRENT_TIMESTAMP" + where.sql();
	}

	private void cleanupOrphanedStagingRows() throws SQLException {
		if (!plan.isDeleteCommittedRoots()) {
			return;
		}
		// Parent-first order makes descendants of a newly deleted orphan eligible
		// when their level is processed.
		for (LoadDataSetWrapper child : plan.getDataSets().stream()
				.filter(dataSet -> dataSet.getParentDataSetId() != null)
				.sorted(Comparator.comparingInt(LoadDataSetWrapper::getHierarchyDepth)).toList()) {
			deleteOrphans(child, dataSets.get(child.getParentDataSetId()));
		}
		connection.commit();
	}

	private void deleteOrphans(LoadDataSetWrapper child, LoadDataSetWrapper parent) throws SQLException {
		String childTable = id(child.getInner().getStagingTable());
		String parentTable = id(parent.getInner().getStagingTable());
		String join = child.getParentJoinKeys().stream()
				.map(key -> childTable + "." + id(key.getInner().getChildStagingColumn()) + "=" + parentTable + "."
						+ id(key.getInner().getParentStagingColumn()))
				.reduce((left, right) -> left + " AND " + right).orElseThrow();
		String sql = "DELETE FROM " + childTable + " WHERE NOT EXISTS (SELECT 1 FROM " + parentTable + " WHERE " + join
				+ ")";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.executeUpdate();
		}
	}

	private String selectSql(LoadDataSetWrapper dataSet, boolean root) {
		String columns = stagingColumns(dataSet).stream().map(this::id).reduce((left, right) -> left + ", " + right)
				.orElseThrow();
		StringBuilder sql = new StringBuilder("SELECT ").append(columns).append(" FROM ")
				.append(id(dataSet.getInner().getStagingTable()));
		if (root) {
			sql.append(" WHERE ").append(id(LOAD_STATUS_COLUMN)).append("='PENDING'");
			List<String> keys = dataSet.getSourceBusinessKey();
			if (!keys.isEmpty()) {
				sql.append(" ORDER BY ")
						.append(keys.stream().map(this::id).reduce((left, right) -> left + ", " + right).orElse(""));
			}
		}
		return sql.toString();
	}

	private Where keyWhere(LoadDataSetWrapper root, Map<String, Object> rootKey) {
		if (rootKey.isEmpty()) {
			throw new CommandException("Source business key is required for root data set: " + root.getId());
		}
		return where(rootKey);
	}

	private Where where(Map<String, Object> values) {
		StringBuilder sql = new StringBuilder(" WHERE ");
		List<Object> parameters = new ArrayList<>();
		int index = 0;
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			if (index++ > 0) {
				sql.append(" AND ");
			}
			sql.append(id(entry.getKey()));
			if (entry.getValue() == null) {
				sql.append(" IS NULL");
			} else {
				sql.append("=?");
				parameters.add(entry.getValue());
			}
		}
		return new Where(sql.toString(), parameters);
	}

	private void copyTargetValues(LoadDataSetWrapper dataSet, Row source, Row target) {
		for (LoadFieldWrapper field : dataSet.getFields()) {
			if (!field.isExtracted() || field.isTargetGenerated() || field.getTargetColumn() == null
					|| "DROP".equals(field.getAction())) {
				continue;
			}
			if (field.getTargetColumn() == null) {
				field.setTargetColumn(
						target.getParent().getParent().getColumns().get(field.getInner().getTargetColumn()));
			}
			if (field.getStagingColumn() == null) {
				field.setStagingColumn(
						source.getParent().getParent().getColumns().get(field.getInner().getStagingColumn()));
			}
			target.put(field.getTargetColumn(), source.get(field.getStagingColumn()));
		}
	}

	private Map<String, Object> keyValues(LoadDataSetWrapper root, Row values) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (String key : root.getSourceBusinessKey()) {
			result.put(key, values.get(key));
		}
		return result;
	}

	private List<String> stagingColumns(LoadDataSetWrapper dataSet) {
		Set<String> columns = new LinkedHashSet<>();
		dataSet.getFields().stream().filter(LoadFieldWrapper::isExtracted).map(o -> o.getInner().getStagingColumn())
				.filter(name -> name != null).forEach(columns::add);
		return new ArrayList<>(columns);
	}

	private List<LoadDataSetWrapper> roots() {
		return plan.getDataSets().stream().filter(dataSet -> dataSet.getParentDataSetId() == null)
				.sorted(Comparator.comparingInt(LoadDataSetWrapper::getLoadOrder)).toList();
	}

	private List<LoadDataSetWrapper> children(String parentId) {
		return plan.getDataSets().stream().filter(dataSet -> parentId.equals(dataSet.getParentDataSetId()))
				.sorted(Comparator.comparingInt(LoadDataSetWrapper::getLoadOrder)).toList();
	}

	private void initialize() {
		if (plan == null || !LegacyMigrationLoadPlan.FORMAT.equals(plan.getFormat())) {
			throw new CommandException("Unsupported legacy migration load plan.");
		}
		if (plan.getVersion() != LegacyMigrationLoadPlan.CURRENT_VERSION) {
			throw new CommandException("Unsupported legacy migration load plan version: " + plan.getVersion());
		}
		if (plan.getRootBatchSize() <= 0 || plan.getCommitEveryRootBatches() <= 0) {
			throw new CommandException("rootBatchSize and commitEveryRootBatches must be greater than zero.");
		}
		for (LoadDataSetWrapper dataSet : plan.getDataSets()) {
			if (dataSets.put(dataSet.getId(), dataSet) != null) {
				throw new CommandException("Duplicate load data set id: " + dataSet.getId());
			}
			// Target
			Table table = tables.stream()
					.filter(item -> equals(item.getSchemaName(), dataSet.getInner().getTargetSchema())
							&& equals(item.getName(), dataSet.getInner().getTargetTable()))
					.findFirst().orElseThrow(() -> new CommandException("Target table was not found: "
							+ dataSet.getInner().getTargetSchema() + "." + dataSet.getTargetTable()));
			dataSet.setTargetTable(table);
			targetTables.put(dataSet.getId(), table);
			for (LoadFieldWrapper field : dataSet.getFields()) {
				if (field.getInner().getTargetColumn() != null && !"DROP".equals(field.getAction())
						&& table.getColumns().get(field.getInner().getTargetColumn()) == null) {
					throw new CommandException(
							"Target column was not found: " + dataSet.getId() + "." + field.getTargetColumn());
				}
				field.setTargetColumn(table.getColumns().get(field.getInner().getTargetColumn()));
			}
		}
		for (LoadDataSetWrapper dataSet : plan.getDataSets()) {
			if (dataSet.getParentDataSetId() != null && !dataSets.containsKey(dataSet.getParentDataSetId())) {
				throw new CommandException("Parent load data set was not found: " + dataSet.getParentDataSetId());
			}
			if (dataSet.getParentDataSetId() == null && dataSet.getSourceBusinessKey().isEmpty()) {
				throw new CommandException("Root source business key is required: " + dataSet.getId());
			}
			Set<String> columns = stagingColumns(dataSet).stream().map(name -> name.toLowerCase(Locale.ROOT))
					.collect(java.util.stream.Collectors.toSet());
			for (String key : dataSet.getSourceBusinessKey()) {
				if (!columns.contains(key.toLowerCase(Locale.ROOT))) {
					throw new CommandException(
							"Source business key staging column was not found: " + dataSet.getId() + "." + key);
				}
			}
			if (dataSet.getParentDataSetId() != null) {
				if (dataSet.getParentJoinKeys().isEmpty()) {
					throw new CommandException("Parent join keys are required: " + dataSet.getId());
				}
				LoadDataSetWrapper parent = dataSets.get(dataSet.getParentDataSetId());
				Set<String> parentColumns = stagingColumns(parent).stream().map(name -> name.toLowerCase(Locale.ROOT))
						.collect(java.util.stream.Collectors.toSet());
				for (JoinKeyWrapper key : dataSet.getParentJoinKeys()) {
					if (key.getInner().getParentStagingColumn() == null || !parentColumns
							.contains(key.getInner().getParentStagingColumn().toLowerCase(Locale.ROOT))) {
						throw new CommandException("Parent join staging column was not found: " + dataSet.getId() + "."
								+ key.getParentStagingColumn());
					}
					if (key.getInner().getChildStagingColumn() == null
							|| !columns.contains(key.getInner().getChildStagingColumn().toLowerCase(Locale.ROOT))) {
						throw new CommandException("Child join staging column was not found: " + dataSet.getId() + "."
								+ key.getChildStagingColumn());
					}
				}
			}
			Set<String> visited = new LinkedHashSet<>();
			LoadDataSetWrapper current = dataSet;
			while (current != null && current.getParentDataSetId() != null) {
				if (!visited.add(current.getId())) {
					throw new CommandException("Cyclic load data set hierarchy at: " + current.getId());
				}
				current = dataSets.get(current.getParentDataSetId());
			}
		}
		TableOperationMode.valueOf(plan.getTableOperationMode());
		try {
			HoldCursorStrategy.valueOf(plan.getRootCursorStrategy());
		} catch (IllegalArgumentException e) {
			throw new CommandException("Unsupported rootCursorStrategy: " + plan.getRootCursorStrategy(), e);
		}
	}

	private void initializeStagingTables() {
		for (LoadDataSetWrapper dataSet : plan.getDataSets()) {
			Table table = new Table(dataSet.getInner().getStagingTable());
			table.setDialect(dialect);
			for (String name : stagingColumns(dataSet)) {
				table.getColumns().add(new Column(name).setDataType(DataType.VARCHAR));
			}
			List<String> keys = dataSet.getSourceBusinessKey();
			if (keys.isEmpty()) {
				keys = dataSet.getParentJoinKeys().stream().map(o -> o.getInner().getChildStagingColumn()).toList();
			}
			table.setPrimaryKey(("PK_" + table.getName()),
					keys.stream().map(name -> table.getColumns().get(name)).toArray(Column[]::new));
			dataSet.setStagingTable(table);
			stagingTables.put(dataSet.getId(), table);
		}
		for (LoadDataSetWrapper child : plan.getDataSets()) {
			if (child.getParentDataSetId() == null) {
				continue;
			}
			Table childTable = stagingTables.get(child.getId());
			Table parentTable = stagingTables.get(child.getParentDataSetId());
			Column[] childColumns = child.getParentJoinKeys().stream()
					.map(key -> childTable.getColumns().get(key.getInner().getChildStagingColumn()))
					.toArray(Column[]::new);
			Column[] parentColumns = child.getParentJoinKeys().stream()
					.map(key -> parentTable.getColumns().get(key.getInner().getParentStagingColumn()))
					.toArray(Column[]::new);
			childTable.getConstraints().addForeignKeyConstraint(
					"FK_" + childTable.getName() + "_" + parentTable.getName(), childColumns, parentColumns);
		}
	}

	private boolean equals(String left, String right) {
		return left == null ? right == null : left.equalsIgnoreCase(right);
	}

	private String id(String value) {
		if (identifierQuote.isEmpty()) {
			return value;
		}
		return identifierQuote + value.replace(identifierQuote, identifierQuote + identifierQuote) + identifierQuote;
	}

	private void setParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
		for (int i = 0; i < parameters.size(); i++) {
			Object value = parameters.get(i);
			if (value == null) {
				statement.setNull(i + 1, Types.NULL);
			} else {
				statement.setObject(i + 1, value);
			}
		}
	}

	private record Where(String sql, List<Object> parameters) {
	}

}
