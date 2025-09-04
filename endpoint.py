import os
from queue import Empty, Queue
import re
import subprocess
import sys
from threading import Thread

from utils import EndpointInfo, Utils


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

    def _read_stdout(self):
        output_format = r"Endpoint is ready: (http://[^: ]*:\d+[^ ]*) ?(http://[^: ]*:\d+[^ ]*)? ?([a-zA-Z0-9]+)?$"
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
                if match:
                    query_url = match.group(1)
                    update_url = match.group(2)
                    update_token = match.group(3)
                    self.queue.put(EndpointInfo(query_url, update_url, update_token))
                current_line = ""

        Utils.read_stream(stream, _process_stdout)

    def _read_stderr(self):
        stream = self.proc.stderr if self.proc else None
        if not stream:
            return

        def _process_stderr(char: str):
            sys.stderr.write(char)
            sys.stderr.flush()

        Utils.read_stream(stream, _process_stderr)

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

    def await_endpoint_info(self, timeout: float) -> EndpointInfo:
        """
        Waits for the script to output a URL, which is expected to be printed in the format:
        "Endpoint is ready: http://localhost:PORT/sparql" - throws an exception upon reaching the 2 minute timeout, automatically calling `stop()`.
        """
        if not self.proc or not self.proc.stdout:
            raise RuntimeError("Process not started or stdout not available.")
        try:
            return self.queue.get(timeout=timeout)  # Wait for up to 2 minutes for the URL to be available
        except Empty as e:
            print(f"Error: No URL received from {self.script} within the timeout period. Stopping the endpoint.")
            self.stop()
            raise TimeoutError("No URL received from the endpoint script within the timeout period.") from e

if __name__ == "__main__":
    print("Error: This script is intended to be imported as a module, not run directly.")
    exit(1)
