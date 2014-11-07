#! /usr/bin/env bash

# Set username and password
export PGUSER=${PGUSER:-`whoami`}
export PGPASSWORD=${PGPASSWORD:-}
export PGPORT=${PGPORT:-5432}
export PGHOST=${PGHOST:-localhost}
export DBNAME=deepdive_test
export PGDATABASE=$DBNAME  # for testing to work with null settings
export GPHOST=${GPHOST:-localhost}
export GPPORT=${GPPORT:-8082}
export GPPATH=${GPPATH:-/tmp}

# Mysql specific
export DBUSER=root
export DBPASSWORD=
export DBHOST=127.0.0.1
export DBPORT=3306
export DBCONNSTRING=jdbc:mysql://$DBHOST:$DBPORT/$DBNAME
echo "CONN STRING: $DBCONNSTRING"
export DEEPDIVE_HOME=`cd $(dirname $0)/../; pwd`

# Set to "1" to use "LOCAL" in data loader, which reads data from client. 
# Should have MySQL server and client both configured to allow this.
# export MYSQL_LOCAL_INFILE=0

# This env var specifies system under test
export DEEPDIVE_TEST_ENV="mysql"

cd $DEEPDIVE_HOME/lib

case $(uname) in
  Darwin)
    export LD_LIBRARY_PATH=$DEEPDIVE_HOME/lib/dw_mac/lib/protobuf/lib:$DEEPDIVE_HOME/lib/dw_mac/lib:$LD_LIBRARY_PATH
    export DYLD_LIBRARY_PATH=$DEEPDIVE_HOME/lib/dw_mac:$DYLD_LIBRARY_PATH
    ;;

  Linux*)
    export LD_LIBRARY_PATH=$DEEPDIVE_HOME/lib/dw_linux/lib:$DEEPDIVE_HOME/lib/dw_linux/lib64:$LD_LIBRARY_PATH
    ;;

  *)
    echo >&2 "$(uname): Unsupported OS"
    false
esac

##### unit tests have not been ported. Run integration tests (spouse example )instead ####
cd $DEEPDIVE_HOME

# Create test database
mysql -u $DBUSER -h $DBHOST -P $DBPORT -e "drop database if exists $DBNAME; 
  create database $DBNAME"

# Run the test

# Separate different tests to fix the issue of unable to run multiple integration tests. If any of the tests return non-0 value, exit with the error code.
export SBT_OPTS="-XX:MaxHeapSize=256m -Xmx512m -XX:MaxPermSize=256m" 
# # Test argument "-- -oF" shows full stack trace when error occurs
# sbt "test-only org.deepdive.test.unit.* -- -oF" && sbt "test-only org.deepdive.test.integration.BiasedCoin -- -oF" && sbt "test-only org.deepdive.test.integration.ChunkingApp -- -oF"
sbt "test-only org.deepdive.test.unit.*" && \
sbt "test-only org.deepdive.test.integration.BiasedCoin -- -oF" && \
sbt "test-only org.deepdive.test.integration.MysqlSpouseExample -- -oF" && \
sbt "test-only org.deepdive.test.integration.ChunkingApp -- -oF"

# Running a specific test with Eclipse debugger
# SBT_OPTS="-agentlib:jdwp=transport=dt_socket,address=localhost:8000,server=y,suspend=y -Xmx4g" sbt/sbt "test-only org.deepdive.test.integration.MysqlSpouseExample"
