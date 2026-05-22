#!/bin/bash

# ensure output folder exist
OUTPUT_FOLDER="$1"
mkdir -p "$OUTPUT_FOLDER"

PROGRAM="$2"

# arguments already taken
shift
shift

PROGRAM_ARGUMENTS=("$@")

cd "/home/jacob/PHD/dacapobench-23.11-MR2-chopin/avrora_dir/" || exit 0

# grant sudo before the loop
sudo -v

# run program
echo $PROGRAM_ARGUMENTS
"$PROGRAM" "${PROGRAM_ARGUMENTS[@]}" &
PID=$!

TIMEOUT=0.1s
SLEEP_AMOUNT=0.5

i=0
# sudo timeout --signal=INT --kill-after=15s 3s perf record -e intel_pt// -p 1128192 -o perf.data
while kill -0 "$PID" 2>/dev/null; do
    OUTPUT_FILE="${OUTPUT_FOLDER}/output_${i}.data"

    sudo timeout --signal=INT --kill-after=3s "$TIMEOUT" \
        perf record -e intel_pt//u -p "$PID" -o "$OUTPUT_FILE"

    ((i++))
    sleep $SLEEP_AMOUNT
done
sudo chown -R "$USER":"$USER" "$OUTPUT_FOLDER"
