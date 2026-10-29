#!/bin/sh
# Compiles and starts Gudotopia (needs Java 17+).
cd "$(dirname "$0")" && mkdir -p out && javac -d out src/*.java && java -cp out Main
