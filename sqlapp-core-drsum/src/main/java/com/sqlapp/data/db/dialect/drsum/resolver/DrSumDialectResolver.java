/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-drsum.
 *
 * sqlapp-core-drsum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-drsum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-drsum.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.drsum.resolver;

import com.sqlapp.data.db.dialect.drsum.DialectHolder;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;

/**
 * Resolves Dr.Sum. The value the Dr.Sum JDBC driver returns from
 * {@code DatabaseMetaData#getDatabaseProductName()} has not been verified, so
 * the pattern accepts the product spellings "Dr.Sum", "DrSum", "Dr Sum" and
 * "Dr.Sum EA" case-insensitively, as well as the dialect name "drsum".
 */
public class DrSumDialectResolver extends ProductNameDialectResolver {

	public DrSumDialectResolver() {
		super("(?i)\\s*dr\\.?\\s*sum\\b.*", (major, minor, revision) -> DialectHolder.defaultDialect);
	}
}
