#! /bin/python

# Builtin modules
import os
import sys
import argparse
import re
import traceback
from typing import Callable

# Custom helpers
from endpoint import EndpointInstance
from evaluator import BsbmEvaluator, GrowingEvaluator, RegularEvaluator, ReplayEvaluator
from utils import EndpointInfo

# TODO: make this a command argument
GENERAL_JVM_ARGS="-XX:+UseStringDeduplication"

# Defining the argument parser
parser = argparse.ArgumentParser(description="Benchmark orchestrator, managing SPARQL endpoints, changing their configuration, and running benchmarks against them.")

# Adding subparsers for the various modes
modes_parser = parser.add_subparsers(title='modes', description='Available modes of operation', dest='mode')

regular_args = modes_parser.add_parser('regular', help='Regular evaluation, utilising queries and regular datasets to evaluate performance.')
replay_args = modes_parser.add_parser('replay', help='Replay evaluation, utilising the replay bench format.')
bsbm_args = modes_parser.add_parser('bsbm', help='BSBM evaluation, using the BSBM runner and endpoint configuration.')
growing_args = modes_parser.add_parser('growing', help='Growing evaluation, where the various RDF datasets are added on top of each other.')

# Bulk adding common arguments to multiple parsers

def append_query_param(*parsers: argparse.ArgumentParser):
    for parser in parsers:
        parser.add_argument('-q', '--queries', required=True, type=str, help='Files or directories containing the SPARQL query files for the benchmark.')

def append_script_param(*parsers: argparse.ArgumentParser):
    for parser in parsers:
        parser.add_argument('-s', '--script', type=str, default=os.path.realpath(os.path.dirname(__file__)), help='The location of the benchmarking script itself.')

def append_input_param(*parsers: argparse.ArgumentParser):
    for parser in parsers:
        parser.add_argument('-i', '--input', required=True, type=str, help='Directory containing the input datasets for the benchmark.')

def append_output_param(*parsers: argparse.ArgumentParser):
    for parser in parsers:
        parser.add_argument('-o', '--output', required=True, type=str, default=os.path.realpath(os.path.join(os.path.dirname(__file__), "output")), help='Directory to store the output of the benchmark.')

def append_iterations_param(*parsers: argparse.ArgumentParser):
    for parser in parsers:
        parser.add_argument('--runs', type=int, default=1, help='Number of times to run evaluations. Default is 1.')

def append_endpoint_param(*parsers: argparse.ArgumentParser):
    for parser in parsers:
        parser.add_argument('-e', '--endpoints', type=str, default=os.path.realpath(os.path.join(os.path.dirname(__file__), "scripts/endpoints")), help='Directory containing the endpoint scripts to run.')

def append_filter_param(*parsers: argparse.ArgumentParser):
    for parser in parsers:
        parser.add_argument('-f', '--filter', type=str, default=None, help='Filter for the endpoints to run. This is a regex that matches the endpoint script names. If not provided, all endpoints are run.')

def append_failfast_param(*parsers: argparse.ArgumentParser):
    for parser in parsers:
        parser.add_argument('--fail-fast', action='store_true', help='If set, the script will stop running endpoints as soon as one fails. Otherwise, it will continue running all endpoints regardless of failures.')

append_query_param(
    regular_args,
    growing_args,
)

append_script_param(
    regular_args,
    replay_args,
    bsbm_args,
    growing_args,
)

append_input_param(
    regular_args,
    replay_args,
    bsbm_args,
    growing_args,
)

append_output_param(
    regular_args,
    replay_args,
    bsbm_args,
    growing_args,
)

append_iterations_param(
    regular_args,
    replay_args,
    growing_args,
)

append_failfast_param(
    regular_args,
    replay_args,
    bsbm_args,
    growing_args,
)

append_endpoint_param(
    regular_args,
    replay_args,
    bsbm_args,
    growing_args,
)

append_filter_param(
    regular_args,
    replay_args,
    bsbm_args,
    growing_args,
)

# Unique arguments
## Adding an argument to specify the memory range, in MB
regular_args.add_argument('--memory-range', type=str, default=None, help='The memory ranges that should be evaluated. Binary search will be used to find the lower-bound limit. The range format is purely numeric, formatted as `lower,upper`, and is interpreted in MB. If none are provided, the value set in `config.sh` is kept, and no binary search is executed.')
## Specifying the UCF file for BSBM mode
bsbm_args.add_argument('-u', '--ucf', type=str, required=True, help='The Use Case File (UCF) to use for the BSBM benchmark.')

