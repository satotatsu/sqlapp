/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

public class SapHanaAlterUserFactory extends SapHanaCreateUserFactory {
	@Override
	protected SqlType operation() {
		return SqlType.ALTER;
	}

	@Override
	public java.util.List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		var original = (User) difference.getOriginal();
		var target = (User) difference.getTarget();
		if (!java.util.Objects.equals(original.getName(), target.getName())
				|| !java.util.Objects.equals(original.getDefaultSchemaName(), target.getDefaultSchemaName())
				|| !java.util.Objects.equals(original.getSpecifics().get("IS_RESTRICTED"),
						target.getSpecifics().get("IS_RESTRICTED"))
				|| !java.util.Objects.equals(original.getSpecifics().get("IS_PASSWORD_ENABLED"),
						target.getSpecifics().get("IS_PASSWORD_ENABLED")))
			throw new IllegalArgumentException("SAP HANA user identity/authentication changes require explicit DDL");
		var settings = target.clone();
		if (java.util.Objects.equals(original.getPassword(), target.getPassword()))
			settings.setPassword(null);
		settings.getSpecifics().remove("IS_RESTRICTED");
		boolean lifetimeChanged = !java.util.Objects.equals(
				original.getSpecifics().get("PASSWORD_LIFETIME_CHECK_ENABLED"),
				target.getSpecifics().get("PASSWORD_LIFETIME_CHECK_ENABLED"));
		for (String key : java.util.List.of("PASSWORD_CHANGE_NEEDED", "PASSWORD_LIFETIME_CHECK_ENABLED",
				"USER_DEACTIVATED")) {
			if (original.getSpecifics().containsKey(key) && !target.getSpecifics().containsKey(key))
				throw new IllegalArgumentException(
						"SAP HANA user setting removal requires an explicit TRUE/FALSE: " + key);
			if (key.equals("PASSWORD_CHANGE_NEEDED") && (settings.getPassword() != null || lifetimeChanged))
				continue;
			if (java.util.Objects.equals(original.getSpecifics().get(key), target.getSpecifics().get(key)))
				settings.getSpecifics().remove(key);
		}
		return createSql(settings);
	}
}
