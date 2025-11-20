"""Integration tests for Spiron Python client.

These tests require a running Spiron cluster.
"""

import pytest
import asyncio
import time
from spiron import SpironClient, EddyState
from spiron.exceptions import TimeoutException, ConnectionException


@pytest.mark.integration
class TestIntegration:
    """Integration tests (require running server)."""
    
    @pytest.fixture
    def client(self):
        """Create a test client."""
        client = SpironClient(
            peers=["localhost:8081"],
            worker_threads=4,
            timeout=5.0,
            max_retries=3
        )
        yield client
        client.close()
    
    @pytest.fixture
    def cluster_client(self):
        """Create a client for 3-node cluster."""
        client = SpironClient(
            peers=["localhost:8081", "localhost:8082", "localhost:8083"],
            worker_threads=10,
            timeout=5.0
        )
        yield client
        client.close()
    
    def test_single_propose(self, client):
        """Test proposing a single eddy."""
        eddy = EddyState("test-single", [0.9, 0.1, 0.0], 1.5)
        
        # Should not raise
        client.propose(eddy)
    
    def test_multiple_propose(self, client):
        """Test proposing multiple eddies."""
        for i in range(10):
            eddy = EddyState(f"test-multi-{i}", [i / 10.0, (10 - i) / 10.0], 1.0 + i * 0.1)
            client.propose(eddy)
    
    @pytest.mark.asyncio
    async def test_async_propose(self, client):
        """Test async propose."""
        eddy = EddyState("test-async", [0.5, 0.5], 2.0)
        await client.propose_async(eddy)
    
    @pytest.mark.asyncio
    async def test_concurrent_propose(self, cluster_client):
        """Test concurrent proposals."""
        eddies = [
            EddyState(f"test-concurrent-{i}", [i / 100.0] * 128, 1.0)
            for i in range(100)
        ]
        
        start = time.time()
        await asyncio.gather(*[
            cluster_client.propose_async(eddy)
            for eddy in eddies
        ])
        duration = time.time() - start
        
        throughput = len(eddies) / duration
        print(f"\nConcurrent throughput: {throughput:.2f} req/s")
        
        assert throughput > 10  # At least 10 req/s
    
    def test_high_dimensional_vectors(self, client):
        """Test with high-dimensional vectors (4096D)."""
        vector = [i / 4096.0 for i in range(4096)]
        eddy = EddyState("test-high-dim", vector, 1.0)
        
        client.propose(eddy)
    
    def test_varying_energies(self, client):
        """Test with varying energy values."""
        for i in range(10):
            energy = 0.5 + i * 0.5
            eddy = EddyState(f"test-energy-{i}", [1.0], energy)
            client.propose(eddy)
    
    def test_cluster_failover(self, cluster_client):
        """Test client handles node failures gracefully."""
        # Propose to cluster
        eddy = EddyState("test-failover", [0.5, 0.5], 1.5)
        
        # Should succeed even if some nodes are down
        # (assuming at least one node is up)
        cluster_client.propose(eddy)
    
    def test_connection_timeout(self):
        """Test handling of connection timeouts."""
        # Use non-existent peer
        client = SpironClient(
            peers=["localhost:9999"],
            timeout=1.0,
            max_retries=1
        )
        
        eddy = EddyState("test-timeout", [1.0], 1.0)
        
        # Should raise timeout or connection exception
        with pytest.raises((TimeoutException, ConnectionException)):
            client.propose(eddy)
        
        client.close()


@pytest.mark.benchmark
class TestBenchmark:
    """Benchmark tests."""
    
    @pytest.fixture
    def client(self):
        """Create a benchmark client."""
        client = SpironClient(
            peers=["localhost:8081", "localhost:8082", "localhost:8083"],
            worker_threads=20,
            timeout=5.0
        )
        yield client
        client.close()
    
    def test_sync_throughput(self, client):
        """Benchmark synchronous throughput."""
        num_requests = 1000
        dimensions = 128
        
        start = time.time()
        for i in range(num_requests):
            vector = [i / num_requests] * dimensions
            eddy = EddyState(f"bench-sync-{i}", vector, 1.0)
            client.propose(eddy)
        
        duration = time.time() - start
        throughput = num_requests / duration
        
        print(f"\nSync throughput: {throughput:.2f} req/s")
        print(f"Duration: {duration:.2f}s")
        
        assert throughput > 10  # At least 10 req/s
    
    @pytest.mark.asyncio
    async def test_async_throughput(self, client):
        """Benchmark async throughput."""
        num_requests = 1000
        dimensions = 128
        batch_size = 100
        
        start = time.time()
        
        for batch_start in range(0, num_requests, batch_size):
            batch_end = min(batch_start + batch_size, num_requests)
            
            tasks = []
            for i in range(batch_start, batch_end):
                vector = [i / num_requests] * dimensions
                eddy = EddyState(f"bench-async-{i}", vector, 1.0)
                tasks.append(client.propose_async(eddy))
            
            await asyncio.gather(*tasks)
        
        duration = time.time() - start
        throughput = num_requests / duration
        
        print(f"\nAsync throughput: {throughput:.2f} req/s")
        print(f"Duration: {duration:.2f}s")
        
        assert throughput > 50  # Async should be faster


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
