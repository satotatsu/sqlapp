package com.sqlapp.data.db.dialect.vertica.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ProjectionReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Bulk named projection design metadata; node instances are not separate Schema
 * objects.
 */
public class VerticaProjectionReader extends ProjectionReader {
	protected VerticaProjectionReader(Dialect dialect) {
		super(dialect);
	}

	/** Full reads enrich the already filtered object set in one export query. */
	@Override
	protected void setMetadataDetail(Connection connection, ParametersContext context, List<Projection> projections)
			throws SQLException {
		if (!projections.isEmpty())
			new VerticaDdlExporter().exportProjectionDefinitions(connection, projections);
	}

	@Override
	protected List<Projection> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		var projections = new LinkedHashMap<List<String>, Projection>();
		var sorts = new IdentityHashMap<Projection, SortedMap<Integer, String>>();
		execute(connection, getSqlNodeCache().getString("projections.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schema = getString(rs, "PROJECTION_SCHEMA");
				String name = getString(rs, "PROJECTION_NAME");
				var key = Arrays.asList(schema, name);
				Projection projection = projections.get(key);
				if (projection == null) {
					projection = new Projection(name);
					projection.setSchemaName(schema);
					projection.setTableSchemaName(getString(rs, "TABLE_SCHEMA_NAME"));
					projection.setTableName(getString(rs, "TABLE_NAME"));
					setSpecifics(rs, "PROJECTION_BASENAME", projection);
					setSpecifics(rs, "IS_SEGMENTED", projection);
					setSpecifics(rs, "SEGMENT_EXPRESSION", projection);
					setSpecifics(rs, "IS_SUPER_PROJECTION", projection);
					projections.put(key, projection);
					sorts.put(projection, new TreeMap<>());
				}
				String column = getString(rs, "PROJECTION_COLUMN_NAME");
				if (column != null && !projection.getColumns().contains(column)) {
					projection.getColumns().add(column);
					var target = projection.getColumns().get(column);
					setSpecifics(rs, "DATA_TYPE", target);
					setSpecifics(rs, "ENCODING_TYPE", target);
				}
				Integer position = VerticaProjectionReader.this.getInteger(rs, "SORT_POSITION");
				if (column != null && position != null)
					sorts.get(projection).put(position, column);
			}
		});
		projections.values().forEach(p -> sorts.get(p).values().forEach(name -> p.getSortColumns().add(name)));
		return new ArrayList<>(projections.values());
	}
}
