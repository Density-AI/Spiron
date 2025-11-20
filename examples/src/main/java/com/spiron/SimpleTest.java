package com.spiron;

import com.spiron.api.SpironClient;
import com.spiron.core.EddyState;
import java.util.List;

public class SimpleTest {
    public static void main(String[] args) throws Exception {
        System.out.println("Testing RPC connection...");
        
        var client = new SpironClient.Builder()
            .peers(List.of("127.0.0.1:8081"))
            .workerThreads(1)
            .build();
        
        double[] vec = {0.9, 0.1, 0.0};
        EddyState eddy = new EddyState("test-1", vec, 1.5, null);
        
        System.out.println("Sending broadcast...");
        client.propose(eddy);
        System.out.println("Broadcast sent!");
        
        Thread.sleep(2000);
        
        client.close();
        System.out.println("Done!");
    }
}
