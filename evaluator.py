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
    def __init__(self, endpoint_info: EndpointInfo, script: str, output_loc: str, output_name: str = "default", iterations: int = 1, timeout_seconds: int = 600):
        self.endpoint = endpoint_info
        self.script = script
        self.output_loc = output_loc
        self.proc = None
        self.stdout_thread = None
        self.stderr_thread = None
        self._current_timeout = None
        self.output_name = output_name
        self.runs = iterations
        self.errors = []
        self.timeout_seconds = timeout_seconds

    def evaluate(self, cmd: list[str], cwd: str | None = None):
        self.proc = subprocess.Popen(
            cmd,
            cwd=cwd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE
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
                print("Timeout reached, shutting down...")
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
        self._current_timeout = time() + self.timeout_seconds

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
            print("No stdout stream available")
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
            print("No stderr stream available")
            return

        def _process_stderr(char: str):
            sys.stdout.write(char)
            sys.stdout.flush()

        Utils.read_stream(stream, _process_stderr)

class ScriptEvaluator(Evaluator):
    def evaluate(self, cmd: list[str]):
        Evaluator.evaluate(self,
            [
                # Ensuring the process is started in its own session to manage signals properly;
                # the application script provided by the Application Plugin does not handle signals properly, so we target the entire
                # process group, which contains the JVM process
                "setsid",
                self.script,
            ] + cmd
        )

class RegularEvaluator(ScriptEvaluator):
    def evaluate(self, dataset_path: str, queries: list[str]):
        query_args = list(itertools.chain(*[("--query", query) for query in queries]))
        ScriptEvaluator.evaluate(self, [
            "query",
            "--url", self.endpoint.formatted(),
            "--runs", f"{self.runs}",
            "--output", os.path.join(self.output_loc, os.path.basename(dataset_path), self.output_name),
        ] + query_args)

class ReplayEvaluator(ScriptEvaluator):
    def evaluate(self, replay_files: list[str]):
        assert self.endpoint.update_url is not None, "ReplayEvaluator requires the SPARQL Update Protocol to be supported by the endpoint"
        replay_args = list(itertools.chain(*[("--input", file) for file in replay_files]))
        ScriptEvaluator.evaluate(self, [
            "replay",
            "--url", self.endpoint.formatted(),
            "--runs", f"{self.runs}",
            "--output", os.path.join(self.output_loc, self.output_name),
        ] + replay_args)

class GrowingEvaluator(ScriptEvaluator):
    def evaluate(self, update_files: list[str], query: str):
        assert self.endpoint.update_url is not None, "GrowingEvaluator requires the SPARQL Update Protocol to be supported by the endpoint"
        input_args = list(itertools.chain(*[("--input", file) for file in update_files]))
        ScriptEvaluator.evaluate(self, [
            "growing",
            "--url", self.endpoint.formatted(),
            "--runs", f"{self.runs}",
            "--output", os.path.join(self.output_loc, self.output_name),
            "--query", query,
        ] + input_args)

class BsbmEvaluator(Evaluator):
    def evaluate(self, dataset_path: str, ucf_file: str):
        base = os.path.join(self.output_loc, os.path.basename(dataset_path).replace('.', '-'), self.endpoint.query_url.split('localhost:')[1].replace('/', '_'))
        os.makedirs(base)
        Evaluator.evaluate(
            self,
            cmd=[
                './' + os.path.basename(self.script),
                "-mt", "1",
                "-ucf", ucf_file,
                "-o", os.path.join(base, self.output_name.replace('.', '-')) + ".xml",
                self.endpoint.query_url,
            ],
            cwd=os.path.dirname(self.script)
        )


if __name__ == "__main__":
    print("Error: This script is intended to be imported as a module, not run directly.")
    exit(1)
