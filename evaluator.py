#! /bin/python

import os
import sys
import subprocess
from time import sleep, time
from threading import Thread
from queue import Queue, Empty
import argparse
import re
import itertools

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

# Adding an argument to specify the memory profiles
parser.add_argument('--profiles', type=str, default=None, help='Various memory profiles applied to the endpoints. This is a comma-separated list of max memory values, e.g., "1g,2g". If none are provided, the value set in `config.sh` is kept.')

# Adding an argument to specify whether to fail fast
parser.add_argument('--fail-fast', action='store_true', help='If set, the script will stop running endpoints as soon as one fails. Otherwise, it will continue running all endpoints regardless of failures.')

# Getting the arguments
args = parser.parse_args()

# Normalizing the paths
args.endpoints = os.path.realpath(args.endpoints)
args.output = os.path.realpath(args.output)
args.input = os.path.realpath(args.input) if args.input else None
args.queries = os.path.realpath(args.queries) if args.queries else None
args.script = os.path.realpath(args.script)
args.memory_profiles = args.profiles.split(',') if args.profiles else None

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

class EndpointInstance:
    """
    A class to manage the execution of a script in a subprocess, capturing its output and handling shutdown gracefully.
    """
    def __init__(self, script, dataset: str | None = None):
        super().__init__()
        self.proc = None
        self.script = script
        self.dataset = dataset
        self.stdout_thread = None
        self.stderr_thread = None
        self.queue = Queue()

    def start(self):
        """
        Starts the script in a subprocess and sets up threads to read its stdout and stderr.
        """
        print(f"Starting script: {self.script}")
        try:
            self.proc = subprocess.Popen(
                # Ensuring the process is started in its own session to manage signals properly
                ['setsid', self.script],
                shell=False,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                env=dict(os.environ, ENDPOINT_DATA=self.dataset) if self.dataset else os.environ,
            )
            # Starting threads to handle stdout and stderr
            self.stdout_thread = Thread(target=self._read_stdout)
            self.stderr_thread = Thread(target=self._read_stderr)
            self.stdout_thread.start()
            self.stderr_thread.start()

        except subprocess.CalledProcessError as e:
            print(f"Error running {self.script}: {e.stderr}")
        except Exception as e:
            print(f"Unexpected error running {self.script}: {str(e)}")

    def __enter__(self):
        """
        Context manager entry point, starts the script.
        """
        self.start()
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        """
        Context manager exit point, stops the script.
        """
        self.stop()

    def _read_stream(self, stream, callback: callable):
        # Automatically closing the stream after we're done reading
        with stream:
            try:
                for char in iter(lambda: stream.read(1), b''):
                    if char:
                        callback(char.decode())
            except ValueError:
                print(f"Error reading from stream, assuming it stopped")

    def _read_stdout(self):
        output_format = r"Endpoint is ready: (http://[^:]*:\d+/.*)$"
        current_line = ""
        stream = self.proc.stdout if self.proc else None
        if not stream:
            return

        def _process_stdout(char: str):
            sys.stdout.write(char)
            sys.stdout.flush()
            nonlocal current_line
            current_line += char

            # Process the current line
            if char == '\n':
                match = re.search(output_format, current_line)
                # print(f"format: {output_format}")
                # print(f"currentl ine: {current_line}")
                if match:
                    url = match.group(1)
                    self.queue.put(url)
                current_line = ""

        self._read_stream(stream, _process_stdout)

    def _read_stderr(self):
        stream = self.proc.stderr if self.proc else None
        if not stream:
            return

        def _process_stderr(char: str):
            sys.stderr.write(char)
            sys.stderr.flush()

        self._read_stream(stream, _process_stderr)

    def stop(self):
        """
        Stops the script by sending a SIGINT signal to the process and waiting for it to terminate.
        """
        if self.proc:
            # Taking ownership of the process to ensure it is stopped
            proc=self.proc
            self.proc = None
            # Now handling its shutdown
            print(f"Stopping script ({proc.pid})")
            proc.send_signal(subprocess.signal.SIGINT)
            print("Waiting for the script to terminate...")
            proc.wait()
            # The end of the process should also cause the streams to be closed as the threads are finishing up
            self.stdout_thread.join()
            self.stderr_thread.join()
            print(f"Script {self.script} stopped.")

    def await_url(self):
        """
        Waits for the script to output a URL, which is expected to be printed in the format:
        "Endpoint is ready: http://localhost:PORT/sparql" - throws an exception upon reaching the 2 minute timeout, automatically calling `stop()`.
        """
        if not self.proc or not self.proc.stdout:
            raise RuntimeError("Process not started or stdout not available.")
        try:
            return self.queue.get(timeout=120)  # Wait for up to 2 minutes for the URL to be available
        except Empty as e:
            print(f"Error: No URL received from {self.script} within the timeout period. Stopping the endpoint.")
            self.stop()
            raise RuntimeError("No URL received from the endpoint script within the timeout period.") from e