# Getting the arguments
args = parser.parse_args()

# Converting the mode into a boolean for easier checks
args.regular = args.mode == 'regular'
args.replay = args.mode == 'replay'
args.bsbm = args.mode == 'bsbm'
args.growing = args.mode == 'growing'

# Normalizing the paths based on the mode
args.endpoints = os.path.realpath(args.endpoints)
args.output = os.path.realpath(args.output)
args.input = os.path.realpath(args.input) if args.input else None
args.script = os.path.realpath(args.script)
if args.regular or args.growing:
    args.queries = os.path.realpath(args.queries) if args.queries else None
if args.bsbm:
    args.ucf = os.path.realpath(args.ucf) if args.ucf else None
    args.script = os.path.realpath(os.path.join(args.script, "bin", "bsbm-tools", "testdriver"))
else:
    args.script = os.path.realpath(os.path.join(args.script, "sparql-bench"))


# Configuring regular evaluation based on memory range presence
if args.regular and args.memory_range:
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

# Validating the remaining 'always present' arguments
if os.path.exists(args.output) and len(os.listdir(args.output)) > 0:
    print(f"Error: The specified output path '{args.output}' exists and is not empty!")
    sys.exit(1)

if not os.path.exists(args.endpoints) or not os.path.isdir(args.endpoints):
    print(f"Error: The specified endpoints directory '{args.endpoints}' does not exist or is not a directory.")
    sys.exit(1)

if not os.path.exists(args.script) or not os.path.isfile(args.script) or not os.access(args.script, os.X_OK):
    print(f"Error: The specified script '{args.script}' does not exist, is not a file, or is not executable.")
    sys.exit(1)

def listdir_abs(path):
    """
    Returns a list of absolute paths for all files in the given directory.
    """
    path = os.path.realpath(path)
    if os.path.isdir(path):
        return [os.path.join(path, file) for file in os.listdir(path)]
    elif os.path.isfile(path):
        return [path]
    else:
        raise FileNotFoundError(f"The specified path '{path}' is not a directory or does not exist.")

# Detecting all relevant scripts
endpoints = [
    file for file in listdir_abs(args.endpoints)
    if re.match(r'^.*/[0-9]{4}-[a-zA-Z\-_]*$', file) and os.path.isfile(file) and os.access(file, os.X_OK) and (args.filter is None or re.search(args.filter, '-'.join(os.path.basename(file).split('-')[1:])))
]

print(f"Found {len(endpoints)} endpoint scripts in '{args.endpoints}'")

def read_file_or_folder(path: str) -> list[str] | None:
    if os.path.isfile(path):
        return [path]
    elif os.path.isdir(path):
        return [file for file in listdir_abs(path) if os.path.isfile(file)]
    return None

def prepare_queries_or_bail(queries_path: str) -> list[str]:
    files = None
    # If a directory is provided, we list all .rq files in it
    if os.path.isfile(queries_path):
        # If a single file is provided, we treat it as the only query
        files = [queries_path]
    elif os.path.isdir(queries_path):
        files = [file for file in listdir_abs(queries_path) if file.endswith('.rq') if os.path.isfile(file)]
    files = read_file_or_folder(queries_path) if files is None else files
    files = [file for file in files if file.endswith('.rq')]

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

    return [read_contents(query_file) for query_file in files]

def eval_bsbm(ucf_file: str):
    for dataset_path in listdir_abs(args.input):
        print(f"Using dataset: {dataset_path}")

        def exec_eval(endpoint_info: EndpointInfo):
            print(f"Running BSBM benchmark against {endpoint_info.query_url}...")
            evaluator = BsbmEvaluator(endpoint_info, script=args.script, output_loc=args.output, output_name=os.path.basename(ucf_file))
            evaluator.evaluate(dataset_path=dataset_path, ucf_file=ucf_file)

        for endpoint_name in endpoints:
            print(f"Running {endpoint_name}...")
            endpoint_eval(endpoint_name=endpoint_name, dataset_path=dataset_path, on_endpoint_ready=exec_eval)

