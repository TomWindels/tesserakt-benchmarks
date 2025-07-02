import os
import argparse
import glob


# Getting the list of files in the filepath set using argparse
def get_args():
    parser = argparse.ArgumentParser(description="Split queries into separate files.")
    # Registering the input argument
    parser.add_argument(
        "filepath",
        type=str,
        help="Path to the directory containing query files to be split.",
    )
    # Registering the output argument
    parser.add_argument(
        "output",
        type=str,
        help="Path to the directory where the split query files will be saved.",
    )
    # Registering the extension argument
    parser.add_argument(
        "--extension",
        type=str,
        default=".rq",
        help="File extension for the split query files (default: .rq).",
    )
    # Registering the pattern that splits individual queries
    parser.add_argument(
        "--pattern",
        type=str,
        default="\\n\\n",
        help="Pattern to split the queries (default: '\\n\\n').",
    )
    args = parser.parse_args()
    # With the args set, we have to adjust the pattern to be used for splitting
    args.pattern = args.pattern.replace("\\n", "\n").replace("\\t", "\t")
    # Ensuring the output directory exists
    if not os.path.exists(args.output):
        os.makedirs(args.output)
    return args


args = get_args()

# Getting the list of files in the specified directory
files = [file for file in glob.glob(args.filepath) if os.path.isfile(file)]

for file in files:
    # Extracting the base name of the file, removing the extension
    base_name = os.path.basename(file).rsplit('.', 1)[0]
    # Reading the content of the file
    with open(file, "r") as f:
        content = f.read()
    
    # Splitting the content by semicolon and stripping whitespace
    queries = [query.strip() for query in content.split(args.pattern)]
    # Filtering out empty queries
    queries = [query for query in queries if query]
    # Filtering out duplicate queries
    distinct = list(dict.fromkeys(queries))

    # Warning the user if the number of queries has decreased
    if len(distinct) < len(queries):
        print(f"Warning: Some queries were duplicates in {file}. Original: {len(queries)}, Remaining: {len(distinct)}")
    else:
        print(f"Info: Extracted ${len(distinct)} queries form {file}")
    
    # Writing each query to a separate file
    for i, query in enumerate(distinct):
        output_file = os.path.join(args.output, f"{base_name}_{i + 1}{args.extension}")
        # Ensuring the output file does not already exist
        if os.path.exists(output_file):
            print(f"Error: Output file {output_file} already exists! Exiting to avoid overwriting.")
            exit(1)
        with open(output_file, "w") as out_f:
            out_f.write(query)
