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

package com.sqlapp.data.db.datatype;

import java.io.InputStream;
import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.sqlapp.data.converter.ByteArrayConverter;
import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.HexBinaryConverter;

import static com.sqlapp.util.CommonUtils.*;

/**
 * BLOBを表す型
 * @author satoh
 *
 */
public class BlobType extends AbstractLengthType<BlobType>{

	/** serialVersionUID */
	private static final long serialVersionUID = -8658816953027318522L;
	/**
	 * コンバータ
	 */
	protected ByteArrayConverter converter=new ByteArrayConverter();
	/**
	 * コンストラクタ
	 */
	public BlobType(){
		this(DataType.BLOB.getTypeName());
	}
	
	protected BlobType(String dataTypeName){
		this.setDataType(DataType.BLOB);
		initialize(dataTypeName);
		this.setJdbcTypeHandler(new BlobTypeHandler(this.getDataType().getJdbcType(), converter));
		this.setDefaultLength(Long.valueOf(LEN_2GB));
		setSupportsArray(false);
		this.setLiteralPrefix("0x");
		this.setLiteralSuffix("");
		this.setSqlTextConverter(new HexBinaryConverter());
	}

	/**
	 * BlobをJDBCで扱うためのデフォルトのハンドラー
	 * @author satoh
	 *
	 */
	private static class BlobTypeHandler extends DefaultJdbcTypeHandler{
		public BlobTypeHandler(java.sql.JDBCType jdbcType, Converter<?> converter) {
			super(jdbcType, converter);
		}
		/** serialVersionUID */
		private static final long serialVersionUID = -3446371652551511555L;
		@Override
		public void setObject(PreparedStatement stmt, int parameterIndex
				, Object x) throws SQLException{
			if (x==null){
				stmt.setNull(parameterIndex, java.sql.Types.BLOB);
				return;
			}
			if (x instanceof Blob){
				stmt.setBlob(parameterIndex, (Blob)x);
				return;
			}
			if (x instanceof InputStream){
				stmt.setBlob(parameterIndex, (InputStream)x);
				return;
			}
			Blob blob=null;
			try{
				blob=stmt.getConnection().createBlob();
				byte[] bytes=cast(this.statementConverter.convertObject(x));
				blob.setBytes(0, bytes);
				stmt.setBlob(parameterIndex, blob);
			} finally{
				if(blob!=null){
					blob.free();
				}
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.datatype.DbDataType#hashCode()
	 */
	@Override
	public int hashCode(){
		return super.hashCode();
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.datatype.DbDataType#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof BlobType)){
			return false;
		}
		return true;
	}
}