def eval_replay(iterations: int, benchmarks: list[str]):
    for endpoint_name in endpoints:
        print(f"Running {endpoint_name}...")
        endpoint_eval_replay(endpoint_name=endpoint_name, iterations=iterations, benchmarks=benchmarks)

def eval_regular(iterations: int, queries: list[str]):
    for endpoint_name in endpoints:
        print(f"Running {endpoint_name}...")
        for dataset_path in listdir_abs(args.input):
            print(f"Using dataset: {dataset_path}")
            endpoint_eval_regular(endpoint_name=endpoint_name, dataset_path=dataset_path, iterations=iterations, queries=queries)

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

def eval_growing(iterations: int, queries: list[str]):
    datasets = [dir for dir in listdir_abs(args.input) if os.path.isdir(dir)]
    for dataset in datasets:
        # The datasets have a specific folder hierarchy: *name*/ratio-XYZ/{initial.nt,update-XYZ/{delta.nt,total.nt}}
        ratios = [dir for dir in listdir_abs(dataset) if os.path.isdir(dir) and re.match(r'.*ratio-[0-9]+$', dir)]
        print(f"Using dataset {dataset} ({len(ratios)} ratios)")
        for ratio in ratios:
            print(f"  Using ratio: {os.path.basename(ratio)}")
            initial_file = os.path.join(ratio, "initial.nt")
            if not os.path.exists(initial_file) or not os.path.isfile(initial_file):
                print(f"Error: The initial dataset file '{initial_file}' does not exist or is not a file.")
                if args.fail_fast:
                    print("Stopping early due to error.")
                    exit(1)
                continue
            update_dirs = [dir for dir in listdir_abs(ratio) if os.path.isdir(dir)]
            update_dirs.sort(key=lambda f: int(re.search(r'update-([0-9]+)$', f).group(1)))
            update_files = [os.path.join(dir, "delta.nt") for dir in update_dirs]
            if any(not os.path.exists(file) or not os.path.isfile(file) for file in update_files):
                print(f"Error: One or more update dataset files in '{os.path.join(ratio, 'update-nt')}' do not exist or are not files.")
                if args.fail_fast:
                    print("Stopping early due to error.")
                    exit(1)
                continue
            if not update_files:
                print(f"Warning: No update files found in '{os.path.join(ratio, 'update-nt')}'. Only the initial dataset will be used.")

            def exec_eval(endpoint_info: EndpointInfo, endpoint_name: str, query_index: int, iter_index: int):
                print(f"Running Growing benchmark against {endpoint_info.query_url}...")
                output_loc = os.path.join(
                    args.output,
                    os.path.basename(dataset),
                    os.path.basename(ratio),
                    os.path.basename(endpoint_name),
                    f"query-{query_index}"
                )
                evaluator = GrowingEvaluator(
                    endpoint_info=endpoint_info,
                    script=args.script,
                    output_loc=output_loc,
                    output_name=f"run-{iter_index}",
                )
                evaluator.evaluate(
                    update_files=update_files,
                    query=queries[query_index],
                )

            for endpoint_name in endpoints:
                print(f"Running {endpoint_name}... (x {iterations})")
                for i in range(iterations):
                    for query_index in range(len(queries)):
                        endpoint_eval(
                            endpoint_name=endpoint_name,
                            dataset_path=initial_file,
                            on_endpoint_ready=lambda endpoint_info: exec_eval(
                                endpoint_info=endpoint_info,
                                endpoint_name=endpoint_name,
                                query_index=query_index,
                                iter_index=i,
                                ),
                        )

def endpoint_eval_replay(endpoint_name: str, benchmarks: list[str], iterations: int = 1):
    def exec_eval(endpoint_info: EndpointInfo):
        assert endpoint_info.update_url is not None, f"ReplayEvaluator requires the SPARQL Update Protocol to be supported by the endpoint (data {endpoint_info})"
        evaluator = ReplayEvaluator(endpoint_info, script=args.script, output_loc=args.output, output_name='default', iterations=iterations)
        print(f"Running benchmark against {endpoint_info.query_url}...")
        evaluator.evaluate(replay_files=benchmarks)

    try:
        endpoint_eval(endpoint_name=endpoint_name, dataset_path=None, on_endpoint_ready=exec_eval)
    except Exception as e:
        print(f"Unexpected error during replay evaluation: {str(e)}")
        print(traceback.format_exc())
        print("Assuming the endpoint failed, skipping any further evaluations.")
        if args.fail_fast:
            print(f"Stopping early due to error: {str(e)}")
            exit(1)

