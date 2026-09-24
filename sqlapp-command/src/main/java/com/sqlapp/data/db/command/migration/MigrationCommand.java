/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import com.sqlapp.data.db.command.AbstractSqlCommand;
import com.sqlapp.data.db.command.migration.DbVersionFileHandler.SqlFile;
import com.sqlapp.data.db.command.migration.MigrationExecutionFailure.Phase;
import com.sqlapp.data.db.command.migration.MigrationExecutionFailure.RecoveryOutcome;
import com.sqlapp.data.db.command.properties.DefaultNoTransactionFileFilter;
import com.sqlapp.data.db.command.properties.NoTransactionFileFilterProperty;
import com.sqlapp.data.db.command.properties.RecursiveProperty;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.data.db.sql.ConnectionSqlExecutor;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbConcurrencyException;
import com.sqlapp.data.schemas.DefaultSchemaEqualsHandler;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexCollection;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.migration.SchemaCompatibilityReport;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.SqlConverter;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.OutputTextBuilder;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MigrationCommand extends AbstractSqlCommand implements NoTransactionFileFilterProperty, RecursiveProperty {

	/**
	 * バージョンアップ用SQLのディレクトリ
	 */
	private File sqlDirectory;
	/**
	 * バージョンダウン用のSQLのディレクトリ
	 */
	private File downSqlDirectory;
	/**
	 * バージョンアップ前に実行するSQLのディレクトリ
	 */
	private File setupSqlDirectory = null;
	/**
	 * バージョンアップ後に実行するSQLのディレクトリ
	 */
	private File finalizeSqlDirectory = null;
	/** Schema Change log table name */
	private String schemaChangeLogTableName = "changelog";

	private String idColumnName = "change_number";

	private String statusColumnName = "status";

	private String appliedByColumnName = "applied_by";

	private String appliedAtColumnName = "applied_at";

	private String descriptionColumnName = "description";

	private String seriesNumberColumnName = "series_number";
	/** Last change to Apply */
	private Long lastChangeToApply = Long.MAX_VALUE;

	private boolean showVersionOnly = false;

	/** Opt-in recording and validation of up SQL source checksums. */
	private boolean checksumValidation = false;

	/** Optional strict rejection of pending versions below the latest completed version. */
	private boolean rejectOutOfOrder = false;

	/** Optional rejection of selected migration files that bypass transactions. */
	private boolean rejectNonTransactional = false;

	/** Optional requirement that every selected migration has down SQL. */
	private boolean requireDownMigration = false;

	/** Optional reviewed plan that must match the selected execution. */
	private File expectedPlanFile;

	/** Optional maximum age of the reviewed plan. */
	private Duration expectedPlanMaxAge;

	/** Optional externally approved fingerprint of the reviewed plan. */
	private String expectedPlanFingerprint;

	/** Optional JDBC timeout for acquiring the migration-history lock. */
	private Integer lockTimeoutSeconds;

	@Setter(lombok.AccessLevel.NONE)
	private MigrationValidationResult validationResult;

	@Setter(lombok.AccessLevel.NONE)
	private MigrationExecutionFailure executionFailure;

	/** Optional expected live schema immediately before migration SQL runs. */
	private File preMigrationSchemaFile;

	@Setter(lombok.AccessLevel.NONE)
	private SchemaCompatibilityReport schemaDriftReport;

	@Getter(lombok.AccessLevel.NONE)
	@Setter(lombok.AccessLevel.NONE)
	private int attemptedStatement;

	private boolean withSeriesNumber = true;

	private String previousState = null;

	private String lastState = null;

	private Table previousTable = null;

	private Table table = null;

	private boolean recursive = false;

	private Predicate<File> noTransactionFileFilter = new DefaultNoTransactionFileFilter();

	@Override
	protected void doRun() {
		executionFailure = null;
		schemaDriftReport = null;
		attemptedStatement = 0;
		executedSqlCount.set(0);
		resetValidationResult();
		if (isChecksumValidation()) {
			requireValidationDirectory();
		}
		validateExpectedPlanConfiguration();
		final DbVersionHandler dbVersionHandler = createDbVersionHandler();
		final DbVersionFileHandler dbVersionFileHandler = new DbVersionFileHandler();
		dbVersionFileHandler.setUpSqlDirectory(this.getSqlDirectory());
		dbVersionFileHandler.setDownSqlDirectory(this.getDownSqlDirectory());
		dbVersionFileHandler.setRecursive(this.isRecursive());
		execute(getDataSource(), connection -> {
			Dialect dialect = this.getDialect(connection);
			if (preMigrationSchemaFile != null) {
				schemaDriftReport = assessPreMigrationDrift(connection);
				if (!schemaDriftReport.isCompatible()) {
					throw new CommandException("Breaking pre-migration schema drift detected: "
							+ schemaDriftReport.changes());
				}
			}
			dbVersionFileHandler.setSqlSplitter(dialect.createSqlSplitter());
			dbVersionFileHandler.setEncoding(this.getEncoding());
			DialectTableHolder holder = logCurrentState(connection, dbVersionHandler, dbVersionFileHandler, true);
			validateExpectedPlan(connection, holder, dbVersionHandler);
			previousTable = holder.table;
			previousState = lastState;
			if (!isShowVersionOnly()) {
				if (!holder.rows.isEmpty()) {
					this.info("");
					executeChangeVersion(connection, holder.dialect, holder.table, holder.rows, holder.sqlFiles,
							dbVersionHandler);
					holder = logCurrentState(connection, dbVersionHandler, dbVersionFileHandler, false);
					table = holder.table;
				} else {
					executeEmptyVersion(holder.dialect, holder.table, holder.rows, holder.sqlFiles, dbVersionHandler);
					holder = logCurrentState(connection, dbVersionHandler, dbVersionFileHandler, false);
					table = holder.table;
				}
			}
		});
	}

	protected DbVersionHandler createDbVersionHandler() {
		final DbVersionHandler dbVersionHandler = new DbVersionHandler();
		dbVersionHandler.setIdColumnName(this.getIdColumnName());
		dbVersionHandler.setAppliedAtColumnName(this.getAppliedAtColumnName());
		dbVersionHandler.setAppliedByColumnName(this.getAppliedByColumnName());
		dbVersionHandler.setStatusColumnName(this.getStatusColumnName());
		dbVersionHandler.setDescriptionColumnName(this.getDescriptionColumnName());
		dbVersionHandler.setSeriesNumberColumnName(this.getSeriesNumberColumnName());
		dbVersionHandler.setWithSeriesNumber(this.withSeriesNumber);
		dbVersionHandler.setWithChecksum(this.checksumValidation);
		return dbVersionHandler;
	}

	protected void validateExpectedPlan(final Connection connection, final DialectTableHolder holder,
			final DbVersionHandler handler) throws SQLException {
		if (expectedPlanFile == null) {
			return;
		}
		final MigrationPlanArtifact artifact = new MigrationPlanIO().read(expectedPlanFile.toPath());
		if (expectedPlanFingerprint != null
				&& !expectedPlanFingerprint.equals(artifact.planFingerprint())) {
			throw new CommandException("expectedPlanFile fingerprint does not match expectedPlanFingerprint: "
					+ expectedPlanFile);
		}
		validateExpectedPlanAge(artifact);
		final MigrationPlan expected = artifact.plan();
		if (expected.hasBlockers()) {
			throw new CommandException("expectedPlanFile contains blockers: " + expectedPlanFile);
		}
		Long current = null;
		for (final Row row : holder.table.getRows()) {
			final Long version = handler.getId(row);
			if (version != null && handler.getStatus(row).isCompleted()
					&& (current == null || version > current)) {
				current = version;
			}
		}
		final Map<Long, SqlFile> files = CommonUtils.map();
		for (final SqlFile file : holder.sqlFiles) {
			files.put(file.getVersionNumber(), file);
		}
		final List<MigrationPlan.Entry> pending = new ArrayList<>();
		for (final Row row : holder.rows) {
			final Long version = handler.getId(row);
			if (version == null) {
				continue;
			}
			final SqlFile file = files.get(version);
			final File source = file == null ? null : file.getUpSqlFile();
			pending.add(new MigrationPlan.Entry(version, source == null ? null : source.getName(), null,
					file == null ? 0 : file.getUpSqls().size(),
					file != null && !getNoTransactionFileFilter().test(source), isChecksumValidation(),
					file != null && !CommonUtils.isEmpty(file.getDownSqls()),
					file == null ? null : file.getUpSqlChecksum()));
		}
		final List<MigrationPlan.Entry> reviewed = expected.pending().stream()
				.map(entry -> new MigrationPlan.Entry(entry.version(), entry.description(), null, entry.statements(),
						entry.transactional(), entry.checksumWillBeRecorded(), entry.rollbackAvailable(),
						entry.sourceChecksum()))
				.toList();
		final long target = getLastChangeToApply() != null ? getLastChangeToApply()
				: current != null ? current : Long.MAX_VALUE;
		if (!java.util.Objects.equals(expected.databaseIdentity(), MigrationPlanCommand.databaseIdentity(connection))
				|| !java.util.Objects.equals(expected.currentVersion(), current) || expected.targetVersion() != target
				|| expected.setupStatements() != read(holder.dialect, getSetupSqlDirectory()).size()
				|| expected.finalizeStatements() != read(holder.dialect, getFinalizeSqlDirectory()).size()
				|| !reviewed.equals(pending)) {
			throw new CommandException("Current migration execution does not match expectedPlanFile: "
					+ expectedPlanFile);
		}
	}

	protected void validateExpectedPlanConfiguration() {
		if (expectedPlanFile == null && (expectedPlanMaxAge != null || expectedPlanFingerprint != null)) {
			throw new CommandException(
					"expectedPlanFile is required when expectedPlanMaxAge or expectedPlanFingerprint is configured");
		}
		if (expectedPlanFingerprint != null
				&& !expectedPlanFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException("expectedPlanFingerprint must be a lowercase SHA-256 value");
		}
		if (lockTimeoutSeconds != null && lockTimeoutSeconds <= 0) {
			throw new CommandException("lockTimeoutSeconds must be greater than zero");
		}
	}

	protected void validateExpectedPlanAge(final MigrationPlanArtifact artifact) {
		if (expectedPlanMaxAge == null) {
			return;
		}
		if (expectedPlanMaxAge.isZero() || expectedPlanMaxAge.isNegative()) {
			throw new CommandException("expectedPlanMaxAge must be greater than zero");
		}
		if (artifact.createdAtEpochMillis() <= 0) {
			throw new CommandException("expectedPlanFile does not contain a creation time: " + expectedPlanFile
					+ ". Generate and review a new migration plan.");
		}
		final long ageMillis = System.currentTimeMillis() - artifact.createdAtEpochMillis();
		if (ageMillis < 0 || ageMillis > expectedPlanMaxAge.toMillis()) {
			throw new CommandException("expectedPlanFile has expired: " + expectedPlanFile
					+ ". Generate and review a new migration plan.");
		}
	}

	protected SchemaCompatibilityReport assessPreMigrationDrift(final Connection connection) throws SQLException {
		if (preMigrationSchemaFile == null || !preMigrationSchemaFile.isFile()) {
			throw new CommandException("preMigrationSchemaFile does not exist: " + preMigrationSchemaFile);
		}
		try {
			final DbCommonObject<?> root = SchemaUtils.readXml(preMigrationSchemaFile);
			final List<Table> tables = SchemaUtils.toTables(root);
			if (tables.isEmpty()) {
				throw new CommandException("preMigrationSchemaFile contains no tables: " + preMigrationSchemaFile);
			}
			return MigrationSchemaDriftAssessor.assess(connection, tables);
		} catch (final IOException e) {
			throw new CommandException("Failed to read preMigrationSchemaFile: " + preMigrationSchemaFile, e);
		}
	}

	protected void validateChecksums(final Table history, final DbVersionHandler handler,
			final List<SqlFile> files) {
		validationResult = MigrationChecksumValidator.validate(history, handler, files);
		for (final MigrationValidationResult.Entry entry : validationResult.entries()) {
			info("Migration " + entry.version() + ": " + entry.state());
		}
		if (validationResult.hasFailures()) {
			throw new IllegalStateException("Migration validation failed: " + validationResult.entries()
					+ ". Restore the applied SQL source; "
					+ "checksums are never automatically rewritten.");
		}
	}

	protected void resetValidationResult() {
		validationResult = null;
	}

	protected void requireValidationDirectory() {
		if (getSqlDirectory() == null || !getSqlDirectory().isDirectory()) {
			throw new IllegalArgumentException("sqlDirectory must be an existing up SQL directory for validation.");
		}
	}

	protected void executeEmptyVersion(final Dialect dialect, final Table table, final List<Row> rows,
			final List<SqlFile> sqlFiles, final DbVersionHandler dbVersionHandler) {
		this.info("No sql file for apply.");
	}

	/**
	 * ディレクトリ内のSQLを取得します。
	 * 
	 * @return SQL
	 */
	protected List<SplitResult> read(final Dialect dialect, final File directory) {
		if (directory == null) {
			return Collections.emptyList();
		}
		final List<File> files = CommonUtils.list();
		final List<SplitResult> result = CommonUtils.list();
		final SqlSplitter splitter = dialect.createSqlSplitter();
		if (directory.exists()) {
			final File[] children = directory.listFiles();
			if (children != null) {
				for (final File file : children) {
					if (!file.getAbsolutePath().endsWith(".sql")) {
						return null;
					}
					files.add(file);
				}
			}
		}
		Collections.sort(files);
		files.forEach(file -> {
			final String text = FileUtils.readText(file, getEncoding());
			final List<SplitResult> splits = splitter.parse(text);
			result.addAll(splits);
		});
		return result;
	}

	protected DialectTableHolder logCurrentState(final Connection connection, final DbVersionHandler dbVersionHandler,
			final DbVersionFileHandler dbVersionFileHandler, final boolean target) throws SQLException {
		final DialectTableHolder holder = new DialectTableHolder();
		holder.sqlFiles = dbVersionFileHandler.read();
		holder.dialect = DialectResolver.getInstance().getDialect(connection);
		holder.table = dbVersionHandler.createVersionTableDefinition(schemaChangeLogTableName);
		checkTable(connection, holder.dialect, holder.table, dbVersionHandler);
		dbVersionHandler.load(connection, holder.dialect, holder.table);
		if (target && isChecksumValidation()) {
			validateChecksums(holder.table, dbVersionHandler, holder.sqlFiles);
		}
		dbVersionHandler.mergeSqlFiles(holder.sqlFiles, holder.table);
		validateOutOfOrder(holder.table, dbVersionHandler);
		if (target) {
			holder.rows = getVersionRows(holder.table, holder.sqlFiles, dbVersionHandler);
		} else {
			dbVersionHandler.markCurrentVersion(holder.table);
		}
		lastState = outputCurrent(holder.table, dbVersionHandler);
		return holder;
	}

	protected void validateOutOfOrder(final Table history, final DbVersionHandler handler) {
		if (!rejectOutOfOrder) {
			return;
		}
		Long latestCompleted = null;
		for (final Row row : history.getRows()) {
			final Long version = handler.getId(row);
			if (version != null && handler.getStatus(row).isCompleted()
					&& (latestCompleted == null || version > latestCompleted)) {
				latestCompleted = version;
			}
		}
		if (latestCompleted == null) {
			return;
		}
		final List<Long> outOfOrder = new ArrayList<>();
		for (final Row row : history.getRows()) {
			final Long version = handler.getId(row);
			if (version != null && version < latestCompleted && handler.getStatus(row).isPending()) {
				outOfOrder.add(version);
			}
		}
		if (!outOfOrder.isEmpty()) {
			throw new CommandException("Out-of-order migrations precede completed version " + latestCompleted
					+ ": " + outOfOrder + ". Disable rejectOutOfOrder only after reviewing them.");
		}
	}

	static class DialectTableHolder {
		public Dialect dialect = null;
		public Table table = null;
		public List<Row> rows = null;
		public List<SqlFile> sqlFiles = null;
	}

	protected void checkTable(final Connection connection, final Dialect dialect, final Table table,
			final DbVersionHandler dbVersionHandler) throws SQLException {
		final Table currentTable = dbVersionHandler.getTable(connection, dialect, table);
		if (currentTable == null) {
			final boolean bool = dbVersionHandler.createTable(connection, dialect, table);
			if (bool) {
				this.info("New change log table [" + getName(table) + "] was created.");
				return;
			}
		} else {
			// Disabling validation must not discard previously recorded checksums.
			if (!table.getColumns().contains(DbVersionHandler.CHECKSUM_COLUMN)
					&& currentTable.getColumns().contains(DbVersionHandler.CHECKSUM_COLUMN)) {
				table.getColumns().add(currentTable.getColumns().get(DbVersionHandler.CHECKSUM_COLUMN).clone());
			}
			final DefaultSchemaEqualsHandler equalsHandler = new DefaultSchemaEqualsHandler();
			equalsHandler.setReferenceEqualsPredicate((object1, object2) -> {
				if (object1 instanceof IndexCollection || object2 instanceof IndexCollection) {
					return true;
				}
				if (object1 instanceof Index || object2 instanceof Index) {
					return true;
				}
				return object1 == object2;
			});
			equalsHandler.setValueEqualsPredicate((propertyName, eq, object1, object2, value1, value2) -> {
				if (object1 instanceof UniqueConstraint || object2 instanceof UniqueConstraint) {
					return true;
				}
				if (value1 instanceof Index || value2 instanceof Index) {
					return true;
				}
				if (SchemaProperties.CATALOG_NAME.getLabel().equals(propertyName)) {
					return true;
				}
				if (SchemaProperties.SCHEMA_NAME.getLabel().equals(propertyName)) {
					return true;
				}
				if (SchemaProperties.DATA_TYPE.getLabel().equals(propertyName)) {
					return true;
				}
				if (SchemaProperties.DATA_TYPE_NAME.getLabel().equals(propertyName)) {
					return true;
				}
				if (SchemaProperties.LENGTH.getLabel().equals(propertyName)) {
					return true;
				}
				if (SchemaProperties.OCTET_LENGTH.getLabel().equals(propertyName)) {
					return true;
				}
				if (SchemaProperties.SPECIFICS.getLabel().equals(propertyName)) {
					return true;
				}
				if (SchemaProperties.STATISTICS.getLabel().equals(propertyName)) {
					return true;
				}
				if (SchemaProperties.CREATED_AT.getLabel().equals(propertyName)) {
					return true;
				}
				if (SchemaProperties.LAST_ALTERED_AT.getLabel().equals(propertyName)) {
					return true;
				}
				if (SchemaProperties.COLLATION.getLabel().equals(propertyName)) {
					return true;
				}
				if (SchemaProperties.CHARACTER_SET.getLabel().equals(propertyName)) {
					return true;
				}
				if (SchemaProperties.CHARACTER_SEMANTICS.getLabel().equals(propertyName)) {
					return true;
				}
				return eq;
			});
			final DbObjectDifference diff = currentTable.diff(table, equalsHandler);
			this.debug(table);
			final ConnectionSqlExecutor executor = new ConnectionSqlExecutor(connection);
			this.debug(diff);
			// 変更管理テーブルの定義を最新化
			SqlFactoryRegistry sqlFactoryRegistry = dialect.createSqlFactoryRegistry();
			final List<SqlOperation> sqlList = sqlFactoryRegistry.createSql(diff);
			if (!sqlList.isEmpty()) {
				final List<SqlOperation> lockTableSqlList = dialect.createSqlFactoryRegistry().createSql(table,
						SqlType.LOCK);
				executor.execute(lockTableSqlList);
				executor.execute(sqlList);
				try (Statement statement = connection.createStatement();) {
					String name = dialect.quote(currentTable.getName());
					if (currentTable.getSchemaName() != null) {
						name = dialect.quote(currentTable.getSchemaName()) + "." + name;
					}
					executeSql(statement, "UPDATE " + name + " SET " + dialect.quote(this.getStatusColumnName()) + "='"
							+ Status.Completed + "' WHERE " + dialect.quote(this.getStatusColumnName()) + " IS NULL");
					if (this.isWithSeriesNumber()) {
						executeSql(statement,
								"UPDATE " + name + " SET " + dialect.quote(this.getSeriesNumberColumnName()) + "="
										+ dialect.quote(this.getIdColumnName()) + " WHERE "
										+ dialect.quote(this.getSeriesNumberColumnName()) + " IS NULL");
					}
				}
			}
		}
	}

	private void executeSql(Statement statement, String sql) throws SQLException {
		debug(sql);
		statement.execute(sql);
	}

	protected String outputCurrent(final Table table, final DbVersionHandler dbVersionHandler) {
		final OutputTextBuilder builder = createOutputTextBuilder();
		builder.append("Database status.");
		builder.lineBreak();
		dbVersionHandler.append(table, builder);
		final String value = builder.toString();
		this.info(value);
		return value;
	}

	protected List<Row> getVersionRows(final Table table, final List<SqlFile> sqlFiles,
			final DbVersionHandler dbVersionHandler) {
		this.info("lastChangeToApply=" + getLastChangeToApply());
		final List<Row> rows = dbVersionHandler.getRowsForVersionUp(table, getLastChangeToApply());
		return rows;
	}

	protected void executeChangeVersion(final Connection connection, final Dialect dialect, final Table table,
			final List<Row> rows, final List<SqlFile> sqlFiles, final DbVersionHandler dbVersionHandler)
			throws SQLException {
		final Map<Long, SqlFile> sqlFileMap = CommonUtils.map();
		for (final SqlFile sqlFile : sqlFiles) {
			sqlFileMap.put(sqlFile.getVersionNumber(), sqlFile);
		}
		validateTransactionPolicy(rows, sqlFileMap, dbVersionHandler);
		validateRollbackPolicy(rows, sqlFileMap, dbVersionHandler);
		Long seriesNumber = null;
		Long id = null;
		Row currentRow = null;
		final SqlFile[] sqlFile = new SqlFile[1];
		final List<Long> committedVersions = new ArrayList<>();
		Phase phase = Phase.SETUP;
		File source = getSetupSqlDirectory();
		boolean nonTransactional = false;
		try {
			final SqlConverter sqlConverter = getSqlConverter();
			connection.setAutoCommit(false);
			final List<SqlOperation> ddlAutoCommitOffSqlList = dialect.createSqlFactoryRegistry()
					.createSql(SqlType.DDL_AUTOCOMMIT_OFF);
			final List<SqlOperation> lockTableSqlList = dialect.createSqlFactoryRegistry().createSql(table,
					SqlType.LOCK);
			final ConnectionSqlExecutor executor = new ConnectionSqlExecutor(connection);
			executor.execute(ddlAutoCommitOffSqlList);
			executeMigrationLock(connection, lockTableSqlList);
			validateExpectedPlanAfterLock(connection, dialect, sqlFiles, dbVersionHandler);
			final List<SplitResult> setupSqls = read(dialect, this.getSetupSqlDirectory());
			if (!CommonUtils.isEmpty(setupSqls)) {
				this.info("********************** execute setup sql. **********************");
			}
			executeSql(connection, sqlConverter, new ParametersContext(), setupSqls);
			if (!CommonUtils.isEmpty(setupSqls)) {
				this.info("******************************************************************");
			}
			if (!CommonUtils.isEmpty(rows)) {
				this.info("********************** execute version sql. **********************");
			}
			for (final Row row : rows) {
				phase = Phase.PRECHECK;
				executedSqlCount.set(0);
				attemptedStatement = 0;
				nonTransactional = false;
				id = dbVersionHandler.getId(row);
				if (id == null) {
					continue;
				}
				sqlFile[0] = sqlFileMap.get(id);
				source = sqlFile[0] == null ? null : getFile(sqlFile[0]);
				executor.execute(ddlAutoCommitOffSqlList);
				executeMigrationLock(connection, lockTableSqlList);
				// Recheck history only after acquiring this change's transaction lock.
				if (!preCheck(connection, dialect, table, id, row, dbVersionHandler)) {
					throw concurrentHistoryChange(id);
				}
				if (isChecksumValidation() && recordsChecksum() && sqlFile[0] != null) {
					row.put(DbVersionHandler.CHECKSUM_COLUMN, sqlFile[0].getUpSqlChecksum());
				}
				if (!startVersion(connection, dialect, table, row, seriesNumber != null ? seriesNumber : id,
						dbVersionHandler)) {
					throw concurrentHistoryChange(id);
				}
				if (seriesNumber == null) {
					seriesNumber = id;
				}
				currentRow = row;
				File file = getFile(sqlFile[0]);
				if (file != null && file.exists()) {
					boolean autoCommit = isNoTransaction(sqlFile[0]);
					nonTransactional = autoCommit;
					phase = Phase.MIGRATION;
					if (autoCommit) {
						executeNoTran(getDataSource(), connInner -> {
							connInner.setAutoCommit(true);
							executeSql(connInner, sqlConverter, sqlFile[0]);
							connInner.commit();
						});
					} else {
						executeSql(connection, sqlConverter, sqlFile[0]);
					}
				} else if (isChecksumValidation() && recordsChecksum()) {
					throw new IllegalStateException("Migration SQL source disappeared before execution: " + file);
				}
				phase = Phase.HISTORY_COMPLETION;
				finalizeVersion(connection, dialect, table, row, id, dbVersionHandler);
				phase = Phase.VERSION_COMMIT;
				commit(connection);
				committedVersions.add(id);
				currentRow = null;
			}
			if (!CommonUtils.isEmpty(rows)) {
				this.info("******************************************************************");
			}
			phase = Phase.FINALIZE;
			id = null;
			source = getFinalizeSqlDirectory();
			nonTransactional = false;
			executedSqlCount.set(0);
			attemptedStatement = 0;
			final List<SplitResult> finalizeSqls = read(dialect, this.getFinalizeSqlDirectory());
			if (!CommonUtils.isEmpty(finalizeSqls)) {
				this.info("********************** execute finalize sql. **********************");
			}
			executeSql(connection, sqlConverter, new ParametersContext(), finalizeSqls);
			if (!CommonUtils.isEmpty(finalizeSqls)) {
				this.info("******************************************************************");
			}
			phase = Phase.FINAL_COMMIT;
			commit(connection);
		} catch (final SQLException | RuntimeException e) {
			if (sqlFile[0] != null && id != null) {
				error(sqlFile[0]);
			}
			RecoveryOutcome rollbackOutcome = RecoveryOutcome.NOT_ATTEMPTED;
			RecoveryOutcome historyOutcome = RecoveryOutcome.NOT_ATTEMPTED;
			try {
				rollback(connection);
				rollbackOutcome = RecoveryOutcome.RETURNED;
			} catch (final RuntimeException recoveryFailure) {
				rollbackOutcome = RecoveryOutcome.FAILED;
				if (e != recoveryFailure) {
					e.addSuppressed(recoveryFailure);
				}
			}
			if (rollbackOutcome == RecoveryOutcome.RETURNED && currentRow != null && id != null
					&& attemptedStatement > 0) {
				try {
					connection.setAutoCommit(false);
					errorVersion(connection, dialect, table, currentRow, id, dbVersionHandler);
					commit(connection);
					historyOutcome = RecoveryOutcome.RETURNED;
				} catch (final SQLException | RuntimeException recoveryFailure) {
					historyOutcome = RecoveryOutcome.FAILED;
					if (e != recoveryFailure) {
						e.addSuppressed(recoveryFailure);
					}
				}
			}
			SQLException sqlFailure = null;
			for (Throwable cause = e; cause != null; cause = cause.getCause()) {
				if (cause instanceof SQLException sqlException) {
					sqlFailure = sqlException;
					break;
				}
			}
			executionFailure = new MigrationExecutionFailure(phase, id,
					source == null ? null : source.getAbsolutePath(), attemptedStatement, executedSqlCount.get(),
					nonTransactional, committedVersions, sqlFailure == null ? null : sqlFailure.getSQLState(),
					sqlFailure == null ? null : sqlFailure.getErrorCode(), rollbackOutcome, historyOutcome);
			error(executionFailure);
			error(executionFailure.recoveryAdvice());
			throw e;
		}
	}

	private boolean isNoTransaction(final SqlFile sqlFile) {
		return this.getNoTransactionFileFilter().test(getFile(sqlFile));
	}

	protected void executeMigrationLock(final Connection connection, final List<SqlOperation> operations)
			throws SQLException {
		try (Statement statement = connection.createStatement()) {
			if (lockTimeoutSeconds != null) {
				statement.setQueryTimeout(lockTimeoutSeconds);
			}
			for (final SqlOperation operation : operations) {
				if (!CommonUtils.isEmpty(operation.getSqlText())) {
					debug(operation.getSqlText());
					statement.execute(operation.getSqlText());
				}
			}
		} catch (final SQLTimeoutException e) {
			final DbConcurrencyException failure = new DbConcurrencyException(
					"Could not acquire migration history lock within " + lockTimeoutSeconds
							+ " seconds. Stop competing migrations or increase lockTimeoutSeconds.");
			failure.initCause(e);
			throw failure;
		}
	}

	protected void validateTransactionPolicy(final List<Row> rows, final Map<Long, SqlFile> sqlFiles,
			final DbVersionHandler handler) {
		if (!rejectNonTransactional) {
			return;
		}
		final List<Long> rejected = new ArrayList<>();
		for (final Row row : rows) {
			final Long version = handler.getId(row);
			final SqlFile sqlFile = sqlFiles.get(version);
			if (sqlFile != null && getNoTransactionFileFilter().test(sqlFile.getUpSqlFile())) {
				rejected.add(version);
			}
		}
		if (!rejected.isEmpty()) {
			throw new CommandException("Non-transactional migrations are rejected: " + rejected
					+ ". Disable rejectNonTransactional only after reviewing partial-failure recovery.");
		}
	}

	/**
	 * Rebuilds the selected plan while holding the migration-history lock. This is
	 * deliberately done before setup SQL so an approved plan cannot become stale in
	 * the interval between its initial check and the first migration side effect.
	 */
	protected void validateExpectedPlanAfterLock(final Connection connection, final Dialect dialect,
			final List<SqlFile> sqlFiles, final DbVersionHandler dbVersionHandler) throws SQLException {
		if (expectedPlanFile == null) {
			return;
		}
		final DialectTableHolder locked = new DialectTableHolder();
		locked.dialect = dialect;
		locked.sqlFiles = sqlFiles;
		locked.table = dbVersionHandler.createVersionTableDefinition(schemaChangeLogTableName);
		dbVersionHandler.load(connection, dialect, locked.table);
		if (isChecksumValidation()) {
			validateChecksums(locked.table, dbVersionHandler, sqlFiles);
		}
		dbVersionHandler.mergeSqlFiles(sqlFiles, locked.table);
		validateOutOfOrder(locked.table, dbVersionHandler);
		locked.rows = getVersionRows(locked.table, sqlFiles, dbVersionHandler);
		validateExpectedPlan(connection, locked, dbVersionHandler);
	}

	protected void validateRollbackPolicy(final List<Row> rows, final Map<Long, SqlFile> sqlFiles,
			final DbVersionHandler handler) {
		if (!requireDownMigration) {
			return;
		}
		final List<Long> missing = new ArrayList<>();
		for (final Row row : rows) {
			final Long version = handler.getId(row);
			final SqlFile sqlFile = sqlFiles.get(version);
			if (sqlFile != null && CommonUtils.isEmpty(sqlFile.getDownSqls())) {
				missing.add(version);
			}
		}
		if (!missing.isEmpty()) {
			throw new CommandException("Down migration SQL is required for versions: " + missing);
		}
	}

	protected boolean recordsChecksum() {
		return true;
	}

	private DbConcurrencyException concurrentHistoryChange(final Long id) {
		return new DbConcurrencyException("Migration history changed concurrently for version " + id
				+ " in " + getSchemaChangeLogTableName()
				+ ". Stop competing migrations, inspect the current history, then retry.");
	}

	protected File getFile(final SqlFile sqlFile) {
		return sqlFile.getUpSqlFile();
	}

	private AtomicInteger executedSqlCount = new AtomicInteger(0);

	protected void executeSql(final Connection connection, final SqlConverter sqlConverter, final SqlFile sqlFile)
			throws SQLException {
		final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		final ParametersContext context = new ParametersContext();
		context.putAll(this.getContext());
		final List<SplitResult> sqls = getSqls(sqlFile);
		executedSqlCount.set(0);
		if (!CommonUtils.isEmpty(sqls)) {
			this.info("versionNumber=" + sqlFile.getVersionNumber());
			for (final SplitResult splitResult : sqls) {
				attemptedStatement = executedSqlCount.get() + 1;
				executeSql(connection, dialect, sqlConverter, context, splitResult);
				executedSqlCount.incrementAndGet();
			}
		}
	}

	protected void executeSql(final Connection connection, final SqlConverter sqlConverter,
			final ParametersContext context, final List<SplitResult> splitResults) throws SQLException {
		final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		for (SplitResult splitResult : splitResults) {
			attemptedStatement = executedSqlCount.get() + 1;
			executeSql(connection, dialect, sqlConverter, context, splitResult);
			executedSqlCount.incrementAndGet();
		}
	}

	protected void executeSql(final Connection connection, final Dialect dialect, final SqlConverter sqlConverter,
			final ParametersContext context, final SplitResult splitResult) throws SQLException {
		final SqlNode sqlNode = sqlConverter.parseSql(dialect, context, splitResult.getText());
		final JdbcHandler jdbcHandler = new JdbcHandler(sqlNode);
		this.debug(splitResult.getText());
		jdbcHandler.execute(connection, context);
	}

	protected List<SplitResult> getSqls(final SqlFile sqlFile) {
		return sqlFile.getUpSqls();
	}

	protected boolean preCheck(final Connection connection, final Dialect dialect, final Table table, final Long id,
			final Row row, final DbVersionHandler dbVersionHandler) throws SQLException {
		return !dbVersionHandler.exists(dialect, connection, table, id);
	}

	protected boolean startVersion(final Connection connection, final Dialect dialect, final Table table, final Row row,
			final Long seriesNumber, final DbVersionHandler dbVersionHandler) throws SQLException {
		try {
			dbVersionHandler.insertVersion(connection, dialect, table, row, seriesNumber, Status.Started);
			return true;
		} catch (final SQLIntegrityConstraintViolationException e) {
			return false;
		}
	}

	protected void finalizeVersion(final Connection connection, final Dialect dialect, final Table table, final Row row,
			final Long id, final DbVersionHandler dbVersionHandler) throws SQLException {
		dbVersionHandler.updateVersion(connection, dialect, table, row, id, Status.Started, Status.Completed);
	}

	protected void errorVersion(final Connection connection, final Dialect dialect, final Table table, final Row row,
			final Long id, final DbVersionHandler dbVersionHandler) throws SQLException {
		if (dbVersionHandler.updateVersion(connection, dialect, table, row, id, Status.Started, Status.Errored) == 0
				&& !dbVersionHandler.exists(dialect, connection, table, id)) {
			// Rollback may have removed Started even when SQL on another connection committed.
			dbVersionHandler.insertVersion(connection, dialect, table, row, id, Status.Errored);
		}
	}

	protected void deleteVersion(final Connection connection, final Dialect dialect, final Table table, final Row row,
			final DbVersionHandler dbVersionHandler) throws SQLException {
		dbVersionHandler.deleteVersion(connection, dialect, table, row);
	}

	protected void deleteVersion(Connection connection, final Table table, final long id) throws SQLException {
		final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		final DbVersionHandler dbVersionHandler = createDbVersionHandler();
		dbVersionHandler.deleteVersion(connection, dialect, table, id);
	}

	protected String getName(final Table table) {
		if (CommonUtils.isEmpty(table.getSchemaName())) {
			return table.getName();
		}
		return table.getSchemaName() + "." + table.getName();
	}

}
