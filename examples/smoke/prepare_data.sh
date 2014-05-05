#! /bin/bash

cd `dirname $0`
BASE_DIR=`pwd`

dropdb -p $PGPORT -h $PGHOST $DBNAME
createdb -p $PGPORT -h $PGHOST $DBNAME

psql -p $PGPORT -h $PGHOST $DBNAME -c "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;"

psql -p $PGPORT -h $PGHOST $DBNAME -c """
    DROP TABLE IF EXISTS person CASCADE;
    DROP TABLE IF EXISTS person_has_cancer CASCADE;
    DROP TABLE IF EXISTS person_smokes CASCADE;
    DROP TABLE IF EXISTS friends CASCADE;

    CREATE TABLE person (
        person_id bigint,
        name text
    );

    CREATE TABLE person_has_cancer (
        person_id bigint,
        has_cancer boolean,
        id bigint
    );

    CREATE TABLE person_smokes (
        person_id bigint,
        smokes boolean,
        id bigint
    );

    CREATE TABLE friends (
        person_id bigint,
        friend_id bigint,
        are_friends boolean,
        id bigint
    );
"""

psql -p $PGPORT -h $PGHOST $DBNAME -c """
    INSERT INTO person(person_id, name) VALUES
        (1, 'Anna'),
        (2, 'Bob'),
        (3, 'Edward'),
        (4, 'Frank'),
        (5, 'Gary'),
        (6, 'Helen')
;"""

psql -p $PGPORT -h $PGHOST $DBNAME -c """
    INSERT INTO person_smokes(person_id, smokes) VALUES
        (1, TRUE),
        (2, NULL),
        (3, TRUE),
        (4, NULL),
        (5, NULL),
        (6, NULL)
;"""

psql -p $PGPORT -h $PGHOST $DBNAME -c """
    INSERT INTO person_has_cancer(person_id, has_cancer) VALUES
        (1, NULL),
        (2, NULL),
        (3, NULL),
        (4, NULL),
        (5, NULL),
        (6, NULL)
;"""

psql -p $PGPORT -h $PGHOST $DBNAME -c """
    INSERT INTO friends(person_id, friend_id, are_friends) VALUES 
        (1, 2, TRUE), (2, 1, TRUE),
        (1, 3, TRUE), (3, 1, TRUE),
        (1, 4, TRUE), (4, 1, TRUE),
        (3, 4, TRUE), (4, 3, TRUE),
        (5, 6, TRUE), (6, 5, TRUE),
        (5, 4, FALSE), (4, 5, FALSE),
;"""