def endpoint_eval_regular(endpoint_name: str, dataset_path: str, queries: list[str], output_name: str = "default", iterations: int = 1) -> bool:
    def exec_eval(endpoint_info: EndpointInfo):
        evaluator = RegularEvaluator(endpoint_info, script=args.script, output_loc=args.output, output_name=output_name, iterations=iterations)
        print(f"Running benchmark against {endpoint_info.query_url}...")
        evaluator.evaluate(dataset_path=dataset_path, queries=queries)

    return endpoint_eval(endpoint_name=endpoint_name, dataset_path=dataset_path, on_endpoint_ready=exec_eval)

def endpoint_eval(endpoint_name: str, dataset_path: str | None, on_endpoint_ready: Callable[[EndpointInfo], None]) -> bool:
    """
    Evaluates a single endpoint with the given dataset, executing a callback for the actual evaluation implementation to be started.
    Returns True if the evaluation was successful, False otherwise.
    """
    with EndpointInstance(endpoint_name, dataset=dataset_path) as endpoint:
        try:
            # Waiting until the endpoint is ready and the URL is available
            # The timeout is based on the file size, 5MiB ~ 1s of wait time, with a minimum of 60s
            filesize_hint_bytes = os.path.getsize(dataset_path) if dataset_path and os.path.exists(dataset_path) else 0
            timeout = max(60, filesize_hint_bytes // (1024 * 1024 * 5))
            print(f"Waiting for the endpoint to be ready ({timeout // 60} min timeout)...")
            endpoint_info = endpoint.await_endpoint_info(timeout=timeout)
            # Running the benchmark job against the endpoint URL
            on_endpoint_ready(endpoint_info)
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
            print(traceback.format_exc())
            if args.fail_fast:
                print(f"Stopping early due to error: {str(e)}")
                exit(1)
    return False

# In case a memory range is provided, we run the binary search to find the lower bound
def bin_search_memory(endpoint_name:str, dataset_path:str, range: tuple[int], queries: list[str]) -> int:
    lower, upper = range
    try:
        while lower < upper:
            mid = (lower + upper) // 2
            # Applying the memory constraint
            print(f"Evaluating a {mid} MB memory limit")
            os.environ['JAVA_FLAGS'] = f"-Xmx{mid}M {GENERAL_JVM_ARGS}"
            if endpoint_eval_regular(endpoint_name=endpoint_name, dataset_path=dataset_path, output_name=str(mid), queries=queries):
                # If we reach here, the evaluation was successful, so we can try a lower memory profile
                print("Evaluation success detected")
                upper = mid
            else:
                # If an error occurred, we need to try a higher memory profile
                print("Evaluation failure detected")
                lower = mid + 1
        return lower
    finally:
        # Cleaning up the environment variable to avoid affecting other runs
        os.environ['JAVA_FLAGS'] = GENERAL_JVM_ARGS

if __name__ == "__main__":
    # Cleaning up the environment first
    os.environ['JAVA_FLAGS'] = GENERAL_JVM_ARGS

    if args.replay:
        # The input is assumed to be of the replay format
        benchmarks = read_file_or_folder(args.input)
        eval_replay(iterations=args.runs, benchmarks=benchmarks)
    elif args.growing:
        # The queries are assumed to be a set of regular queries
        queries = prepare_queries_or_bail(args.queries)
        print(f"Loaded {len(queries)} queries from '{args.queries}'")
        eval_growing(iterations=args.runs, queries=queries)
    elif args.bsbm:
        # The queries are assumed to be a single UCF file
        ucf_files = read_file_or_folder(args.ucf)
        if not ucf_files or len(ucf_files) != 1:
            print("Error: When using BSBM mode, the input must be a single UCF file.")
            exit(1)
        ucf_file = ucf_files[0]
        print(f"Using UCF file: {ucf_file}")
        eval_bsbm(ucf_file=ucf_file)
    else:
        # Regular evaluator will be used, which requires queries to operate, reading all benchmark queries based on the arguments
        queries = prepare_queries_or_bail(args.queries)
        print(f"Loaded {len(queries)} queries from '{args.queries}'")
        if args.memory_range:
            eval_memory(range=args.memory_range, queries=queries)
        else:
            eval_regular(iterations=args.runs, queries=queries)
