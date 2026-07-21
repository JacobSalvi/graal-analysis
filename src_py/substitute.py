from argparse import ArgumentParser
from pathlib import Path
from typing import Dict
import json


def load_iprof(path: Path) -> Dict:
    with open(path) as f:
        return json.load(f)


def main():
    parser = ArgumentParser()
    parser.add_argument("-p", "--perf-profile", type=Path, required=True)
    parser.add_argument("-g", "--graal-profile", type=Path, required=True)
    parser.add_argument("-o", "--output", type=Path, required=True)
    args = parser.parse_args()

    perf_profile_file = args.perf_profile
    graal_profile_file = args.graal_profile

    perf_profile = load_iprof(perf_profile_file)
    graal_profile = load_iprof(graal_profile_file)

    substitute_conditional_profiles(perf_profile, graal_profile)
    output: Path = args.output
    output.touch()
    with output.open("w") as f:
        json.dump(graal_profile, f)
    return

def substitute_conditional_profiles(perf_profile: Dict, graal_profile: Dict):
    perf_cp: Dict = perf_profile.get("conditionalProfiles", [])
    perf_methods: Dict = perf_profile.get("methods", [])
    perf_types: Dict = perf_profile.get("types", [])
    perf_types = {el.get("id"): el.get("name") for el in perf_types}

    graal_methods: Dict = graal_profile.get("methods", [])
    graal_types: Dict = graal_profile.get("types", [])
    graal_types = {el.get("id"): el.get("name") for el in graal_types}
    new_cp= []
    cache = {}
    for conditional_profile in perf_cp:
        ctx = conditional_profile.get("ctx", "")
        records = conditional_profile.get("records", [])

        method_calls = ctx.split("<")
        new_method_call = []

        for method_call in method_calls:
            split_call = method_call.split(":")
            method_id = int(split_call[0])
            bci = split_call[1]
            # use cache to accelerate computation
            if method_id in cache:
                new_method_call.append(f"{cache.get(method_id)}:{bci}")
                continue

            # look for the right method in the perf profile
            m = next(method for method in perf_methods if method.get("id") == method_id)
            sig = [perf_types.get(t) for t in m.get("signature")]
            # for t in m.get("signature"):
            #     sig.append(perf_types.get(t))
                # for pt in perf_types:
                #     if pt.get("id") == t:
                #         sig.append(pt)
            # match this to the corresponding method in the graal_profile
            candidate_methods = [
                gm
                for gm in graal_methods
                if gm.get("name") == m.get("name")
                and len(gm.get("signature")) == len(m.get("signature"))
            ]
            result= None
            for candidate_method in candidate_methods:
                c_sig = candidate_method.get("signature")
                c_sig = [graal_types.get(t) for t in candidate_method.get("signature")]
                if all(pt == gp for pt, gp in zip(sig, c_sig)):
                    result = candidate_method
                    break
            if result is not None:
                new_method_call.append(f"{result.get("id")}:{bci}")
                cache[method_id] = result.get('id')
        if len(new_method_call) == len(method_calls):
            new_cp.append({"ctx": "<".join(new_method_call), "records": records})

    graal_profile["conditionalProfiles"] = new_cp
    return


if __name__ == "__main__":
    main()
