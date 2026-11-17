/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.metadata;

import com.sqlapp.data.schemas.ProductVersionInfo;

/** Catalog version boundaries shared by the H2 metadata readers. */
final class H2Versions {
	private H2Versions() {
	}

	/** H2 2.0 and later use the modern INFORMATION_SCHEMA. */
	static boolean isModern(ProductVersionInfo version) {
		return version != null && version.getMajorVersion() != null && version.getMajorVersion() >= 2;
	}

	/** H2 2.2 and later expose UNIQUE null treatment. */
	static boolean supportsNullTreatment(ProductVersionInfo version) {
		return isModern(version) && (version.getMajorVersion() > 2
				|| version.getMinorVersion() != null && version.getMinorVersion() >= 2);
	}
}
