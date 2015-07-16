# Makefile for Scala ##########################################################

# Why do we use Makefile instead of SBT?
# - Makefile can build components written in any language.  We use it anyway
#   for handling dependency and installation.
# - It's much easier to run integration tests with non-Scala components outside
#   SBT or ScalaTest.
# - SBT is slow.
#
# How can Makefile build and test Scala code?
# - By exporting the CLASSPATH and running java directly instead, we can avoid
#   SBT's poor command-line interface performance.  Note that we try to invoke
#   SBT as least as possible through out the Makefile, duplicating some commands.
# - Since there are test-specific classes and dependencies, there has to be two
#   CLASSPATHs.
# - When built for tests, the classes are instrumented such that coverage can
#   be measured.

# Some path names for Scala
SCALA_BUILD_FILES             = build.sbt $(wildcard project/*.*)
SCALA_MAIN_SOURCES            = $(shell find src/main/scala -name '*.scala' 2>/dev/null)
SCALA_MAIN_CLASSES_DIR        = target/scala-2.10/classes
SCALA_MAIN_CLASSPATH_EXPORTED = target/scala-2.10/classpath
SCALA_TEST_SOURCES            = $(shell find src/test/scala -name '*.scala' 2>/dev/null)
SCALA_TEST_CLASSES_DIR        = target/scala-2.10/test-classes
SCALA_TEST_CLASSPATH_EXPORTED = target/scala-2.10/test-classpath
SCALA_COVERAGE_DIR            = target/scala-2.10/scoverage-data
SCALA_ASSEMBLY_JAR            = target/scala-2.10/*-assembly-*.jar

# SBT settings
SBT_OPTS ?= -Xmx4g -XX:MaxHeapSize=4g -XX:MaxPermSize=4g
export SBT_OPTS

.PHONY: scala-build
scala-build: $(SCALA_MAIN_CLASSES_DIR) $(SCALA_MAIN_CLASSPATH_EXPORTED)
# How to build main Scala code and export main CLASSPATH
$(SCALA_MAIN_CLASSES_DIR): $(SCALA_MAIN_SOURCES) $(SCALA_BUILD_FILES)
	# Compiling Scala code
	sbt compile
	touch $(SCALA_MAIN_CLASSES_DIR)
$(SCALA_MAIN_CLASSPATH_EXPORTED): $(SCALA_BUILD_FILES)
	# Exporting CLASSPATH
	sbt --error "export compile:full-classpath" | tee /dev/stderr | \
	    tail -1 >$@

# How to build test Scala code with coverage and export test CLASSPATH
.PHONY: scala-test-build
scala-test-build: $(SCALA_COVERAGE_DIR) $(SCALA_TEST_CLASSES_DIR) $(SCALA_TEST_CLASSPATH_EXPORTED)
$(SCALA_COVERAGE_DIR) $(SCALA_TEST_CLASSES_DIR): $(SCALA_MAIN_SOURCES) $(SCALA_TEST_SOURCES) $(SCALA_BUILD_FILES)
	# Compiling Scala code for test with coverage
	sbt coverage compile test:compile
	touch $(SCALA_COVERAGE_DIR) $(SCALA_TEST_CLASSES_DIR)
$(SCALA_TEST_CLASSPATH_EXPORTED): $(SCALA_BUILD_FILES)
	# Exporting CLASSPATH for tests
	sbt --error coverage "export test:full-classpath" | tee /dev/stderr | \
	    tail -1 | tee $(SCALA_MAIN_CLASSPATH_EXPORTED) >$@

# How to build an assembly jar
.PHONY: scala-assembly-jar
scala-assembly-jar: $(SCALA_ASSEMBLY_JAR)
$(SCALA_ASSEMBLY_JAR): $(SCALA_MAIN_SOURCES) $(SCALA_BUILD_FILES)
	sbt clean assembly

# How to clean
.PHONY: scala-clean
scala-clean:
	sbt clean
	rm -f $(SCALA_MAIN_CLASSPATH_EXPORTED) $(SCALA_TEST_CLASSPATH_EXPORTED) $(SCALA_ASSEMBLY_JAR)
