package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Event;

/** Scheduler inventory is not a complete deployment representation. */
public class OracleSchedulerJobSqlFactory extends AbstractSqlFactory<Event, OracleSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Event event) {
		throw new IllegalArgumentException(
				"Oracle Scheduler job metadata is read-only; supply an explicit DBMS_SCHEDULER migration "
						+ "including arguments, program/schedule dependencies and enable state: "
						+ event.getSchemaName() + "." + event.getName());
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (!difference.getState().isChanged())
			return List.of();
		return createSql(difference.getTarget() instanceof Event event ? event : difference.getOriginal(Event.class));
	}
}
