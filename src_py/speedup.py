from argparse import ArgumentParser
from pathlib import Path
from collections import defaultdict
from statistics import geometric_mean
import csv


def main():
    argparser = ArgumentParser()
    argparser.add_argument("--input", type=Path, required=True)
    argparser.add_argument("--geomeans", type=Path, required=True)
    argparser.add_argument("--means", type=Path, required=True)
    args = argparser.parse_args()
    
    benchmark_to_variants = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
    with open(args.input, newline='') as csvfile:
        reader = csv.DictReader(csvfile)
        for row in reader:
            benchmark_to_variants[row["benchmark"]][row["variant"]]["time_ms"].append(int(row["time_ms"]))
            benchmark_to_variants[row["benchmark"]][row["variant"]]["last_it_us"].append(int(row["last_it_us"]))

    benchmark_to_variants_to_means = defaultdict(lambda: defaultdict(dict))
    for b, variants in benchmark_to_variants.items():
        for variant, times in variants.items():
            time_ms = times["time_ms"]
            last_it_us = times["last_it_us"]
            benchmark_to_variants_to_means[b][variant]["time_ms"] = sum(time_ms) / len(time_ms)
            benchmark_to_variants_to_means[b][variant]["last_it_us"] = sum(last_it_us) / len(last_it_us)

    # geomean between pgo and merged
    gmean_pgo_merged = geometric_mean([v["pgo"]["time_ms"]/v["merged"]["time_ms"]
                               for b, v in benchmark_to_variants_to_means.items()])
    # geomean between pgo-ex and merged-ex
    gmean_pgo_ex_merged_ex = geometric_mean([v["pgo-ex"]["time_ms"] / v["merged-ex"]["time_ms"]
                               for b, v in benchmark_to_variants_to_means.items()])

    # geomean between pgo and merged last iteration
    gmean_pgo_merged_li = geometric_mean([v["pgo"]["last_it_us"] / v["merged"]["last_it_us"]
                                       for b, v in benchmark_to_variants_to_means.items()])
    # geomean between pgo-ex and merged-ex last iteration
    gmean_pgo_ex_merged_ex_li = geometric_mean([v["pgo-ex"]["last_it_us"] / v["merged-ex"]["last_it_us"]
                                             for b, v in benchmark_to_variants_to_means.items()])

    with open(args.means, "w") as f:
        # benchmark, baseline_ms, baseline_li, pgo_ms, pgo_li, merged_ms, merged_li, pgo_ex_ms, pgo_ex_li, merged_ex_ms, merged_ex_li
        f.write("benchmark, baseline_ms, baseline_li, pgo_ms, pgo_li, merged_ms, merged_li, pgo_ex_ms, pgo_ex_li, merged_ex_ms, merged_ex_li\n")
        for benchmark, variants in benchmark_to_variants_to_means.items():
            line = []
            line.append(benchmark)
            line.append(variants["baseline"]["time_ms"])
            line.append(variants["baseline"]["last_it_us"])
            line.append(variants["pgo"]["time_ms"])
            line.append(variants["pgo"]["last_it_us"])
            line.append(variants["merged"]["time_ms"])
            line.append(variants["merged"]["last_it_us"])
            line.append(variants["pgo-ex"]["time_ms"])
            line.append(variants["pgo-ex"]["last_it_us"])
            line.append(variants["merged-ex"]["time_ms"])
            line.append(variants["merged-ex"]["last_it_us"])
            line = [str(x) for x in line]
            f.write(f"{",".join(line)}\n")

    with open(args.geomeans, "w") as f:
        f.write(f"gmean_pgo_merged: {gmean_pgo_merged}\n")
        f.write(f"gmean_pgo_ex_merged: {gmean_pgo_ex_merged_ex}\n")
        f.write(f"gmean_pgo_merged_li: {gmean_pgo_merged_li}\n")
        f.write(f"gmean_pgo_ex_merged_ex: {gmean_pgo_ex_merged_ex_li}\n")
    return

if __name__ == "__main__":
    main()
