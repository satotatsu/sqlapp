#!/bin/bash
sleep 20

if [ `ls -U1 /var/opt/mssql/data | grep DEMO | wc -l` -eq 0 ]; then
    cd /docker-entrypoint-initdb.d
    sql_files=`ls *.sql`

    for file in $sql_files;
    do
        for i in {1..30};
        do
            /opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P $SA_PASSWORD -i $file
            if [ $? -eq 0 ]
            then
                echo "${file} completed."
                break
            else
                echo "${file} failed."
                sleep 1
            fi
        done
    done

    csv_files=`ls *.csv`
    for file in $csv_files;
    do
        for i in {1..30};
        do
            table_name=`basename $file .csv`
            /opt/mssql-tools/bin/bcp $table_name in $file -c -t',' -S localhost -U sa -P $SA_PASSWORD
            if [ $? -eq 0 ]
            then
                echo "${file} completed."
                break
            else
                echo "${file} failed."
                sleep 1
            fi
        done
    done
fi