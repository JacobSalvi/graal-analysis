from argparse import ArgumentParser
from pathlib import Path
from collections import defaultdict
from statistics import geometric_mean
import numpy as np
import matplotlib.pyplot as plt
import csv


def extract_times_dacapo(input: Path) -> list[int]:
    with open(input, "r") as f:
        # Version: avrora cvs-20131011 (use -p to print nominal benchmark stats)
        # ===== DaCapo unknown avrora starting warmup 1 =====
        # ===== DaCapo unknown avrora completed warmup 1 in 12293 msec =====
        # ===== DaCapo unknown avrora starting =====
        # ===== DaCapo unknown avrora PASSED in 8508 msec =====
        lines = f.readlines()
        times = []
        for line in lines:
            if "completed warmup" in line:
                time_ms = int(line.split(" ")[-3])
                times.append(time_ms)
            if "PASSED" in line:
                time_ms = int(line.split(" ")[-3])
                times.append(time_ms)
    return times

def extract_times_ren(input: Path) -> list[int]:
    times = []
    with open(input) as f:
        reader = csv.DictReader(f)
        for row in reader:
            print(input)
            times = [int(row["duration_ns"]) for row in reader]
    return times


def extract_times_awfy(input: Path) -> list[int]:
    # Starting Bounce benchmark ...
    # Bounce: iterations=1 runtime: 838us
    # Bounce: iterations=1 runtime: 817us
    # Bounce: iterations=1 runtime: 799us
    # Bounce: iterations=1 runtime: 807us
    # Bounce: iterations=1 runtime: 804us
    # Bounce: iterations=1 runtime: 824us
    # Bounce: iterations=1 runtime: 799us
    # Bounce: iterations=1 runtime: 798us
    # Bounce: iterations=1 runtime: 798us
    # Bounce: iterations=1 runtime: 857us
    # Bounce: iterations=10 average: 814us total: 8141us
    # Total Runtime: 8141us
    times = []
    with open(input) as f:
        for line in f:
            if "runtime: " in line:
                times.append(int(line.split("runtime: ")[1].replace("us\n", "")))

    return times


def bar_chart(benchmark_to_variants_to_means: dict, output_folder: Path):

    benchmarks = list(benchmark_to_variants_to_means.keys())
    variants = ["pgo", "merged", "pgo-ex", "merged-ex"]

    def plot_metric(metric, output_file):
        x = np.arange(len(benchmarks))
        width = 0.25

        fig, ax = plt.subplots(figsize=(10, 5))

        for i, variant in enumerate(variants):
            values = []
            for b in benchmarks:
                baseline = benchmark_to_variants_to_means[b]["baseline"][metric]
                current = benchmark_to_variants_to_means[b][variant][metric]

                # relative difference (%)
                if current == 0 and baseline == 0:
                    values.append(1)
                else:
                    values.append(baseline/current)

            ax.bar(x + (i - 1) * width, values, width, label=variant)

        ax.axhline(1, color="black", linestyle="--", linewidth=1, label="baseline")
        ax.set_xticks(x)
        ax.set_xticklabels(benchmarks, rotation=45, ha="right")
        ax.set_ylabel("Runtime relative to baseline")
        ax.set_title(metric)
        ax.legend()
        plt.tight_layout()
        plt.savefig(output_file)

    plot_metric("iteration_average", output_folder.joinpath("iteration_average_file.png"))
    plot_metric("total_average", output_folder.joinpath("total_average_file.png"))
    return



