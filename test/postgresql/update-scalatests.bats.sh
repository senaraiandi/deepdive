#!/usr/bin/env bash
# A script for regnerating scalatests.bats
set -eu

cd "$(dirname "$0")"
{
    cat <<EOF
#!/usr/bin/env bats
# DeepDive Scala Tests
# Generated: $(date +%FT%T)

. "\$BATS_TEST_DIRNAME"/env.sh >&2

EOF

    # enumerate all tests with SBT
    (cd ../.. && sbt "export printTests") | grep ^org.deepdive.test.unit | sort |
    # generate a corresponding Bats test
    sed 's#.*#@test "$DBVARIANT ScalaTest &" { java org.scalatest.tools.Runner -oDF -s & ; }#'
}
