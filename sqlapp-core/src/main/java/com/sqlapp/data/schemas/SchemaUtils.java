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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.properties.CharacterSemanticsProperty;
import com.sqlapp.data.schemas.properties.CharacterSetProperty;
import com.sqlapp.data.schemas.properties.CollationProperty;
import com.sqlapp.data.schemas.properties.DataTypeNameProperty;
import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.data.schemas.properties.ProductProperties;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.data.schemas.properties.TableNameProperty;
import com.sqlapp.util.ClassFinder;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;
import com.sqlapp.util.Factory;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.SeparatedStringBuilder;
import com.sqlapp.util.SimpleBeanUtils;
import com.sqlapp.util.StaxReader;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.StringUtils;
import com.sqlapp.util.TripleKeyMap;
import com.sqlapp.util.xml.StaxElementHandler;

/**
 * スキーマ関連のユーティリティ
 * 
 * @author tatsuo satoh
 * 
 */
public class SchemaUtils {

	private SchemaUtils() {
	}

	/**
	 * 指定したファイルのXMLの内容を自動判定してDBオブジェクトを返します。内容の読み込みは行いません。
	 * 
	 * @param path
	 * @throws FileNotFoundException
	 * @throws XMLStreamException
	 */
	private static <V extends DbCommonObject<?>> V getByXml(final Class<?> clazz,
			final String path) throws FileNotFoundException, XMLStreamException {
		final InputStream fis = FileUtils.getInputStream(clazz, path);
		if (fis == null) {
			throw new FileNotFoundException(path);
		}
		return getByXml(fis);
	}

	/**
	 * 指定したStreamのXMLの内容を自動判定してDBオブジェクトを返します。内容の読み込みは行いません。
	 * 
	 * @param is
	 * @throws FileNotFoundException
	 * @throws XMLStreamException
	 */
	private static <V extends DbCommonObject<?>> V getByXml(final InputStream is) throws XMLStreamException {
		BufferedInputStream bis = null;
		try {
			bis = new BufferedInputStream(is);
			@SuppressWarnings("resource")
			final
			StaxReader staxReader = new StaxReader(bis, "UTF-8");
			staxReader.nextFristStartElement();
			if (!staxReader.isStartElement()) {
				return null;
			}
			final String name = staxReader.getName().getLocalPart();
			return createInstance(name);
		} catch (final XMLStreamException e) {
			throw e;
		} finally {
			FileUtils.close(bis);
			FileUtils.close(is);
		}
	}

	/**
	 * 指定したファイルのXMLの内容を自動判定してDBオブジェクトを返します。内容の読み込みは行いません。
	 * 
	 * @param path
	 * @throws FileNotFoundException
	 * @throws XMLStreamException
	 */
	private static <V extends DbCommonObject<?>> V getByXml(final String path)
			throws FileNotFoundException, XMLStreamException {
		return getByXml(SchemaUtils.class, path);
	}

	/**
	 * 指定したファイルのXMLの内容を自動判定してDBオブジェクトを返します。
	 * 
	 * @param clazz
	 * @param path
	 * @throws XMLStreamException
	 * @throws IOException 
	 */
	public static <V extends DbCommonObject<?>> V readXml(final Class<?> clazz,
			final String path) throws XMLStreamException, IOException {
		return readXml(FileUtils.getInputStream(clazz, path));
	}

	/**
	 * 指定したファイルのXMLの内容を自動判定してDBオブジェクトを返します。
	 * 
	 * @param path
	 * @throws FileNotFoundException
	 * @throws XMLStreamException
	 */
	public static <V extends DbCommonObject<?>> V readXml(final String path)
			throws FileNotFoundException, XMLStreamException {
		final V obj = getByXml(path);
		obj.loadXml(path);
		return obj;
	}

	/**
	 * 指定したファイルのXMLの内容を自動判定してDBオブジェクトを返します。
	 * 
	 * @param file
	 * @throws XMLStreamException
	 * @throws IOException 
	 */
	public static <V extends DbCommonObject<?>> V readXml(final File file)
			throws XMLStreamException, IOException {
		return readXml(new FileInputStream(file));
	}

	/**
	 * 指定したInputStreamのXMLの内容を自動判定してDBオブジェクトを返します。
	 * 
	 * @param is 入力になるXMLファイル
	 * @throws XMLStreamException
	 * @throws IOException 
	 */
	public static <V extends DbCommonObject<?>> V readXml(final InputStream is)
			throws XMLStreamException, IOException {
		BufferedInputStream bis = null;
		final int readlimit=1024*10;
		try {
			bis = new BufferedInputStream(is, readlimit);
			bis.mark(readlimit);
			@SuppressWarnings("resource")
			final
			StaxReader staxReader = new StaxReader(bis, "UTF-8");
			staxReader.nextFristStartElement();
			if (!staxReader.isStartElement()) {
				return null;
			}
			final String name = staxReader.getName().getLocalPart();
			final V obj= createInstance(name);
			bis.reset();
			obj.loadXml(bis);
			return obj;
		} catch (final XMLStreamException e) {
			throw e;
		} finally {
			FileUtils.close(bis);
			FileUtils.close(is);
		}
	}

