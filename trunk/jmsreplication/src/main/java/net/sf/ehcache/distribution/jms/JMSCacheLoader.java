package net.sf.ehcache.distribution.jms;

import net.sf.ehcache.loader.CacheLoader;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Status;
import static net.sf.ehcache.distribution.jms.JMSUtil.CACHE_MANAGER_UID;
import static net.sf.ehcache.distribution.jms.JMSUtil.localCacheManagerUid;
import net.sf.jsr107cache.CacheException;

import javax.jms.QueueConnection;
import javax.jms.Queue;
import javax.jms.QueueSession;
import javax.jms.QueueSender;
import javax.jms.JMSException;
import javax.jms.ExceptionListener;
import javax.jms.TemporaryQueue;
import javax.jms.ObjectMessage;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import java.util.Map;
import java.util.Collection;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.Serializable;

/**
 * @author Greg Luck
 *
 */
public class JMSCacheLoader implements CacheLoader {

    private static final Logger LOG = Logger.getLogger(JMSCacheLoader.class.getName());

    private AcknowledgementMode acknowledgementMode;
    private Status status;

    private QueueConnection getQueueConnection;
    private Queue getQueue;
    private QueueSender getQueueSender;
    private QueueSession getQueueSession;
    private int timeoutMillis;
    private String defaultLoaderArgument;
    private Ehcache cache;

    public JMSCacheLoader(Ehcache cache, String defaultLoaderArgument,
                          QueueConnection getQueueConnection,
                          Queue getQueue,
                          AcknowledgementMode acknowledgementMode,
                          int timeoutMillis) {

        this.cache = cache;
        this.defaultLoaderArgument = defaultLoaderArgument;
        this.getQueueConnection = getQueueConnection;
        this.acknowledgementMode = acknowledgementMode;
        this.getQueue = getQueue;
        this.timeoutMillis = timeoutMillis;
        status = Status.STATUS_UNINITIALISED;
    }


    /**
     * loads an object. Application writers should implement this
     * method to customize the loading of cache object. This method is called
     * by the caching service when the requested object is not in the cache.
     * <p/>
     *
     * @param key the key identifying the object being loaded
     * @return The object that is to be stored in the cache.
     * @throws net.sf.jsr107cache.CacheException
     *
     */
    public Object load(Object key) throws CacheException {
        return load(key, null);
    }


    /**
     * Load using both a key and an argument.
     * <p/>
     * JCache will call through to the load(key) method, rather than this method, where the argument is null.
     *
     * @param key      the key to load the object for.
     * @param argument can be anything that makes sense to the loader. The argument is converted to a String with toString()
     * to use for the JMS StringProperty loaderArgument
     * @return the Object loaded
     * @throws net.sf.jsr107cache.CacheException
     *
     */
    public Object load(Object key, Object argument) throws CacheException {
        Serializable keyAsSerializable = (Serializable) key;
        Serializable argumentAsSerializable = (Serializable) argument;

        //todo handle non-Java responders.

        Serializable effectiveLoaderArgument;
        if (argument == null) {
            effectiveLoaderArgument = defaultLoaderArgument;
        } else {
            effectiveLoaderArgument = argumentAsSerializable;    
        }


        Object value;

        MessageConsumer replyReceiver = null;
        TemporaryQueue temporaryReplyQueue = null;
        try {
            JMSEventMessage jmsEventMessage = new JMSEventMessage(Action.GET.toInt(),
                    keyAsSerializable, null, effectiveLoaderArgument.toString());
            ObjectMessage loadRequest = getQueueSession.createObjectMessage(jmsEventMessage);
            temporaryReplyQueue = getQueueSession.createTemporaryQueue();
            replyReceiver = getQueueSession.createConsumer(temporaryReplyQueue);
            loadRequest.setJMSReplyTo(temporaryReplyQueue);
            loadRequest.setIntProperty(CACHE_MANAGER_UID, localCacheManagerUid(cache));
            getQueueSender.send(loadRequest, DeliveryMode.NON_PERSISTENT, 9, timeoutMillis);

            //must send first before getting id
            String initialMessageId = loadRequest.getJMSMessageID();


            ObjectMessage reply = (ObjectMessage) replyReceiver.receive(timeoutMillis);
            if (reply == null) {
                return null;
            }
            String messageId = reply.getJMSCorrelationID();
            LOG.info("Initial ID: " + initialMessageId + ". Reply Correlation ID. " + messageId);

            String responder = reply.getStringProperty("responder");
            LOG.info("Responder: " + responder);
            assert initialMessageId.equals(messageId) : "The load request received an uncorrelated request. " +
                        "Request ID was " + messageId;
            value = reply.getObject();
        } catch (JMSException e) {
            throw new CacheException("Problem loading: " + e.getMessage(), e);
        } finally {
            try {
                replyReceiver.close();
                temporaryReplyQueue.delete();
            } catch (JMSException e) {
                LOG.log(Level.SEVERE, "Problem closing JMS Resources: " + e.getMessage(), e);
            }
        }
        return value;
    }


