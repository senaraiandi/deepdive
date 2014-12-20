#! /usr/bin/env python

from dd import dep_path_between_words, materialize_span, Span, Word

MAX_KW_LENGTH = 3

dictionaries = dict()


def get_sentence(
        begin_char_offsets, end_char_offsets, words, lemmas, poses,
        dependencies, ners):
    sentence = []
    dep_parents = [-1, ] * len(words)
    dep_labels = ["ROOT", ] * len(words)
    if dependencies != ["", ]:
        for dep in dependencies:
            tokens = dep.split("(")
            label = tokens[0]
            tokens = tokens[1].split(", ")
            parent = int(tokens[0].split("-")[-1]) - 1
            assert parent < len(words)
            parent_word = "-".join(tokens[0].split("-")[:-1])
            child = int(",".join(tokens[1:]).split("-")[-1][:-1]) - 1
            child_word = "-".join(",".join(tokens[1:]).split("-")[:-1])
            dep_parents[child] = parent
            dep_labels[child] = label
            if parent != -1:
                assert words[parent] == parent_word
            assert words[child] == child_word
    for i in range(len(words)):
        word = Word(
            begin_char_offset=begin_char_offsets[i],
            end_char_offset=end_char_offsets[i], lemma=lemmas[i],
            word=words[i], pos=poses[i], ner=ners[i], dep_par=dep_parents[i],
            dep_label=dep_labels[i])
        sentence.append(word)
    return sentence


def load_dictionary(filename, dict_id="", func=lambda x: x):
    """Load a dictionary to be used for generic features.

    Returns the id used to identify the dictionary.

    Args:
        filename: full path to the dictionary. The dictionary is actually a set
        of words, one word per line.
        dict_id: (optional) specify the id to be used to identify the
        dictionary. By default it is a sequential number.
        func: (optional) A function to be applied to each row of the file
    """
    if dict_id == "":
        dict_id = str(len(dictionaries))
    with open(filename, 'rt') as dict_file:
        dictionary = set()
        for line in dict_file:
            dictionary.add(func(line.strip()))
        dictionary = frozenset(dictionary)
        dictionaries[dict_id] = dictionary
    return dict_id


def get_generic_features_mention(sentence, span):
    """Yield 'generic' features for a mention in a sentence.

    Args:
        sentence: a list of Word objects
        span: a Span namedtuple
    """
    # Mention sequence features (words, lemmas, ners, and poses)
    for seq_feat in _get_seq_features(sentence, span):
        yield seq_feat
    # Window (left and right, up to size 3, with combinations) around the
    # mention
    for window_feat in _get_window_features(sentence, span):
        yield window_feat
    # Is (substring of) mention in a dictionary?
    for dict_indicator_feat in _get_dictionary_indicator_features(
            sentence, span):
        yield dict_indicator_feat
    # Dependency path(s) from mention to keyword(s). Various transformations of
    # the dependency path are done.
    for (i, j) in _get_substring_indices(len(sentence), MAX_KW_LENGTH):
        if i >= span.begin_word_id and i < span.begin_word_id + span.length:
            continue
        if j > span.begin_word_id and j < span.begin_word_id + span.length:
            continue
        is_in_dictionary = False
        for dict_id in dictionaries:
            if " ".join(map(lambda x: x.lemma, sentence[i:j])) in \
                    dictionaries[dict_id]:
                is_in_dictionary = True
                yield "KW_IND_[" + str(dict_id) + "]"
                break
        if is_in_dictionary:
            kw_span = Span(begin_word_id=i, length=j-i)
            for dep_path_feature in _get_min_dep_path_features(
                    sentence, span, kw_span, "KW"):
                yield dep_path_feature
    # The mention starts with a capital
    if sentence[span.begin_word_id].word[0].isupper():
        yield "STARTS_WITH_CAPITAL"
    # Length of the mention
    length_feat = "LENGTH_" + str(
        len(" ".join(materialize_span(sentence, span, lambda x: x.word))))
    yield length_feat


