#!/bin/sh
# Compiles and starts Gudotopia (needs Java 17+). The SQLite driver is in lib/.
cd "$(dirname "$0")" && mkdir -p out && javac -d out -cp "lib/*" $(ls src/*.java) && java -cp "out:lib/*" Main
