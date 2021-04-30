FROM adoptopenjdk/openjdk11:alpine
RUN  mkdir -p /opt \
  && wget https://h2database.com/h2-2019-10-14.zip \
  && unzip h2-*.zip \
  && rm -f h2-*.zip \
  && mv h2 /opt \
  && mkdir -p /opt/h2/data
EXPOSE 9092 5435 8082
VOLUME /opt/h2/data
CMD ["java", "-cp", "/opt/h2/bin/h2-1.4.200.jar", "org.h2.tools.Server", "-tcp", "-tcpAllowOthers", "-web", "-webAllowOthers", "-ifNotExists", "-pg", "-pgAllowOthers", "-baseDir", "/opt/h2/data"]
