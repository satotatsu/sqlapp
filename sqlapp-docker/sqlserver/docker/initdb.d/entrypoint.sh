#!/bin/bash

LOG_OUT=/var/opt/mssql/log/init-stdout.log
LOG_ERR=/var/opt/mssql/log/init-stderr.log

exec 1>>$LOG_OUT
exec 2>>$LOG_ERR

#/opt/mssql/bin/sqlservr & /docker-entrypoint-initdb.d/import-data.sh
/docker-entrypoint-initdb.d/import-data.sh & /opt/mssql/bin/sqlservr
