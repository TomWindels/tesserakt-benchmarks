import argparse
import random
import os

BSBM_SEPARATOR = "#__SEP__"
"""The various sizes of the initial dataset, as a ratio of the total dataset size (line-wise)"""
INITIAL_DATASET_SIZE_RATIO = [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9]

argparser = argparse.ArgumentParser(description="Splits a BSBM update file into separate updates, generating segmented updates")

argparser.add_argument("-f", "--file", type=str, help="The BSBM update file to split")
# The random seed to use when creating the various updates
argparser.add_argument("--seed", type=int, default=42, help="The random seed to use when creating the various updates")
# Toggle to enable the creation of the `total` dataset per update
argparser.add_argument("--generate-totals", action='store_true', help="Enable the creation of the `total.nt` dataset per update")

args = argparser.parse_args()


file = os.path.realpath(args.file)

chunks = []

with open(file, 'r') as f:
    # reading line by line
    current_chunk = []
    for line in f:
        line = line.strip()
        if line == BSBM_SEPARATOR:
            print(f"Added chunk {len(chunks) + 1} with {len(current_chunk)} triples")
            if current_chunk:
                chunks.append(current_chunk)
                current_chunk = []
            continue
        elif line:
            current_chunk.append(line)
        # else... nothing to do; skipping empty lines

# Mixing the chunk data based on the seed, creating an initial dataset

ratios = INITIAL_DATASET_SIZE_RATIO

random.seed(args.seed)

def generate_deltas(lines: list[str]) -> list[list[str]]:
    # ~ 1/2 of a typical chunk size
    current_delta_line_count = 64
    deltas = []
    while current_delta_line_count < len(lines):
        current_delta = lines[0:current_delta_line_count]
        deltas.append(current_delta)
        current_delta_line_count += 64
        if len(deltas) >= 250:
            # We cannot generate too many deltas, so we stop here
            break
    return deltas[0:250]


for ratio in ratios:
    filepath = os.path.join(os.path.dirname(file), f'ratio-{int(ratio*100)}')
    os.makedirs(filepath, exist_ok=True)

    # Making a deep copy of the chunks list as we plan on consuming it as we go
    remaining_chunks = [[chunk_line for chunk_line in chunk] for chunk in chunks]

    # Creating the initial dataset, which is a random selection of lines from the various chunks
    total_lines = sum(len(chunk) for chunk in remaining_chunks)
    target_lines = int(total_lines * ratio)
    initial_dataset = []

    # First adding a set of chunks with the guarantee of being completely present while allowed by the target size
    while remaining_chunks:
        chunk = random.choice(remaining_chunks)
        if len(initial_dataset) + len(chunk) > target_lines:
            break
        initial_dataset.extend(chunk)
        remaining_chunks.remove(chunk)

    # Then adding random lines from the remaining chunks until we reach the target size
    while len(initial_dataset) < target_lines and remaining_chunks:
        chunk = random.choice(remaining_chunks)
        line = random.choice(chunk)
        chunk.remove(line)
        initial_dataset.append(line)

    # This dataset can now be written as the initial state
    with open(os.path.join(filepath, 'initial.nt'), 'w') as out:
        # Writing out the total dataset
        for line in initial_dataset:
            out.write(line + '\n')

    # The various deltas can now also be generated
    deltas = generate_deltas(lines=[line for chunk in remaining_chunks for line in chunk])
    print(f"Generated {len(deltas)} deltas for ratio {ratio}")

    # These deltas can now also be written to disk, each in their own folder
    for i, delta in enumerate(deltas):
        subpath = os.path.join(filepath, f'update-{i}')
        os.makedirs(subpath, exist_ok=True)
        with open(os.path.join(subpath, 'delta.nt'), 'w') as out:
            # Writing out the delta alone
            for line in delta:
                out.write(line + '\n')
        if not args.generate_totals:
            continue
        with open(os.path.join(subpath, 'total.nt'), 'w') as out:
            # Writing out the total dataset
            for line in initial_dataset:
                out.write(line + '\n')
            for line in delta:
                out.write(line + '\n')
