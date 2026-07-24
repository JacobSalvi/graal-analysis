import json
import argparse
import csv
import numpy as np
import matplotlib.pyplot as plt
from collections import defaultdict
from typing import Dict
from pathlib import Path


def load_iprof(path: Path) -> Dict:
    with open(path) as f:
        return json.load(f)

def build_method_map(iprof: Dict) -> Dict:
    type_map = {t["id"]: t["name"] for t in iprof["types"]}
    method_map = {}
    for m in iprof["methods"]:
        parts = [type_map[e] for e in m["signature"]]
        qualified_name = f"{parts[0]}.{m["name"]}({",".join(parts[1:])})"
        method_map[m["id"]] = qualified_name
    # return {m["id"]: qualified_name for m in iprof["methods"]}
    return method_map



def canonicalize_ctx(ctx, method_map):
    parts = ctx.split("<")
    out = []
    for p in parts:
        mid, bci = p.split(":")
        mid = int(mid)
        name = method_map.get(mid, f"UNKNOWN_{mid}")
        if "UNKNOWN" in name:
            pass
        out.append(f"{name}:{bci}")
    return "<".join(out)

def extract_conditional(iprof, method_map):
    """
    Returns:
      dict[(canonical_ctx, bci, index)] = count
    """
    result = {}
    for entry in iprof.get("conditionalProfiles", []):
        canon = canonicalize_ctx(entry["ctx"], method_map)
        rec = entry["records"]

        # records = [bci0, idx0, count0, bci1, idx1, count1, ...]
        for i in range(0, len(rec), 3):
            bci = rec[i]
            idx = rec[i+1]
            cnt = rec[i+2]
            result[(canon, bci, idx)] = cnt

    return result

def compute_ratios(profile):
    """
    Groups by (ctx, bci) and computes taken ratio for each branch index.
    Returns:
      dict[(ctx, bci)] = { index: ratio }
    """
    grouped = defaultdict(lambda: defaultdict(int))

    for (ctx, bci, idx), cnt in profile.items():
        grouped[ctx][bci] += cnt

    ratios = {}
    for key, branches in grouped.items():
        total = sum(branches.values())
        if total == 0:
            continue
        ratios[key] = (total, {idx: branches[idx] for idx in branches})
        # ratios[key] = (total, (branches[idx] for idx in branches), {idx: branches[idx] / total for idx in branches})

    return ratios

def reconciliation_keys(bci_to_ratio_a: Dict[str, float], bci_to_ratio_b: Dict[str, float]):
    sorted_keys_a = sorted(bci_to_ratio_a.keys())
    sorted_keys_b = sorted(bci_to_ratio_b.keys())
    for (key_a, key_b) in zip(sorted_keys_a, sorted_keys_b):
        if key_a == key_b:
            continue
        bci_to_ratio_b[key_a] = bci_to_ratio_b.pop(key_b)

    return

def compare_ratios(ratios_perf, ratios_graalvm, output_folder: Path):
    """
    Compare ratios for matching (ctx, bci).
    Returns list of:
      (difference_score, ctx, bci, ratiosA, ratiosB)
    """
    # keys = set(rA.keys()) | set(rB.keys())
    keys = set(ratios_perf.keys())
    out = []

    with open(output_folder.joinpath("keys_perf"), "w") as f:
        f.writelines([f"{e}\n" for e in ratios_perf.keys()])
    with open(output_folder.joinpath("keys_graalvm"),"w") as f:
        f.writelines([f"{e}\n" for e in ratios_graalvm.keys()])

    for key in keys:
        perf_total, perf_ratio = ratios_perf.get(key)
        perf_ratio = {k: v/ perf_total for k,v in perf_ratio.items()}
        graalvm_total, graalvm_ratio = ratios_graalvm.get(key) if ratios_graalvm.get(key) != None else (None, None)
        if ratios_graalvm.get(key) == None:
            candidates = [k for k in ratios_graalvm if key in k]
            if candidates:
                graalvm_total = 0
                graalvm_ratio = defaultdict(int)
                for k in candidates:
                    temp_total, temp_ratio = ratios_graalvm.get(k)
                    graalvm_total += temp_total
                    for k, v in temp_ratio.items():
                        graalvm_ratio[k] += v

            pass
        if graalvm_ratio != None:
            graalvm_ratio = {k: v/ graalvm_total for k,v in graalvm_ratio.items()}

        if perf_ratio is None or graalvm_ratio is None:
            tmp = perf_ratio if perf_ratio is not None else graalvm_ratio
            itemstmp = list(tmp.items())
            out.append((
                1,
                1,
                key,
                itemstmp[0][0],
                itemstmp[1][0] if len(itemstmp) > 1 else None,
                itemstmp[0][1],
                itemstmp[1][1] if len(itemstmp) > 1 else None,
                -1,
                -1,
            ))
            continue
        if perf_ratio.keys() != graalvm_ratio.keys():
            reconciliation_keys(perf_ratio, graalvm_ratio)
            print("different keys")
        # assert perf_ratio.keys() == graalvm_ratio.keys()
        # assert len(perf_ratio.keys()) == 2

        if len(perf_ratio) == 1 or len(graalvm_ratio) == 1:
            for k, v in perf_ratio.items():
                graalvm_ratio.setdefault(k, v)
            for k, v in graalvm_ratio.items():
                perf_ratio.setdefault(k, v)
        # The difference must be symmetric
        # if for one profile the true branch has ratio 0.8 and false branch has ratio 0.2
        # while for the other profile the ratios are 0.8 and 0.2
        # the difference between the ratios for the two branches must be the same, with inverted sign
        first_value_a = sorted(perf_ratio.items())[0][1]
        first_value_b = sorted(graalvm_ratio.items())[0][1]
        diff = abs(first_value_a - first_value_b)

        items_perf = sorted(perf_ratio.items())
        items_graalvm = sorted(graalvm_ratio.items())
        if len(items_graalvm) > 2:
            continue

        if len(items_perf) == 1:
            out.append((
                perf_total,
                diff,
                key,
                items_perf[0][0],
                None,
                items_perf[0][1],
                None,
                items_graalvm[0][1],
                None,
            ))
        else:
            out.append((
                perf_total,
                diff,
                key,
                items_perf[0][0],
                items_perf[1][0],
                items_perf[0][1],
                items_perf[1][1],
                items_graalvm[0][1],
                items_graalvm[1][1],
            ))

    return sorted(out, key=lambda x: x[0])

