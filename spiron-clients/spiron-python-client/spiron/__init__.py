"""Spiron Python Client - AI/LLM-first consensus engine client library."""

__version__ = "0.1.0"

from .client import SpironClient
from .eddy import EddyState
from .exceptions import SpironException, TimeoutException, ConnectionException

__all__ = [
    "SpironClient",
    "EddyState", 
    "SpironException",
    "TimeoutException",
    "ConnectionException",
]
