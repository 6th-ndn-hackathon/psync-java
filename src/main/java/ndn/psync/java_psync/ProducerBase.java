package ndn.psync.java_psync;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import ndn.psync.java_psync.detail.HashTableEntry;
import ndn.psync.java_psync.detail.IBLT;
import net.named_data.jndn.ContentType;
import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.MetaInfo;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.tpm.TpmBackEnd.Error;

public abstract class ProducerBase {
	private int m_expectedNumEntries;
	protected Face m_face;
	@SuppressWarnings("unused")
	private Name m_syncPrefix;
	@SuppressWarnings("unused")
	private double m_syncReplyFreshness, m_helloReplyFreshness;
	private Map<Name, Long> m_prefixes;
	private Map<Name, Integer> m_prefix2hash = new HashMap<Name, Integer>();
	private Map<Integer, Name> m_hash2prefix = new HashMap<Integer, Name>();
	private KeyChain m_keyChain;

	private IBLT m_iblt;
	
	public
	ProducerBase(int expectedNumEntries,
                 Face face,
	             Name syncPrefix,
	             Name userPrefix,
	             double syncReplyFreshness,
	             double helloReplyFreshness,
	             KeyChain keyChain)
	{
		m_expectedNumEntries = expectedNumEntries;
		m_face = face;
		m_syncPrefix = syncPrefix;
		m_syncReplyFreshness = syncReplyFreshness;
		m_helloReplyFreshness = helloReplyFreshness;
		
		m_iblt = new IBLT(m_expectedNumEntries);
		addUserNode(userPrefix);
	}

	public boolean
	addUserNode(Name prefix)
	{
		return m_prefixes.putIfAbsent(prefix, (long) 0) == null ? false : true;
	}

	public void
	removeUserNode(Name prefix)
	{
		Long seqNo = m_prefixes.get(prefix);
		if (seqNo != null) {
			m_prefixes.remove(prefix);
			Name prefixWithSeq = new Name(prefix);
			prefixWithSeq.appendSequenceNumber(seqNo);

			Integer hash = m_prefix2hash.get(prefixWithSeq);
			if (hash != null) {
				m_prefix2hash.remove(prefixWithSeq);
				m_hash2prefix.remove(hash);
				m_iblt.erase(hash);
			}
		}
	}
	
	protected void
	updateSeqNo(Name prefix, Long seq)
	{
		Long oldSeq = m_prefixes.get(prefix);
		if (oldSeq == null) {
			return;
		}

		if (oldSeq >= seq) {
			System.out.println("Update has lower/equal seq no for prefix, doing nothing!");
			return;
		}

		if (oldSeq != 0) {
			Name prefixWithSeq = new Name(prefix);
			prefixWithSeq.appendSequenceNumber(oldSeq);
		    Integer hash = m_prefix2hash.get(prefixWithSeq);
		    if (hash != null) {
		      m_prefix2hash.remove(prefixWithSeq);
		      m_hash2prefix.remove(hash);
		      m_iblt.erase(hash);
		    }
		}

		m_prefixes.put(prefix, seq);
		Name prefixWithSeq = new Name(prefix);
		prefixWithSeq.appendSequenceNumber(seq);
		int newHash = HashTableEntry.murmurHash3(HashTableEntry.N_HASHCHECK, prefixWithSeq.toUri());
		m_prefix2hash.put(prefixWithSeq, newHash);
		m_hash2prefix.put(newHash, prefix);
		m_iblt.insert(newHash); 
	}
	
	protected void
	sendApplicationNack(Interest interest)
	{
		System.out.println("Sending application nack");
		Name dataName = new Name(interest.getName());
		m_iblt.appendToName(dataName);
		
		Data data = new Data(dataName);
		MetaInfo metaInfo = new MetaInfo();
		metaInfo.setFreshnessPeriod(m_syncReplyFreshness);
		metaInfo.setOtherTypeCode(ContentType.NACK.getNumericType());
		data.setMetaInfo(metaInfo);

		try {
			m_keyChain.sign(data);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Error e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (net.named_data.jndn.security.pib.PibImpl.Error e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (net.named_data.jndn.security.KeyChain.Error e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			m_face.putData(data);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected final OnRegisterFailed onRegisterFailed = new OnRegisterFailed() {
		public void onRegisterFailed(Name arg0) {
			System.out.println("Register failed for: " + arg0);
		}
	 };
}