def main():
    parser = argparse.ArgumentParser(description="Compare conditional profiles between two iprofs.")
    parser.add_argument("iprof_perf", help="First iprof file")
    parser.add_argument("iprof_graalvm", help="Second iprof file")
    parser.add_argument("--threshold", type=float, default=0.01,
                        help="Minimum difference score to include in CSV output")
    parser.add_argument("--output_folder", default=Path("Output"), type=Path,
                        help="Output folder")

    args = parser.parse_args()

    iprof_perf: Dict = load_iprof(args.iprof_perf)
    iprof_graalvm: Dict = load_iprof(args.iprof_graalvm)

    output_folder: Path = args.output_folder
    if not output_folder.is_dir():
        output_folder.mkdir()
    output_csv: Path = output_folder.joinpath("output.csv")

    method_map_perf: Dict = build_method_map(iprof_perf)
    method_map_graalvm: Dict = build_method_map(iprof_graalvm)

    cond_perf = extract_conditional(iprof_perf, method_map_perf)
    cond_graalvm = extract_conditional(iprof_graalvm, method_map_graalvm)

    ratios_perf = compute_ratios(cond_perf)
    ratios_graalvm = compute_ratios(cond_graalvm)

    diffs = compare_ratios(ratios_perf, ratios_graalvm, output_folder)
    # ------------------------------------------------------------------
    # Histogram of diff values
    # ------------------------------------------------------------------

    diff_values = [d[1] for d in diffs]
    counts = [d[0] for d in diffs]

    # Weighted average diff
    weighted_avg = sum(c * d for c, d in zip(counts, diff_values)) / sum(counts)

    print(f"Weighted average diff: {weighted_avg:.6f}")

    # Create bins [0.0, 0.1, 0.2, ..., 1.0]
    bin_width = 0.01
    bins = np.arange(0.0, 1.0 + bin_width, bin_width)

    # ----------------------------------------------------------
    # Unweighted histogram
    # ----------------------------------------------------------
    plt.figure(figsize=(10, 6))

    hist, edges = np.histogram(diff_values, bins=bins)

    plt.bar(
        edges[:-1],
        hist,
        width=bin_width,
        align="edge",
        edgecolor="black"
    )

    plt.axvline(
        weighted_avg,
        color="red",
        linestyle="--",
        label=f"Weighted avg = {weighted_avg:.3f}"
    )

    plt.xlabel("Difference")
    plt.ylabel("Number of profiles")
    plt.title("Perf vs GraalVM Diff Distribution")
    plt.legend()
    plt.tight_layout()

    plt.savefig(output_folder / "diff_histogram.png", dpi=300)
    plt.close()


    # ----------------------------------------------------------
    # Weighted histogram
    # ----------------------------------------------------------
    plt.figure(figsize=(10, 6))

    weighted_hist, edges = np.histogram(
        diff_values,
        bins=bins,
        weights=counts,
        density=True
    )

    plt.bar(
        edges[:-1],
        weighted_hist,
        width=bin_width,
        align="edge",
        edgecolor="black"
    )

    plt.axvline(
        weighted_avg,
        color="red",
        linestyle="--",
        label=f"Weighted avg = {weighted_avg:.3f}"
    )

    plt.xlabel("Difference")
    plt.ylabel("Total execution count")
    plt.title("Perf vs GraalVM Diff Distribution (Weighted by Count)")
    plt.legend()
    plt.tight_layout()

    plt.savefig(output_folder / "diff_histogram_weighted.png", dpi=300)
    plt.close()

    # Write CSV
    with open(output_csv, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["count", "difference", "ctx", "bcitrue", "bcifalse", "count_true_a", "count_false_a", "count_true_b", "count_false_b"])

        for count, diff, ctx, bci, bcifalse, at, af, bt, bf in diffs:
            # if diff >= args.threshold:
            writer.writerow([count, diff, ctx, bci, bcifalse, at, af, bt, bf])

    print(f"Done. Wrote results with diff >= {args.threshold} to {output_csv}")

if __name__ == "__main__":
    main()
