"""Setup script for spiron-client package."""

from setuptools import setup, find_packages

# For backward compatibility with older tools
if __name__ == "__main__":
    setup(
        packages=find_packages(where=".", include=["spiron*"]),
        package_dir={"": "."},
    )
