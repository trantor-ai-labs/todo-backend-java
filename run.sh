#!/usr/bin/env bash
# Build and run with nothing but a JDK.
#
# Maven is declared in pom.xml (and `mvn package` works), but is not required: there are no
# dependencies to resolve, so javac alone is enough. That matters for a demo — the backend must
# never be the reason a migration showcase fails to start.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p target/classes
javac --release 21 -d target/classes src/main/java/xyz/trantor/todo/*.java
echo "compiled with $(javac -version 2>&1)"
exec java -cp target/classes xyz.trantor.todo.TodoServer
