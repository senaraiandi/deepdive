# Makefile for DeepDive

.DEFAULT_GOAL := install

.PHONY: depends
depends:
	@echo "=== Installing and Checking dependencies... ==="
	util/install.sh deepdive_build_deps deepdive_runtime_deps
	lib/check-depends.sh
	@echo "=== Extracting sampler library... ==="
	lib/dw_extract.sh

# Some path names for Scala
SCALA_BUILD_FILES             = build.sbt $(wildcard project/*.*)
SCALA_MAIN_SOURCES            = $(shell find src/main/scala -name '*.scala')
SCALA_MAIN_CLASSES_DIR        = target/scala-2.10/classes
SCALA_MAIN_CLASSPATH_EXPORTED = shell/deepdive-run.classpath
SCALA_TEST_SOURCES            = $(shell find src/test/scala -name '*.scala')
SCALA_TEST_CLASSES_DIR        = target/scala-2.10/test-classes
SCALA_TEST_CLASSPATH_EXPORTED = test/.classpath

# SBT on PATH
$(SCALA_MAIN_CLASSES_DIR) \
$(SCALA_MAIN_CLASSPATH_EXPORTED) \
$(SCALA_TEST_CLASSES_DIR) \
$(SCALA_TEST_CLASSPATH_EXPORTED) \
install: PATH := $(PATH):$(shell pwd)/sbt

.PHONY: build
build: $(SCALA_MAIN_CLASSES_DIR) $(SCALA_MAIN_CLASSPATH_EXPORTED)
$(SCALA_MAIN_CLASSES_DIR): $(SCALA_MAIN_SOURCES)
	# Compiling Scala code
	sbt compile
	touch $(SCALA_MAIN_CLASSES_DIR)
$(SCALA_MAIN_CLASSPATH_EXPORTED): $(SCALA_BUILD_FILES)
	# Exporting CLASSPATH
	sbt "export compile:full-classpath" | tail -1 >$@

.PHONY: test coverage-build
test: ONLY = $(shell test/enumerate-tests.sh)
test: $(ONLY) $(SCALA_TEST_CLASSES_DIR) $(SCALA_TEST_CLASSPATH_EXPORTED)
	# Running $(words $(ONLY)) tests with Bats
	#  To test selectively, run:  make test ONLY=/path/to/bats/files
	#  For a list of tests, run:  make test-list
	test/bats/bin/bats $(ONLY)
coverage-build \
$(SCALA_TEST_CLASSES_DIR): $(SCALA_MAIN_SOURCES) $(SCALA_TEST_SOURCES)
	# Compiling Scala code for test with coverage
	sbt coverage compile test:compile
	touch $(SCALA_TEST_CLASSES_DIR)
$(SCALA_TEST_CLASSPATH_EXPORTED): $(SCALA_BUILD_FILES)
	# Exporting CLASSPATH for tests
	sbt coverage "export test:full-classpath" | tail -1 >$@
test/%/scalatests.bats: test/postgresql/update-scalatests.bats.sh $(SCALA_TEST_SOURCES)
	# Regenerating .bats for Scala tests
	$< >$@
	chmod +x $@

.PHONY: test-list
test-list:
	@test/enumerate-tests.sh | sed 's/^/make test ONLY=/'

.PHONY: checkstyle
checkstyle:
	@./test/checkstyle.sh

.PHONY: install
PREFIX = ~/local
DEST = $(PREFIX)/bin
install: depends build
	# Installing DeepDive to $(PREFIX)/
	mkdir -p $(DEST)
	ln -sfnv $(realpath shell/deepdive) $(DEST)/
	@if [ -x $(DEST)/deepdive ]; then \
		echo '# DeepDive binary has been install to $(DEST)/.'; \
		echo '# Make sure your shell is configured to include the directory in PATH environment, e.g.:'; \
		echo '  PATH=$(DEST):$$PATH'; \
	fi


.PHONY: build-sampler
build-sampler:
	git submodule update --init sampler
	[ -e sampler/lib/gtest -a -e sampler/lib/tclap ] || $(MAKE) -C sampler dep
	$(MAKE) -C sampler dw
ifeq ($(shell uname),Linux)
	cp -f sampler/dw util/sampler-dw-linux
endif
ifeq ($(shell uname),Darwin)
	cp -f sampler/dw util/sampler-dw-mac
endif

.PHONY: build-mindbender
build-mindbender:
	git submodule update --init mindbender
	$(MAKE) -C mindbender
	cp -f mindbender/mindbender-LATEST-*.sh util/mindbender

.PHONY: build-ddlog
build-ddlog:
	git submodule update --init ddlog
	$(MAKE) -C ddlog ddlog.jar
	cp -f ddlog/ddlog.jar util/ddlog.jar