    /**
     * loads multiple object. Application writers should implement this
     * method to customize the loading of cache object. This method is called
     * by the caching service when the requested object is not in the cache.
     * <p/>
     *
     * @param keys a Collection of keys identifying the objects to be loaded
     * @return A Map of objects that are to be stored in the cache.
     * @throws net.sf.jsr107cache.CacheException
     *
     */
    public Map loadAll(Collection keys) throws CacheException {
        return null;       //todo
    }

    /**
     * Load using both a key and an argument.
     * <p/>
     * JCache will use the loadAll(key) method where the argument is null.
     *
     * @param keys     the keys to load objects for
     * @param argument can be anything that makes sense to the loader
     * @return a map of Objects keyed by the collection of keys passed in.
     * @throws net.sf.jsr107cache.CacheException
     *
     */
    public Map loadAll(Collection keys, Object argument) throws CacheException {
        return null;        //todo
    }

    /**
     * Gets the name of a CacheLoader
     *
     * @return the name of this CacheLoader
     */
    public String getName() {
        return "JMSCacheLoader with default loaderArgument: " + defaultLoaderArgument;
    }

    /**
     * Creates a clone of this extension. This method will only be called by ehcache before a
     * cache is initialized.
     * <p/>
     * Implementations should throw CloneNotSupportedException if they do not support clone
     * but that will stop them from being used with defaultCache.
     *
     * @return a clone
     * @throws CloneNotSupportedException if the extension could not be cloned.
     */
    public CacheLoader clone(Ehcache cache) throws CloneNotSupportedException {
        throw new CloneNotSupportedException("not supported");
    }

    /**
     * Notifies providers to initialise themselves.
     * <p/>
     * This method is called during the Cache's initialise method after it has changed it's
     * status to alive. Cache operations are legal in this method.
     *
     * @throws net.sf.ehcache.CacheException
     */
    public void init() {

        try {
            getQueueConnection.setExceptionListener(new ExceptionListener() {

                public void onException(JMSException e) {
                    LOG.log(Level.SEVERE, "Exception on getQueue Connection: " + e.getMessage(), e);
                }
            });
      
            getQueueSession = getQueueConnection.createQueueSession(false, acknowledgementMode.toInt());
            getQueueSender = getQueueSession.createSender(getQueue);


            getQueueConnection.start();

            status = Status.STATUS_ALIVE;
        } catch (JMSException e) {
            throw new net.sf.ehcache.CacheException("Exception while creating JMS connections: " + e.getMessage(), e);
        }
    }

    /**
     * Providers may be doing all sorts of exotic things and need to be able to clean up on
     * dispose.
     * <p/>
     * Cache operations are illegal when this method is called. The cache itself is partly
     * disposed when this method is called.
     *
     * @throws net.sf.ehcache.CacheException
     */
    public void dispose() throws net.sf.ehcache.CacheException {
        try {
            getQueueConnection.close();
        } catch (JMSException e) {
            throw new net.sf.ehcache.CacheException("Problem stopping queue connection: "  + e.getMessage(), e);
        }
        status = Status.STATUS_SHUTDOWN;
    }

    /**
     * @return the status of the extension
     */
    public Status getStatus() {
        return status;
    }

}
