FROM adoptopenjdk/openjdk11:alpine
RUN apk update && apt add sqlite3 libsqlite3-dev
VOLUME /opt/sqlite/data
sqlite3 
