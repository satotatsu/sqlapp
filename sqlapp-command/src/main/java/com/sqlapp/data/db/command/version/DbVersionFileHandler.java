/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command.version;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DateUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.ToStringBuilder;

public class DbVersionFileHandler {

	/**
	 * バージョンアップ用SQLのディレクトリ
	 */
	private File upSqlDirectory;
	/**
	 * バージョンダウン用のSQLのディレクトリ
	 */
	private File downSqlDirectory;
	
	private final Pattern fileNamePattern=Pattern.compile("([0-9]{1,20})\\_(.*\\.sql)");
	/**ファイルエンコーディング*/
	private String encoding="UTF8";

	private SqlSplitter sqlSplitter=new SqlSplitter();

	protected String getResource(final String fileName, final String encoding) {
		final InputStream is = FileUtils.getInputStream(this.getClass(), fileName);
		final String text = FileUtils.readText(is, encoding);
		return text;
	}

	/**
	 * @return the upSqlDirectory
	 */
	public File getUpSqlDirectory() {
		return upSqlDirectory;
	}

	/**
	 * @param upSqlDirectory the upSqlDirectory to set
	 */
	public void setUpSqlDirectory(final File upSqlDirectory) {
		this.upSqlDirectory = upSqlDirectory;
	}

	/**
	 * @param upSqlDirectory the upSqlDirectory to set
	 */
	public void setUpSqlDirectory(final String upSqlDirectory) {
		this.upSqlDirectory = new File(upSqlDirectory);
	}

	/**
	 * @return the downSqlDirectory
	 */
	public File getDownSqlDirectory() {
		return downSqlDirectory;
	}

	/**
	 * @param downSqlDirectory the downSqlDirectory to set
	 */
	public void setDownSqlDirectory(final File downSqlDirectory) {
		this.downSqlDirectory = downSqlDirectory;
	}

	/**
	 * @param downSqlDirectory the downSqlDirectory to set
	 */
	public void setDownSqlDirectory(final String downSqlDirectory) {
		this.downSqlDirectory = new File(downSqlDirectory);
	}

	/**
	 * 現在日時をバージョンとして指定したdescriptionとともにSQLファイルを追加します。
	 * @param description
	 * @throws IOException
	 */
	public void add(final String description) throws IOException{
		add(new Date(), description);
	}

	/**
	 * 指定した日時をversionとして、descriptionとともにSQLファイルを追加します。
	 * @param date
	 * @param description
	 * @throws IOException
	 */
	public void add(final Date date, final String description) throws IOException{
		add(DateUtils.format(date, "yyyyMMddHHmmssSSS"), description);
	}

	/**
	 * @return the sqlSplitter
	 */
	public SqlSplitter getSqlSplitter() {
		return sqlSplitter;
	}

	/**
	 * @param sqlSplitter the sqlSplitter to set
	 */
	public void setSqlSplitter(final SqlSplitter sqlSplitter) {
		this.sqlSplitter = sqlSplitter;
	}

	/**
	 * 指定したversionおよびdescriptionでSQLファイルを追加します。
	 * @param version
	 * @param description
	 * @throws IOException
	 */
	public void add(final String version, final String description) throws IOException{
		final String current=getFileName(version, description);
		File file=new File(this.upSqlDirectory, current);
		if (downSqlDirectory!=null&&!CommonUtils.eq(upSqlDirectory.getAbsolutePath(), downSqlDirectory.getAbsolutePath())){
			if (!file.exists()){
				FileUtils.createParentDirectory(file);
				file.createNewFile();
			}
			file=new File(this.downSqlDirectory, current);
			if (!file.exists()){
				FileUtils.createParentDirectory(file);
				file.createNewFile();
			}
		} else{
			if (!file.exists()){
				FileUtils.createParentDirectory(file);
				FileUtils.writeText(file.getAbsolutePath(), getEncoding(), getResource("template.sql", "UTF8"));
			}
		}
	}

	public void addUpDownSql(final Date date, final String name, final String upSql, final String downSql) throws IOException{
		addUpDownSql(DateUtils.format(date, "yyyyMMddHHmmssSSS"), name, upSql, downSql);
	}
	
