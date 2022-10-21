/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.schemas;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.map;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.util.CommonUtils;

/**
 * インデックスの種類
 * 
 * @author satoh
 * 
 */
public enum IndexType implements EnumProperties {
	BTree("BTREE", "(NORMAL|Btree)"),
	/**
	 * Compressed Prefix Btree
	 */
	CPBTree("CPBTREE"),
	/**
	 * RTREE
	 */
	RTree("RTREE"),
	/**
	 * TTREE
	 */
	TTree("TTREE"),
	/**
	 * Block Range Index
	 */
	BlockRange("BRIN", "(BRIN|BLOCK.*RANGE.*)"),
	/**
	 * Hash
	 */
	Hash("HASH"),
	/** Generalized Search Tree */
	Gist("GIST"),
	/** Generalized Inverted Index */
	Gin("GIN"),
	/**
	 * 空間インデックス
	 */
	Spatial("SPATIAL")
	, 
	/**
	 * XML
	 */
	Xml("XML")
	,
	/**
	 * CLUSTERED
	 */
	Clustered("CLUSTERED"){
		@Override
		public boolean isClusterd() {
			return true;
		}
	},
	/**
	 * CLUSTERED COLUMN STORE
	 */
	ClusteredColumnStore("CLUSTERED", "CLUSTERED\\s*COLUMN\\s*STORE"){
		@Override
		public boolean isClusterd() {
			return true;
		}
		@Override
		public boolean isColumnStore() {
			return true;
		}
	}, 
	/**
	 * NON CLUSTERED COLUMNSTORE
	 */
	NonClusteredColumnStore("NONCLUSTERED COLUMNSTORE", "CLUSTERED\\s*COLUMN\\s*STORE"){
		@Override
		public boolean isColumnStore() {
			return true;
		}
	}, 
	/**
	 * 
	 */
	BitMap("BITMAP"){
		@Override
		public boolean isBitMap() {
			return true;
		}
	},
	/**
	 * 
	 */
	Function("FUNCTION NORMAL", "FUNCTION.*NORMAL"){
		@Override
		public boolean isFunction() {
			return true;
		}
	},
	/**
	 * 
	 */
	FunctionBitmap("FUNCTION BITMAP", "FUNCTION.*BITMAP"){
		@Override
		public boolean isFunction() {
			return true;
		}
		@Override
		public boolean isBitMap() {
			return true;
		}
	},
	/**
	 * 
	 */
	FunctionDomain("FUNCTION DOMAIN", "FUNCTION.*DOMAIN"){
		@Override
		public boolean isFunction() {
			return true;
		}
	},
	/**
	 * 
	 */
	FullText("FULLTEXT", "FULL*.*TEXT"), 
	/**
	 * 
	 */
	InvertedValue("INVERTED VALUE", "INVERTED*.*VALUE"), 
	/**
	 * 
	 */
	InvertedHash("INVERTED HASH", "INVERTED*.*HASH"), 
	/**
	 * 
	 */
	InvertedIndivisual("INVERTED INDIVIDUAL", "INVERTED*.*INDIVIDUAL"), 
	/**
	 * DOMAIN
	 */
	Domain("DOMAIN"), 
	/**
	 * OTHER
	 */
	Other("OTHER");
	/**
	 * 代替型マップ
	 */
	private static Map<IndexType, IndexType> surrogateMap = map();

	/**
	 * 代替型マップの初期化
	 */
	static void initializeSurrogateMap() {
		surrogateMap.put(TTree, BTree);
		surrogateMap.put(RTree, BTree);
		surrogateMap.put(Hash, BTree);
		surrogateMap.put(Gist, BTree);
		surrogateMap.put(Gin, BTree);
		surrogateMap.put(Spatial, BTree);
		surrogateMap.put(Clustered, BTree);
		surrogateMap.put(ClusteredColumnStore, Clustered);
		surrogateMap.put(BitMap, BTree);
		surrogateMap.put(FunctionBitmap, Function);
		surrogateMap.put(FunctionDomain, Function);
		surrogateMap.put(InvertedHash, InvertedValue);
		surrogateMap.put(InvertedIndivisual, InvertedValue);
		surrogateMap.put(InvertedValue, BTree);
		surrogateMap.put(CPBTree, BTree);
	}

	/**
	 * スタティックコンストラクタ
	 */
	static {
		initializeSurrogateMap();
	}

	private final String text;

	private final Pattern pattern;

	private IndexType(final String text) {
		this.text = text.toUpperCase();
		pattern = Pattern.compile(text, Pattern.CASE_INSENSITIVE);
	}

	private IndexType(final String text, final String pattenText) {
		this.text = text;
		pattern = Pattern.compile(pattenText, Pattern.CASE_INSENSITIVE);
	}

	private static Map<String,IndexType> typeCache=CommonUtils.upperMap();
	
	/**
	 * インデックスタイプの文字列からの取得
	 * 
	 * @param text
	 */
	public static IndexType parse(final String text) {
		if (isEmpty(text)) {
			return null;
		}
		final IndexType type=typeCache.get(text);
		if (type!=null) {
			return type;
		}
		for (final IndexType enm : IndexType.values()) {
			final Matcher matcher = enm.pattern.matcher(text);
			if (matcher.matches()) {
				typeCache.put(text, enm);
				return enm;
			}
		}
		return null;
	}

	/**
	 * 代替型の取得
	 * 
	 */
	public IndexType getSurrogate() {
		if (surrogateMap.containsKey(this)) {
			return surrogateMap.get(this);
		}
		return null;
	}

	public boolean isClusterd() {
		return false;
	}

	public boolean isBitMap() {
		return false;
	}

	public boolean isColumnStore() {
		return false;
	}

	public boolean isFunction() {
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.EnumProperties#getDisplayName()
	 */
	@Override
	public String getDisplayName() {
		return text;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.EnumProperties#getDisplayName(java.util.Locale)
	 */
	@Override
	public String getDisplayName(final Locale locale) {
		return getDisplayName();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.EnumProperties#getSqlValue()
	 */
	@Override
	public String getSqlValue() {
		return getDisplayName();
	}
}