def get_generic_features_relation(sentence, span1, span2):
    """Yield 'generic' features for a relation in a sentence.

    Args:
        sentence: a list of Word objects
        span1: the first Span of the relation
        span2: the second Span of the relation
    """
    # Check whether the order of the spans is inverted. We use this information
    # to add a prefix to *all* the features.
    order = sorted([
        span1.begin_word_id, span1.begin_word_id + span1.length,
        span2.begin_word_id, span2.begin_word_id + span2.length])
    begin = order[0]
    betw_begin = order[1]
    betw_end = order[2]
    end = order[3]
    if begin == span2.begin_word_id:
        inverted = "INV_"
        yield "IS_INVERTED"
    else:
        inverted = ""
    betw_span = Span(begin_word_id=betw_begin, length=betw_end - betw_begin)
    covering_span = Span(begin_word_id=begin, length=end - begin)
    # Words, Lemmas, Ners, and Poses sequence between the mentions
    for seq_feat in _get_seq_features(sentence, betw_span):
        yield inverted + seq_feat
    # Window feature (left and right, up to size 3, combined)
    for window_feat in _get_window_features(
            sentence, covering_span, isolated=False):
        yield inverted + window_feat
    # Ngrams of up to size 3 between the mentions
    for ngram_feat in _get_ngram_features(sentence, betw_span):
        yield inverted + ngram_feat
    # Indicator features of whether the mentions are in dictionaries
    found1 = False
    for feat1 in _get_dictionary_indicator_features(
            sentence, span1, prefix=inverted + "IN_DICT"):
        found1 = True
        found2 = False
        for feat2 in _get_dictionary_indicator_features(
                sentence, span2, prefix=""):
            found2 = True
            yield feat1 + feat2
        if not found2:
            yield feat1 + "_[_NONE]"
    if not found1:
        for feat2 in _get_dictionary_indicator_features(
                sentence, span2, prefix=""):
            found2 = True
            yield inverted + "IN_DICT_[_NONE]" + feat2
    # Dependency path (and transformations) between the mention
    for betw_dep_path_feature in _get_min_dep_path_features(
            sentence, span1, span2, inverted + "BETW"):
        yield betw_dep_path_feature
    # Dependency paths (and transformations) between the mentions and keywords
    for (i, j) in _get_substring_indices(len(sentence), MAX_KW_LENGTH):
        if (i >= begin and i < betw_begin) or (i >= betw_end and i < end):
            continue
        if (j > begin and j <= betw_begin) or (j > betw_end and j <= end):
            continue
        is_in_dictionary = False
        for dict_id in dictionaries:
            if " ".join(map(lambda x: x.lemma, sentence[i:j])) in \
                    dictionaries[dict_id]:
                is_in_dictionary = True
                yield inverted + "KW_IND_[" + str(dict_id) + "]"
                break
        if is_in_dictionary:
            kw_span = Span(begin_word_id=i, length=j-i)
            path1 = _get_min_dep_path(sentence, span1, kw_span)
            lemmas1 = []
            labels1 = []
            for edge in path1:
                lemmas1.append(edge.word2.lemma)
                labels1.append(edge.label)
            both1 = []
            for j in range(len(labels1)):
                both1.append(labels1[j])
                both1.append(lemmas1[j])
            both1 = both1[:-1]
            path2 = _get_min_dep_path(sentence, span2, kw_span)
            lemmas2 = []
            labels2 = []
            for edge in path2:
                lemmas2.append(edge.word2.lemma)
                labels2.append(edge.label)
            both2 = []
            for j in range(len(labels2)):
                both2.append(labels2[j])
                both2.append(lemmas2[j])
            both2 = both2[:-1]
            yield inverted + "KW_[" + " ".join(both1) + "]_[" + \
                " ".join(both2) + "]"
            yield inverted + "KW_L_[" + " ".join(labels1) + "]_[" + \
                " ".join(labels2) + "]"
            for j in range(1, len(both1), 2):
                for dict_id in dictionaries:
                    if both1[j] in dictionaries[dict_id]:
                        both1[j] = "DICT_" + str(dict_id)
                        break  # Picking up the first dictionary we find
            for j in range(1, len(both2), 2):
                for dict_id in dictionaries:
                    if both2[j] in dictionaries[dict_id]:
                        both2[j] = "DICT_" + str(dict_id)
                        break  # Picking up the first dictionary we find
            yield inverted + "KW_D_[" + " ".join(both1) + "]_[" + \
                " ".join(both2) + "]"
    # The mentions start with a capital letter
    first_capital = sentence[span1.begin_word_id].word[0].isupper()
    second_capital = sentence[span2.begin_word_id].word[0].isupper()
    capital_feat = inverted + "STARTS_WITH_CAPITAL_[" + str(first_capital) + \
        "_" + str(second_capital) + "]"
    yield capital_feat
    # The lengths of the mentions
    first_length = str(len(" ".join(materialize_span(
        sentence, span1, lambda x: x.word))))
    second_length = str(len(" ".join(materialize_span(
        sentence, span2, lambda x: x.word))))
    length_feat = inverted + "LENGTHS_[" + first_length + "_" + \
        second_length + "]"
    yield length_feat


