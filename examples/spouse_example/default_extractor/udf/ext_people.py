#! /usr/bin/env python

import json, sys

# For each sentence
for row in sys.stdin:
  sentence_obj = json.loads(row)
  # Find phrases that are continuous words tagged with PERSON.
  phrases = []   # Store (start_position, length, text)
  words = sentence_obj["words"]         # a list of all words
  ner_tags = sentence_obj["ner_tags"]   # ner_tags for each word
  start_index = 0
  
  while start_index < len(words):
    # Checking if there is a PERSON phrase starting from start_index
    index = start_index
    while index < len(words) and ner_tags[index] == "PERSON": 
      index += 1
    if index != start_index:   # a person from "start_index" to "index"
      text = ' '.join(words[start_index:index])
      length = index - start_index
      phrases.append((start_index, length, text))
    start_index = index + 1
  
  # Output a tuple for each PERSON phrase
  for start_position, length, text in phrases:
    print json.dumps({
      "sentence_id": sentence_obj["sentence_id"],
      "start_position": start_position,
      "length": length,
      "text": text,
      "mention_id": None
    })
