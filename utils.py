from attr import dataclass

class Utils:
    @staticmethod
    def read_stream(stream, callback: callable):
        # Automatically closing the stream after we're done reading
        with stream:
            try:
                for char in iter(lambda: stream.read(1), b''):
                    if char:
                        callback(char.decode())
            except ValueError:
                print(f"Error reading from stream, assuming it stopped")

@dataclass
class EndpointInfo:
    """
    A simple data class to hold information about an active endpoint.
    """
    query_url: str
    update_url: str | None = None
    update_token: str | None = None

    def formatted(self) -> str:
        if self.update_url and self.update_token:
            return f"{self.query_url},{self.update_url},{self.update_token}"
        elif self.update_url:
            return f"{self.query_url},{self.update_url}"
        else:
            return self.query_url

if __name__ == "__main__":
    print("Error: This script is intended to be imported as a module, not run directly.")
    exit(1)