def _get_substring_indices(_len, max_substring_len):
    for start in range(_len):
        for end in reversed(range(start + 1, min(
                            _len, start + 1 + max_substring_len))):
            yield (start, end)


def _get_ngram_features(sentence, span, window=3):
    """Yields ngram features

    Args:
        sentence: a list of Word objects
        span: the Span identifying the area for generating the substrings
        window: maximum size of a substring
    """
    for i in range(span.begin_word_id, span.begin_word_id + span.length):
        for j in range(1, window + 1):
            if i+j <= span.begin_word_id + span.length:
                yield "NGRAM_" + str(j) + "_[" + " ".join(
                    map(lambda x: x.lemma, sentence[i:i+j])) + "]"


def _get_min_dep_path(sentence, span1, span2):
    """Get the shortest dependency paths between two Span objects

    Args:
        sentence: a list of Word objects
        span1: the first Span
        span2: the second Span
    """
    min_path = None
    min_path_length = 200  # ridiculously high number?
    for i in range(span1.begin_word_id, span1.begin_word_id + span1.length):
        for j in range(
                span2.begin_word_id, span2.begin_word_id + span2.length):
            p = dep_path_between_words(sentence, i, j)
            if len(p) < min_path_length:
                min_path = p
    return min_path


def _get_min_dep_path_features(sentence, span1, span2, prefix="BETW_"):
    """Yield the minimum dependency path features between two Span objects

    Args:
        sentence: a list of Word objects
        span1: the first Span
        span2: the second Span
        prefix: string prepended to all features
    """
    min_path = _get_min_dep_path(sentence, span1, span2)
    if min_path:
        min_path_lemmas = []
        min_path_labels = []
        for edge in min_path:
            min_path_lemmas.append(edge.word2.lemma)
            min_path_labels.append(edge.label)
        both = []
        for j in range(len(min_path_labels)):
            both.append(min_path_labels[j])
            both.append(min_path_lemmas[j])
        both = both[:-1]
        yield prefix + "_[" + " ".join(both) + "]"
        yield prefix + "_L_[" + " ".join(min_path_labels) + "]"
        for j in range(1, len(both), 2):
            for dict_id in dictionaries:
                if both[j] in dictionaries[dict_id]:
                    both[j] = "DICT_" + str(dict_id)
                    break  # Picking up the first dictionary we find
        yield prefix + "_D_[" + " ".join(both) + "]"


def _get_seq_features(sentence, span):
    """Yield the sequence features in a Span

    Args:
        sentence: a list of Word objects
        span: the span
    """
    word_seq_feat = "WORD_SEQ_[" + " ".join(materialize_span(
        sentence, span, lambda x: x.word)) + "]"
    yield word_seq_feat
    lemma_seq_feat = "LEMMA_SEQ_[" + " ".join(materialize_span(
        sentence, span, lambda x: x.lemma)) + "]"
    yield lemma_seq_feat
    ner_seq_feat = "NER_SEQ_[" + " ".join(materialize_span(
        sentence, span, lambda x: x.ner)) + "]"
    yield ner_seq_feat
    pos_seq_feat = "POS_SEQ_[" + " ".join(materialize_span(
        sentence, span, lambda x: x.pos)) + "]"
    yield pos_seq_feat


