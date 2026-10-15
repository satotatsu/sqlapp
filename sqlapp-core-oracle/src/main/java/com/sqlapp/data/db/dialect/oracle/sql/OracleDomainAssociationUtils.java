/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.DomainColumn;

/** Oracle-specific roles of components in flexible domain associations. */
public final class OracleDomainAssociationUtils {
	public static final String KIND = "oracle.domain.kind";
	public static final String DISCRIMINANT = "oracle.domain.discriminant";
	private OracleDomainAssociationUtils() { }
	public static boolean isFlexible(Domain domain) {
		return "FLEXIBLE".equals(domain.getSpecifics().get(KIND));
	}
	public static boolean isDiscriminant(DomainColumn column) {
		String value = column.getSpecifics().get(DISCRIMINANT);
		if (value == null || "false".equalsIgnoreCase(value)) return false;
		if ("true".equalsIgnoreCase(value)) return true;
		throw new IllegalArgumentException("Invalid domain discriminant flag: " + column.getName());
	}
}
