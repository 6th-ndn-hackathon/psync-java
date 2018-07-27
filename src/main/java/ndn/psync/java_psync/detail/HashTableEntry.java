package ndn.psync.java_psync.detail;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;

public class HashTableEntry {
	public int count = 0;
	public long keySum = 0;
	public long keyCheck = 0;

	public static int N_HASHCHECK = 11;
	
	public boolean isPure()
	{
	  if (count == 1 || count == -1) {
	    return keyCheck == Util.murmurHash3(N_HASHCHECK, (int) keySum);
	  }

	  return false;
	}

	public boolean isEmpty()
	{
	  return count == 0 && keySum == 0 && keyCheck == 0;
	}
	
	
}
