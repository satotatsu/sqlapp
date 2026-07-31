package com.sqlapp.data.schemas.migration;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.sql.ColumnSelectionStrategy;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.db.sql.SqlSignature.ColumnsHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.JsonConverter;

public class JobTable implements Closeable {
	private final Connection connection;
	private final Dialect dialect;

	private String schemaName = null;
	private String tableName = "_JOB";

	private String jobNameColumn = "JOB_NAME";

	private String rootTableNameColumn = "ROOT_TABLE_NAME";

	private String rootSequenceColumn = "ROOT_SEQUENCE";

	private String statusColumn = "STATUS";

	private String updatedAtColumn = "UPDATED_AT";

	private String lastRootKeyColumn = "LAST_ROOT_KEY";

	private Table jobTable;

	private Column _jobNameColumn;

	private Column _rootTableNameColumn;

	private Column _rootSequenceColumn;

	private Column _statusColumn;

	private Column _lastRootKeyColumn;

	private Column _updatedAtColumn;

	private JsonConverter jsonConverter = new JsonConverter();

	public JobTable(Connection connection) {
		this.connection = connection;
		this.dialect = DialectResolver.getInstance().getDialect(connection);
	}

	public void createTableIfNotExists() throws SQLException {
		TableReader reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(schemaName);
		reader.setObjectName(tableName);
		List<Table> tables = reader.getAllFull(connection);
		if (tables.isEmpty()) {
			Table table = createTableDefinition();
			SqlFactoryRegistry registory = dialect.createSqlFactoryRegistry();
			List<SqlNode> sqlNodes = registory.createSqlNodes(table, SqlType.CREATE);
			final ParametersContext context = new ParametersContext();
			boolean autoCommit = connection.getAutoCommit();
			try {
				if (autoCommit) {
					connection.setAutoCommit(false);
				}
				for (final SqlNode sqlNode : sqlNodes) {
					final JdbcHandler jdbcHandler = new JdbcHandler(sqlNode);
					jdbcHandler.execute(connection, context);
				}
				connection.commit();
			} catch (SQLException e) {
				connection.rollback();
				throw e;
			} finally {
				if (autoCommit) {
					connection.setAutoCommit(autoCommit);
				}
			}
			this.jobTable = table;
			setColumn(table);
		}
		this.jobTable = tables.getFirst();
		setColumn(jobTable);
	}

	private void setColumn(Table table) {
		_jobNameColumn = table.getColumns().get(jobNameColumn);
		_rootTableNameColumn = table.getColumns().get(rootTableNameColumn);
		_rootSequenceColumn = table.getColumns().get(rootSequenceColumn);
		_statusColumn = table.getColumns().get(statusColumn);
		_updatedAtColumn = table.getColumns().get(updatedAtColumn);
		_lastRootKeyColumn = table.getColumns().get(lastRootKeyColumn);
	}

	private Set<Column> keyColumns = null;

	private PreparedStatement updateJobKeyStatement = null;

	public long updateJobStatus(String jobName, Table table, long sequenceNo, Row row) throws SQLException {
		if (keyColumns == null) {
			final ColumnSelectionStrategy strategy = ColumnSelectionStrategy.PRIMARY_KEY_OR_UNIQUE_KEY_OR_NOT_NULL_UNIQUE_INDEX;
			SqlSignature sqlSignature = new SqlSignature(table, List.of(row));
			ColumnsHolder columnsHolder = strategy.get(sqlSignature);
			keyColumns = columnsHolder.getKeyColumns();
		}
		if (updateJobKeyStatement == null) {
			AbstractSqlBuilder<?> builder = dialect.createSqlBuilder();
			builder.update().name(table);
			builder.lineBreak();
			builder.set().name(_rootSequenceColumn).eq().space()._add("?");
			builder.lineBreak();
			builder.comma().name(_lastRootKeyColumn).eq().space()._add("?");
			builder.lineBreak();
			builder.comma().name(_updatedAtColumn).eq().space()._add("?");
			builder.lineBreak();
			builder.where().true_();
			builder.lineBreak();
			builder.and().name(_jobNameColumn).eq().space()._add("?");
			builder.lineBreak();
			builder.and().name(_rootTableNameColumn).eq().space()._add("?");
			updateJobKeyStatement = connection.prepareStatement(builder.toString());
		}
		Map<String, Object> keyMap = toKeyMap(row, keyColumns);
		String json = jsonConverter.toJsonString(keyMap);
		int i = 1;
		setParamenter(updateJobKeyStatement, i++, _rootSequenceColumn, sequenceNo);
		setParamenter(updateJobKeyStatement, i++, _lastRootKeyColumn, json);
		setParamenter(updateJobKeyStatement, i++, _updatedAtColumn, LocalDateTime.now());
		setParamenter(updateJobKeyStatement, i++, _jobNameColumn, jobName);
		setParamenter(updateJobKeyStatement, i++, _rootTableNameColumn, getTableName(table));
		return updateJobKeyStatement.executeLargeUpdate();
	}

