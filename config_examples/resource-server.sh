#!/bin/bash
VERSION="0.0.1"
JAR_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
log_file=/opt/ssh-authz-server/log/resource-server.log
cd $JAR_DIR
exec java -jar resource-server-$VERSION.jar $@ >> ${log_file} 2>&1