	/**
	 * 指定したReaderのXMLの内容を自動判定してDBオブジェクトを返します。
	 * 
	 * @param reader 入力になるXMLファイルのReader
	 * @throws XMLStreamException
	 * @throws IOException 
	 */
	public static <V extends DbCommonObject<?>> V readXml(final Reader reader)
			throws XMLStreamException, IOException {
		BufferedReader br = null;
		final int readlimit=1024*10;
		try {
			br = new BufferedReader(reader, readlimit);
			br.mark(readlimit);
			@SuppressWarnings("resource")
			final
			StaxReader staxReader = new StaxReader(reader);
			staxReader.nextFristStartElement();
			if (!staxReader.isStartElement()) {
				return null;
			}
			final String name = staxReader.getName().getLocalPart();
			final V obj= createInstance(name);
			br.reset();
			obj.loadXml(br);
			return obj;
		} catch (final XMLStreamException e) {
			throw e;
		} finally {
			FileUtils.close(br);
			FileUtils.close(reader);
		}
	}

	/**
	 * オブジェクトのリストをXMLに書き込みます。
	 * 
	 * @param stream
	 *            出力先のストリーム
	 * @throws XMLStreamException
	 */
	public static <V extends DbCommonObject<?>> void writeAllXml(final List<V> list,
			final OutputStream stream) throws XMLStreamException {
		for (final V obj : list) {
			obj.writeXml(stream);
		}
	}

	/**
	 * オブジェクトのリストをXMLに書き込みます。
	 * 
	 * @param writer
	 *            Writer
	 * @throws XMLStreamException
	 */
	public static <V extends DbCommonObject<?>> void writeAllXml(final List<V> list,
			final Writer writer) throws XMLStreamException {
		for (final V obj : list) {
			obj.writeXml(writer);
		}
	}

	/**
	 * オブジェクトのリストをXMLに書き込みます。
	 * 
	 * @param writer
	 *            StaxWriter
	 * @throws XMLStreamException
	 */
	public static <V extends DbCommonObject<?>> void writeAllXml(final List<V> list,
			final StaxWriter writer) throws XMLStreamException {
		for (final V obj : list) {
			obj.writeXml(writer);
		}
	}

	/**
	 * オブジェクトのリストをXMLに書き込みます。
	 * 
	 * @param list
	 * @param path
	 * @throws XMLStreamException
	 * @throws FileNotFoundException
	 */
	public static <V extends DbCommonObject<?>> void writeAllXml(final List<V> list,
			final String path) throws XMLStreamException, FileNotFoundException {
		FileOutputStream fos = null;
		BufferedOutputStream bos = null;
		try {
			fos = new FileOutputStream(path);
			bos = new BufferedOutputStream(fos);
			final StaxWriter staxWriter = new StaxWriter(bos);
			for (final V obj : list) {
				obj.writeXml(staxWriter);
			}
		} finally {
			FileUtils.close(bos);
			FileUtils.close(fos);
		}
	}

	private static final Map<String, Factory<? extends DbCommonObject<?>>> FACTORYS = CommonUtils
			.upperMap();

	/**
	 * 指定した名称のオブジェクトを作成します
	 * 
	 * @param name
	 */
	@SuppressWarnings("unchecked")
	public static <V extends DbCommonObject<?>> V createInstance(final String name) {
		if (FACTORYS.size()==0){
			loadFactories();
		}
		final Factory<? extends DbCommonObject<?>> factory = FACTORYS.get(name);
		if (factory == null) {
			throw new UnsupportedOperationException(name + " does not support.");
		}
		return (V) factory.newInstance();
	}

	protected static void registerFactory(final String name,
			final Factory<? extends DbCommonObject<?>> factory) {
		FACTORYS.put(name, factory);
	}
	
	@SuppressWarnings("unchecked")
	private static synchronized void loadFactories(){
		final Set<Class<?>> clazzes=getSubClasses(DbCommonObject.class);
		for(final Class<?> clazz:clazzes){
			registerFactory((Class<DbCommonObject<?>>)clazz);
		}
	}

	protected static void registerFactory(
			final Class<? extends DbCommonObject<?>> clazz) {
		if (clazz.getSimpleName().endsWith("Collection")) {
			registerFactory(StringUtils.uncapitalize(getPluralName(clazz)),
					new Factory<DbCommonObject<?>>() {
						@Override
						public DbCommonObject<?> newInstance() {
							return newInstanceAtSchemas(clazz);
						}
					});
		} else {
			registerFactory(StringUtils.uncapitalize(clazz.getSimpleName()),
					new Factory<DbCommonObject<?>>() {
						@Override
						public DbCommonObject<?> newInstance() {
							return newInstanceAtSchemas(clazz);
						}
					});
		}
	}

	/**
	 * 指定したクラスの複数形を取得します
	 * 
	 * @param clazz
	 */
	public static String getPluralName(final Class<?> clazz) {
		final String val = clazz.getSimpleName();
		return getPluralName(val);
	}

	/**
	 * 指定した名称の複数形を取得します
	 * 
	 * @param name
	 */
	public static String getPluralName(final String name) {
		String value=PLURAL_NAME_CACHE.get(name);
		if (value!=null) {
			return value;
		}
		
		value= getPluralNameInternal(name);
		PLURAL_NAME_CACHE.putIfAbsent(name, value);
		return value;
	}

