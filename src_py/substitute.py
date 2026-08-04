from argparse import ArgumentParser
from pathlib import Path
from typing import Dict, List
import json


class Method:
    id: int
    name_raw: str
    signature: List
    resolved_name: str

    def __init__(self, id, raw_name, signature, name):
        self.id = id
        self.name_raw = raw_name
        self.signature = signature
        self.resolved_name = name


class ConditionalProfile:
    canon: str
    records: List
    ctx: str
    def __init__(self, canon, records, ctx):
        self.canon = canon
        self.records = records
        self.ctx = ctx


class Iprof:
    type_map: Dict
    method_map: Dict
    conditional_profiles: List

    def __init__(self, raw_iprof):
        self.type_map = {t["id"]: t["name"] for t in raw_iprof["types"]}
        self.method_map = {}
        self.conditional_profiles = []
        # methods
        for m in raw_iprof["methods"]:
            parts = [self.type_map[e] for e in m["signature"]]
            qualified_name = f"{parts[0]}.{m["name"]}({",".join(parts[1:])})"
            method = Method(m.get("id"), m.get("name", ""), m.get("signature", []), qualified_name)
            self.method_map[m["id"]] = method
        # conditional profiles
        for conditional_profile in raw_iprof.get("conditionalProfiles", {}):
            ctx = conditional_profile.get("ctx", "")
            canon = canonicalize_ctx(ctx, self.method_map)
            records = conditional_profile.get("records", [])
            self.conditional_profiles.append(ConditionalProfile(canon, records, ctx))
            pass
        pass


def load_iprof(path: Path) -> Dict:
    with open(path) as f:
        return json.load(f)

def canonicalize_ctx(ctx, method_map):
    parts = ctx.split("<")
    out = []
    for p in parts:
        mid, bci = p.split(":")
        mid = int(mid)
        method = method_map.get(mid, f"UNKNOWN_{mid}")
        name = method.resolved_name
        if "UNKNOWN" in name:
            pass
        out.append(f"{name}:{bci}")
    return "<".join(out)

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

    perf_iprof: Iprof = Iprof(perf_profile)
    graal_iprof: Iprof = Iprof(graal_profile)

    conditional_profiles = substitute_conditional_profiles(perf_iprof, graal_iprof)
    graal_profile["conditionalProfiles"] = conditional_profiles
    output: Path = args.output
    output.touch()
    with output.open("w") as f:
        json.dump(graal_profile, f)
    return

def substitute_conditional_profiles(perf_iprof: Iprof, graal_iprof: Iprof) -> List:
    new_cp= []
    for conditional_profile in perf_iprof.conditional_profiles:
        perf_canon = conditional_profile.canon
        perf_records = conditional_profile.records
        found = False

        # one to one correspondence between perf conditional profiles and graalvm conditional profile.
        for graal_cond_prof in graal_iprof.conditional_profiles:
            if perf_canon == graal_cond_prof.canon:
                new_cp.append({"ctx": graal_cond_prof.ctx, "records": perf_records})
                found = True

        if found:
            continue
        # try to find a inclusion reltionship between the two iprofs.
        candidates = [cp for cp in graal_iprof.conditional_profiles if perf_canon in cp.canon]
        for candidate in candidates:
            new_cp.append({"ctx": candidate.ctx, "records": perf_records})
        pass

    return new_cp

# def substitute_conditional_profiles(perf_iprof: Iprof, graal_iprof: Iprof) -> Dict:
#     perf_cp: Dict = perf_iprof.get("conditionalProfiles", [])
#     perf_methods: Dict = perf_iprof.get("methods", [])
#     perf_types: Dict = perf_iprof.get("types", [])
#     perf_types = {el.get("id"): el.get("name") for el in perf_types}

#     graal_methods: Dict = graal_iprof.get("methods", [])
#     graal_types: Dict = graal_iprof.get("types", [])
#     graal_types = {el.get("id"): el.get("name") for el in graal_types}
#     new_cp= []
#     cache = {}
#     for conditional_profile in perf_cp:
#         ctx = conditional_profile.get("ctx", "")
#         records = conditional_profile.get("records", [])

#         method_calls = ctx.split("<")
#         new_method_call = []

#         for method_call in method_calls:
#             split_call = method_call.split(":")
#             method_id = int(split_call[0])
#             bci = split_call[1]
#             # use cache to accelerate computation
#             if method_id in cache:
#                 new_method_call.append(f"{cache.get(method_id)}:{bci}")
#                 continue

#             # look for the right method in the perf profile
#             m = next(method for method in perf_methods if method.get("id") == method_id)
#             sig = [perf_types.get(t) for t in m.get("signature")]
#             # for t in m.get("signature"):
#             #     sig.append(perf_types.get(t))
#                 # for pt in perf_types:
#                 #     if pt.get("id") == t:
#                 #         sig.append(pt)
#             # match this to the corresponding method in the graal_profile
#             candidate_methods = [
#                 gm
#                 for gm in graal_methods
#                 if gm.get("name") == m.get("name")
#                 and len(gm.get("signature")) == len(m.get("signature"))
#             ]
#             result= None
#             for candidate_method in candidate_methods:
#                 c_sig = candidate_method.get("signature")
#                 c_sig = [graal_types.get(t) for t in candidate_method.get("signature")]
#                 if all(pt == gp for pt, gp in zip(sig, c_sig)):
#                     result = candidate_method
#                     break
#             if result is not None:
#                 new_method_call.append(f"{result.get("id")}:{bci}")
#                 cache[method_id] = result.get('id')
#         if len(new_method_call) == len(method_calls):
#             new_cp.append({"ctx": "<".join(new_method_call), "records": records})

#     graal_iprof["conditionalProfiles"] = new_cp
#     return


if __name__ == "__main__":
    main()
