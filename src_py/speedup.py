from argparse import ArgumentParser
from pathlib import Path
from collections import defaultdict
from statistics import geometric_mean
import csv


def main():
    argparser = ArgumentParser()
    argparser.add_argument("--input", type=Path, required=True)
    argparser.add_argument("--output", type=Path, required=True)
    args = argparser.parse_args()
    pgo_times = defaultdict(list)
    perf_times = defaultdict(list)
    with open(args.input, newline='') as csvfile:
        reader = csv.DictReader(csvfile)
        for row in reader:
            if row.get("variant") == "pgo":
                pgo_times[row.get("benchmark")].append(row.get("time_ms"))
            else:
                perf_times[row.get("benchmark")].append(row.get("time_ms"))

    benchmark_to_mean = {}
    for k, v in pgo_times.items():
        perf_v = perf_times[k]
        ratio = [int(x)/int(y) for x, y in zip(v, perf_v)]
        mean = sum(ratio) / len(ratio)
        benchmark_to_mean[k] = mean
    gmean = geometric_mean(benchmark_to_mean.values())
    with open(args.output, "w") as f:
        f.write(f"geo mean: {gmean}\n")
    return

if __name__ == "__main__":
    main()