	private static Map<String, String> PLURAL_NAME_CACHE=CommonUtils.concurrentMap();
	
	/**
	 * 指定した名称の複数形を取得します
	 * 
	 * @param clazz
	 */
	private static String getPluralNameInternal(String name) {
		if (name.endsWith("ss")) {
			return name+"es";
		}
		if (name.endsWith("s")) {
			return name;
		}
		if (name.endsWith("Collection")) {
			name = name.substring(0, name.length() - "Collection".length());
		}
		if (name.endsWith("ss")) {
			name = name + "es";
		}else if (name.endsWith("x")) {
			name = name + "es";
		} else if (name.endsWith("y")) {
			name = name.substring(0, name.length() - 1) + "ies";
		} else {
			name = name + "s";
		}
		return name;
	}

	
	/**
	 * 指定した名称の単数形を取得します
	 * 
	 * @param name
	 */
	public static String getSingularName(String name) {
		if (name.endsWith("ss")) {
			return name;
		}else if (name.endsWith("ies")) {
			name = name.substring(0, name.length() - 3) + "y";
		}else if (name.endsWith("xes")) {
			name = name.substring(0, name.length() - 2);
		}else if (name.endsWith("ses")) {
			name = name.substring(0, name.length() - 2);
		}else if (name.endsWith("s")) {
			name = name.substring(0, name.length() - 1);
		}else if (name.endsWith("Collection")) {
			name = name.substring(0, name.length() - "Collection".length());
		}
		return name;
	}

	/**
	 * インスタンスの作成
	 * 
	 * @param <V>
	 * @param clazz
	 */
	public static <V> V newInstanceAtSchemas(final Class<V> clazz) {
		try {
			return clazz.getDeclaredConstructor().newInstance();
		} catch (final Exception e) {
			try {
				return clazz.getConstructor().newInstance();
			} catch (final Exception e1) {
				throw new RuntimeException(e1);
			}
		}
	}

	/**
	 * NamedArgumentにRoutineを設定します
	 * 
	 * @param arg
	 * @param routine
	 */
	public static void setRoutine(final NamedArgument arg, final Routine<?> routine) {
		arg.setRoutine(routine);
	}

	/**
	 * リストをソートした結果を返します。
	 * 
	 * @param list
	 * @param comparator
	 */
	@SuppressWarnings("unchecked")
	public static <V> List<V> getNewSortedList(final List<V> list,
			final Comparator<V> comparator) {
		final List<V> result = CommonUtils.list(list);
		final V t=CommonUtils.first(list);
		if (t instanceof Table){
			return (List<V>)getNewSortedTableList((List<Table>)list, (Comparator<Table>)comparator, table->table);
		}
		Collections.sort(result, comparator);
		return result;
	}

	/**
	 * テーブルをソートした結果を返します。
	 * 
	 * @param list
	 * @param comparator
	 */
	public static List<Table> getNewSortedTableList(final List<Table> list,
			final Comparator<Table> comparator) {
		return getNewSortedTableList(list, comparator, table->table);
	}
	
	/**
	 * テーブルをソートした結果を返します。
	 * 
	 * @param list
	 * @param comparator
	 */
	public static <V> List<V> getNewSortedTableList(final List<V> list,
			final Comparator<Table> comparator, final java.util.function.Function<V, Table> f) {
		if (!(comparator instanceof TableCreateOrderComparator)&&!(comparator instanceof TableDropOrderComparator)){
			final List<V> tmp = CommonUtils.list(list);
			Collections.sort(tmp, (o1,o2)->comparator.compare(f.apply(o1), f.apply(o2)));
			return tmp;
		}
		if (CommonUtils.isEmpty(list)){
			return list;
		}
		final List<V> noReferences = CommonUtils.list();
		final List<V> hasReference = CommonUtils.list();
		final TripleKeyMap<String,String,String, Table> referenced = CommonUtils.tripleKeyMap();
		final TripleKeyMap<String,String,String, Table> hasFks = CommonUtils.tripleKeyMap();
		for(final V t:list){
			final Table table=f.apply(t);
			final List<ForeignKeyConstraint> fks=table.getConstraints().getForeignKeyConstraints();
			if (!fks.isEmpty()){
				hasFks.put(null, table.getSchemaName(), table.getName(), table);
			}
			for(final ForeignKeyConstraint fk:fks){
				referenced.put(null, fk.getRelatedTable().getSchemaName(), fk.getRelatedTable().getName(), fk.getRelatedTable());
			}
		}
		for(final V t:list){
			final Table table=f.apply(t);
			Table refTable=hasFks.get(null, table.getSchemaName(), table.getName());
			if (refTable==null){
				refTable=referenced.get(null, table.getSchemaName(), table.getName());
				if (refTable==null){
					noReferences.add(t);
					continue;
				}
			}
			hasReference.add(t);
		}
		final List<V> sorted1=getNewSortedTableListInternal(noReferences, comparator, f);
		final List<V> sorted2=getNewSortedTableListInternal(hasReference, comparator, f);
		sorted1.addAll(sorted2);
		return sorted1;
	}
	