	/**
	 * 指定したprefixおよび名称でSQLファイルを追加します。
	 * @param prefix
	 * @param name
	 * @param upSql
	 * @param downSql
	 * @throws IOException
	 */
	public void addUpDownSql(final String prefix, final String name, final String upSql, final String downSql) throws IOException{
		final String current=getFileName(prefix, name);
		File file=new File(this.upSqlDirectory, current);
		if (downSqlDirectory!=null&&!CommonUtils.eq(upSqlDirectory.getAbsolutePath(), downSqlDirectory.getAbsolutePath())){
			if (!file.exists()){
				FileUtils.createParentDirectory(file);
				FileUtils.writeText(file.getAbsolutePath(), getEncoding(), upSql);
			}
			file=new File(this.downSqlDirectory, current);
			if (!file.exists()){
				FileUtils.createParentDirectory(file);
				FileUtils.writeText(file.getAbsolutePath(), getEncoding(), downSql);
			}
		} else{
			if (!file.exists()){
				FileUtils.createParentDirectory(file);
				FileUtils.writeText(file.getAbsolutePath(), getEncoding(), getResource("template.sql", "UTF8"));
			}
		}
	}
	
	private String getFileName(final String prefix, final String name){
		final String current=prefix.replace(' ', '_')+"_"+name.replace(' ', '_')+".sql";
		return current;
	}
	
	/**
	 * 指定したprefixおよび名称のSQLファイルを削除します。
	 * @param prefix
	 * @param name
	 * @throws IOException
	 */
	public void remove(final String prefix, final String name) throws IOException{
		final String current=getFileName(prefix, name);
		File file=new File(this.upSqlDirectory, current);
		if (downSqlDirectory!=null&&!CommonUtils.eq(upSqlDirectory.getAbsolutePath(), downSqlDirectory.getAbsolutePath())){
			if (!file.exists()){
				file.delete();
			}
			file=new File(this.downSqlDirectory, current);
			if (!file.exists()){
				file.delete();
			}
		} else{
			if (file.exists()){
				file.delete();
			}
		}
	}

	/**
	 * ディレクトリ内の全バージョン差分SQLファイルを取得します。
	 * @return SQLファイルリスト
	 */
	public List<SqlFile> read(){
		final List<SqlFile> result=CommonUtils.list();
		final Map<String,SqlFile> map=CommonUtils.map();
		if (upSqlDirectory.exists()){
			final File[] files=upSqlDirectory.listFiles();
			if (files!=null) {
				for(final File file:files){
					final SqlFile sqlFile=getTargetSqlFile(file);
					if (sqlFile==null){
						continue;
					}
					sqlFile.setUpSqlFile(file);
					map.put(file.getName(), sqlFile);
					result.add(sqlFile);
				}
			}
		}
		if (downSqlDirectory!=null&&!CommonUtils.eq(upSqlDirectory.getAbsolutePath(), downSqlDirectory.getAbsolutePath())){
			final File[] files=downSqlDirectory.listFiles();
			if (files!=null) {
				for(final File file:files){
					SqlFile sqlFile=map.get(file.getName());
					if (sqlFile==null){
						sqlFile=getTargetSqlFile(file);
						if (sqlFile!=null){
							result.add(sqlFile);
							continue;
						}
					}
					if (sqlFile!=null){
						sqlFile.setDownSqlFile(file);
					}
				}
			}
		}
		Collections.sort(result);
		return result;
	}

	private SqlFile getTargetSqlFile(final File file){
		if (!file.isFile()){
			return null;
		}
		if (!file.getAbsolutePath().endsWith(".sql")){
			return null;
		}
		final String name=file.getName();
		final Matcher matcher=fileNamePattern.matcher(name);
		if (!matcher.matches()){
			return null;
		}
		final SqlFile sqlFile=new SqlFile(Long.valueOf(matcher.group(1)), this.getSqlSplitter());
		sqlFile.setEncoding(this.getEncoding());
		return sqlFile;
	}
	
