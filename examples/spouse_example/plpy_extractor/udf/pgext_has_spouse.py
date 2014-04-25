#! /usr/bin/env python
#! /usr/bin/env python

# Imports written in this area is useless, just for local debugging
import ddext
from ddext import SD
import itertools
import os
from collections import defaultdict

# input: sentences.id, p1.id, p1.text, p2.id, p2.text
# output: has_spouse
# returns: 

def init():
  # SD['json'] = __import__('json')
  ddext.import_lib('csv')
  ddext.import_lib('os')

  # from collections import defaultdict
  ddext.import_lib('defaultdict', 'collections')

  # Other examples of import_lib:
  # # from collections import defaultdict as defdict:
  # ddext.import_lib('defaultdict', 'collections', 'defdict')
  # # import defaultdict as defdict:
  # ddext.import_lib('defaultdict', as_name='defdict')
  
  # Input commands MUST HAVE CORRECT ORDER: 
  # SAME AS SELECT ORDER, and SAME AS "run" ARGUMENT ORDER
  ddext.input('sentence_id', 'bigint')
  ddext.input('p1_id', 'bigint')
  ddext.input('p1_text', 'text')
  ddext.input('p2_id', 'bigint')
  ddext.input('p2_text', 'text')

  # Returns commands MUST HAVE CORRECT ORDER
  ddext.returns('person1_id', 'bigint')
  ddext.returns('person2_id', 'bigint')
  ddext.returns('sentence_id', 'bigint')
  ddext.returns('description', 'text')
  ddext.returns('is_true', 'boolean')


def run(sentence_id, p1_id, p1_text, p2_id, p2_text):
  
  ####### NOTICE: SHARED MEMORY ########
  # If you really need shared memory / global dir, do "from ddext import SD."
  # Use SD as the global shared dict.

  # Load the spouse dictionary for distant supervision
  spouses = defaultdict(lambda: None)
  if 'spouses' in SD:
    spouses = SD['spouses']
  else:  # Read data from file once, and share it
    SD['spouses'] = spouses
    # Read dict from file: MAKE SURE YOUR DATABASE SERVER 
    #   HAVE THE ACCESS TO FILE!!
    # e.g. put this file in /dfs/rulk, where all machines are accessible, then properly name the path.
    with open ("/dfs/rulk/0/deepdive/shared/spouses.csv") as csvfile:
      reader = csv.reader(csvfile)
      for line in reader:
        spouses[line[0].strip().lower()] = line[1].strip().lower()


  # NOTICE: PLPY DOES NOT ALLOW overwritting input arguments!!! TODO
  # UnboundLocalError
  p1_t = p1_text.strip()
  p2_t = p2_text.strip()
  p1_text_lower = p1_t.lower()
  p2_text_lower = p2_t.lower()

  is_true = None
  if spouses[p1_text_lower] == p2_text_lower:
    is_true = True
  elif (p1_t == p2_t) or (p1_t in p2_t) or (p2_t in p1_t):
    is_true = False

  # SEMANTICS UPDATE: Must return tuples of arrays.
  return [[p1_id], [p2_id], [sentence_id], ["%s-%s" % (p1_t, p2_t)], [is_true]]