	/**
	 * テーブルをもつオブジェクトをソートした結果を返します。
	 * 
	 * @param list
	 * @param comparator
	 * @param f テーブルを返す関数
	 */
	private static <V> List<V> getNewSortedTableListInternal(final List<V> list,
			final Comparator<Table> comparator, final java.util.function.Function<V, Table> f) {
		if (list.isEmpty()){
			return list;
		}
		final List<V> tmp = CommonUtils.list(list);
		Collections.sort(tmp, (o1,o2)->comparator.compare(f.apply(o1), f.apply(o2)));
		final List<TablePoint<V>> tpList=tmp.stream().map(t->{
			final TablePoint<V> tp=new TablePoint<V>(t, f);
			return tp;
		}).collect(Collectors.toList());
		int counter=0;
		for(int i=tpList.size()-1;i>=0;i--){
			final TablePoint<V> tp=tpList.get(i);
			tp.minus(counter++);
		}
		for(int i=0;i<tpList.size();i++){
			for(int j=i+1;j<tpList.size();j++){
				final TablePoint<V> tp1=tpList.get(i);
				final TablePoint<V> tp2=tpList.get(j);
				final int ret=comparator.compare(tp1.getTable(), tp2.getTable());
				if (ret>0){
					tp2.setPoint(Math.min(tp1.point, tp2.point)).minus(ret);
				} else if (ret<0){
					tp1.setPoint(Math.min(tp1.point, tp2.point)).minus(-ret);
				}
			}
		}
		for(int i=0;i<tpList.size()-1;i++){
			for(int j=i+1;j<tpList.size();j++){
				final TablePoint<V> tp1=tpList.get(i);
				final TablePoint<V> tp2=tpList.get(j);
				tpList.set(j, tp2);
				tpList.set(i, tp1);
			}
		}
//		Collections.sort(tpList);
		final List<V> result=tpList.stream().map(tp->tp.getObject()).collect(Collectors.toList());
		//Collections.sort(result, comparator);
		return result;
	}
	
	static class TablePoint<V> implements Comparable<TablePoint<V>>{
		TablePoint(final V object, final java.util.function.Function<V, Table> f){
			this.object=object;
			this.f=f;
		}
		private final V object;
		private final java.util.function.Function<V, Table> f;
		private int point=Integer.MAX_VALUE;

		/**
		 * @return the table
		 */
		public V getObject() {
			return object;
		}
		
		/**
		 * @return the table
		 */
		public Table getTable() {
			return f.apply(object);
		}

		public TablePoint<V> setPoint(final int point){
			this.point=point;
			return this;
		}
		
		public TablePoint<V> minus(){
			point--;
			return this;
		}

		public TablePoint<V> minus(final int val){
			this.point=point-val;
			return this;
		}

		public long point(){
			return point;
		}

		@Override
		public int compareTo(final TablePoint<V> o) {
			return this.point-o.point;
		}
	
		@Override
		public String toString(){
			final StringBuilder builder=new StringBuilder();
			builder.append(getTable().getName());
			builder.append("(");
			builder.append(point);
			builder.append(")");
			return builder.toString();
		}
	}
	
	/**
	 * オブジェクトの検証を行います
	 * 
	 * @param object
	 */
	public static void validate(final AbstractDbObject<?> object) {
		object.validate();
	}

	/**
	 * オブジェクトの検証を行います
	 * 
	 * @param object
	 */
	public static void validate(final AbstractDbObjectCollection<?> object) {
		object.validate();
	}

	private static final Map<Class<?>, Set<Class<?>>> CLASSES_CACHE = CommonUtils
			.map();

	/**
	 * DROP可能なクラスを取得します
	 * 
	 */
	public static Set<Class<?>> getDroppableClasses() {
		final Set<Class<?>> classes = getNamedObjectClasses();
		final Set<Class<?>> result = CommonUtils.set(classes);
		result.remove(AssemblyFile.class);
		result.remove(Catalog.class);
		result.remove(Column.class);
		result.remove(DimensionLevel.class);
		result.remove(DimensionLevelColumn.class);
		result.remove(NamedArgument.class);
		result.remove(Setting.class);
		result.remove(TableSpaceFile.class);
		result.remove(TypeColumn.class);
		result.remove(Partition.class);
		result.remove(DimensionAttribute.class);
		result.remove(DimensionHierarchy.class);
		result.remove(DimensionHierarchyLevel.class);
		result.remove(DimensionAttributeColumn.class);
		result.remove(DimensionHierarchyJoinKeyColumn.class);
		return result;
	}

	/**
	 * 名前付きオブジェクトのコレクションを名前をキーにしたマップに変換します。
	 * @param c 名前付きオブジェクトのコレクション
	 * @return 名前をキーにしたマップ
	 */
	public static <V extends NameProperty<?>> Map<String, V> toMap(final Collection<V> c){
		if (c==null){
			return Collections.emptyMap();
		}
		final Map<String, V> map=CommonUtils.map();
		final Map<String, V> cmap=CommonUtils.caseInsensitiveLinkedMap();
		c.stream().forEach(v->{
			map.put(v.getName(), v);
			cmap.put(v.getName(), v);
		});
		if (map.size()==cmap.size()){
			return cmap;
		}
		return map;
	}
	
