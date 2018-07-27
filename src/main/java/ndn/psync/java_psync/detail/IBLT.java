package ndn.psync.java_psync.detail;

import java.nio.ByteBuffer;
import java.util.Set;

import com.google.common.hash.Hashing;

import net.named_data.jndn.Name;
import net.named_data.jndn.Name.Component;

public class IBLT {
	private HashTableEntry m_hashTable[];
	private static int INSERT = 1;
	private int ERASE = -1;
	private int N_HASH = 3;
	
	public
	IBLT(int expectedNumEntries) {
		int nEntries = expectedNumEntries + expectedNumEntries / 2;
	    // make nEntries exactly divisible by N_HASH
	    int remainder = nEntries % N_HASH;
	    if (remainder != 0) {
  	        nEntries += (N_HASH - remainder);
	    }
        m_hashTable = new HashTableEntry[nEntries];
        
        for (int i = 0; i < m_hashTable.length; i++) {
        	m_hashTable[i] = new HashTableEntry();
        }
	}
	
	public HashTableEntry []
	getHashTable()
	{
		return m_hashTable;
	}
	
	public void
	initialize(Component ibltName) throws Exception
	{
		long [] values = extractValueFromName(ibltName);
		if (3 * m_hashTable.length != values.length) {
			throw new Exception("Received IBLT cannot be decoded!");
		}
		
		for (int i = 0; i < m_hashTable.length; i++) {
		    if (values[i * 3] != 0) {
		    	m_hashTable[i].count = (int) values[i * 3];
		    	m_hashTable[i].keySum = values[(i * 3) + 1];
		    	m_hashTable[i].keyCheck = values[(i * 3) + 2];
		    }
		}
	}
	
	private static long[] extractValueFromName(Component ibltName)
	{
		ByteBuffer buffer = ByteBuffer.allocate(ibltName.getValue().buf().capacity());
		buffer.put(ibltName.getValue().buf());
		byte[] ibltValues = buffer.array();
		
		int n = ibltValues.length / 4;

		long [] values = new long[n];
		
		for (int i = 0; i < 4 * n; i += 4) {
		    long t = ((0xFFL & ibltValues[i + 3]) << 24) +
		             ((0xFFL & ibltValues[i + 2]) << 16) +
		             ((0xFFL & ibltValues[i + 1]) << 8)  +
		             (0xFFL & ibltValues[i]);
		    values[i / 4] = Integer.toUnsignedLong((int) t);
		}
	
		return values;
	}
	
	@SuppressWarnings("unused")
	private void
	update(int plusOrMinus, long key)
	{
	  int bucketsPerHash = m_hashTable.length / N_HASH;

	  for (int i = 0; i < N_HASH; i++) {
	    int startEntry = i * bucketsPerHash;
	    long h = Util.murmurHash3(i, (int) key);
	    HashTableEntry entry = m_hashTable[startEntry + (int) (h % bucketsPerHash)];
	    entry.count += plusOrMinus;
	    entry.keySum ^= key;
	    entry.keyCheck ^= Util.murmurHash3(HashTableEntry.N_HASHCHECK, (int) key);
	  }
	}
	
	public void
	insert(long key)
	{
	  update(INSERT, key);
	}

	public void
	erase(long key)
	{
	  update(ERASE, key);
	}
	
	class ListResult {
		public Set<Long> positive, negative;
		public boolean success;
	}
	
	public ListResult
	listEntries()
	{
	  ListResult result = new ListResult();
	  result.success = true;

	  IBLT peeled = this;

	  int nErased = 0;
	  do {
	    nErased = 0;
	    for (HashTableEntry entry : peeled.m_hashTable) {
	      if (entry.isPure()) {
	        if (entry.count == 1) {
	        	result.positive.add(entry.keySum);
	        }
	        else {
	        	result.negative.add(entry.keySum);
	        }
	        peeled.update(-entry.count, entry.keySum);
	        ++nErased;
	      }
	    }
	  } while (nErased > 0);

	  // If any buckets for one of the hash functions is not empty,
	  // then we didn't peel them all:
	  for (HashTableEntry entry : peeled.m_hashTable) {
	    if (entry.isEmpty() != true) {
	      result.success = false;
	      return result;
	    }
	  }

	  return result;
	}
	
