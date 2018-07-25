package ndn.psync.java_psync.detail;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;

public class HashTableEntry {
	public int count;
	public int keySum;
	public int keyCheck;

	public static int N_HASHCHECK = 11;
	
	public boolean isPure()
	{
	  if (count == 1 || count == -1) {
		int check = murmurHash3(N_HASHCHECK, keySum);
	    return keyCheck == check;
	  }

	  return false;
	}

	public boolean isEmpty()
	{
	  return count == 0 && keySum == 0 && keyCheck == 0;
	}

	public static int murmurHash3(int seed, int key)
	{
		com.google.common.hash.HashFunction hashImplementation = Hashing.murmur3_32(seed);
		return hashImplementation.newHasher().putInt(key).hash().asInt();
	}
	
	public static int murmurHash3(int seed, String key)
	{
		com.google.common.hash.HashFunction hashImplementation = Hashing.murmur3_32(seed);
		return hashImplementation.newHasher().putString(key, Charsets.UTF_8).hash().asInt();
	}
}
