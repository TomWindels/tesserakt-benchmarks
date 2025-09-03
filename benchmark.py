#! /bin/python

# Builtin modules
import os
import sys
import argparse
import re

# Custom helpers
from endpoint import EndpointInstance
from evaluator import RegularEvaluator

# TODO: make this a command argument
GENERAL_JVM_ARGS="-XX:+UseStringDeduplication"

# Defining the argument parser
parser = argparse.ArgumentParser(description="Benchmark orchestrator, managing SPARQL endpoints, changing their configuration, and running benchmarks against them.")

# Adding an argument to specify the endpoint script directory
parser.add_argument('-e', '--endpoints', type=str, default=os.path.realpath(os.path.join(os.path.dirname(__file__), "scripts/endpoints")), help='Directory containing the endpoint scripts to run.')

# Adding an argument to specify the filter for the endpoints
parser.add_argument('-f', '--filter', type=str, default=None, help='Filter for the endpoints to run. This is a regex that matches the endpoint script names. If not provided, all endpoints are run.')

# Adding an argument to specify the output directory
parser.add_argument('-o', '--output', type=str, default=os.path.realpath(os.path.join(os.path.dirname(__file__), "output")), help='Directory to store the output of the benchmark.')

# Adding an argument to specify the input datasets
parser.add_argument('-i', '--input', type=str, help='Directory containing the input datasets for the benchmark.')

# Adding an argument to specify the input queries
parser.add_argument('-q', '--queries', type=str, help='Directory containing the input queries for the benchmark.')

# Adding an argument to specify the benchmark script
parser.add_argument('-s', '--script', type=str, default=os.path.realpath(os.path.join(os.path.dirname(__file__), "sparql-bench")), help='The location of the benchmarking script itself.')

# Adding an argument to specify the memory range, in MB
parser.add_argument('--memory-range', type=str, default=None, help='The memory ranges that should be evaluated. Binary search will be used to find the lower-bound limit. The range format is purely numeric, formatted as `lower,upper`, and is interpreted in MB. If none are provided, the value set in `config.sh` is kept, and no binary search is executed.')

# Adding an argument to specify whether to fail fast
parser.add_argument('--fail-fast', action='store_true', help='If set, the script will stop running endpoints as soon as one fails. Otherwise, it will continue running all endpoints regardless of failures.')

# Adding an argument to specify replay mode
parser.add_argument('--replay', action='store_true', help='If set, the script assumes replay benchmark files are passed in as input, and any provided queries are ignored.')

# Making the number of runs configurable
parser.add_argument('--runs', type=int, default=1, help='Number of times to run each query against each endpoint. Default is 1.')

# Getting the arguments
args = parser.parse_args()

# Normalizing the paths
args.endpoints = os.path.realpath(args.endpoints)
args.output = os.path.realpath(args.output)
args.input = os.path.realpath(args.input) if args.input else None
args.queries = os.path.realpath(args.queries) if args.queries else None
args.script = os.path.realpath(args.script)
if args.memory_range:
    if args.runs != 1:
        print("Warning: When using memory range evaluation, the number of runs is forced to 1 to avoid excessive runtimes.")
    args.memory_range = args.memory_range.split(',')
    if len(args.memory_range) != 2 or not all(re.match(r'^[0-9]+$', val) for val in args.memory_range):
        print("Error: The memory range must be in the format 'lower,upper', where both lower and upper are integers representing memory in MB.")
        sys.exit(1)
    args.memory_range = (int(args.memory_range[0]), int(args.memory_range[1]))
    if args.memory_range[0] >= args.memory_range[1]:
        print("Error: The lower bound of the memory range must be less than the upper bound.")
        sys.exit(1)
if args.replay and args.queries:
    print("Warning: When using replay mode, any provided queries are ignored.")
if not args.replay and not args.queries:
    print("Error: Either replay mode must be used in conjunction with replay benchmark files, or a query directory must be provided.")
    sys.exit(1)

# Validating the arguments
if not os.path.exists(args.input) or not os.path.isdir(args.input):
    print(f"Error: The specified input directory '{args.input}' does not exist or is not a directory.")
    sys.exit(1)

if os.path.exists(args.output) and len(os.listdir(args.output)) > 0:
    print(f"Error: The specified output path '{args.output}' exists and is not empty!")
    sys.exit(1)

if not os.path.exists(args.endpoints) or not os.path.isdir(args.endpoints):
    print(f"Error: The specified endpoints directory '{args.endpoints}' does not exist or is not a directory.")
    sys.exit(1)

if not os.path.exists(args.script) or not os.path.isfile(args.script) or not os.access(args.script, os.X_OK):
    print(f"Error: The specified script '{args.script}' does not exist, is not a file, or is not executable.")
    sys.exit(1)

# Ensuring we're working with an absolute path for the script
args.script = os.path.realpath(args.script)

def listdir_abs(path):
    """
    Returns a list of absolute paths for all files in the given directory.
    """
    return [os.path.join(path, file) for file in os.listdir(path)]