	/**
	 * スキーマオブジェクトのコレクションをスキーマ名、名前をキーにしたマップに変換します。
	 * @param c 名前付きオブジェクトのコレクション
	 * @return スキーマ名、名前をキーにしたマップ
	 */
	public static <V extends AbstractSchemaObject<?>> DoubleKeyMap<String, String, V> toDoubleKeyMap(final Collection<V> c){
		return DoubleKeyMap.toMap(c, v->v.getSchemaName(), v->v.getName());
	}

	/**
	 * スキーマオブジェクトのコレクションをスキーマ名、名前をキーにしたマップに変換します。
	 * @param c 名前付きオブジェクトのコレクション
	 * @return スキーマ名、名前をキーにしたマップ
	 */
	public static <V extends AbstractSchemaObject<?>> DoubleKeyMap<String, String, List<V>> toDoubleKeyListMap(final Collection<V> c, final java.util.function.Function<V,String> func){
		return DoubleKeyMap.toListMap(c, v->v.getSchemaName(), v->func.apply(v));
	}

	
	/**
	 * スキーマオブジェクトのコレクションをカタログ名、スキーマ名、名前をキーにしたマップに変換します。
	 * @param c 名前付きオブジェクトのコレクション
	 * @return カタログ名、スキーマ名、名前をキーにしたマップ
	 */
	public static <V extends AbstractSchemaObject<?>> TripleKeyMap<String, String, String, V> toTripleKeyMap(final Collection<V> c){
		return TripleKeyMap.toMap(c, v->v.getCatalogName(), v->v.getSchemaName(), v->v.getName());
	}

	/**
	 * スキーマオブジェクトのコレクションをスキーマ名、名前をキーにしたマップに変換します。
	 * @param c 名前付きオブジェクトのコレクション
	 * @return スキーマ名、名前をキーにしたマップ
	 */
	public static <V extends AbstractSchemaObject<?>> TripleKeyMap<String, String, String, List<V>> toTripleKeyListMap(final Collection<V> c, final java.util.function.Function<V,String> func){
		return TripleKeyMap.toListMap(c, v->v.getCatalogName(), v->v.getSchemaName(), v->func.apply(v));
	}
	
	/**
	 * スキーマオブジェクトクラスを取得します
	 * 
	 */
	public static Set<Class<?>> getSchemaObjectClasses() {
		return getSubClasses(AbstractSchemaObject.class);
	}

	/**
	 * スキーマオブジェクトクラスを取得します
	 * 
	 */
	public static Set<Class<?>> getDbObjectClasses() {
		return getSubClasses(DbObject.class);
	}

	/**
	 * 名前付きオブジェクトクラスを取得します
	 * 
	 */
	public static Set<Class<?>> getNamedObjectClasses() {
		return getSubClasses(NameProperty.class);
	}

	/**
	 * 引数で指定したクラスのサブクラスを取得します
	 * 
	 * @param classes
	 */
	public static Set<Class<?>> getSubClasses(final Class<?>... classes) {
		final Set<Class<?>> ret = CommonUtils.set();
		for (final Class<?> clazz : classes) {
			ret.addAll(getSubClasses(clazz));
		}
		return ret;
	}
	
	public static String getSimpleName(final DbCommonObject<?> obj) {
		if (obj instanceof Row) {
			return ((Row)obj).getSimpleName();
		}else if (obj instanceof RowCollection) {
			return ((RowCollection)obj).getSimpleName();
		}else if (obj instanceof AbstractBaseDbObject) {
			return ((AbstractBaseDbObject<?>)obj).getSimpleName();
		}else if (obj instanceof AbstractBaseDbObjectCollection) {
			return ((AbstractBaseDbObjectCollection<?>)obj).getSimpleName();
		}
		return null;
	}

	/**
	 * 引数で指定したクラスのサブクラスを取得します
	 * 
	 * @param clazz
	 */
	public static Set<Class<?>> getSubClasses(final Class<?> clazz) {
		Set<Class<?>> classes = CLASSES_CACHE.get(clazz);
		if (classes != null) {
			return classes;
		}
		final List<Class<?>> ret = getClassesInternal(clazz);
		classes = Collections.unmodifiableSet(CommonUtils.linkedSet(ret));
		CLASSES_CACHE.put(clazz, classes);
		return classes;
	}

	protected static List<Class<?>> getClassesInternal(final Class<?> clazz) {
		final ClassFinder finder = new ClassFinder(Schema.class.getClassLoader());
		final Predicate<Class<?>> filter = new Predicate<Class<?>>() {
			@Override
			public boolean test(final Class<?> obj) {
				if (!clazz.isAssignableFrom(obj)) {
					return false;
				}
				if (Modifier.isAbstract(obj.getModifiers())) {
					return false;
				}
				if (obj.getSimpleName().startsWith("Dummy")) {
					return false;
				}
				if (obj.getSimpleName().startsWith("Reference")) {
					return false;
				}
				return true;
			}
		};
		finder.setFilter(filter);
		final List<Class<?>> ret = finder.find(Schema.class.getPackage().getName());
		return ret;
	}

	/**
	 * カタログ内の全テーブルを取得します
	 * 
	 * @param catalog
	 *            カタログ
	 */
	public static List<Table> getTables(final Catalog catalog) {
		final List<Table> tables = CommonUtils.list();
		for (final Schema schema : catalog.getSchemas()) {
			tables.addAll(schema.getTables());
		}
		return tables;
	}

