---
layout: default
---

# Probabilistic Inference

### Random Variables and Joint Inference

Probabilistic inference is the task of assigning values to unobserved [random variables](http://en.wikipedia.org/wiki/Random_variable). For example, a random variable may describe the event that John has cancer. Such a variable could take a value of 1 (John has cancer) or 0 (John does not have cancer). Deepdive uses probabilistic inference to estimate the expected value of a random variable. In the case of Boolean variables, a value of of 0.78 would mean that John is 78% likely to have cancer.

DeepDive uses [inference rules](doc/inference.html) to estimate the probability of a random variable. If a rule has a high positive *weight*, then the variables used in the rule are likely to take on values that would make the rule evaluate to true. We may have a rule that says something like "If John smokes than he is likely to have cancer". If this rule has a high weight and we are sure that John smokes, then we are also reasonably sure that John has cancer. However, if we are not sure whether or not John smokes, then we cannot be sure about him having cancer either. In the latter case, both "John does not have cancer", and "John has cancer" would make the rule evaluate to true.

This is a subtle but very important point. Contrary to many traditional Machine Learning algorithms, which often assume prior knowledge to be exact and make preditions in isolation, Deepdive performs *joint inference*, which means that DeepDive determines the most likely values of all events jointly, or at the same time. This allows many kinds of events to influence each other if they are (directly or indirectly) connected through inference rules. Thus, the uncertainty of one event (John smoking) may influence the uncertainty of another event (John having cancer). As the relationships among events become more complex the model becomes very powerful. For example, you could imagine the event "John smokes" being influenced by whether or not John is friends with other people who smoke, and so on. This is particularly useful if you are dealing with inherently noisy signals, such as human language.

### How does DeepDive create random variables?

DeepDive creates random variables based on entries in the [schema definition](doc/schema.html). For example, if your schema definition contains an entry such as `people.has_cancer: Boolean`, then DeepDive will create a Boolean random variable for each unique ID in the people table for the event `has_cancer`. Thus, if there are 100 entries in the `people` table, then DeepDive will create 100 Boolean random variables, each describing the event of one person having cancer. More formally, a random variable is created for each `(relation_name, column_name, ID)` triple, where `relation_name` and `column_name` are defined in the schema definition. You should create random variables for each event you are interested in predicting.

### How does DeepDive determine the weights for inference rules?

In order to *learn* weights for the inference rules, DeepDive uses existing knowledge about the world. It is your task to give this knowledge to DeepDive in the form of *evidence*. If you have a lot of positive evidence that smoking leads to cancer, then such a rule would get a large positive weight. This may sound simple, but the fact that many rules compete with one another, and that the weight values must make sense relative to each other, makes this a challenging problem. Luckily, Deepdive does the hard work of learning the weights for you.

Evidence is often obtained through [Distant Supervision (TODO: Link)](), for example by using an already existing database. In order to determine the correct weights for the inference rules and to avoid bias, it is important to have both positive and negative evidence. Positive evidence is often easy to obtain, but [generating negative evidence can be somewhat of an art (TODO: Link to tutorial here)]().

In Deepdive, you specify evidence by simply setting a field to its desired value. For example, if you know that Sarah has cancer you would set her `has_cancer` field value to `true`. All fields that map to random variales and have an contain an existing value are treated as evidence. Fields that do not have a value (they are NULL) are predicted by DeepDive.


