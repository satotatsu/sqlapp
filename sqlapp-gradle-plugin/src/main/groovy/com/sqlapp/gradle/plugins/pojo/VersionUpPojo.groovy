/*
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-gradle-plugin.
 *
 * sqlapp-gradle-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-gradle-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-gradle-plugin.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.gradle.plugins.pojo

import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional;

class VersionUpPojo extends DbPojo{
	VersionUpPojo(Project project) {
		super(project)
	}
	/**file directory*/
	@InputDirectory
	@Optional
	def fileDirectory=new File("./");
	/**encoding*/
	@Input
	@Optional
	String encoding="UTF-8";
	/**
	 * バージョンアップ用SQLのディレクトリ
	 */
	@InputDirectory
	@Optional
	def sqlDirectory;
	/**
	 * バージョンダウン用のSQLのディレクトリ
	 */
	@InputDirectory
	@Optional
	def downSqlDirectory;
	/**
	 * バージョンアップ前に実行するSQLのディレクトリ
	 */
	@InputDirectory
	@Optional
	def setupSqlDirectory=null;
	/**
	 * バージョンアップ後に実行するSQLのディレクトリ
	 */
	@InputDirectory
	@Optional
	def finalizeSqlDirectory=null;
	@Input
	@Optional
	Long lastChangeNumber=null;

	@Input
	@Optional
	Boolean showVersionOnly=false;

	@Input
	@Optional
	Boolean withSeriesNumber=true;
	/**Schema Change log table name*/
	@Input
	@Optional
	ChangeTablePojo changeTable=null;

	@Input
	@Optional
	String placeholderPrefix='${';
	@Input
	@Optional
	String placeholderSuffix='}';
	@Input
	@Optional
	Boolean placeholders=false;

	void fileDirectory(def fileDirectory){
		this.setFileDirectory(fileDirectory);
	}

	void encoding(String encoding) {
		this.encoding=encoding
	}

	void sqlDownDirectory(def downSqlDirectory){
		this.setDownSqlDirectory(downSqlDirectory);
	}

	void sqlDirectory(String sqlDirectory){
		this.setSqlDirectory(sqlDirectory);
	}

	void setupSqlDirectory(def setupSqlDirectory){
		this.setSetupSqlDirectory(setupSqlDirectory);
	}

	void finalizeSqlDirectory(def finalizeSqlDirectory){
		this.setFinalizeSqlDirectory(finalizeSqlDirectory);
	}

	void changeTable(Closure closure) {
		if (this.changeTable==null){
			this.changeTable=project.configure(new ChangeTablePojo(this.getProject()), closure)
		}else{
			project.configure(this.changeTable, closure)
		}
	}

	void setChangeTable(ChangeTablePojo changeTable) {
		this.changeTable=changeTable
	}

	void lastChangeNumber(String lastChangeNumber){
		this.setLastChangeNumber(lastChangeNumber);
	}

	void setLastChangeNumber(String lastChangeNumber){
		if (lastChangeNumber!=null){
			this.lastChangeNumber=Long.valueOf(lastChangeNumber);
		}
	}

	void showVersionOnly(boolean showVersionOnly){
		this.setShowVersionOnly(showVersionOnly);
	}

	void setShowVersionOnly(Boolean showVersionOnly){
		this.showVersionOnly=showVersionOnly!=null?showVersionOnly:false;
	}

	void withSeriesNumber(boolean withSeriesNumber){
		this.setWithSeriesNumber(withSeriesNumber);
	}

	void setWithSeriesNumber(boolean withSeriesNumber){
		this.withSeriesNumber=withSeriesNumber!=null?withSeriesNumber:true;
	}

	@Override
	VersionUpPojo clone(){
		VersionUpPojo clone= super.clone();
		if (this.changeTable!=null){
			clone.changeTable=this.changeTable.clone();
		}
		return clone;
	}

	void placeholderPrefix(String placeholderPrefix){
		this.placeholderPrefix=placeholderPrefix;
	}

	void placeholderSuffix(String placeholderSuffix){
		this.placeholderSuffix=placeholderSuffix;
	}

	void placeholders(Boolean placeholders){
		this.placeholders=placeholders!=null?placeholders:false;
	}
}
