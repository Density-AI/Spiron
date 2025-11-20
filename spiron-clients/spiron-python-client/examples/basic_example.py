"""Basic usage example for Spiron Python client."""

import sys
import time
from spiron import SpironClient, EddyState


def main():
    """Run basic example."""
    print("Spiron Python Client - Basic Example\n")
    
    # Create client
    peers = sys.argv[1:] if len(sys.argv) > 1 else ["localhost:8081"]
    print(f"Connecting to peers: {peers}")
    
    client = SpironClient(peers=peers, worker_threads=4)
    
    try:
        # Create some eddy states
        print("\nCreating and proposing eddy states...")
        
        for i in range(5):
            # Create a simple 3D vector
            vector = [i / 5.0, (5 - i) / 5.0, 0.5]
            energy = 1.0 + i * 0.2
            
            eddy = EddyState(
                id=f"example-eddy-{i}",
                vector=vector,
                energy=energy
            )
            
            print(f"  Proposing {eddy.id} with energy {energy:.2f}")
            client.propose(eddy)
            
            time.sleep(0.1)  # Small delay between proposals
        
        print("\n✅ All proposals completed successfully!")
        
    except Exception as e:
        print(f"\n❌ Error: {e}")
        return 1
    
    finally:
        print("\nClosing client...")
        client.close()
    
    return 0


if __name__ == "__main__":
    sys.exit(main())
