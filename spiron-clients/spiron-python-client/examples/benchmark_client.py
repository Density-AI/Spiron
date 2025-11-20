"""Benchmark client for Spiron Python client."""

import sys
import time
import random
from concurrent.futures import ThreadPoolExecutor, as_completed
from spiron import SpironClient, EddyState


def generate_vector(dimensions: int, template_id: int, num_templates: int) -> list:
    """Generate a clustered vector based on template."""
    # Create template base
    base = [(template_id / num_templates) * random.uniform(0.9, 1.1) for _ in range(dimensions)]
    
    # Add small noise
    return [val + random.gauss(0, 0.01) for val in base]


def benchmark_sync(client: SpironClient, num_requests: int, dimensions: int):
    """Run synchronous benchmark."""
    print(f"\n=== Synchronous Benchmark ===")
    print(f"Requests: {num_requests:,}")
    print(f"Dimensions: {dimensions}")
    
    start = time.time()
    
    # Warmup
    for i in range(min(100, num_requests // 10)):
        vector = [random.random() for _ in range(dimensions)]
        eddy = EddyState(f"warmup-{i}", vector, 1.0)
        client.propose(eddy)
    
    warmup_time = time.time() - start
    print(f"Warmup: {warmup_time:.2f}s")
    
    # Actual benchmark
    num_templates = 20
    start = time.time()
    
    for i in range(num_requests):
        template_id = i % num_templates
        vector = generate_vector(dimensions, template_id, num_templates)
        energy = 1.0 + (i % 10) * 0.1
        
        eddy = EddyState(f"bench-sync-{i}", vector, energy)
        client.propose(eddy)
        
        if (i + 1) % 1000 == 0:
            elapsed = time.time() - start
            current_throughput = (i + 1) / elapsed
            print(f"  Progress: {i + 1:,}/{num_requests:,} ({current_throughput:.2f} req/s)")
    
    duration = time.time() - start
    throughput = num_requests / duration
    
    print(f"\n✅ Sync Results:")
    print(f"   Duration: {duration:.2f}s")
    print(f"   Throughput: {throughput:.2f} req/s")
    print(f"   Latency: {(duration / num_requests) * 1000:.2f}ms per request")
    
    return throughput


def benchmark_concurrent(client: SpironClient, num_requests: int, dimensions: int, concurrent_clients: int):
    """Run concurrent benchmark with multiple threads."""
    print(f"\n=== Concurrent Benchmark ===")
    print(f"Requests: {num_requests:,}")
    print(f"Dimensions: {dimensions}")
    print(f"Concurrent clients: {concurrent_clients}")
    
    num_templates = 20
    
    def worker(worker_id: int, requests_per_worker: int):
        """Worker function for concurrent requests."""
        for i in range(requests_per_worker):
            template_id = (worker_id * requests_per_worker + i) % num_templates
            vector = generate_vector(dimensions, template_id, num_templates)
            energy = 1.0 + (i % 10) * 0.1
            
            eddy = EddyState(f"bench-concurrent-{worker_id}-{i}", vector, energy)
            client.propose(eddy)
        
        return requests_per_worker
    
    requests_per_worker = num_requests // concurrent_clients
    
    start = time.time()
    
    with ThreadPoolExecutor(max_workers=concurrent_clients) as executor:
        futures = [
            executor.submit(worker, worker_id, requests_per_worker)
            for worker_id in range(concurrent_clients)
        ]
        
        completed = 0
        for future in as_completed(futures):
            completed += future.result()
            if completed % 1000 == 0:
                elapsed = time.time() - start
                current_throughput = completed / elapsed
                print(f"  Progress: {completed:,}/{num_requests:,} ({current_throughput:.2f} req/s)")
    
    duration = time.time() - start
    throughput = num_requests / duration
    
    print(f"\n✅ Concurrent Results:")
    print(f"   Duration: {duration:.2f}s")
    print(f"   Throughput: {throughput:.2f} req/s")
    print(f"   Latency: {(duration / num_requests) * 1000:.2f}ms per request")
    
    return throughput


def main():
    """Run benchmark."""
    print("=" * 60)
    print("Spiron Python Client - Benchmark")
    print("=" * 60)
    
    # Parse arguments
    peers = ["localhost:8081", "localhost:8082", "localhost:8083"]
    num_requests = 10000
    dimensions = 4096
    concurrent_clients = 20
    
    if len(sys.argv) > 1:
        num_requests = int(sys.argv[1])
    if len(sys.argv) > 2:
        dimensions = int(sys.argv[2])
    if len(sys.argv) > 3:
        concurrent_clients = int(sys.argv[3])
    
    print(f"\nConfiguration:")
    print(f"  Peers: {peers}")
    print(f"  Total requests: {num_requests:,}")
    print(f"  Vector dimensions: {dimensions}")
    print(f"  Concurrent clients: {concurrent_clients}")
    
    # Create client
    client = SpironClient(
        peers=peers,
        worker_threads=concurrent_clients * 2,
        timeout=5.0,
        max_retries=3
    )
    
    try:
        # Run benchmarks
        sync_throughput = benchmark_sync(client, min(num_requests, 1000), dimensions)
        concurrent_throughput = benchmark_concurrent(client, num_requests, dimensions, concurrent_clients)
        
        # Summary
        print(f"\n" + "=" * 60)
        print("Summary:")
        print(f"  Sync throughput: {sync_throughput:.2f} req/s")
        print(f"  Concurrent throughput: {concurrent_throughput:.2f} req/s")
        print(f"  Speedup: {concurrent_throughput / sync_throughput:.2f}x")
        print("=" * 60)
        
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
        return 1
    
    finally:
        print("\nClosing client...")
        client.close()
    
    return 0


if __name__ == "__main__":
    sys.exit(main())
