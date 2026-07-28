package com.sqlapp.data.schemas.migration;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

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

	private Table table;

	private Column _jobNameColumn;

	private Column _rootTableNameColumn;

	private Column _rootSequenceColumn;

	private Column _statusColumn;

	private Column _lastRootKeyColumn;

	private Column _updatedAtColumn;

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
			this.table = table;
			setColumn(table);
		}
		this.table = tables.getFirst();
		setColumn(table);
	}

	private void setColumn(Table table) {
		_jobNameColumn = table.getColumns().get(jobNameColumn);
		_rootTableNameColumn = table.getColumns().get(rootTableNameColumn);
		_rootSequenceColumn = table.getColumns().get(rootSequenceColumn);
		_statusColumn = table.getColumns().get(statusColumn);
		_updatedAtColumn = table.getColumns().get(updatedAtColumn);
		_lastRootKeyColumn = table.getColumns().get(lastRootKeyColumn);
	}

	public void updateJobKey(String jobName, Table table, Row row) {
		final ColumnSelectionStrategy strategy = ColumnSelectionStrategy.PRIMARY_KEY_OR_UNIQUE_KEY_OR_NOT_NULL_UNIQUE_INDEX;
		SqlSignature sqlSignature = new SqlSignature(table, List.of(row));
		ColumnsHolder columnsHolder = strategy.get(sqlSignature);
		AbstractSqlBuilder<?> builder = dialect.createSqlBuilder();
		builder.update().name(table);
		builder.lineBreak();
		builder.set().name(_statusColumn).eq().space()._add("?");
		builder.comma().name(_rootSequenceColumn).eq().space()._add("?");
		builder.comma().name(_lastRootKeyColumn).eq().space()._add("?");
		builder.comma().name(_updatedAtColumn).eq().space()._add("?");
		builder.lineBreak();
		builder.where().true_();
		builder.lineBreak();
		builder.and().name(_jobNameColumn).eq().space()._add("?");
		builder.lineBreak();
		builder.and().name(_rootTableNameColumn).eq().space()._add("?");
	}

	public Table createTableDefinition() {
		Table table = new Table(tableName);
		table.setSchemaName(schemaName);
		table.getColumns().add(c -> {
			c.setName(jobNameColumn);
			setDataType(c, DataType.VARCHAR);
			c.setLength(255);
		});
		table.getColumns().add(c -> {
			c.setName(rootTableNameColumn);
			setDataType(c, DataType.VARCHAR);
			c.setLength(255);
		});
		table.getColumns().add(c -> {
			c.setName(rootSequenceColumn);
			setDataType(c, DataType.BIGINT);
		});
		table.getColumns().add(c -> {
			c.setName(statusColumn);
			setDataType(c, DataType.VARCHAR);
			c.setLength(16);
		});
		table.getColumns().add(c -> {
			c.setName(lastRootKeyColumn);
			setDataType(c, DataType.VARCHAR);
			c.setLength(4000);
		});
		table.getColumns().add(c -> {
			c.setName(updatedAtColumn);
			setDataType(c, DataType.DATETIME);
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

	}

}
