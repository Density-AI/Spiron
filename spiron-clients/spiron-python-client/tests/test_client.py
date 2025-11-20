"""Tests for Spiron Python client."""

import pytest
from spiron import SpironClient, EddyState
from spiron.exceptions import ValidationException


class TestEddyState:
    """Test EddyState class."""
    
    def test_create_eddy_state(self):
        """Test creating an eddy state."""
        eddy = EddyState("test-1", [0.9, 0.1], 1.5)
        assert eddy.id == "test-1"
        assert eddy.vector == [0.9, 0.1]
        assert eddy.energy == 1.5
        assert eddy.timestamp is not None
        assert eddy.signature is None
    
    def test_eddy_state_validation(self):
        """Test eddy state validation."""
        # Empty ID
        with pytest.raises(ValueError, match="ID cannot be empty"):
            EddyState("", [1.0], 1.0)
        
        # Empty vector
        with pytest.raises(ValueError, match="Vector cannot be empty"):
            EddyState("test", [], 1.0)
        
        # Non-numeric vector
        with pytest.raises(ValueError, match="only numeric values"):
            EddyState("test", ["a", "b"], 1.0)
        
        # Non-numeric energy
        with pytest.raises(ValueError, match="Energy must be"):
            EddyState("test", [1.0], "not a number")
    
    def test_dimension(self):
        """Test dimension calculation."""
        eddy = EddyState("test", [1.0, 2.0, 3.0], 1.0)
        assert eddy.dimension() == 3
    
    def test_normalize(self):
        """Test vector normalization."""
        eddy = EddyState("test", [3.0, 4.0], 1.0)
        normalized = eddy.normalize()
        
        # Check magnitude is 1
        import math
        magnitude = math.sqrt(sum(x * x for x in normalized.vector))
        assert abs(magnitude - 1.0) < 1e-10
        
        # Check values are correct
        assert abs(normalized.vector[0] - 0.6) < 1e-10
        assert abs(normalized.vector[1] - 0.8) < 1e-10
    
    def test_distance_to(self):
        """Test distance calculation."""
        eddy1 = EddyState("test1", [0.0, 0.0], 1.0)
        eddy2 = EddyState("test2", [3.0, 4.0], 1.0)
        
        distance = eddy1.distance_to(eddy2)
        assert abs(distance - 5.0) < 1e-10
    
    def test_distance_to_different_dimensions(self):
        """Test distance calculation with different dimensions."""
        eddy1 = EddyState("test1", [1.0, 2.0], 1.0)
        eddy2 = EddyState("test2", [1.0, 2.0, 3.0], 1.0)
        
        with pytest.raises(ValueError, match="different dimensions"):
            eddy1.distance_to(eddy2)
    
    def test_cosine_similarity(self):
        """Test cosine similarity calculation."""
        eddy1 = EddyState("test1", [1.0, 0.0], 1.0)
        eddy2 = EddyState("test2", [1.0, 0.0], 1.0)
        
        # Identical vectors should have similarity = 1
        similarity = eddy1.cosine_similarity(eddy2)
        assert abs(similarity - 1.0) < 1e-10
        
        # Orthogonal vectors should have similarity = 0
        eddy3 = EddyState("test3", [0.0, 1.0], 1.0)
        similarity = eddy1.cosine_similarity(eddy3)
        assert abs(similarity) < 1e-10
    
    def test_to_bytes(self):
        """Test byte serialization."""
        eddy = EddyState("test-1", [0.9, 0.1], 1.5)
        data = eddy.to_bytes()
        
        assert isinstance(data, bytes)
        assert len(data) > 0
    
    def test_repr(self):
        """Test string representation."""
        eddy = EddyState("test-1", [0.9, 0.1, 0.2], 1.5)
        repr_str = repr(eddy)
        
        assert "test-1" in repr_str
        assert "dim=3" in repr_str
        assert "energy=1.5" in repr_str


class TestSpironClient:
    """Test SpironClient class."""
    
    def test_create_client(self):
        """Test creating a client."""
        client = SpironClient(
            peers=["localhost:8081"],
            worker_threads=4,
            timeout=2.0,
            max_retries=3
        )
        
        assert client.peers == ["localhost:8081"]
        assert client.timeout == 2.0
        assert client.max_retries == 3
        assert client.executor is not None
        
        client.close()
    
    def test_context_manager(self):
        """Test using client as context manager."""
        with SpironClient(peers=["localhost:8081"]) as client:
            assert client is not None
        
        # Client should be closed after context
        assert client.executor._shutdown
    
    def test_multiple_peers(self):
        """Test client with multiple peers."""
        peers = ["localhost:8081", "localhost:8082", "localhost:8083"]
        client = SpironClient(peers=peers)
        
        assert len(client.peers) == 3
        
        client.close()
    
    @pytest.mark.asyncio
    async def test_propose_async(self):
        """Test async propose (requires running server)."""
        # This test would need a running server
        # For now, just test that the method exists
        client = SpironClient(peers=["localhost:8081"])
        
        assert hasattr(client, 'propose_async')
        assert callable(client.propose_async)
        
        client.close()
    
    def test_propose_sync(self):
        """Test sync propose (requires running server)."""
        # This test would need a running server
        # For now, just test that the method exists
        client = SpironClient(peers=["localhost:8081"])
        
        assert hasattr(client, 'propose')
        assert callable(client.propose)
        
        client.close()


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