	/**
	 * オブジェクトが持つDB情報からDialectを取得します。
	 * @param dbObject
	 */
	public static Dialect getDialect(final DbCommonObject<?> dbObject){
		if (dbObject instanceof ProductProperties){
			return getDialect((ProductProperties<?>)dbObject);
		}
		if (dbObject instanceof CatalogCollection){
			final CatalogCollection cc=(CatalogCollection)dbObject;
			return getDialect((ProductProperties<?>)cc.get(0));
		}else if (dbObject instanceof SchemaCollection){
			final SchemaCollection cc=(SchemaCollection)dbObject;
			return getDialect((ProductProperties<?>)cc.get(0));
		}
		return null;
	}

	/**
	 * オブジェクトが持つDB情報からDialectを取得します。
	 * @param obj
	 */
	public static Dialect getDialect(final ProductProperties<?> obj){
		if (obj==null){
			return null;
		}
		if (obj.getProductName()!=null){
			return DialectResolver.getInstance().getDialect(obj.getProductName(), obj.getProductMajorVersion(), obj.getProductMinorVersion());
		}
		return null;
	}

	/**
	 * Rowに直接値を設定します。
	 * @param row
	 * @param column
	 * @param value
	 */
	public static Object putDialect(final Row row, final Column column, final Object value){
		return row.putDirect(column, value);
	}
	
	/**
	 * 2つのカラムの名前が等しいかを調べます。
	 * @param column1
	 * @param column2
	 */
	public static boolean nameEquals(final NameProperty<?> column1, final NameProperty<?> column2){
		if (column1==null){
			if (column2==null){
				return true;
			} else{
				return false;
			}
		} else{
			if (column2==null){
				return false;
			} else{
				if (!CommonUtils.eq(column1.getName(), column2.getName())){
					return false;
				}
				if (column1 instanceof TableNameProperty&&column2 instanceof TableNameProperty){
					if (!CommonUtils.eq(((TableNameProperty<?>)column1).getTableName(), ((TableNameProperty<?>)column2).getTableName())){
						return false;
					}
				}
				if (column1 instanceof SchemaNameProperty&&column2 instanceof SchemaNameProperty){
					if (!CommonUtils.eq(((SchemaNameProperty<?>)column1).getSchemaName(), ((SchemaNameProperty<?>)column2).getSchemaName())){
						return false;
					}
				}
			}
		}
		return true;
	}


	public static Schema getSchema(final AbstractSchemaObject<?> obj){
		if (obj.getParent()==null){
			return null;
		}
		return obj.getAncestor(Schema.class);
	}

	public static Schema getSchemaFromParent(final Schema obj, final DbCommonObject<?> dbObject) {
		return getNamedObjectFromParentInternal(obj, dbObject, (name,catalog)->{
			if (obj==null||obj.getName()==null){
				return dbObject.getAncestor(Schema.class);
			}
			return catalog.getSchemas().get(name);
		});
	}

	public static TableSpace getTableSpaceFromParent(final TableSpace obj, final DbCommonObject<?> dbObject) {
		return getNamedObjectFromParentInternal(obj, dbObject, (name,catalog)->catalog.getTableSpaces().get(name));
	}

	public static User getUserFromParent(final User obj, final DbCommonObject<?> dbObject) {
		return getNamedObjectFromParentInternal(obj, dbObject, (name,catalog)->catalog.getUsers().get(name));
	}

	public static Role getRoleFromParent(final Role obj, final DbCommonObject<?> dbObject) {
		return getNamedObjectFromParentInternal(obj, dbObject, (name,catalog)->catalog.getRoles().get(name));
	}

	public static PartitionScheme getPartitionSchemeFromParent(final PartitionScheme obj, final DbCommonObject<?> dbObject) {
		return getNamedObjectFromParentInternal(obj, dbObject, (name,catalog)->catalog.getPartitionSchemes().get(name));
	}

	public static PartitionFunction getPartitionFunctionFromParent(final PartitionFunction obj, final DbCommonObject<?> dbObject) {
		return getNamedObjectFromParentInternal(obj, dbObject, (name,catalog)->catalog.getPartitionFunctions().get(name));
	}

	private static <V extends NameProperty<?>> V getNamedObjectFromParentInternal(final V obj, final DbCommonObject<?> dbObject, final BiFunction<String, Catalog, V> func) {
		if (obj == null) {
			return null;
		}
		if (obj.getName()==null) {
			return obj;
		}
		final Catalog catalog = dbObject.getAncestor(Catalog.class);
		if (catalog == null) {
			return obj;
		}
		final V getObject=func.apply(obj.getName(), catalog);
		if (getObject != null) {
			return getObject;
		}
		return obj;
	}
	
	public static Operator getOperatorFromParent(final Operator obj, final DbCommonObject<?> dbObject) {
		return getSchemaObjectFromParentInternal(obj, dbObject, (name,schema)->schema.getOperators().get(name));
	}

	public static Type getTypeFromParent(final Type obj, final DbCommonObject<?> dbObject) {
		return getSchemaObjectFromParentInternal(obj, dbObject, (name,schema)->schema.getTypes().get(name));
	}
	
