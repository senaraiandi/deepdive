#! /bin/bash
# A script for setting up common holding environment variables for DeepDive's spouse example

export APP_HOME="$PWD"/

# Database Configuration
export DBNAME=${DBNAME:-deepdive_spouse_inc}

export PGUSER=${PGUSER:-`whoami`}
export PGPASSWORD=${PGPASSWORD:-}
export PGPORT=${PGPORT:-5432}
export PGHOST=${PGHOST:-localhost}
