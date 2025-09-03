import itertools
import os
import subprocess
import sys
from threading import Thread
from time import sleep, time
from utils import EndpointInfo, Utils


class Evaluator:
    """
    A class wrapping the sparql-bench evaluator script, allowing a fixed command to be issued.
    Throws a TimeoutError if the evaluator is idling based on stdout, indicating an unresponsive endpoint.
    """
    def __init__(self, endpoint_info: EndpointInfo, script: str, output_loc: str, output_name: str = "default", iterations: int = 1):
        self.query_url = endpoint_info.query_url
        self.update_url = endpoint_info.update_url
        self.script = script
        self.output_loc = output_loc
        self.proc = None
        self.stdout_thread = None
        self.stderr_thread = None
        self._current_timeout = None
        self.output_name = output_name
        self.runs = iterations
        self.errors = []

    def evaluate(self, cmd: list[str]):
        self.proc = subprocess.Popen(
            [
                # Ensuring the process is started in its own session to manage signals properly;
                # the application script provided by the Application Plugin does not handle signals properly, so we target the entire
                # process group, which contains the JVM process
                "setsid",
                self.script,
            ] + cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL
        )
        self.stdout_thread = Thread(target=self._read_stdout)
        self.stderr_thread = Thread(target=self._read_stderr)
        self.stdout_thread.start()
        self.stderr_thread.start()

        # With the thread active, we now busy loop until the process is done or we hit a timeout
        self._update_current_time()
        while self.proc.poll() is None:
            # Checking if we've hit the timeout
            if time() > self._current_timeout:
                # Timeout hit, killing the process and throwing an error
                self._shutdown()
                raise TimeoutError(f"Evaluator timed out, assuming the endpoint is unresponsive.")
            # Sleeping, periodically checking the timeout & process status
            sleep(1)
            # If we have an error, we raise it, halting execution
            if self.errors:
                self._shutdown()
                raise RuntimeError(f"Evaluator encountered an error: {"\n".join(self.errors)}")
        # Even though we finished regularly, we need to ensure the thread is done
        self._shutdown()

    def _update_current_time(self):
        # Allowing 10 minutes of idle time before we assume the endpoint is unresponsive
        self._current_timeout = time() + 10 * 60

    def _shutdown(self):
        if self.proc:
            # Sending a SIGKILL to the entire process group to ensure everything is killed
            print(f"Shutting down evaluator ({self.proc.pid})")
            subprocess.Popen(['kill', '-SIGINT', f'-{self.proc.pid}']).wait()
            self.proc.wait()
            self.proc = None
            self.stdout_thread.join()
            self.stderr_thread.join()

    def _read_stdout(self):
        stream = self.proc.stdout if self.proc else None
        if not stream:
            return
        current_line = ""

        def _process_stdout(char: str):
            # We received data, so the timeout can be moved along
            self._update_current_time()
            sys.stdout.write(char)
            sys.stdout.flush()
            nonlocal current_line
            current_line += char
            # Notifying the active evaluation of any failed queries
            if char == '\n':
                if "fail" in current_line:
                    self.errors.append("evaluation failure detected")
                current_line = ""

        Utils.read_stream(stream, _process_stdout)

    def _read_stderr(self):
        stream = self.proc.stderr if self.proc else None
        if not stream:
            return
        current_error = ""

        def _process_stderr(char: str):
            nonlocal current_error
            if char != '\n':
                current_error += char
            else:
                if current_error:
                    self.errors.append(current_error)
                    current_error = ""

        Utils.read_stream(stream, _process_stderr)


class RegularEvaluator(Evaluator):
    def evaluate(self, dataset_path: str, queries: list[str]):
        query_args = list(itertools.chain(*[("--query", query) for query in queries]))
        Evaluator.evaluate(self, [
            "query",
            "--url", self.query_url if self.update_url is None else f"{self.query_url},{self.update_url}",
            "--runs", f"{self.runs}",
            "--output", os.path.join(self.output_loc, os.path.basename(dataset_path), self.output_name),
        ] + query_args)


class ReplayEvaluator(Evaluator):
    def evaluate(self, replay_files: list[str]):
        assert self.update_url is not None, "ReplayEvaluator requires the SPARQL Update Protocol to be supported by the endpoint"
        replay_args = list(itertools.chain(*[("--input", file) for file in replay_files]))
        Evaluator.evaluate(self, [
            "replay",
            "--url", f"{self.query_url},{self.update_url}",
            "--runs", f"{self.runs}",
            "--output", os.path.join(self.output_loc, self.output_name),
        ] + replay_args)


if __name__ == "__main__":
    print("Error: This script is intended to be imported as a module, not run directly.")
    exit(1)
