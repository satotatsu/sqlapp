/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.db2.sql.*;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

/**
 * Complete definitions compose the existing Db2 factories without borrowing LUW
 * modeled options.
 */
public final class Db2HostDefinitionFactories {
	private Db2HostDefinitionFactories() {
	}

	public static class ProcedureCreate extends Db2CreateProcedureFactory {
		@Override
		public List<SqlOperation> createSql(Procedure routine) {
			requireDefinition(routine.getDefinition(), "PROCEDURE", routine.getName());
			List<SqlOperation> result = new ArrayList<>();
			for (String sql : Db2CreationContext.sql(routine.getSpecifics().get("QUALIFIER"),
					routine.getSpecifics().get("FUNC_PATH")))
				addSql(result, createSqlBuilder()._add(sql), SqlType.SET_SEARCH_PATH_TO_SCHEMA, routine);
			result.addAll(super.createSql(routine));
			return result;
		}
	}

	public static class FunctionCreate extends Db2CreateFunctionFactory {
		@Override
		public List<SqlOperation> createSql(Function routine) {
			requireDefinition(routine.getDefinition(), "FUNCTION", routine.getName());
			List<SqlOperation> result = new ArrayList<>();
			for (String sql : Db2CreationContext.sql(routine.getSpecifics().get("QUALIFIER"),
					routine.getSpecifics().get("FUNC_PATH")))
				addSql(result, createSqlBuilder()._add(sql), SqlType.SET_SEARCH_PATH_TO_SCHEMA, routine);
			result.addAll(super.createSql(routine));
			return result;
		}
	}

	public static class TriggerCreate extends Db2CreateTriggerFactory {
		@Override
		protected List<Trigger> sort(List<Trigger> triggers) {
			return triggers;
		}

		@Override
		public List<SqlOperation> createSql(Trigger trigger) {
			requireDefinition(trigger.getDefinition(), "TRIGGER", trigger.getName());
			if (!trigger.isEnable())
				throw new IllegalArgumentException(
						"Disabled host trigger requires an explicit enable/disable restore plan: " + trigger.getName());
			return super.createSql(trigger);
		}
	}

	private static void requireDefinition(List<String> definition, String kind, String name) {
		if (definition.size() != 1 || definition.get(0) == null || definition.get(0).indexOf('\0') >= 0
				|| !definition.get(0).stripLeading().matches("(?is)CREATE\\s+(?:OR\\s+REPLACE\\s+)?" + kind + "\\b.*"))
			throw new IllegalArgumentException("Host " + kind
					+ " requires one complete CREATE definition; a catalog body alone cannot restore all options: "
					+ name);
	}
}
