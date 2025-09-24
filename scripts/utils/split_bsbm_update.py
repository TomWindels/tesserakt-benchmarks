import argparse
import random
import os

BSBM_SEPARATOR = "#__SEP__"
"""The various sizes of the initial dataset, as a ratio of the total dataset size (line-wise)"""
INITIAL_DATASET_SIZE_RATIO = [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9]

argparser = argparse.ArgumentParser(description="Splits a BSBM update file into separate updates, generating segmented updates")

argparser.add_argument("-f", "--file", type=str, help="The BSBM update file to split")
argparser.add_argument("--seed", type=int, default=42, help="The random seed to use when creating the various updates")

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

for ratio in ratios:
    filepath = os.path.join(os.path.dirname(file), f'ratio-{int(ratio*100)}')
    os.makedirs(filepath, exist_ok=True)

    # Making a deep copy of the chunks list as we plan on consuming it as we go
    remaining_chunks = [[chunk_line for chunk_line in chunk] for chunk in chunks]

    # Creating the initial dataset, which is a random selection of lines from the various chunks
    total_lines = sum(len(chunk) for chunk in remaining_chunks)
    target_lines = int(total_lines * ratio)
    current_dataset = []

    # First adding a set of chunks with the guarantee of being completely present while allowed by the target size
    while remaining_chunks:
        chunk = random.choice(remaining_chunks)
        if len(current_dataset) + len(chunk) > target_lines:
            break
        current_dataset.extend(chunk)
        remaining_chunks.remove(chunk)

    # Then adding random lines from the remaining chunks until we reach the target size
    while len(current_dataset) < target_lines and remaining_chunks:
        chunk = random.choice(remaining_chunks)
        line = random.choice(chunk)
        chunk.remove(line)
        current_dataset.append(line)

    # This dataset can now be written as the initial state
    with open(os.path.join(filepath, 'initial.nt'), 'w') as out:
        # Writing out the total dataset
        for line in current_dataset:
            out.write(line + '\n')

    # The various deltas can now also be generated, taking the remainder of the various incomplete chunks
    deltas = remaining_chunks

    # These deltas can now also be written to disk, each in their own folder
    for i, delta in enumerate(deltas):
        subpath = os.path.join(filepath, f'update-{i}')
        os.makedirs(subpath, exist_ok=True)
        with open(os.path.join(subpath, 'delta.nt'), 'w') as out:
            # Writing out the delta alone
            for line in delta:
                out.write(line + '\n')
        with open(os.path.join(subpath, 'total.nt'), 'w') as out:
            # Writing out the total dataset
            current_dataset.extend(delta)
            for line in current_dataset:
                out.write(line + '\n')
