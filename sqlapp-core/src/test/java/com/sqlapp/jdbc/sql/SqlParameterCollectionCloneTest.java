package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

class SqlParameterCollectionCloneTest {

	@Test
	void cloneCopiesHoldersAndParameterStateWithoutMutatingOriginal() {
		SqlParameterCollection original = new SqlParameterCollection(null);
		Column column = new Column("id").setDataType(DataType.INT);
		BindParameter parameter = new BindParameter();
		parameter.setColumn(column);
		parameter.setValue(17);
		parameter.fixed();
		original.add(parameter);
		BindParameterHolder group = new BindParameterHolder();
		group.getBindParameters().add(parameter.clone());
		original.add(group);
		SqlParameterCollection copy = original.clone();
		assertEquals(2, original.getBindParameters().size());
		assertEquals(2, copy.getParameterSize());
		BindParameter copied = copy.getBindParameters().get(0).getBindParameter();
		assertNotSame(parameter, copied);
		assertSame(column, copied.getColumn());
		assertEquals(17, copied.getValue());
		assertTrue(copied.isFixed());
		copied.setValue(99);
		copy.getBindParameters().get(1).getBindParameters().clear();
		copy.getBindParameters().clear();
		copy.addSql(" modified");
		assertEquals(17, parameter.getValue());
		assertEquals(1, group.getBindParameters().size());
		assertEquals(2, original.getBindParameters().size());
		assertEquals("?", original.getSql());
	}
}
