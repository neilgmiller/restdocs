#!/bin/bash
# Produces a clean build of the distribution zip (build/distributions/*.zip)
set -e

DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
cd "$DIR"

./gradlew clean distZip

echo
echo "Distribution zip:"
ls -1 build/distributions/*.zip
