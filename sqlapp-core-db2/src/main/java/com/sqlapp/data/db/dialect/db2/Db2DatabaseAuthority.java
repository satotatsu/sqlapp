/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.ProductVersionInfo;

/** Database-level SQL authority names and their versioned catalog columns. */
public enum Db2DatabaseAuthority {
	BINDADD("BINDADDAUTH", 0), CONNECT("CONNECTAUTH", 0), CREATETAB("CREATETABAUTH", 0), DBADM("DBADMAUTH", 0),
	CREATE_NOT_FENCED_ROUTINE("NOFENCEAUTH", 0), IMPLICIT_SCHEMA("IMPLSCHEMAAUTH", 0), LOAD("LOADAUTH", 0),
	CREATE_EXTERNAL_ROUTINE("EXTERNALROUTINEAUTH", 91), QUIESCE_CONNECT("QUIESCECONNECTAUTH", 91),
	SECADM("SECURITYADMAUTH", 91), ACCESSCTRL("ACCESSCTRLAUTH", 97), DATAACCESS("DATAACCESSAUTH", 97),
	SQLADM("SQLADMAUTH", 97), WLMADM("WLMADMAUTH", 97), EXPLAIN("EXPLAINAUTH", 97),
	CREATE_SECURE_OBJECT("CREATESECUREAUTH", 101);

	private final String columnName;
	private final int version;

	Db2DatabaseAuthority(String columnName, int version) {
		this.columnName = columnName;
		this.version = version;
	}

	public String columnName() {
		return columnName;
	}

	public boolean supports(ProductVersionInfo product) {
		return switch (version) {
		case 91 -> product.gte(9, 1, 0);
		case 97 -> product.gte(9, 7, 0);
		case 101 -> product.gte(10, 1, 0);
		default -> true;
		};
	}

	public boolean supports(Dialect dialect) {
		return switch (version) {
		case 91 -> dialect instanceof Db2_910;
		case 97 -> dialect instanceof Db2_970;
		case 101 -> dialect instanceof Db2_1010;
		default -> true;
		};
	}
}
