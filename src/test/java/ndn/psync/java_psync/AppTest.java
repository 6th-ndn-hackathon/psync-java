package ndn.psync.java_psync;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.util.Base64;

//import com.google.common.base.Charsets;
//import com.google.common.hash.BloomFilter;
//import com.google.common.hash.Funnels;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import ndn.psync.java_psync.detail.BloomFilter;
import ndn.psync.java_psync.detail.HashTableEntry;
import ndn.psync.java_psync.detail.IBLT;
import ndn.psync.java_psync.detail.Util;
import net.named_data.jndn.Name;
import net.named_data.jndn.Name.Component;
import net.named_data.jndn.psync.LogicConsumer;

/**
 * Unit test for simple App.
 */
public class AppTest 
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public AppTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( AppTest.class );
    }

    /**
     * Rigourous Test :-)
     */
    public void testApp()
    {
        assertTrue( true );
    }
    
    /*public void testHello()
    {
        // send a hello
        Name syncName = new Name("/sync/stuff");
        Name defaultCertificateName;
            try {
                defaultCertificateName = keyChain.createIdentityAndCertificate(testIdName);
                keyChain.getIdentityManager().setDefaultIdentity(testIdName);
            } 
            catch (SecurityException e2) {
                defaultCertificateName = new Name("/bogus/certificate/name");
            }
            face.setCommandSigningInfo(keyChain, defaultCertificateName);
            try {
                face.processEvents();
            }
            catch (IOException | EncodingException e) {
                e.printStackTrace();
            }
        LogicConsumer consumer = new LogicConsumer(syncName, face, 4, 0.001);
        consumer.sendHelloInterest();
        face.processEvents();
    }*/
    
    public void testIBLT()
    {
    	IBLT iblt = new IBLT(10);
    	// System.out.println(iblt.toString());

    	Name userPrefix = new Name("/test/memphis");
    	userPrefix.append(Component.fromNumber(1));

    	long hash =  Util.murmurHash3(11, userPrefix.toUri());
    	assertTrue(hash == 4138171066L);

    	iblt.insert(hash);
    	// System.out.println(iblt);

    	// 15 = 10 + 10/2
    	HashTableEntry hashTableExpected[] = new HashTableEntry[15];
        for (int i = 0; i < hashTableExpected.length; i++) {
        	hashTableExpected[i] = new HashTableEntry();
        }

        long expectedKeyCheck = Util.murmurHash3(HashTableEntry.N_HASHCHECK, (int) 4138171066L);
        for (int i = 0; i < hashTableExpected.length; i += 6) {
        	hashTableExpected[i].count = 1;
        	hashTableExpected[i].keySum = 4138171066L;
        	hashTableExpected[i].keyCheck = expectedKeyCheck;
        }

        HashTableEntry [] currentTable = iblt.getHashTable();
        for (int i = 0; i < hashTableExpected.length; i++) {
        	assertTrue(hashTableExpected[i].count == currentTable[i].count);
        	assertTrue(hashTableExpected[i].keySum == currentTable[i].keySum);
        	assertTrue(hashTableExpected[i].keyCheck == currentTable[i].keyCheck);
        }

        Name syncInterestName = new Name("/sync");
        Name nameWithIblt = iblt.appendToName(syncInterestName);

        Name expectedName = new Name("/sync/%01%00%00%00%BAz%A7%F6cM%A3k%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%01%00%00%00%BAz%A7%F6cM%A3k%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%01%00%00%00%BAz%A7%F6cM%A3k%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00");
        assertTrue(expectedName.equals(nameWithIblt));

        Component ibltName = new Component(expectedName.get(1).getValue());
        // System.out.println(expectedName.get(1).toEscapedString());
        IBLT iblt2 = new IBLT(10);
        try {
			iblt2.initialize(ibltName);
		} catch (Exception e) {
			e.printStackTrace();
			assertTrue(false);
		}

        assertTrue(iblt.equals(iblt2));
    }

    public void testBloom()
    {
    	BloomFilter bloomFilter = new BloomFilter(100, 0.001);
    	bloomFilter.insert("/memphis");
    	Name syncPrefix = new Name("test");
    	syncPrefix = bloomFilter.appendToName(syncPrefix);
    	// System.out.println(syncPrefix);

    	Name expectedBloomFilter = new Name("/test/d/%01/%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%08%08%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%10%00%00%00%00%00%00%00%00%00%00%00%00%00%00%80%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%08%00%00%20%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%40%00%00%00%18%00%00%20%00%00%00"); 
    	assertTrue(syncPrefix.equals(expectedBloomFilter));
    	
    	assertTrue(bloomFilter.contains("/memphis") == true);
    	assertTrue(bloomFilter.contains("/test") == false);

    	bloomFilter.insert("/ucla");
    	assertTrue(bloomFilter.contains("/ucla") == true);

    	Name newSyncPrefix = new Name("test");
    	newSyncPrefix = bloomFilter.appendToName(newSyncPrefix);
    	BloomFilter bfFromName = null;
		try {
			bfFromName = new BloomFilter(newSyncPrefix.get(1).toNumber(),
										 newSyncPrefix.get(2).toNumber()/1000.0,
										 newSyncPrefix.get(3));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	assertTrue(bloomFilter.equals(bfFromName));
    }
}
