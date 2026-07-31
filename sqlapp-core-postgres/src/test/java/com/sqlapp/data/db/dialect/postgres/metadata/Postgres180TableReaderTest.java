package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;
import java.sql.Types;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;

class Postgres180TableReaderTest {

	@Test
	void testPostgres18CatalogColumnsAreRetained() throws Exception {
		CachedRowSet rowSet = rowSet();
		ExResultSet resultSet = new ExResultSet(rowSet);
		Table table = new TestReader().read(resultSet);

		assertEquals("42", table.getSpecifics().get("relallfrozen"));
		assertEquals("12.5",
				table.getStatistics().get("total_vacuum_time"));
		assertEquals("3.25",
				table.getStatistics().get("total_autovacuum_time"));
		assertEquals("7.75",
				table.getStatistics().get("total_analyze_time"));
		assertEquals("1.5",
				table.getStatistics().get("total_autoanalyze_time"));
	}

	private CachedRowSet rowSet() throws SQLException {
		String[] names = {
				"table_name", "schema_name", "remarks", "table_id",
				"relallfrozen", "total_vacuum_time",
				"total_autovacuum_time", "total_analyze_time",
				"total_autoanalyze_time"
		};
		int[] types = {
				Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
				Types.BIGINT, Types.DOUBLE, Types.DOUBLE, Types.DOUBLE,
				Types.DOUBLE
		};
		RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
		metadata.setColumnCount(names.length);
		for (int i = 0; i < names.length; i++) {
			metadata.setColumnName(i + 1, names[i]);
			metadata.setColumnLabel(i + 1, names[i]);
			metadata.setColumnType(i + 1, types[i]);
		}
		CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
		rowSet.setMetaData(metadata);
		rowSet.moveToInsertRow();
		rowSet.updateString("table_name", "events");
		rowSet.updateString("schema_name", "public");
		rowSet.updateString("remarks", "");
		rowSet.updateString("table_id", "100");
		rowSet.updateLong("relallfrozen", 42L);
		rowSet.updateDouble("total_vacuum_time", 12.5);
		rowSet.updateDouble("total_autovacuum_time", 3.25);
		rowSet.updateDouble("total_analyze_time", 7.75);
		rowSet.updateDouble("total_autoanalyze_time", 1.5);
		rowSet.insertRow();
		rowSet.moveToCurrentRow();
		rowSet.beforeFirst();
		rowSet.next();
		return rowSet;
	}

	private static class TestReader extends Postgres180TableReader {
		TestReader() {
			super(DialectHolder.postgreSQL180);
		}

		Table read(ExResultSet resultSet) throws SQLException {
			return createTable(resultSet);
		}
	}
}
