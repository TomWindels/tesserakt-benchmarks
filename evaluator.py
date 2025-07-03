#! /bin/python

import os
import sys
import subprocess
from time import sleep, time
from threading import Thread
from queue import Queue
import argparse
import re
import itertools

# Defining the argument parser
parser = argparse.ArgumentParser(description="Benchmark orchestrator, managing SPARQL endpoints, changing their configuration, and running benchmarks against them.")

# Adding an argument to specify the endpoint script directory
parser.add_argument('-e', '--endpoints', type=str, default=os.path.realpath(os.path.join(os.path.dirname(__file__), "scripts/endpoints")), help='Directory containing the endpoint scripts to run.')

# Adding an argument to specify the output directory
parser.add_argument('-o', '--output', type=str, default=os.path.realpath(os.path.join(os.path.dirname(__file__), "output")), help='Directory to store the output of the benchmark.')

# Adding an argument to specify the input datasets
parser.add_argument('-i', '--input', type=str, help='Directory containing the input datasets for the benchmark.')

# Adding an argument to specify the input queries
parser.add_argument('-q', '--queries', type=str, help='Directory containing the input queries for the benchmark.')

# Adding an argument to specify the benchmark script
parser.add_argument('-s', '--script', type=str, default=os.path.realpath(os.path.join(os.path.dirname(__file__), "sparql-bench")), help='The location of the benchmarking script itself.')

# Getting the arguments
args = parser.parse_args()

# Normalizing the paths
args.endpoints = os.path.realpath(args.endpoints)
args.output = os.path.realpath(args.output)
args.input = os.path.realpath(args.input) if args.input else None
args.queries = os.path.realpath(args.queries) if args.queries else None
args.script = os.path.realpath(args.script)

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
    if re.match(r'^.*/[0-9]{4}-[a-zA-Z]*$', file) and os.path.isfile(file) and os.access(file, os.X_OK)
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
                [self.script],
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

    def _read_stdout(self):
        output_format = r"Endpoint is ready: (http://[^:]*:\d+/.*)$"
        current_line = ""
        if self.proc and self.proc.stdout:
            for char in iter(lambda: self.proc.stdout.read(1), b''):
                char = char.decode()
                current_line += char
                sys.stdout.write(char)
                sys.stdout.flush()

                if char == '\n':
                    # Process the current line
                    match = re.search(output_format, current_line)
                    if match:
                        url = match.group(1)
                        self.queue.put(url)
                    current_line = ""

    def _read_stderr(self):
        if self.proc and self.proc.stderr:
            for char in iter(lambda: self.proc.stderr.read(1), b''):
                sys.stderr.write(char.decode())
                sys.stderr.flush()

    def stop(self):
        """
        Stops the script by sending a SIGINT signal to the process and waiting for it to terminate.
        """
        if self.proc:
            self.proc.send_signal(subprocess.signal.SIGINT)
            self.proc.wait()
            self.proc.stdout.close()
            self.proc.stderr.close()
            self.stdout_thread.join()
            self.stderr_thread.join()
            self.proc = None
            print(f"Script {self.script} stopped.")

    def await_url(self):
        """
        Waits for the script to output a URL, which is expected to be printed in the format:
        "Endpoint is ready: http://localhost:PORT/sparql"
        """
        if not self.proc or not self.proc.stdout:
            raise RuntimeError("Process not started or stdout not available.")
        return self.queue.get()

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

queries = map(
    read_contents,
    queries
)

query_args = list(itertools.chain(*[("--query", query) for query in queries]))

# Running the scripts in subprocesses
for endpoint in endpoints:
    print(f"Running {endpoint}...")
    try:
        for dataset_path in listdir_abs(args.input):
            job = EndpointInstance(endpoint, dataset=dataset_path)
            job.start()
            # Waiting until the endpoint is ready and the URL is available
            url = job.await_url()

            # Running the benchmark job against the endpoint URL
            process = subprocess.Popen(
                [
                    args.script, "query",
                    "--url", url,
                    "--output", os.path.join(args.output, os.path.basename(dataset_path)),
                ] + query_args,
            )
            process.wait()
            if process.returncode != 0:
                print(f"Error running benchmark against {url}: {process.stderr}")
                print(f"Stopping early")
                # Now stopping the endpoint again
                job.stop()
                exit(1)
            else:
                sleep(1)
                # Now we can shut it down and go to the next endpoint
                print(f"Stopping...")
                # Now stopping the endpoint again
                job.stop()
    except Exception as e:
        print(f"Unexpected error running {endpoint}: {str(e)}")
