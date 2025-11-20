"""Test configuration file."""

import pytest


def pytest_configure(config):
    """Configure pytest."""
    config.addinivalue_line("markers", "integration: integration tests (require running server)")
    config.addinivalue_line("markers", "benchmark: benchmark tests")


def pytest_addoption(parser):
    """Add command line options."""
    parser.addoption(
        "--run-integration",
        action="store_true",
        default=False,
        help="Run integration tests (requires running server)"
    )
    parser.addoption(
        "--run-benchmark",
        action="store_true",
        default=False,
        help="Run benchmark tests"
    )


def pytest_collection_modifyitems(config, items):
    """Modify test collection based on options."""
    skip_integration = pytest.mark.skip(reason="need --run-integration option to run")
    skip_benchmark = pytest.mark.skip(reason="need --run-benchmark option to run")
    
    for item in items:
        if "integration" in item.keywords and not config.getoption("--run-integration"):
            item.add_marker(skip_integration)
        if "benchmark" in item.keywords and not config.getoption("--run-benchmark"):
            item.add_marker(skip_benchmark)
