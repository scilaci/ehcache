package net.sf.ehcache.transaction.local;

import net.sf.ehcache.Element;
import net.sf.ehcache.transaction.TransactionID;

/**
 * @author Ludovic Orban
 */
public interface SoftLock {

    Object getKey();

    Element getOldElement();

    Element getNewElement();

    void setNewElement(Element newElement);

    TransactionID getTransactionID();

    long getExpirationTimestamp();

    void lock();

    boolean waitForRelease() throws InterruptedException;

    void unlock();

    SoftLock copy();

}
