#! /usr/bin/env python
# File: udf/ext_has_spouse_features.py

# Sample input data (piped into STDIN):
'''
{"p2.length":2,"p1.length":2,"words":["The","strange","case","of","the","death","of","'50s","TV","Superman","George","Reeves","is","deconstructed","in","``","Hollywoodland",",","''","starring","Adrien","Brody",",","Diane","Lane",",","Ben","Affleck","and","Bob","Hoskins","."],"relation_id":12190,"p1.start_position":20,"p2.start_position":10}
{"p2.length":2,"p1.length":2,"words":["Political","coverage","has","not","been","the","same","since","The","National","Enquirer","published","photographs","of","Donna","Rice","in","the","former","Sen.","Gary","Hart","'s","lap","20","years","ago","."],"relation_id":34885,"p1.start_position":14,"p2.start_position":20}
'''

import sys, json
import ddlib     # DeepDive python utility

# For each input tuple
for row in sys.stdin:
  obj = json.loads(row)
  words = obj["words"]
  # Unpack input into tuples.
  span1 = ddlib.Span(begin_word_id=obj['p1.start_position'], length=obj['p1.length'])
  span2 = ddlib.Span(begin_word_id=obj['p2.start_position'], length=obj['p2.length'])

  # Features for this pair come in here
  features = set()
  
  # Feature 1: Bag of words between the two phrases
  words_between = ddlib.tokens_between_spans(words, span1, span2)
  for word in words_between.elements:
    features.add("word_between=" + word)

  # Feature 2: Number of words between the two phrases
  features.add("num_words_between=%s" % len(words_between.elements))

  # Feature 3: Does the last word (last name) match?
  last_word_left = ddlib.materialize_span(words, span1)[-1]
  last_word_right = ddlib.materialize_span(words, span2)[-1]
  if (last_word_left == last_word_right):
    features.add("potential_last_name_match")

  ######################## 
  # Improved Feature Set #
  ########################

  # # Feature 1: Find out if a lemma of marry occurs.
  # # A better feature would ensure this is on the dependency path between the two.
  # words_between = ddlib.tokens_between_spans(words, span1, span2)
  # lemma_between = ddlib.tokens_between_spans(obj["lemma"], span1, span2)
  # married_words = ['marry', 'widow', 'wife', 'fiancee', 'spouse']
  # non_married_words = ['father', 'mother', 'brother', 'sister', 'son']
  # # Make sure the distance between mention pairs is not too long
  # if len(words_between.elements) <= 10:
  #   for mw in married_words + non_married_words:
  #     if mw in lemma_between.elements: 
  #       features.add("important_word=%s" % mw)

  # # Feature 2: Number of words between the two phrases
  # # Intuition: if they are close by, the link may be stronger.
  # l = len(words_between.elements)
  # if l < 5: features.add("few_words_between")
  # else: features.add("many_words_between")

  # # Feature 3: Does the last word (last name) match?
  # last_word_left = ddlib.materialize_span(words, span1)[-1]
  # last_word_right = ddlib.materialize_span(words, span2)[-1]
  # if (last_word_left == last_word_right):
  #   features.add("potential_last_name_match")

  #######################

  # # Use this line if you want to print out all features extracted:
  # ddlib.log(features)

  for feature in features:  
    print json.dumps({
      "relation_id": obj["relation_id"],
      "feature": feature
    })