#! /bin/bash

. env_mysql.sh

export APP_HOME=`cd $(dirname $0)/; pwd`
export DEEPDIVE_HOME=`cd $(dirname $0)/../../../; pwd`

# Database Configuration
export DBNAME=deepdive_spouse_tsv

# Initialize database
bash $APP_HOME/../setup_database_mysql.sh $DBNAME

cd $DEEPDIVE_HOME

deepdive -c $APP_HOME/application_mysql.conf
# SBT_OPTS="-Xmx4g" sbt "run -c $APP_HOME/application_mysql.conf"