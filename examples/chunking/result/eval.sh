#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")"

deepdive sql "COPY (
    SELECT a.word, a.pos, a.true_tag, a.category
      FROM words_tag_inference a LEFT JOIN words_tag_inference b
        ON a.word_id = b.word_id AND a.expectation < b.expectation
     WHERE b.expectation IS NULL
     ORDER BY a.word_id
  ) TO STDOUT DELIMITER ' ';" >result

python convert.py

./conlleval.pl <output
