package ndn.psync.java_psync.detail;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;

import net.named_data.jndn.Name;

public class Util {
	public static long murmurHash3(int seed, long key)
	{
		com.google.common.hash.HashFunction hashImplementation = Hashing.murmur3_32(seed);
		return Integer.toUnsignedLong(hashImplementation.newHasher().putLong(key).hash().asInt());
	}
	
	public static long murmurHash3(int seed, int key)
	{
		com.google.common.hash.HashFunction hashImplementation = Hashing.murmur3_32(seed);
		return Integer.toUnsignedLong(hashImplementation.newHasher().putInt(key).hash().asInt());
	}
	
	public static long murmurHash3(int seed, String key)
	{
		com.google.common.hash.HashFunction hashImplementation = Hashing.murmur3_32(seed);
		return Integer.toUnsignedLong(hashImplementation.newHasher().putString(key, Charsets.UTF_8).hash().asInt());
	}
}
