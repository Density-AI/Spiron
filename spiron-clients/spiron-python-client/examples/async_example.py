"""Async example using Spiron Python client."""

import asyncio
import sys
from spiron import SpironClient, EddyState


async def propose_batch(client: SpironClient, batch_id: int, count: int):
    """Propose a batch of eddy states asynchronously."""
    print(f"  Batch {batch_id}: Starting {count} proposals")
    
    tasks = []
    for i in range(count):
        vector = [(batch_id * count + i) / 100.0] * 128  # 128D vector
        energy = 1.0 + (i % 10) * 0.1
        
        eddy = EddyState(
            id=f"batch-{batch_id}-eddy-{i}",
            vector=vector,
            energy=energy
        )
        
        tasks.append(client.propose_async(eddy))
    
    # Wait for all proposals to complete
    await asyncio.gather(*tasks)
    print(f"  Batch {batch_id}: Completed {count} proposals")


async def main():
    """Run async example."""
    print("Spiron Python Client - Async Example\n")
    
    # Create client
    peers = sys.argv[1:] if len(sys.argv) > 1 else ["localhost:8081"]
    print(f"Connecting to peers: {peers}")
    
    client = SpironClient(peers=peers, worker_threads=20)
    
    try:
        # Propose multiple batches concurrently
        print("\nProposing 5 batches of 100 eddies each (500 total)...")
        
        start = asyncio.get_event_loop().time()
        
        # Create 5 batches, each with 100 proposals
        batch_tasks = [
            propose_batch(client, batch_id, 100)
            for batch_id in range(5)
        ]
        
        # Run all batches concurrently
        await asyncio.gather(*batch_tasks)
        
        duration = asyncio.get_event_loop().time() - start
        throughput = 500 / duration
        
        print(f"\n✅ Completed 500 proposals in {duration:.2f}s")
        print(f"   Throughput: {throughput:.2f} req/s")
        
    except Exception as e:
        print(f"\n❌ Error: {e}")
        return 1
    
    finally:
        print("\nClosing client...")
        client.close()
    
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