	public static class SqlFile implements Comparable<SqlFile>{
		/**バージョン番号*/
		private Long versionNumber;
		/**適用対象SQLファイル*/
		private File upSqlFile;
		/**適用対象UNDO SQLファイル*/
		private File downSqlFile;
		/**文字エンコード*/
		private String encoding;
		
		private final SqlSplitter sqlSplitter;
		
		private List<SplitResult> upSqls=null;
		
		private List<SplitResult> downSqls=null;
		
		private static final Pattern UNDO_PATTERN=Pattern.compile("--[\\s]*//@UNDO\\s*", Pattern.CASE_INSENSITIVE);
		
		public SqlFile(final Long versionNumber, final SqlSplitter sqlSplitter){
			this.versionNumber=versionNumber;
			this.sqlSplitter=sqlSplitter;
		}

		/**
		 * @return the versionNumber
		 */
		public Long getVersionNumber() {
			return versionNumber;
		}

		/**
		 * @param versionNumber the versionNumber to set
		 */
		public void setVersionNumber(final Long versionNumber) {
			this.versionNumber = versionNumber;
		}

		/**
		 * @return the upSqlFile
		 */
		public File getUpSqlFile() {
			return upSqlFile;
		}

		/**
		 * @param upSqlFile the upSqlFile to set
		 */
		public void setUpSqlFile(final File upSqlFile) {
			this.upSqlFile = upSqlFile;
		}

		/**
		 * @return the downSqlFile
		 */
		public File getDownSqlFile() {
			return downSqlFile;
		}

		/**
		 * @param downSqlFile the downSqlFile to set
		 */
		public void setDownSqlFile(final File downSqlFile) {
			this.downSqlFile = downSqlFile;
		}

		/**
		 * @return the sqlSplitter
		 */
		public SqlSplitter getSqlSplitter() {
			return sqlSplitter;
		}

		public List<SplitResult> getUpSqls(){
			if (upSqls==null){
				final String text=FileUtils.readText(this.getUpSqlFile(), getEncoding());
				final List<SplitResult> splits=this.getSqlSplitter().parse(text);
				boolean undo=false;
				final List<SplitResult> up=CommonUtils.list();
				final List<SplitResult> down=CommonUtils.list();
				for(final SplitResult splitResult:splits){
					if (splitResult.getTextType().isComment()){
						final Matcher matcher=UNDO_PATTERN.matcher(splitResult.getText());
						if (matcher.matches()){
							undo=true;
						}
						continue;
					}
					if (undo){
						down.add(splitResult);
					} else{
						up.add(splitResult);
					}
				}
				this.upSqls=up;
				if (undo){
					this.downSqls=down;
				}
			}
			return upSqls;
		}

		public List<SplitResult> getDownSqls(){
			getUpSqls();
			if (downSqls==null&&this.getDownSqlFile()!=null){
				final String text=FileUtils.readText(this.getDownSqlFile(), getEncoding());
				final List<SplitResult> splits=this.getSqlSplitter().parse(text);
				this.downSqls=splits;
			}
			return downSqls;
		}

		/**
		 * @return the encoding
		 */
		public String getEncoding() {
			return encoding;
		}

		/**
		 * @param encoding the encoding to set
		 */
		public void setEncoding(final String encoding) {
			this.encoding = encoding;
		}

		@Override
		public String toString(){
			final ToStringBuilder builder=new ToStringBuilder(this.getClass());
			builder.add("versionNumber", versionNumber);
			if (upSqlFile!=null){
				builder.add("\nupSqlFile", upSqlFile.getAbsolutePath());
			}
			if (downSqlFile!=null){
				builder.add("\ndownSqlFile", downSqlFile.getAbsolutePath());
			}
			return builder.toString();
		}

		@Override
		public int compareTo(final SqlFile o) {
			return this.versionNumber.compareTo(o.versionNumber);
		}
		
	}

	/**
	 * @return the encoding
	 */
	public String getEncoding() {
		return encoding;
	}

	/**
	 * @param encoding the encoding to set
	 */
	public void setEncoding(final String encoding) {
		this.encoding = encoding;
	}

}
