FROM frolvlad/alpine-oraclejdk8:slim
ADD agent /service/agent
ADD bglib /service/bglib

ADD entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
WORKDIR /service

ENV SERVICE_JAR=/service/service.jar
ENV SERVICE_LIB=./lib
ENV AGENT_OPTS=-javaagent:./agent/aspectjweaver-1.8.10.jar
ENV DEFAULT_OPTS=-Djava.security.egd=file:/dev/./urandom
ENV JAVA_OPTS=-Xmx128m
ENV DEBUG_PORT=5005
ENV DEBUG_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=
ENV MAIN_CLASS=set.the.main.class
ENV DEBUG_PORT=@DEBUG_PORT@
ENV DEBUG_PORT=@DEBUG_PORT@
ENTRYPOINT ["/entrypoint.sh"]