	public static Table getTableFromParent(final Table obj, final DbCommonObject<?> dbObject) {
		return getSchemaObjectFromParentInternal(obj, dbObject, (name,schema)->schema.getTables().get(name));
	}

	public static Table getTableOnlyFromParent(final String schemaName,final String name, final DbCommonObject<?> dbObject) {
		return getSchemaObjectFromParentInternal(schemaName, name, dbObject, (nm,schema)->schema.getTables().get(nm));
	}

	public static Table getTableFromParent(final String schemaName,final String name, final DbCommonObject<?> dbObject) {
		return getSchemaObjectFromParentInternal(schemaName, name, dbObject, (nm,schema)->schema.getTable(nm));
	}

	public static Function getFunctionFromParent(final Function obj, final DbCommonObject<?> dbObject) {
		return getSchemaObjectFromParentInternal(obj, dbObject, (name,schema)->schema.getFunctions().get(name));
	}

	public static Sequence getSequenceFromParent(final Sequence obj, final DbCommonObject<?> dbObject) {
		return getSchemaObjectFromParentInternal(obj, dbObject, (name,schema)->schema.getSequences().get(name));
	}

	public static Index getIndexFromParent(final Index obj, final DbCommonObject<?> dbObject) {
		return getTableObjectFromParentInternal(obj, dbObject, (name,table)->table.getIndexes().get(name));
	}

	private static <V extends AbstractSchemaObject<?>> V getSchemaObjectFromParentInternal(final V obj, final DbCommonObject<?> dbObject, final BiFunction<String, Schema, V> func) {
		if (obj == null) {
			return null;
		}
		if (obj.getName()==null) {
			return obj;
		}
		final V ret=getSchemaObjectFromParentInternal(obj.getSchemaName(), obj.getName(), dbObject, func);
		if (ret != null) {
			return ret;
		}
		return obj;
	}

	private static <V extends AbstractSchemaObject<?>> V getSchemaObjectFromParentInternal(final String schemaName,final String name, final DbCommonObject<?> dbObject, final BiFunction<String, Schema, V> func) {
		if (name==null) {
			return null;
		}
		final String objSchemaName=(String)SchemaProperties.SCHEMA_NAME.getValue(dbObject);
		Schema schema;
		if (CommonUtils.eq(schemaName, objSchemaName)||schemaName==null){
			schema = dbObject.getAncestor(Schema.class);
		} else{
			final SchemaCollection schemas = dbObject.getAncestor(SchemaCollection.class);
			if (schemas==null){
				return null;
			}
			schema = schemas.get(schemaName);
		}
		if (schema == null) {
			return null;
		}
		final V getObject=func.apply(name, schema);
		if (getObject != null) {
			return getObject;
		}
		return null;
	}

	private static <V extends AbstractSchemaObject<?>> V getTableObjectFromParentInternal(final V obj, final DbCommonObject<?> dbObject, final BiFunction<String, Table, V> func) {
		if (obj == null) {
			return null;
		}
		if (obj.getName()==null) {
			return obj;
		}
		final Table table=dbObject.getAncestor(Table.class);
		if (table == null) {
			return obj;
		}
		final V getObject=func.apply(obj.getName(), table);
		if (getObject != null) {
			return getObject;
		}
		return obj;
	}

	public static Function getFunctionFromParent(final Function function,
			final OperatorArgument leftArgument, final OperatorArgument rightArgument, final DbCommonObject<?> dbObject) {
		if (function == null) {
			return function;
		}
		final Schema schema=dbObject.getAncestor(Schema.class);
		if (schema==null){
			return function;
		}
		OperatorArgument[] args = null;
		if (leftArgument == null) {
			if (rightArgument == null) {
				args = new OperatorArgument[0];
			} else {
				args = new OperatorArgument[1];
				args[0] = rightArgument;
			}
		} else {
			if (rightArgument == null) {
				args = new OperatorArgument[1];
				args[0] = leftArgument;
			} else {
				args = new OperatorArgument[2];
				args[0] = leftArgument;
				args[1] = rightArgument;
			}
		}
		final String name = getSpecificFunctionName(function, args);
		Function getFunction = schema.getFunctions()
				.get(name);
		if (getFunction != null) {
			return getFunction;
		}
		getFunction = schema.getFunctions()
				.get(function.getName());
		return function;
	}

	private static String getSpecificFunctionName(final Function function,
			final OperatorArgument[] args) {
		final SeparatedStringBuilder builder = new SeparatedStringBuilder(",")
				.setStart(function.getName() + "(").setEnd(")");
		for (int i = 0; i < args.length; i++) {
			builder.add(args[i].getDataTypeName());
		}
		return builder.toString();
	}

	private static Map<Class<?>, Set<ISchemaProperty>> ALL_SCHEMA_PROPERTIES_SET=CommonUtils.map();

	public static Set<ISchemaProperty> getAllSchemaProperties(final Class<?> clazz){
		Set<ISchemaProperty> set=ALL_SCHEMA_PROPERTIES_SET.get(clazz);
		if (set==null){
			final Set<ISchemaProperty> schemaProperties=getSchemaProperties(clazz);
			final Set<ISchemaProperty> schemaObjectProperties=getSchemaObjectProperties(clazz);
			set=CommonUtils.linkedSet(schemaProperties.size()+schemaObjectProperties.size());
			set.addAll(schemaProperties);
			set.addAll(schemaObjectProperties);
			if (!set.isEmpty()){
				ALL_SCHEMA_PROPERTIES_SET.put(clazz, set);
			}
		}
		return set;
	}