# Detecting all relevant scripts
endpoints = [
    file for file in listdir_abs(args.endpoints)
    if re.match(r'^.*/[0-9]{4}-[a-zA-Z]*$', file) and os.path.isfile(file) and os.access(file, os.X_OK) and (args.filter is None or re.search(args.filter, os.path.basename(file)))
]

print(f"Found {len(endpoints)} endpoint scripts in '{args.endpoints}'")

def prepare_queries_or_bail(queries_path: str) -> list[str]:
    files = None
    # If a directory is provided, we list all .rq files in it
    if os.path.isfile(queries_path):
        # If a single file is provided, we treat it as the only query
        files = [queries_path]
    elif os.path.isdir(queries_path):
        files = [file for file in listdir_abs(queries_path) if file.endswith('.rq') if os.path.isfile(file)]

    if not files:
        print(f"Error: The specified queries directory '{queries_path}' does not exist, not a directory or contains no queries (files ending with `.rq`).")
        sys.exit(1)

    # Mapping the various query files to their contents
    def read_contents(file):
        """
        Reads the contents of a file and returns it.
        """
        with open(file, 'r') as f:
            return f.read()

    return [query_content for query_content in map(read_contents, files)]


def eval_replay():
    pass

def eval_regular(iterations: int, queries: list[str]):
    for endpoint_name in endpoints:
        # Ensuring no other value is set
        os.environ['JAVA_FLAGS'] = GENERAL_JVM_ARGS
        print(f"Running {endpoint_name}...")
        for dataset_path in listdir_abs(args.input):
            print(f"Using dataset: {dataset_path}")
            eval(endpoint_name=endpoint_name, dataset_path=dataset_path, iterations=iterations, queries=queries)

def eval_memory(range: tuple[int, int], queries: list[str]):
    # Validating all endpoints and datasets
    if not os.path.exists(args.output):
        os.makedirs(args.output)
    with open(os.path.join(args.output, "memory_profiles.txt"), 'w') as mem_file:
        mem_file.write("Endpoint,Dataset,Memory(MB)\n")
        for endpoint_name in endpoints:
            for dataset_path in listdir_abs(args.input):
                result = bin_search_memory(endpoint_name=endpoint_name, dataset_path=dataset_path, range=range, queries=queries)
                mem_file.write(f"{os.path.basename(endpoint_name)},{os.path.basename(dataset_path)},{result}\n")
                mem_file.flush()

def eval(endpoint_name: str, dataset_path: str, queries: list[str], output_name: str = "default", iterations: int = 1) -> bool:
    """
    Evaluates a single endpoint with the given dataset.
    Returns True if the evaluation was successful, False otherwise.
    """
    with EndpointInstance(endpoint_name, dataset=dataset_path) as endpoint:
        try:
            # Waiting until the endpoint is ready and the URL is available
            # The timeout is based on the file size, 5MiB ~ 1s of wait time, with a minimum of 60s
            timeout = max(60, os.path.getsize(dataset_path) // (1024 * 1024 * 5))
            print(f"Waiting for the endpoint to be ready ({timeout // 60} min timeout)...")
            endpoint_info = endpoint.await_endpoint_info(timeout=timeout)
            # Running the benchmark job against the endpoint URL
            evaluator = RegularEvaluator(endpoint_info, script=args.script, output_loc=args.output, output_name=output_name, iterations=iterations)
            print(f"Running benchmark against {endpoint_info.query_url}...")
            evaluator.evaluate(dataset_path=dataset_path, queries=queries)
            return True
        except KeyboardInterrupt as e:
            print("Keyboard interrupt received, stopping...")
            print(e)
            exit(1)
        except TimeoutError as e:
            print(f"Timeout reached: {str(e)}")
            # We never exit here, as and endpoint or evaluator timeout is not considered a failure
        except Exception as e:
            print(f"Unexpected error running {endpoint_name}: {str(e)}")
            if args.fail_fast:
                print(f"Stopping early due to error: {str(e)}")
                exit(1)
    return False

# In case a memory range is provided, we run the binary search to find the lower bound
def bin_search_memory(endpoint_name:str, dataset_path:str, range: tuple[int], queries: list[str]) -> int:
    lower, upper = range
    while lower < upper:
        mid = (lower + upper) // 2
        # Applying the memory constraint
        print(f"Evaluating a {mid} MB memory limit")
        os.environ['JAVA_FLAGS'] = f"-Xmx{mid}M {GENERAL_JVM_ARGS}"
        if eval(endpoint_name=endpoint_name, dataset_path=dataset_path, output_name=str(mid), queries=queries):
            # If we reach here, the evaluation was successful, so we can try a lower memory profile
            upper = mid
        else:
            # If an error occurred, we need to try a higher memory profile
            lower = mid + 1
    return lower

if __name__ == "__main__":
    if args.replay:
        eval_replay()
    else:
        # Regular evaluator will be used, which requires queries to operate, reading all benchmark queries based on the arguments
        queries = prepare_queries_or_bail(args.queries)
        print(f"Loaded {len(queries)} queries from '{args.queries}'")
        if args.memory_range:
            eval_memory(range=args.memory_range, queries=queries)
        else:
            eval_regular(iterations=args.runs, queries=queries)