def _get_window_features(
        sentence, span, window=3, combinations=True, isolated=True):
    """Yield the window features around a Span

    Args:
        sentence: a list of Word objects
        span: the span
        window: the maximum size of the window
        combinations: Whether to yield features that combine the windows on
            the left and on the right
        isolated: Whether to yield features that do not combine the windows on
            the left and on the right
    """
    span_end_idx = span.begin_word_id + span.length - 1
    left_lemmas = []
    left_ners = []
    right_lemmas = []
    right_ners = []
    try:
        for i in range(1, window + 1):
            lemma = sentence[span.begin_word_id - i].lemma
            try:
                float(lemma)
                lemma = "_NUMBER"
            except ValueError:
                pass
            left_lemmas.append(lemma)
            left_ners.append(sentence[span.begin_word_id - i].ner)
    except IndexError:
        pass
    left_lemmas.reverse()
    left_ners.reverse()
    try:
        for i in range(1, window + 1):
            lemma = sentence[span_end_idx + i].lemma
            try:
                float(lemma)
                lemma = "_NUMBER"
            except ValueError:
                pass
            right_lemmas.append(lemma)
            right_ners.append(sentence[span_end_idx + i].ner)
    except IndexError:
        pass
    if isolated:
        for i in range(len(left_lemmas)):
            yield "LEFT_" + str(i+1) + "_[" + " ".join(left_lemmas[-i-1:]) + \
                "]"
            yield "LEFT_NER_" + str(i+1) + "_[" + " ".join(left_ners[-i-1:]) +\
                "]"
        for i in range(len(right_lemmas)):
            yield "RIGHT_" + str(i+1) + "_[" + " ".join(right_lemmas[:i+1]) +\
                "]"
            yield "RIGHT_NER_" + str(i+1) + "_[" + \
                " ".join(right_ners[:i+1]) + "]"
    if combinations:
        for i in range(len(left_lemmas)):
            curr_left_lemmas = " ".join(left_lemmas[-i-1:])
            try:
                curr_left_ners = " ".join(left_ners[-i-1:])
            except TypeError:
                new_ners = []
                for ner in left_ners[-i-1:]:
                    to_add = ner
                    if not to_add:
                        to_add = "None"
                    new_ners.append(to_add)
                curr_left_ners = " ".join(new_ners)
            for j in range(len(right_lemmas)):
                curr_right_lemmas = " ".join(right_lemmas[:j+1])
                try:
                    curr_right_ners = " ".join(right_ners[:j+1])
                except TypeError:
                    new_ners = []
                    for ner in right_ners[:j+1]:
                        to_add = ner
                        if not to_add:
                            to_add = "None"
                        new_ners.append(to_add)
                    curr_right_ners = " ".join(new_ners)
                yield "LEMMA_L_" + str(i+1) + "_R_" + str(j+1) + "_[" + \
                    curr_left_lemmas + "]_[" + curr_right_lemmas + "]"
                yield "NER_L_" + str(i+1) + "_R_" + str(j+1) + "_[" + \
                    curr_left_ners + "]_[" + curr_right_ners + "]"


def _get_dictionary_indicator_features(
        sentence, span, window=3, prefix="IN_DICT"):
    """Yield the indicator features for whether a substring of the span is in
the dictionaries

    Args:
        sentence: a list of Word objects
        span: the span
        window: the maximum size of a substring
        prefix: a string to prepend to all yielded features
    """
    in_dictionaries = set()
    for i in range(window + 1):
        for j in range(span.length - i):
            phrase = " ".join(map(lambda x: x.lemma, sentence[j:j+i+1]))
            for dict_id in dictionaries:
                if phrase in dictionaries[dict_id]:
                    in_dictionaries.add(dict_id)
    for dict_id in in_dictionaries:
        yield prefix + "_[" + str(dict_id) + "]"
    # yield prefix + "_JOIN_[" + " ".join(
    #    map(lambda x: str(x), sorted(in_dictionaries))) + "]"