	private static Map<Class<?>, Set<ISchemaProperty>> SCHEMA_PROPERTIES_SET=CommonUtils.map();
	
	public static Set<ISchemaProperty> getSchemaProperties(final Class<?> clazz){
		Set<ISchemaProperty> set=SCHEMA_PROPERTIES_SET.get(clazz);
		if (set==null){
			set=CommonUtils.linkedSet();
			for(final ISchemaProperty prop:SchemaProperties.values()){
				if (prop.getPropertyClass().isAssignableFrom(clazz)){
					set.add(prop);
				}
			}
			if (!set.isEmpty()){
				SCHEMA_PROPERTIES_SET.put(clazz, set);
			}
		}
		return set;
	}

	public static Set<ISchemaProperty> copySchemaProperties(final Object from, final Object to){
		final Set<ISchemaProperty> set=getSchemaProperties(from.getClass());
		for(final ISchemaProperty prop:SchemaProperties.values()){
			final Object value=prop.getCloneValue(from);
			prop.setValue(to, value);
		}
		return set;
	}

	private static Map<Class<?>, Set<ISchemaProperty>> SCHEMA_OBJECT_PROPERTIES_SET=CommonUtils.map();

	public static Set<ISchemaProperty> getSchemaObjectProperties(final Class<?> clazz){
		Set<ISchemaProperty> set=SCHEMA_OBJECT_PROPERTIES_SET.get(clazz);
		if (set==null){
			set=CommonUtils.linkedSet();
			for(final ISchemaProperty prop:SchemaObjectProperties.values()){
				if (prop.getPropertyClass().isAssignableFrom(clazz)){
					set.add(prop);
				}
			}
			if (!set.isEmpty()){
				SCHEMA_OBJECT_PROPERTIES_SET.put(clazz, set);
			}
		}
		return set;
	}

	public static StaxElementHandler getStaxElementHandler(final Object obj){
		if (obj instanceof AbstractBaseDbObjectCollection){
			final AbstractBaseDbObjectCollection<?> c=(AbstractBaseDbObjectCollection<?>)obj;
			return c.getDbObjectXmlReaderHandler();
		}else if (obj instanceof AbstractBaseDbObject){
			final AbstractBaseDbObject<?> c=(AbstractBaseDbObject<?>)obj;
			return c.getDbObjectXmlReaderHandler();
		}else if (obj instanceof Column[]){
			return new ColumnCollectionXmlReaderHandler();
		}
		return null;
	}
	
	public static CharacterSemantics getParentCharacterSemantics(final DbCommonObject<?> obj){
		final CharacterSemanticsProperty<?> prop = obj.getAncestor(o->o instanceof CharacterSemanticsProperty);
		if (prop == null) {
			return null;
		}
		return prop.getCharacterSemantics();
	}

	public static String getParentCharacterSet(final DbCommonObject<?> obj){
		final CharacterSetProperty<?> prop = obj.getAncestor(o->o instanceof CharacterSetProperty);
		if (prop == null) {
			return null;
		}
		return prop.getCharacterSet();
	}

	public static String getParentCollation(final DbCommonObject<?> obj){
		final CollationProperty<?> prop = obj.getAncestor(o->o instanceof CollationProperty);
		if (prop == null) {
			return null;
		}
		return prop.getCollation();
	}
	
	public static String getProductInfo(final DbCommonObject<?> obj){
		Schema schema=null;
		if (obj instanceof Schema){
			schema=(Schema)obj;
		} else{
			schema=obj.getAncestor(Schema.class);
		}
		if (schema!=null){
			return schema.getProductVersionInfo().toString();
		}
		SchemaCollection schemas=null;
		if (obj instanceof SchemaCollection){
			schemas=(SchemaCollection)obj;
		} else{
			schemas=obj.getAncestor(SchemaCollection.class);
		}
		Catalog catalog=null;
		if (schemas!=null){
			catalog= schemas.getParent();
		}
		if (catalog!=null){
			return catalog.getProductVersionInfo().toString();
		}
		if (obj instanceof Catalog){
			catalog=(Catalog)obj;
		} else{
			catalog=obj.getAncestor(Catalog.class);
		}
		if (catalog!=null){
			return catalog.getProductVersionInfo().toString();
		}
		return null;
	}

	public static boolean setDataTypeNameInternal(final String dataTypeName, final DataTypeNameProperty<?> prop) {
		if (prop instanceof AbstractColumn) {
			((AbstractColumn<?>)prop).dataTypeName=dataTypeName;
			return true;
		}
		final boolean bool= SimpleBeanUtils.setField(prop, SchemaProperties.DATA_TYPE_NAME.getLabel(), dataTypeName);
		return bool;
	}

	public static String getDataTypeNameInternal(final DataTypeNameProperty<?> prop) {
		if (prop instanceof AbstractColumn) {
			return ((AbstractColumn<?>)prop).dataTypeName;
		}
		return SimpleBeanUtils.getField(prop, SchemaProperties.DATA_TYPE_NAME.getLabel());
	}

}
