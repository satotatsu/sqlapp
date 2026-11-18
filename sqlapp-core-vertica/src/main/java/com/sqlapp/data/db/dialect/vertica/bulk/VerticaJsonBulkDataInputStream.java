/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica.bulk;

import java.util.LinkedHashMap;
import com.sqlapp.data.db.datatype.IntervalArrays;
import com.sqlapp.data.db.dialect.vertica.VerticaIntervalJdbcTypeHandler;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.util.JsonConverter;

/** One JSON object per row; typed interval leaves never pass through floating point. */
final class VerticaJsonBulkDataInputStream extends VerticaBulkDataInputStream {
	private final JsonConverter json = new JsonConverter();
	VerticaJsonBulkDataInputStream(Table table, BulkOption options) { super(validate(table,options),options); }
	private static Table validate(Table table,BulkOption options) {
		for (var column : table.getColumns()) {
			if (writable(column,options) && column.getArrayDimension()>0 && (column.getDataType()==null || !column.getDataType().isInterval()
					|| column.getDataType().name().contains("YEAR") || column.getDataType()==com.sqlapp.data.db.datatype.DataType.INTERVAL_MONTH))
				throw new IllegalArgumentException("Native JSON COPY requires DAY/TIME interval array columns: "+column.getName());
		}
		return table;
	}
	@Override protected String encodeRow(Row row) {
		var record=new LinkedHashMap<String,Object>();
		for (var column : getColumns()) {
			Object raw=row.get(column.getOrdinal());
			if(binary(column)) {
				if(raw!=null && !(raw instanceof byte[]))throw new IllegalArgumentException("JSON COPY requires byte[] for "+column.getName());
				byte[] bytes=(byte[])raw;
				if(bytes!=null && bytes.length>65000)throw new IllegalArgumentException("JSON COPY binary cells support up to 65000 bytes");
				String hex=bytes==null?null:java.util.HexFormat.of().formatHex(bytes);
				record.put(binaryInputName(column,0),hex==null?null:hex.substring(0,Math.min(65000,hex.length())));
				record.put(binaryInputName(column,1),hex==null?null:hex.substring(Math.min(65000,hex.length())));
			} else record.put(inputName(column),value(column,raw));
		}
		return json.toJsonString(record)+"\n";
	}
	private Object value(Column column,Object value) {
		if(value==null) return null;
		if(column.getDataType()!=null && column.getDataType().isInterval()) {
			var handler=new VerticaIntervalJdbcTypeHandler(column.getDataType());
			Object projected=handler.prepareInterval(value,column).value();
			var scalar=column.clone().setArrayDimension(0);
			return IntervalArrays.map(projected,column.getArrayDimension(),leaf->handler.text(leaf,scalar));
		}
		if(value instanceof Float floatNumber && !Float.isFinite(floatNumber) || value instanceof Double doubleNumber && !Double.isFinite(doubleNumber))
			throw new IllegalArgumentException("JSON COPY requires finite numbers");
		if(value instanceof Number || value instanceof Boolean || value instanceof String) return value;
		if(value instanceof Character || value instanceof java.util.UUID || value instanceof java.time.temporal.TemporalAccessor
				|| value instanceof java.sql.Date || value instanceof java.sql.Time || value instanceof java.sql.Timestamp) return value.toString();
		throw new IllegalArgumentException("Unsupported native JSON COPY scalar for column "+column.getName());
	}
}
