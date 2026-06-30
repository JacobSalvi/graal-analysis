def main():
    input_file = "/home/jacob/PHD/graal-ws/experiment/Control/exp_f2/source_mapping.txt"
    output_file = "/home/jacob/PHD/graal-ws/experiment/Control/exp_f2/source_mapping_l.txt"


    def convert_ops_to_hex(op_field):
        parts = op_field.strip().split()
        out = []

        for p in parts:
            try:
                n = int(p)
                if n < 0:
                    out.append(f"{n+256:02x}")
                else:
                    out.append(f"{n:02x}")
            except ValueError:
                return op_field  # fallback if malformed

        return " ".join(out)


    def process_line(line):
        line = line.strip()
        if not line:
            return line + "\n"

        parts = line.split(",", -1)
        if len(parts) < 4:
            return line + "\n"

        # range stays unchanged
        range_part = parts[0].strip()

        # convert ops
        ops_part = convert_ops_to_hex(parts[1])

        # at... parts unchanged (may be multiple)
        at_parts = parts[2:-1]
        at_joined = ",".join(p.strip() for p in at_parts if p.strip())

        # method
        method_part = parts[-1].strip()

        return f"{range_part},{ops_part},{at_joined},{method_part}\n"


    with open(input_file, "r") as f, open(output_file, "w") as out:
        for line in f:
            out.write(process_line(line))


if __name__ == "__main__":
    main()