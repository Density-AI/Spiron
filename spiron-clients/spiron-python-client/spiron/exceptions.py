"""Exception classes for Spiron client."""


class SpironException(Exception):
    """Base exception for Spiron client errors."""
    pass


class TimeoutException(SpironException):
    """Raised when a request times out."""
    pass


class ConnectionException(SpironException):
    """Raised when connection to peers fails."""
    pass


class ValidationException(SpironException):
    """Raised when eddy state validation fails."""
    pass


class SignatureException(SpironException):
    """Raised when BLS signature operations fail."""
    pass
