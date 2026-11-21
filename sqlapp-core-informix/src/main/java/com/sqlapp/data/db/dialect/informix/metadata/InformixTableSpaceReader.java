/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TableSpaceFileReader;
import com.sqlapp.data.db.metadata.TableSpaceReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.TableSpace;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Server storage-space inventory, without guessing chunk locations or CREATE
 * DDL.
 */
public class InformixTableSpaceReader extends TableSpaceReader {
	public InformixTableSpaceReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<TableSpace> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo version) {
		// pagesize was added in Informix 10. Older/unknown versions use only the
		// baseline columns.
		final boolean pageSize = version != null && version.getMajorVersion() != null
				&& version.getMajorVersion() >= 10;
		final var parameters = context.clone();
		parameters.put("hasPageSize", pageSize);
		final List<TableSpace> result = new ArrayList<>();
		execute(connection, getSqlNodeCache().getString("tableSpaces.sql"), parameters, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String name = required(rs, "space_name");
				final var space = new TableSpace(name).setOwnerName(required(rs, "space_owner"));
				space.setId(required(rs, "space_number"));
				final int nativeFlags = Integer.parseInt(required(rs, "space_flags"));
				if (nativeFlags < Short.MIN_VALUE || nativeFlags > 0xffff)
					throw new IllegalStateException("Invalid Informix storage flags: " + name);
				final int flags = nativeFlags & 0xffff;
				space.getSpecifics().put("INFORMIX_SPACE_FLAGS", Integer.toString(flags));
				space.getSpecifics().put("INFORMIX_SPACE_KIND",
						(flags & 0x8000) != 0 ? "SBSPACE" : (flags & 0x10) != 0 ? "BLOBSPACE" : "DBSPACE");
				if (pageSize) {
					final int size = Integer.parseInt(required(rs, "page_size"));
					if (size <= 0)
						throw new IllegalStateException("Invalid Informix storage page size: " + name);
					space.getSpecifics().put("INFORMIX_PAGE_SIZE", Integer.toString(size));
				}
				result.add(space);
			}
		});
		return result;
	}

	static String required(final ExResultSet rs, final String label) throws SQLException {
		final String value = rs.getString(label);
		if (value == null || value.isBlank())
			throw new IllegalStateException("Missing Informix storage metadata: " + label);
		return value.stripTrailing();
	}

	@Override
	protected TableSpaceFileReader newTableSpaceFileReader() {
		return null;
	}
}
