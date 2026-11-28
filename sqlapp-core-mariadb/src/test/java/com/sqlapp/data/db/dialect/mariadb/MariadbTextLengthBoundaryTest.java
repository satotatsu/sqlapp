package com.sqlapp.data.db.dialect.mariadb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.LengthProperties;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;

class MariadbTextLengthBoundaryTest {
	@Test
	void selectsTextTypesAtStorageBoundaries() {
		for (int[] version : new int[][] { { 5, 5 }, { 10, 0 }, { 10, 5 }, { 11, 4 }, { 11, 8 }, { 12, 1 } }) {
			var dialect = DialectResolver.getInstance().getDialect("mariadb", version[0], version[1], 0);
			for (long[] boundary : new long[][] { { 255, 0 }, { 256, 1 }, { 65535, 1 }, { 65536, 2 }, { 16777215, 2 },
					{ 16777216, 3 }, { 4294967295L, 3 } }) {
				String expected = new String[] { "TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT" }[(int) boundary[1]];
				var type = dialect.getDbDataTypes().getDbType(DataType.LONGVARCHAR, boundary[0]);
				assertEquals(expected, type.getTypeName(), dialect + ": " + boundary[0]);
				long max = new long[] { 255, 65535, 16777215, 4294967295L }[(int) boundary[1]];
				assertEquals(max, ((LengthProperties<?>) type).getMaxLength().longValue());
				var column = new Column("body").setDataType(DataType.LONGVARCHAR).setLength(boundary[0]);
				assertEquals(expected, dialect.createSqlBuilder().addTypeDefinition(column).toString());
			}
		}
	}
}
