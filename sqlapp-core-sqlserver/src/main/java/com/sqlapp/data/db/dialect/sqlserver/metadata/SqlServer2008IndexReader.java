/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * SqlServer2008のインデックス読み込みクラス
 * 
 * @author satoh
 * 
 */
public class SqlServer2008IndexReader extends SqlServer2005IndexReader {

	public SqlServer2008IndexReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("indexes2008.sql");
	}

	@Override
	protected IndexReader newFullTextIndexReader() {
		final IndexReader reader = new SqlServer2008FullTextIndexReader(
				this.getDialect());
		return reader;
	}

	/**
	 * フィルタ選択されたインデックスに含まれる行のサブセットの式
	 */
	protected static final String FILTER_DEFINITION = "filter_definition";
	/**
	 * テセレーション スキームの名前(GEOMETRY_GRID OR GEOGRAPHY_GRID)
	 */
	protected static final String TESSELLATION_SCHEMA = "tessellation_scheme";
	/**
	 * 境界ボックスの右上隅の X 座標(GEOMETRY_GRIDのみ)
	 */
	protected static final String BOUNDING_BOX_XMAX = "bounding_box_xmax";
	/**
	 * 境界ボックスの左上隅の X 座標(GEOMETRY_GRIDのみ)
	 */
	protected static final String BOUNDING_BOX_XMIN = "bounding_box_xmin";
	/**
	 * 境界ボックスの右上隅の Y 座標(GEOMETRY_GRIDのみ)
	 */
	protected static final String BOUNDING_BOX_YMAX = "bounding_box_ymax";
	/**
	 * 境界ボックスの左下隅の Y 座標(GEOMETRY_GRIDのみ)
	 */
	protected static final String BOUNDING_BOX_YMIN = "bounding_box_ymin";
	/**
	 * 最上位レベルのグリッドのグリッド密度
	 */
	protected static final String LEVEL_1_GRID = "level_1_grid";
	/**
	 * 第 2レベルのグリッドのグリッド密度
	 */
	protected static final String LEVEL_2_GRID = "level_2_grid";
	/**
	 * 第 3レベルのグリッドのグリッド密度
	 */
	protected static final String LEVEL_3_GRID = "level_3_grid";
	/**
	 * 第 4レベルのグリッドのグリッド密度
	 */
	protected static final String LEVEL_4_GRID = "level_4_grid";
	/**
	 * 空間オブジェクトごとのセル数
	 */
	protected static final String CELLS_PER_OBJECT = "cells_per_object";
	/**
	 * STATISTICS_NORECOMPUTE
	 */
	public static final String STATISTICS_NORECOMPUTE = "STATISTICS_NORECOMPUTE";
	/**
	 * ALLOW_PAGE_LOCKS
	 */
	public static final String ALLOW_PAGE_LOCKS = "ALLOW_PAGE_LOCKS";
	/**
	 * ONLINE
	 */
	public static final String ONLINE = "ONLINE";
	/**
	 * SORT_IN_TEMPDB
	 */
	public static final String SORT_IN_TEMPDB = "SORT_IN_TEMPDB";
	/**
	 * MAXDOP
	 */
	public static final String MAXDOP = "MAXDOP";
	/**
	 * ONLINE
	 */
	public static final String COMPRESSION = "COMPRESSION";
	 
	@Override
	protected Index createIndex(final ExResultSet rs) throws SQLException {
		final Index index = super.createIndex(rs);
		index.setWhere(getString(rs, FILTER_DEFINITION));
		if (index.getIndexType() == IndexType.Spatial) {
			setSpecifics(rs, TESSELLATION_SCHEMA, index);
			setSpecifics(rs, BOUNDING_BOX_XMAX, index);
			setSpecifics(rs, BOUNDING_BOX_XMIN, index);
			setSpecifics(rs, BOUNDING_BOX_YMAX, index);
			setSpecifics(rs, BOUNDING_BOX_YMIN, index);
			setSpecifics(rs, LEVEL_1_GRID, index);
			setSpecifics(rs, LEVEL_2_GRID, index);
			setSpecifics(rs, LEVEL_3_GRID, index);
			setSpecifics(rs, LEVEL_4_GRID, index);
			setSpecifics(rs, CELLS_PER_OBJECT, index);
		}
		setSpecifics(rs, STATISTICS_NORECOMPUTE, index);
		setSpecifics(rs, ALLOW_PAGE_LOCKS, index);
		setSpecifics(rs, ONLINE, index);
		setSpecifics(rs, SORT_IN_TEMPDB, index);
		setSpecifics(rs, MAXDOP, index);
		setSpecifics(rs, COMPRESSION, index);
		return index;
	}
}
