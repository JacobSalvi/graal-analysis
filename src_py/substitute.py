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
        candidates = [cp for cp in graal_iprof.conditional_profiles if  cp.canon.endswith(perf_canon) or f"{perf_canon}<" in cp.canon]
        for candidate in candidates:
            new_cp.append({"ctx": candidate.ctx, "records": perf_records})
        pass

    return new_cp



if __name__ == "__main__":
    main()
