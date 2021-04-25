FROM mcr.microsoft.com/mssql/server:2019-latest

USER root
SHELL ["/bin/bash", "-c"]

WORKDIR /docker-entrypoint-initdb.d
COPY ./initdb.d/ /docker-entrypoint-initdb.d/
RUN chmod -R +x /docker-entrypoint-initdb.d

EXPOSE 1433

ENTRYPOINT ["/bin/bash", "./entrypoint.sh"]