def main():
    argparser = ArgumentParser()
    argparser.add_argument("--input", dest="input", type=Path, required=True)
    argparser.add_argument("--output", type=Path, required=True)
    argparser.add_argument("--kind",choices=["dacapo", "ren", "awfy"], type=str, required=True)
    args = argparser.parse_args()
    
    kind: str = args.kind
    root: Path = args.input
    output_folder: Path = args.output
    mean_file: Path = output_folder.joinpath("means.csv")
    geomean_file: Path = output_folder.joinpath("geomean.txt")
    if kind == "dacapo":
       time_extractor = extract_times_dacapo
    elif kind == "ren":
        time_extractor = extract_times_ren
    else:
        time_extractor = extract_times_awfy

    benchmark_to_data = defaultdict(lambda: defaultdict(list))
    for folder in root.iterdir():
        if not folder.is_dir():
            continue
        name = folder.name
        run_files = [f for f in folder.iterdir() if "run_" in f.name and "total" not in f.name]
        run_total_files = [f for f in folder.iterdir() if "run_total" in f.name]
        for run_file in run_files:
            s = run_file.stem.split("_")
            variant = s[1]
            run_number = s[2]
            times = time_extractor(run_file)
            # average over the last 5 iterations to account for warmup
            avg = sum(times[:-5])/5
            # get total time
            total_file = [f for f in run_total_files if variant in f.name and f.stem.split("_")[-1] == run_number][0]
            with open(total_file) as f:
                total = int(f.readline().strip())
            benchmark_to_data[name][variant].append((run_number, avg, total))    

    benchmark_to_variants_to_means = defaultdict(lambda: defaultdict(dict))
    for b, variants in benchmark_to_data.items():
        for variant, data in variants.items():
            # total
            average = sum(el[1] for el in data) / len(data)
            total_average = sum(el[2] for el in data) / len(data)
            benchmark_to_variants_to_means[b][variant]["total_average"] = total_average
            benchmark_to_variants_to_means[b][variant]["iteration_average"] = average

    # geomean between pgo and merged
    gmean_pgo_merged = geometric_mean([v["pgo"]["total_average"]/v["merged"]["total_average"]
                               for b, v in benchmark_to_variants_to_means.items()])
    # geomean between pgo-ex and merged-ex
    gmean_pgo_ex_merged_ex = geometric_mean([v["pgo-ex"]["total_average"] / v["merged-ex"]["total_average"]
                               for b, v in benchmark_to_variants_to_means.items()])

    # geomean between pgo and merged last iteration
    gmean_pgo_merged_li = geometric_mean([v["pgo"]["iteration_average"] / (v["merged"]["iteration_average"] if v["merged"]["iteration_average"] != 0 else 1)
                                       for b, v in benchmark_to_variants_to_means.items()])
    # geomean between pgo-ex and merged-ex last iteration
    gmean_pgo_ex_merged_ex_li = geometric_mean([v["pgo-ex"]["iteration_average"] / (v["merged-ex"]["iteration_average"] if v["merged-ex"]["iteration_average"] != 0 else 1)
                                             for b, v in benchmark_to_variants_to_means.items()])
    # geomean between baseline and pgo
    gmean_baseline_pgo = geometric_mean([v["baseline"]["total_average"] / v["pgo"]["total_average"]
                                                for b, v in benchmark_to_variants_to_means.items()])
    # geomean between baseline and merged
    gmean_baseline_merged = geometric_mean([v["baseline"]["total_average"] / v["merged"]["total_average"]
                                         for b, v in benchmark_to_variants_to_means.items()])

    with open(mean_file, "w") as f:
        # benchmark, baseline_ms, baseline_li, pgo_ms, pgo_li, merged_ms, merged_li, pgo_ex_ms, pgo_ex_li, merged_ex_ms, merged_ex_li
        f.write("benchmark, baseline_total, baseline_it, pgo_total, pgo_it, merged_total, merged_it, pgo_ex_total, pgo_ex_it, merged_ex_total, merged_ex_it\n")
        for benchmark, variants in benchmark_to_variants_to_means.items():
            line = []
            line.append(benchmark)
            line.append(variants["baseline"]["total_average"])
            line.append(variants["baseline"]["iteration_average"])
            line.append(variants["pgo"]["total_average"])
            line.append(variants["pgo"]["iteration_average"])
            line.append(variants["merged"]["total_average"])
            line.append(variants["merged"]["iteration_average"])
            line.append(variants["pgo-ex"]["total_average"])
            line.append(variants["pgo-ex"]["iteration_average"])
            line.append(variants["merged-ex"]["total_average"])
            line.append(variants["merged-ex"]["iteration_average"])
            line = [str(x) for x in line]
            f.write(f"{",".join(line)}\n")

    with open(geomean_file, "w") as f:
        f.write(f"gmean_pgo_merged: {gmean_pgo_merged}\n")
        f.write(f"gmean_pgo_ex_merged: {gmean_pgo_ex_merged_ex}\n")
        f.write(f"gmean_pgo_merged_li: {gmean_pgo_merged_li}\n")
        f.write(f"gmean_pgo_ex_merged_ex: {gmean_pgo_ex_merged_ex_li}\n")
        f.write(f"gmean_baseline_pgo: {gmean_baseline_pgo}\n")
        f.write(f"gmean_baseline_merged: {gmean_baseline_merged}\n")
    bar_chart(benchmark_to_variants_to_means=benchmark_to_variants_to_means, output_folder=output_folder)
    return

if __name__ == "__main__":
    main()
