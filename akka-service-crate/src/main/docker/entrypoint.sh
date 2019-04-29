#!/bin/sh
CLASSPATH="./service.jar:./bglib/*:${SERVICE_LIB}/*"

echo "Running with CLASSPATH:"
echo ${CLASSPATH}
echo "AGENTS:"
echo "${AGENT_OPTS} ${DEBUG_OPTS}${DEBUG_PORT}"
echo "JAVA_OPTS:"
echo "${DEFAULT_OPTS} ${JAVA_OPTS}"
echo "Main:"
echo "${MAIN_CLASS} $@"
exec java -cp ${CLASSPATH} ${AGENT_OPTS} ${DEBUG_OPTS}${DEBUG_PORT} ${DEFAULT_OPTS} ${JAVA_OPTS} ${MAIN_CLASS} $@
                            