def main():
    input_file = "/home/jacob/PHD/graal-ws/experiment/Control/exp_f2/source_mapping.txt"
    output_file = "/home/jacob/PHD/graal-ws/experiment/Control/exp_f2/source_mapping_l.txt"



    def convert_numbers_to_hex(line):
        parts = line.strip().split()

        if not parts:
            return line

        converted = []

        for p in parts:
            try:
                n = int(p)
                if n < 0:
                    converted.append(f"{n+256:02x}")
                else:
                    converted.append(f"{n:02x}")
            except ValueError:
                return line

        return " ".join(converted) + "\n"


    with open(input_file, "r") as f:
        lines = f.readlines()

    out = []
    i = 0
    n = len(lines)

    while i < n:
        line = lines[i]

        if line.startswith("Source mapping:"):
            j = i + 1

            # skip optional numeric line
            if j < n and lines[j].strip():
                maybe_numbers = lines[j].strip().split()

                is_numbers = True
                for x in maybe_numbers:
                    try:
                        int(x)
                    except ValueError:
                        is_numbers = False
                        break

                if is_numbers:
                    j += 1

            # remove block if immediately followed by empty line
            if j < n and lines[j].strip() == "":
                while j < n and lines[j].strip() == "":
                    j += 1
                i = j
                continue

        # convert numeric-only lines to hex
        out.append(convert_numbers_to_hex(line))

        i += 1

    with open(output_file, "w") as f:
        f.writelines(out)
    return


if __name__ == "__main__":
    main()