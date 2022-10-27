/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.saphana.util;

import java.sql.SQLException;
import java.util.function.Consumer;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.properties.SpecificsProperty;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.EnumUtils;

public enum SapHanaIndexOptions {
	LOAD_UNIT() {
		@Override
		public void setIndex(Index obj, Object value) {
			setSpecificsProperty(obj, value);
		}
		@Override
		public void setUniqueConstraint(UniqueConstraint obj, Object value) {
			setSpecificsProperty(obj, value);
		}
	},
	FILLFACTOR(){
		@Override
		public void setIndex(Index obj, Object value) {
			setSpecificsProperty(obj, value);
		}
		@Override
		public void setUniqueConstraint(UniqueConstraint obj, Object value) {
			setSpecificsProperty(obj, value);
		}
		@Override
		protected void setSpecificsProperty(SpecificsProperty<?> obj, final Object value){
			Integer val=Converters.getDefault().convertObject(value, Integer.class);
			if (val==null) {
				obj.getSpecifics().remove(getColumnKey());
				obj.getSpecifics().remove(toString());
			}else if (val.intValue()>=0&&val.intValue()<=100) {
				obj.getSpecifics().remove(getColumnKey());
				obj.getSpecifics().remove(toString());
				if (val.intValue()!=0) {
					obj.getSpecifics().put(this.toString(), val);
				}
			}
		}
		@Override
		public String getColumnKey() {
			return "BTREE_FILL_FACTOR";
		}
	},
	;

	public Class<?> getValueClass() {
		return null;
	}

	public Object getDefaultValue() {
		return null;
	}

	public void setIndex(Index obj, Object value){
		setSpecificsProperty(obj, value);
	}

	public void setTable(Table obj, String value) {
	}

	public void setUniqueConstraint(UniqueConstraint obj, Object value) {
		setSpecificsProperty(obj, value);
	}

	public void setTable(final ExResultSet rs, Table table) throws SQLException {
		setParams(rs, val->{
			if (val!=null) {
				setTable(table, val.toString());
			} else {
				setTable(table, null);
			}
		});
	}

	public void setIndex(final ExResultSet rs, Index index) throws SQLException {	
		setParams(rs, val->{
			if (val!=null) {
				setIndex(index, val);
			} else {
				setIndex(index, null);
			}
		});
	}

	public static void setAllIndex(final ExResultSet rs, Index index) throws SQLException {	
		for(SapHanaIndexOptions enm:SapHanaIndexOptions.values()) {
			enm.setIndex(rs, index);
		}
	}

	public String getColumnKey() {
		return this.toString();
	}

	protected void setSpecificsProperty(SpecificsProperty<?> obj, final Object value){
		if (value==null) {
			obj.getSpecifics().remove(this.toString().toUpperCase());
		} else {
			obj.getSpecifics().put(this.toString().toUpperCase(), value.toString().toUpperCase());
		}
	}

	public void setUniqueConstraint(final ExResultSet rs, UniqueConstraint obj) throws SQLException {	
		setParams(rs, val->{
			if (val!=null) {
				setUniqueConstraint(obj, val);
			} else {
				setUniqueConstraint(obj, null);
			}
		});
	}

	public static void setAllUniqueConstraint(final ExResultSet rs, UniqueConstraint uk) throws SQLException {	
		for(SapHanaIndexOptions enm:SapHanaIndexOptions.values()) {
			enm.setUniqueConstraint(rs, uk);
		}
	}

	protected void setParams(final ExResultSet rs, Consumer<Object> cons) throws SQLException {
		if (!rs.contains(this.getColumnKey())) {
			return;
		}
		Object val = rs.getObject(this.getColumnKey());
		cons.accept(val);
	}

	public static SapHanaIndexOptions parse(Object obj) {
		SapHanaIndexOptions enm=EnumUtils.parse(SapHanaIndexOptions.class, obj);
		if (enm!=null) {
			return enm;
		}
		if (obj instanceof String) {
			String val=String.class.cast(obj).toUpperCase().replace("_", "");
			for(SapHanaIndexOptions e:SapHanaIndexOptions.values()) {
				if (CommonUtils.eq(val, e.toString().replace("_", ""))) {
					return e;
				}
			}
		}
		return null;
	}

}
