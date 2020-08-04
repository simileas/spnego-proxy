#!/usr/bin/env bash

set -x

APPHOME="$(cd `dirname $0`/..; pwd)"
LIB=${APPHOME}/lib
CLASSPATH=$(find "$LIB" -name '*.jar' -printf '%p:' | sed 's/:$//')

PID_FILE=${APPHOME}/bin/current.pid

echo $$ > ${PID_FILE}

exec java -cp ${CLASSPATH} -Xmx1024m -XX:+UseG1GC \
  com.nopadding.internal.SpnegoProxy -p ${APPHOME}/bin/application.properties