	private PreparedStatement insertStatement = null;

	public long insertJobKey(String jobName, Table table) throws SQLException {
		if (insertStatement == null) {
			AbstractSqlBuilder<?> builder = dialect.createSqlBuilder();
			builder.insert().into().name(table);
			builder.lineBreak();
			builder.brackets(true, () -> {
				int i = 0;
				for (Column column : jobTable.getColumns()) {
					builder.comma(i > 0).name(column);
				}
			});
			builder.lineBreak();
			builder.value();
			builder.brackets(true, () -> {
				int i = 0;
				for (Column column : jobTable.getColumns()) {
					builder.comma(i > 0)._add("?");
				}
			});
			insertStatement = connection.prepareStatement(builder.toString());
		}
		Row row = jobTable.newRow();
		row.put(_jobNameColumn, jobName);
		row.put(_rootTableNameColumn, getTableName(table));
		row.put(_statusColumn, JobStatus.Status.CREATED);
		row.put(_updatedAtColumn, LocalDateTime.now());
		int i = 1;
		for (Column column : jobTable.getColumns()) {
			setParamenter(insertStatement, i++, column, row.get(column));
		}
		return insertStatement.executeLargeUpdate();
	}

	private PreparedStatement selectStatement = null;

	public JobStatus select(String jobName, Table table) throws SQLException {
		if (selectStatement == null) {
			AbstractSqlBuilder<?> builder = dialect.createSqlBuilder();
			builder.select().space()._add("*");
			builder.lineBreak();
			builder.from().name(table);
			builder.where().true_();
			builder.lineBreak();
			builder.and().name(_jobNameColumn).eq().space()._add("?");
			builder.lineBreak();
			builder.and().name(_rootTableNameColumn).eq().space()._add("?");
			selectStatement = connection.prepareStatement(jobName, ResultSet.TYPE_FORWARD_ONLY,
					ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
			selectStatement.setFetchSize(1);
		}
		int i = 1;
		setParamenter(updateJobKeyStatement, i++, _jobNameColumn, jobName);
		setParamenter(updateJobKeyStatement, i++, _rootTableNameColumn, getTableName(table));
		try (ResultSet resultSet = selectStatement.executeQuery()) {
			Row[] rows = new Row[1];
			jobTable.readData(resultSet, row -> {
				rows[0] = row;
			});
			if (rows[0] != null) {

			}
		}
		return null;
	}

	private JobStatus toJobStatus(Row row) {
		JobStatus obj = new JobStatus();
		return obj;
	}

	private String getTableName(Table table) {
		if (table.getSchemaName() == null) {
			return table.getName();
		}
		return table.getSchemaName() + "." + table.getName();
	}

	private void setParamenter(PreparedStatement statement, int index, Column column, Object value)
			throws SQLException {
		statement.setObject(index, value, column.getDataType().getJdbcType());
	}

	private Map<String, Object> toKeyMap(Row row, Set<Column> columns) {
		Map<String, Object> map = CommonUtils.linkedMap();
		for (Column column : columns) {
			map.put(column.getName(), row.get(column));
		}
		return map;
	}

	public Table createTableDefinition() {
		Table table = new Table(tableName);
		table.setSchemaName(schemaName);
		table.getColumns().add(c -> {
			c.setName(jobNameColumn);
			setDataType(c, DataType.VARCHAR);
			c.setLength(255);
			c.setNotNull(true);
		});
		table.getColumns().add(c -> {
			c.setName(rootTableNameColumn);
			setDataType(c, DataType.VARCHAR);
			c.setLength(255);
			c.setNotNull(true);
		});
		table.getColumns().add(c -> {
			c.setName(rootSequenceColumn);
			setDataType(c, DataType.BIGINT);
		});
		table.getColumns().add(c -> {
			c.setName(statusColumn);
			setDataType(c, DataType.VARCHAR);
			c.setLength(16);
			c.setNotNull(true);
		});
		table.getColumns().add(c -> {
			c.setName(lastRootKeyColumn);
			setDataType(c, DataType.VARCHAR);
			c.setLength(4000);
		});
		table.getColumns().add(c -> {
			c.setName(updatedAtColumn);
			setDataType(c, DataType.DATETIME);
			c.setNotNull(true);
		});
		table.setPrimaryKey(table.getColumns().get(jobNameColumn), table.getColumns().get(rootTableNameColumn));
		return table;
	}

	private void setDataType(Column column, DataType dataType) {
		if (dataType == DataType.VARCHAR) {
			if (this.dialect.recommendsNTypeChar()) {
				column.setDataType(DataType.NVARCHAR);
			} else {
				column.setDataType(dataType);
			}
		} else {
		}
	}

	@Override
	public void close() {
		FileUtils.close(insertStatement);
		FileUtils.close(updateJobKeyStatement);
	}

}