# Reading all benchmark queries based on the arguments
if os.path.isfile(args.queries):
    # If a single file is provided, we treat it as the only query
    queries = [args.queries]
elif os.path.isdir(args.queries):
    # If a directory is provided, we list all .rq files in it
    if not os.path.exists(args.queries) or not os.path.isdir(args.queries):
        print(f"Error: The specified queries directory '{args.queries}' does not exist or is not a directory.")
        sys.exit(1)
    queries = [file for file in listdir_abs(args.queries) if file.endswith('.rq') if os.path.isfile(file)]

# Mapping the various queries to its contents
def read_contents(file):
    """
    Reads the contents of a file and returns it.
    """
    with open(file, 'r') as f:
        return f.read()

query_count = len(queries)
# Now overwriting the queries variable with the actual contents
queries = map(
    read_contents,
    queries
)

query_args = list(itertools.chain(*[("--query", query) for query in queries]))

# Running the scripts in subprocesses
for endpoint_name in endpoints:
    for memory_profile in args.memory_profiles or [None]:
        if memory_profile:
            os.environ['JAVA_FLAGS'] = f"-Xmx{memory_profile}"
            print(f"Running {endpoint_name} ({memory_profile})...")
        else:
            # Ensuring no other value is set
            os.environ['JAVA_FLAGS'] = ""
            print(f"Running {endpoint_name}...")
        for dataset_path in listdir_abs(args.input):
            with EndpointInstance(endpoint_name, dataset=dataset_path) as endpoint:
                try:
                    # Waiting until the endpoint is ready and the URL is available
                    url = endpoint.await_url()

                    # Running the benchmark job against the endpoint URL
                    process = subprocess.Popen(
                        [
                            args.script, "query",
                            "--url", url,
                            # FIXME - make this configurable
                            "--runs", "1",
                            "--output", os.path.join(args.output, os.path.basename(dataset_path), memory_profile if memory_profile else "default"),
                        ] + query_args,
                        stderr=subprocess.DEVNULL
                    )

                    # Executing the process with an extra timeout set; if it fails, we assume the endpoint DNF
                    try:
                        # scaling max duration according to the number of queries, 10s per query
                        timeout = 1 * query_count
                        print(f"Waiting for the benchmark to complete (timeout set to {timeout}s)...")
                        sleep(1)
                        process.wait(timeout=timeout)
                        if process.returncode != 0 and args.fail_fast:
                            print(f"Error running benchmark against {url}: {process.stderr}")
                            print(f"Stopping early")
                            exit(1)
                        else:
                            sleep(1)
                            # Now we can shut it down and go to the next endpoint
                            print(f"Received exit code {process.returncode}")
                            print(f"Stopping...")
                    except subprocess.TimeoutExpired:
                        print(f"Benchmark against {url} timed out.")
                        process.kill()
                        process.wait()
                        if args.fail_fast:
                            print(f"Stopping early due to timeout.")
                            exit(1)
                except KeyboardInterrupt as e:
                    print("Keyboard interrupt received, stopping...")
                    print(e)
                    exit(1)
                except Exception as e:
                    print(f"Unexpected error running {endpoint_name}: {str(e)}")
                    if args.fail_fast:
                        print(f"Stopping early due to error: {str(e)}")
                        exit(1)
