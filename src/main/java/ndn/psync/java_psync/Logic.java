package ndn.psync.java_psync;

import java.util.HashMap;
import java.util.Map;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.util.Blob;
import net.named_data.jndn.util.MemoryContentCache;
import se.rosenbaum.iblt.Cell;
import se.rosenbaum.iblt.IBLT;
import se.rosenbaum.iblt.data.IntegerData;
import se.rosenbaum.iblt.hash.HashFunction;
import se.rosenbaum.iblt.hash.HashFunctions;
import se.rosenbaum.iblt.hash.IntegerDataHashFunction;
import se.rosenbaum.iblt.hash.IntegerDataSubtablesHashFunctions;

public class Logic {
	
	public Logic(int expectedNumEntries, Face face, Name syncPrefix, Name userPrefix,
	             double syncReplyFreshness, double helloReplyFreshness, KeyChain keyChain) //UpdateCallback onUpdateCallBack)
	{
		m_face = face;
		m_syncPrefix = syncPrefix;
		m_expectedNumEntries = expectedNumEntries;
		m_syncReplyFreshness = syncReplyFreshness;
		m_helloReplyFreshness = helloReplyFreshness;
		m_keyChain = keyChain;
		
		m_expectedNumEntries = (int) (m_expectedNumEntries*1.5);

		// Make number of cells (buckets) a multiple of hash function count (N_Hash)
		while (true) {
	        if (m_expectedNumEntries % m_hashFunctionCount == 0) {
	        	break;
	        } else {
	        	++m_expectedNumEntries;
	        }
		}
		// m_expectedNumEntries = (int) (m_expectedNumEntries*1.5);
		Cell[] cells = new Cell[m_expectedNumEntries];
        HashFunction<IntegerData, IntegerData> hashFunction = new IntegerDataHashFunction();
        for (int i = 0; i < m_expectedNumEntries; i++) {
            cells[i] = new Cell(new IntegerData(0), new IntegerData(0), new IntegerData(0), hashFunction, 0);
        }

		m_iblt = new IBLT<IntegerData, IntegerData>(cells, new IntegerDataSubtablesHashFunctions(m_expectedNumEntries, m_hashFunctionCount));
		
		Name prefix = new Name("test");
		appendIBLT(prefix);
		System.out.println(prefix);
		
		m_iblt = new IBLT<IntegerData, IntegerData>(cells, new IntegerDataSubtablesHashFunctions(m_expectedNumEntries, 5));

		m_prefixes = new HashMap<String, Integer>();

		m_contentCacheForUserData = new MemoryContentCache(m_face);
		
		addUserPrefix(userPrefix.toString());

		try {
			m_face.registerPrefix(new Name(syncPrefix).append("hello"),
					              onHelloInterest, onRegisterFailed);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void appendIBLT (Name prefix) {
		byte[] table = new byte[m_iblt.getCells().length*4];

		int i = 0;
		for (Cell<IntegerData, IntegerData> cell : m_iblt.getCells()) {
			table[i] = (byte) cell.getCount();
			table[i+1] = (byte) cell.getKeySum().getValue();
			table[i+2] = (byte) cell.getValueSum().getValue();
			IntegerData integerData = (IntegerData) cell.getHashKeySum();
			table[i+3] = (byte) integerData.getValue();
			i++;
		}
		prefix.append(table);
		prefix.append(Integer.toString(m_iblt.getCells().length));
	}
	
	public void addUserPrefix(String prefix) {
		if (!m_prefixes.containsKey(prefix)) {
			m_prefixes.put(prefix, 0);
		}
		
		//m_contentCacheForUserData.registerPrefix(new Name(prefix), onRegisterFailed);
	}

    private final OnInterestCallback onHelloInterest = new OnInterestCallback() {
        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                               InterestFilter filterData) {
        	System.out.println("Received Hello Interest " + interest.getName());
        	String content = "";
            for (String prefix1 : m_prefixes.keySet()){
                content += prefix1 + "/" + m_prefixes.get(prefix1) + "\n";
            }

            Data data = new Data(prefix);
            // Append IBF
            // data.getName().append(digest);
            data.setContent(new Blob(content));
            try {
				m_keyChain.sign(data);
				m_face.putData(data);
			} catch (Exception e) {
				e.printStackTrace();
			}
       }
	 };
	 
    private final OnInterestCallback onPartialSyncInterest = new OnInterestCallback() {
        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                               InterestFilter filterData) {
        	
        }
	 };

	 private final OnRegisterFailed onRegisterFailed = new OnRegisterFailed() {
		public void onRegisterFailed(Name arg0) {
			System.out.println("Register failed for: " + arg0);
		}
	 };
	 
	private int m_expectedNumEntries;
	private Face m_face;
	private Name m_syncPrefix;
	private double m_syncReplyFreshness, m_helloReplyFreshness;
	private double m_syncInterestLifetime = 1000;
	private Map<String, Integer> m_prefixes;
	private KeyChain m_keyChain;
	private MemoryContentCache m_contentCacheForUserData;
	private MemoryContentCache m_contentCacheForSyncData;
	
	private static int m_hashFunctionCount = 3;
	
	private IBLT<IntegerData, IntegerData> m_iblt;
}

