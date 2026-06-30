#!/bin/bash
set -euo pipefail

NAME=$1
NATIVE_IMAGE_INPUT=$2
PROGRAM_ARGUMENTS=$3

# rebuild with mvn for safety
mvn package

# create folder
mkdir -p "$NAME"
mkdir -p "$NAME"_reduced

# create native image
BUILD_LOG=$(mktemp)
  # -jar target/Control-1.0-SNAPSHOT.jar
  # -H:Dump=:4 -H:MethodFilter="Main.*" \
  # -H:Dump=:4 \
"$JAVA_HOME"/bin/native-image \
  -g \
  -H:+DebugCodeInfoUseSourceMappings \
  $NATIVE_IMAGE_INPUT | tee "$BUILD_LOG"

# Extract the produced binary name
BIN_NAME=$(grep "Finished generating" "$BUILD_LOG" | sed -E "s/.*'([^']+)'.*/\1/")

# echo "Binary produced: $BIN_NAME"

if [[ -z "$BIN_NAME" ]]; then
  echo "Could not detect native-image output binary"
  exit 1
fi

# move extracted info
mv condition_mapping.txt source_mapping.txt "$NAME"

# run perf
objdump -d "$BIN_NAME" -l >  "$NAME"/disassembly.S

perf record -e intel_pt//u -o "$NAME"/perf.data -- "./$BIN_NAME" $PROGRAM_ARGUMENTS

perf script --insn-trace  --no-demangle \
  --fields ip,sym,symoff \
   -i "$NAME"/perf.data > "$NAME"/perf_clean


cp "$NAME"/* "$NAME"_reduced

java -Xms512m -Xmx20g -classpath target/classes dag.usi.ch.App \
     --objdump="$NAME"_reduced/disassembly.S \
     --sm="$NAME"_reduced/source_mapping.txt \
     --perf="$NAME"_reduced/ \
     --condition="$NAME"_reduced/condition_mapping.txt