	public IBLT
	subtract(IBLT first, IBLT other)
	{
	  assert first.m_hashTable.length == other.m_hashTable.length;

	  IBLT result = first;
	  for (int i = 0; i < m_hashTable.length; i++) {
	    HashTableEntry e1 = result.m_hashTable[i];
	    HashTableEntry e2 = other.m_hashTable[i];
	    e1.count -= e2.count;
	    e1.keySum ^= e2.keySum;
	    e1.keyCheck ^= e2.keyCheck;
	  }

	  return result;
	}
	
	public String
	toString()
	{
		String out;
		out = "count keySum keyCheckMatch\n";
		  for (HashTableEntry entry : m_hashTable) {
		    out += entry.count + " " + entry.keySum + " ";
		    out += ((Util.murmurHash3(HashTableEntry.N_HASHCHECK, (int) entry.keySum) == entry.keyCheck) ||
		           (entry.isEmpty())? "true" : "false");
		    out += "\n";
		  }
		return out;
	}
	
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
 
        if (!(o instanceof IBLT)) {
            return false;
        }
        
        HashTableEntry [] otherHashTable = ((IBLT) o).getHashTable();
        if (otherHashTable.length != m_hashTable.length) {
        	return false;
        }
        
        for (int i = 0; i < m_hashTable.length; i++) {
        	if (otherHashTable[i].count != m_hashTable[i].count ||
        		otherHashTable[i].keySum != m_hashTable[i].keySum ||
        		otherHashTable[i].keyCheck != m_hashTable[i].keyCheck) {
        		return false;
        	}
        }
		return true;
    }
	
	public Name
	appendToName(Name name)
	{
	  int n = m_hashTable.length;
	  int unitSize = (32 * 3) / 8; // hard coding
	  int tableSize = unitSize * n;

      // Even though Java does not have unsigned byte
	  // name.append produces output == name.append uint8_t in C++
	  byte [] table = new byte[tableSize];

	  for (int i = 0; i < n; i++) {
	    // table[i*12],   table[i*12+1], table[i*12+2], table[i*12+3] --> hashTable[i].count

	    table[(i * unitSize)]   = (byte) (0xFF & m_hashTable[i].count);
	    table[(i * unitSize) + 1] = (byte) (0xFF & (m_hashTable[i].count >> 8));
	    table[(i * unitSize) + 2] = (byte) (0xFF & (m_hashTable[i].count >> 16));
	    table[(i * unitSize) + 3] = (byte) (0xFF & (m_hashTable[i].count >> 24));

	    // table[i*12+4], table[i*12+5], table[i*12+6], table[i*12+7] --> hashTable[i].keySum

	    table[(i * unitSize) + 4] = (byte) (0xFF & m_hashTable[i].keySum);
	    table[(i * unitSize) + 5] = (byte) (0xFF & (m_hashTable[i].keySum >> 8));
	    table[(i * unitSize) + 6] = (byte) (0xFF & (m_hashTable[i].keySum >> 16));
	    table[(i * unitSize) + 7] = (byte) (0xFF & (m_hashTable[i].keySum >> 24));

	    // table[i*12+8], table[i*12+9], table[i*12+10], table[i*12+11] --> hashTable[i].keyCheck

	    table[(i * unitSize) + 8] = (byte) (0xFF & m_hashTable[i].keyCheck);
	    table[(i * unitSize) + 9] = (byte) (0xFF & (m_hashTable[i].keyCheck >> 8));
	    table[(i * unitSize) + 10] = (byte) (0xFF & (m_hashTable[i].keyCheck >> 16));
	    table[(i * unitSize) + 11] = (byte) (0xFF & (m_hashTable[i].keyCheck >> 24));
	  }

	  name.append(table);
	  return name;
	}
}
