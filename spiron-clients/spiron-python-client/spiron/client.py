"""Spiron client implementation."""

import asyncio
import logging
from concurrent.futures import ThreadPoolExecutor
from typing import List, Optional
import grpc

from .eddy import EddyState
from .exceptions import SpironException, TimeoutException, ConnectionException


logger = logging.getLogger(__name__)


class SpironClient:
    """
    Spiron consensus client.
    
    Provides both synchronous and asynchronous APIs for interacting with
    a Spiron cluster.
    """
    
    def __init__(
        self,
        peers: List[str],
        worker_threads: int = 4,
        timeout: float = 2.0,
        max_retries: int = 3,
        signer=None  # Optional BLS signer
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
        self.peers = peers
        self.timeout = timeout
        self.max_retries = max_retries
        self.signer = signer
        
        # Thread pool for async operations
        self.executor = ThreadPoolExecutor(max_workers=worker_threads)
        
        # gRPC channels (lazy initialization)
        self._channels: List[grpc.Channel] = []
        self._stubs = []
        
        # Initialize connections
        self._init_connections()
        
        logger.info(f"SpironClient initialized with {len(peers)} peers")
    
    def _init_connections(self):
        """Initialize gRPC channels to all peers."""
        for peer in self.peers:
            try:
                # Create insecure channel (use secure channel in production)
                channel = grpc.insecure_channel(
                    peer,
                    options=[
                        ('grpc.max_send_message_length', 100 * 1024 * 1024),  # 100MB
                        ('grpc.max_receive_message_length', 100 * 1024 * 1024),
                        ('grpc.keepalive_time_ms', 30000),
                        ('grpc.keepalive_timeout_ms', 10000),
                    ]
                )
                self._channels.append(channel)
                
                # Note: You'll need to import the generated gRPC stub
                # For now, we'll use a placeholder
                # from .proto import spiron_pb2_grpc
                # stub = spiron_pb2_grpc.SpironServiceStub(channel)
                # self._stubs.append(stub)
                
                logger.debug(f"Connected to peer: {peer}")
            except Exception as e:
                logger.warning(f"Failed to connect to peer {peer}: {e}")
    
    async def propose_async(self, state: EddyState) -> None:
        """
        Propose an eddy state asynchronously (non-blocking).
        
        Args:
            state: The eddy state to propose
            
        Raises:
            TimeoutException: If request times out
            ConnectionException: If connection fails
            SpironException: For other errors
        """
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(self.executor, self.propose, state)
    
    def propose(self, state: EddyState) -> None:
        """
        Propose an eddy state synchronously (blocking).
        
        Args:
            state: The eddy state to propose
            
        Raises:
            TimeoutException: If request times out
            ConnectionException: If connection fails
            SpironException: For other errors
        """
        # Sign if signer is available
        if self.signer:
            state.signature = self.signer.sign(state.to_bytes())
        
        # Try each peer with retries
        last_error = None
        for attempt in range(self.max_retries):
            for i, channel in enumerate(self._channels):
                try:
                    # TODO: Replace with actual gRPC call
                    # request = spiron_pb2.ProposeRequest(
                    #     id=state.id,
                    #     vector=state.vector,
                    #     energy=state.energy,
                    #     timestamp=state.timestamp,
                    #     signature=state.signature
                    # )
                    # response = self._stubs[i].Propose(request, timeout=self.timeout)
                    
                    # For now, simulate success
                    logger.debug(f"Proposed {state.id} to peer {self.peers[i]}")
                    return
                    
                except grpc.RpcError as e:
                    last_error = e
                    if e.code() == grpc.StatusCode.DEADLINE_EXCEEDED:
                        logger.warning(f"Timeout on peer {self.peers[i]}, attempt {attempt + 1}")
                        continue
                    elif e.code() == grpc.StatusCode.UNAVAILABLE:
                        logger.warning(f"Peer {self.peers[i]} unavailable, attempt {attempt + 1}")
                        continue
                    else:
                        raise SpironException(f"RPC error: {e.details()}") from e
                except Exception as e:
                    last_error = e
                    logger.error(f"Error proposing to peer {self.peers[i]}: {e}")
        
        # All retries exhausted
        if last_error:
            if isinstance(last_error, grpc.RpcError):
                if last_error.code() == grpc.StatusCode.DEADLINE_EXCEEDED:
                    raise TimeoutException("All peers timed out")
                elif last_error.code() == grpc.StatusCode.UNAVAILABLE:
                    raise ConnectionException("All peers unavailable")
            raise SpironException(f"Failed to propose after {self.max_retries} retries") from last_error
    
    async def commit_async(self, state: EddyState) -> None:
        """
        Commit an eddy state asynchronously (non-blocking).
        
        Args:
            state: The eddy state to commit
        """
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(self.executor, self.commit, state)
    
    def commit(self, state: EddyState) -> None:
        """
        Commit an eddy state synchronously (blocking).
        
        Args:
            state: The eddy state to commit
        """
        # Similar to propose but for commit operation
        # For now, delegate to propose (in real implementation, use different RPC)
        self.propose(state)
    
    def close(self) -> None:
        """Close the client and cleanup resources."""
        logger.info("Closing SpironClient")
        
        # Close all channels
        for channel in self._channels:
            try:
                channel.close()
            except Exception as e:
                logger.warning(f"Error closing channel: {e}")
        
        # Shutdown executor
        self.executor.shutdown(wait=True)
        
        logger.info("SpironClient closed")
    
    def __enter__(self):
        """Context manager entry."""
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context manager exit."""
        self.close()
        return False
    
    @classmethod
    def from_config_file(cls, config_path: str) -> "SpironClient":
        """
        Create a client from a configuration file.
        
        Args:
            config_path: Path to YAML configuration file
            
        Returns:
            Configured SpironClient instance
        """
        import yaml
        
        with open(config_path, 'r') as f:
            config = yaml.safe_load(f)
        
        return cls(
            peers=config.get('peers', []),
            worker_threads=config.get('worker_threads', 4),
            timeout=config.get('timeout', 2.0),
            max_retries=config.get('max_retries', 3),
        )
