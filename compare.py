import json
import argparse
import csv
from collections import defaultdict
from typing import Dict


def load_iprof(path):
    with open(path) as f:
        return json.load(f)

def build_method_map(iprof):
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
        ratios[key] = {idx: branches[idx] / total for idx in branches}

    return ratios

def reconciliation_keys(bci_to_ratio_a: Dict[str, float], bci_to_ratio_b: Dict[str, float]):
    sorted_keys_a = sorted(bci_to_ratio_a.keys())
    sorted_keys_b = sorted(bci_to_ratio_b.keys())
    for (key_a, key_b) in zip(sorted_keys_a, sorted_keys_b):
        if key_a == key_b:
            continue
        bci_to_ratio_b[key_a] = bci_to_ratio_b.pop(key_b)

    return

def compare_ratios(ratios_perf, ratios_graalvm):
    """
    Compare ratios for matching (ctx, bci).
    Returns list of:
      (difference_score, ctx, bci, ratiosA, ratiosB)
    """
    # keys = set(rA.keys()) | set(rB.keys())
    keys = set(ratios_perf.keys())
    out = []

    with open("keysA", "w") as f:
        f.writelines([f"{e}\n" for e in ratios_perf.keys()])
    with open("keysB","w") as f:
        f.writelines([f"{e}\n" for e in ratios_graalvm.keys()])

    for key in keys:
        a = ratios_perf.get(key)
        b = ratios_graalvm.get(key)

        if a is None or b is None:
            tmp = a if a is not None else b
            itemstmp = list(tmp.items())
            out.append((
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
        if a.keys() != b.keys():
            reconciliation_keys(a, b)
            print("different keys")
        assert a.keys() == b.keys()
        assert len(a.keys()) == 2
        # The difference must be symmetric
        # if for one profile the true branch has ratio 0.8 and false branch has ratio 0.2
        # while for the other profile the ratios are 0.8 and 0.2
        # the difference between the ratios for the two branches must be the same, with inverted sign
        first_value_a = sorted(a.items())[0][1]
        first_value_b = sorted(b.items())[0][1]
        diff = abs(first_value_a - first_value_b)

        itemsa = sorted(a.items())
        itemsb = sorted(b.items())

        out.append((
            diff,
            key,
            itemsa[0][0],
            itemsa[1][0],
            itemsa[0][1],
            itemsa[1][1],
            itemsb[0][1],
            itemsb[1][1],
        ))

    return sorted(out, key=lambda x: x[0])

def main():
    parser = argparse.ArgumentParser(description="Compare conditional profiles between two iprofs.")
    parser.add_argument("iprofA", help="First iprof file")
    parser.add_argument("iprofB", help="Second iprof file")
    parser.add_argument("--threshold", type=float, default=0.01,
                        help="Minimum difference score to include in CSV output")
    parser.add_argument("--csv", default="output.csv",
                        help="Output CSV file path")

    args = parser.parse_args()

    iprof_perf = load_iprof(args.iprofA)
    iprof_graalvm = load_iprof(args.iprofB)

    method_map_perf = build_method_map(iprof_perf)
    method_map_graalvm = build_method_map(iprof_graalvm)

    cond_perf = extract_conditional(iprof_perf, method_map_perf)
    cond_graalvm = extract_conditional(iprof_graalvm, method_map_graalvm)

    ratios_perf = compute_ratios(cond_perf)
    ratios_graalvm = compute_ratios(cond_graalvm)

    diffs = compare_ratios(ratios_perf, ratios_graalvm)

    # Write CSV
    with open(args.csv, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["difference", "ctx", "bcitrue", "bcifalse", "count_true_a", "count_false_a", "count_true_b", "count_false_b"])

        for diff, ctx, bci, bcifalse, at, af, bt, bf in diffs:
            # if diff >= args.threshold:
            writer.writerow([diff, ctx, bci, bcifalse, at, af, bt, bf])

    print(f"Done. Wrote results with diff >= {args.threshold} to {args.csv}")

if __name__ == "__main__":
    main()
