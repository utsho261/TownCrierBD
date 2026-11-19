package com.example.towncrierbd;
import org.junit.Test;
import static org.junit.Assert.*;
import com.example.towncrierbd.utils.SellerMovement;
public class SellerMovementTest {
    @Test public void detectsApproachingAndLeaving() {
        assertEquals(-1,SellerMovement.direction(23.8,90.4,23.802,90.4,23.801,90.4,10000,20000,21000));
        assertEquals(1,SellerMovement.direction(23.8,90.4,23.801,90.4,23.802,90.4,10000,20000,21000));
    }
    @Test public void ignoresGpsJitterAndStaleSamples() {
        assertEquals(0,SellerMovement.direction(23.8,90.4,23.801,90.4,23.80101,90.4,10000,20000,21000));
        assertEquals(2,SellerMovement.direction(23.8,90.4,23.802,90.4,23.801,90.4,10000,20000,200000));
        assertEquals(2,SellerMovement.direction(23.8,90.4,23.802,90.4,23.801,90.4,0,20000,21000));
    }
}
