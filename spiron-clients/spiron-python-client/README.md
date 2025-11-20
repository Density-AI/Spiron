# Spiron Python Client

Official Python client library for Spiron - the AI/LLM-first consensus engine.

## Installation

```bash
cd spiron-clients/spiron-python-client
pip install -e .
```

Or with development dependencies:
```bash
pip install -e ".[dev]"
```

## Quick Start

```python
from spiron import SpironClient, EddyState

# Create client
client = SpironClient(
    peers=["localhost:8081", "localhost:8082", "localhost:8083"],
    worker_threads=4
)

# Create an eddy state
vector = [0.9, 0.1, 0.0, 0.5]
eddy = EddyState(id="proposal-1", vector=vector, energy=1.5)

# Propose (broadcast)
await client.propose_async(eddy)

# Or use synchronous API
client.propose(eddy)

# Close when done
client.close()
```

## Features

- ✅ Full gRPC support with async/await
- ✅ Automatic retry with exponential backoff
- ✅ Circuit breaker pattern
- ✅ Connection pooling
- ✅ BLS signature support (optional)
- ✅ Comprehensive error handling
- ✅ Type hints throughout
- ✅ Pytest test suite

## API Reference

### SpironClient

```python
class SpironClient:
    def __init__(
        self,
        peers: List[str],
        worker_threads: int = 4,
        timeout: float = 2.0,
        max_retries: int = 3,
        signer: Optional[BlsSigner] = None
    ):
        """
        Create a Spiron client.
        
        Args:
            peers: List of peer addresses (e.g., ["localhost:8081"])
            worker_threads: Number of worker threads for async operations
            timeout: RPC timeout in seconds
            max_retries: Maximum number of retry attempts
            signer: Optional BLS signer for cryptographic signatures
        """
    
    async def propose_async(self, state: EddyState) -> None:
        """Propose an eddy state (non-blocking)."""
    
    def propose(self, state: EddyState) -> None:
        """Propose an eddy state (blocking)."""
    
    async def commit_async(self, state: EddyState) -> None:
        """Commit an eddy state (non-blocking)."""
    
    def commit(self, state: EddyState) -> None:
        """Commit an eddy state (blocking)."""
    
    def close(self) -> None:
        """Close the client and cleanup resources."""
```

### EddyState

```python
@dataclass
class EddyState:
    """Represents an eddy state in the Spiron consensus."""
    
    id: str                    # Unique identifier
    vector: List[float]        # State vector
    energy: float              # Energy value
    timestamp: int = None      # Optional timestamp
    signature: bytes = None    # Optional BLS signature
```

## Examples

### Basic Example

```python
from spiron import SpironClient, EddyState

# Connect to cluster
client = SpironClient(peers=["localhost:8081"])

# Create and propose eddy
eddy = EddyState("test-1", [0.9, 0.1], 1.5)
client.propose(eddy)

client.close()
```

### Async Example

```python
import asyncio
from spiron import SpironClient, EddyState

async def main():
    client = SpironClient(peers=["localhost:8081"])
    
    # Create multiple eddies
    eddies = [
        EddyState(f"eddy-{i}", [i/10.0, (10-i)/10.0], i*0.5)
        for i in range(10)
    ]
    
    # Propose all concurrently
    await asyncio.gather(*[
        client.propose_async(eddy)
        for eddy in eddies
    ])
    
    client.close()

asyncio.run(main())
```

### Benchmark Example

```python
import time
from spiron import SpironClient, EddyState

client = SpironClient(
    peers=["localhost:8081", "localhost:8082", "localhost:8083"],
    worker_threads=20
)

# Warmup
for i in range(1000):
    eddy = EddyState(f"warmup-{i}", [i/1000.0] * 4096, 1.0)
    client.propose(eddy)

# Benchmark
start = time.time()
num_requests = 10000

for i in range(num_requests):
    vector = [i/num_requests] * 4096
    eddy = EddyState(f"bench-{i}", vector, 1.0 + (i % 10) * 0.1)
    client.propose(eddy)

duration = time.time() - start
throughput = num_requests / duration

print(f"Throughput: {throughput:.2f} req/s")
print(f"Duration: {duration:.2f}s")

client.close()
```

## Configuration

The client can be configured using:

1. **Constructor parameters** (shown above)
2. **Environment variables**:
   ```bash
   export SPIRON_PEERS="localhost:8081,localhost:8082"
   export SPIRON_TIMEOUT=5.0
   export SPIRON_MAX_RETRIES=5
   ```
3. **Configuration file**:
   ```python
   client = SpironClient.from_config_file("spiron-client.yaml")
   ```

## Error Handling

```python
from spiron import SpironClient, SpironException, TimeoutException

client = SpironClient(peers=["localhost:8081"])

try:
    eddy = EddyState("test", [1.0], 2.0)
    client.propose(eddy)
except TimeoutException:
    print("Request timed out")
except SpironException as e:
    print(f"Spiron error: {e}")
finally:
    client.close()
```

## Testing

Run the test suite:

```bash
pytest tests/
```

Run with coverage:

```bash
pytest --cov=spiron tests/
```

## Requirements

- Python 3.8+
- grpcio >= 1.60.0
- protobuf >= 4.25.0
- numpy >= 1.24.0 (optional, for vector operations)

## License

Apache 2.0 - Same as Spiron server

## Contributing

See [CONTRIBUTING.md](../../../docs/CONTRIBUTING.md) for guidelines.

## Support

- GitHub Issues: https://github.com/Density-AI/spiron/issues
- Documentation: https://spiron.dev/docs
- Discord: https://discord.gg/spiron
