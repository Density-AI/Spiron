"""Eddy state representation."""

import time
from dataclasses import dataclass, field
from typing import List, Optional


@dataclass
class EddyState:
    """
    Represents an eddy state in the Spiron consensus.
    
    An eddy is a probabilistic state in the consensus protocol, characterized
    by an ID, a high-dimensional vector, and an energy value.
    """
    
    id: str
    """Unique identifier for this eddy state."""
    
    vector: List[float]
    """High-dimensional state vector (typically 128-4096 dimensions)."""
    
    energy: float
    """Energy value associated with this state."""
    
    timestamp: int = field(default_factory=lambda: int(time.time() * 1000))
    """Timestamp in milliseconds since epoch."""
    
    signature: Optional[bytes] = None
    """Optional BLS signature for authentication."""
    
    def __post_init__(self):
        """Validate the eddy state after initialization."""
        if not self.id:
            raise ValueError("Eddy ID cannot be empty")
        
        if not self.vector:
            raise ValueError("Vector cannot be empty")
        
        if not all(isinstance(x, (int, float)) for x in self.vector):
            raise ValueError("Vector must contain only numeric values")
        
        if not isinstance(self.energy, (int, float)):
            raise ValueError("Energy must be a numeric value")
    
    def to_bytes(self) -> bytes:
        """
        Convert the eddy state to bytes for signing.
        
        Returns:
            Byte representation of the eddy state
        """
        # Simple serialization: id + vector + energy + timestamp
        import struct
        
        data = bytearray()
        
        # Add ID
        id_bytes = self.id.encode('utf-8')
        data.extend(struct.pack('I', len(id_bytes)))
        data.extend(id_bytes)
        
        # Add vector
        data.extend(struct.pack('I', len(self.vector)))
        for val in self.vector:
            data.extend(struct.pack('d', val))
        
        # Add energy
        data.extend(struct.pack('d', self.energy))
        
        # Add timestamp
        data.extend(struct.pack('Q', self.timestamp))
        
        return bytes(data)
    
    def dimension(self) -> int:
        """
        Get the dimension of the vector.
        
        Returns:
            Number of dimensions in the vector
        """
        return len(self.vector)
    
    def normalize(self) -> "EddyState":
        """
        Create a normalized copy of this eddy state.
        
        Normalizes the vector to unit length.
        
        Returns:
            New EddyState with normalized vector
        """
        import math
        
        magnitude = math.sqrt(sum(x * x for x in self.vector))
        if magnitude == 0:
            return self
        
        normalized_vector = [x / magnitude for x in self.vector]
        
        return EddyState(
            id=self.id,
            vector=normalized_vector,
            energy=self.energy,
            timestamp=self.timestamp,
            signature=self.signature
        )
    
    def distance_to(self, other: "EddyState") -> float:
        """
        Calculate Euclidean distance to another eddy state.
        
        Args:
            other: Another eddy state
            
        Returns:
            Euclidean distance between the vectors
        """
        import math
        
        if len(self.vector) != len(other.vector):
            raise ValueError("Cannot calculate distance between vectors of different dimensions")
        
        return math.sqrt(sum((a - b) ** 2 for a, b in zip(self.vector, other.vector)))
    
    def cosine_similarity(self, other: "EddyState") -> float:
        """
        Calculate cosine similarity to another eddy state.
        
        Args:
            other: Another eddy state
            
        Returns:
            Cosine similarity (-1 to 1)
        """
        import math
        
        if len(self.vector) != len(other.vector):
            raise ValueError("Cannot calculate similarity between vectors of different dimensions")
        
        dot_product = sum(a * b for a, b in zip(self.vector, other.vector))
        mag_a = math.sqrt(sum(a * a for a in self.vector))
        mag_b = math.sqrt(sum(b * b for b in other.vector))
        
        if mag_a == 0 or mag_b == 0:
            return 0.0
        
        return dot_product / (mag_a * mag_b)
    
    def __repr__(self) -> str:
        """String representation of the eddy state."""
        vector_preview = self.vector[:3] if len(self.vector) > 3 else self.vector
        vector_str = f"{vector_preview}..." if len(self.vector) > 3 else str(vector_preview)
        return f"EddyState(id='{self.id}', dim={len(self.vector)}, vector={vector_str}, energy={self.energy})"
