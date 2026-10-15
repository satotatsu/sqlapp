/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.sql;

import com.sqlapp.data.schemas.Domain;
import java.util.List;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Create Domain生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractCreateDomainFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractCreateNamedObjectFactory<Domain, S> {
	@Override
	public List<SqlOperation> createSql(Domain domain) {
		if (supportsDomainDefinition() && !CommonUtils.isEmpty(domain.getDefinition())) {
			return super.createSql(domain);
		}
		if (!domain.getColumns().isEmpty()) {
			if (!supportsDomainColumns()) {
				throw new IllegalArgumentException("Multi-column domains are not supported by this SQL factory: " + domain.getName());
			}
			if (domain.getDataType() != null || domain.getDataTypeName() != null || domain.getDefaultValue() != null || domain.isNotNull()) {
				throw new IllegalArgumentException("Configure types, defaults and nullability on domain columns: " + domain.getName());
			}
			for (var column : domain.getColumns()) {
				if (column.getDataType() == null && column.getDataTypeName() == null) {
					throw new IllegalArgumentException("Domain column requires a data type: " + column.getName());
				}
			}
		}
		if (!domain.getChecks().isEmpty()) {
			if (!supportsDomainChecks()) {
				throw new IllegalArgumentException("Named domain checks are not supported by this SQL factory: " + domain.getName());
			}
			if (!CommonUtils.isEmpty(domain.getCheck())) {
				throw new IllegalArgumentException("Use either check or checks for domain: " + domain.getName());
			}
			for (var check : domain.getChecks()) {
				if (check.getExpression() == null || check.getExpression().isBlank()) {
					throw new IllegalArgumentException("Domain check requires an expression: " + domain.getName() + "." + check.getName());
				}
			}
		}
		return super.createSql(domain);
	}

	protected boolean supportsDomainChecks() {
		return false;
	}

	/** Whether a complete definition makes separately modeled components optional. */
	protected boolean supportsDomainDefinition() {
		return false;
	}

	protected boolean supportsDomainColumns() {
		return false;
	}

}
