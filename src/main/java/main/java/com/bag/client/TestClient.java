package main.java.com.bag.client;

import bftsmart.tom.ServiceProxy;
import bftsmart.tom.util.Extractor;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import main.java.com.bag.util.Constants;
import main.java.com.bag.util.Log;
import main.java.com.bag.util.NodeStorage;
import main.java.com.bag.util.RelationshipStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Class handling the client.
 */
public class TestClient extends ServiceProxy
{
    /**
     * Should the transaction run in secure mode?
     */
    private boolean secureMode = false;
    /**
     * Sets to log reads, updates, deletes and node creations.
     */
    private HashMap<NodeStorage, NodeStorage> readsSetNode;
    private HashMap<NodeStorage, NodeStorage> updateSetNode;
    private ArrayList<NodeStorage>            deleteSetNode;
    private ArrayList<NodeStorage>            createSetNode;

    /**
     * Sets to log reads, updates, deletes and relationship creations.
     */
    private HashMap<RelationshipStorage, RelationshipStorage> readsSetRelationship;
    private HashMap<RelationshipStorage, RelationshipStorage> updateSetRelationship;
    private ArrayList<RelationshipStorage>                    deleteSetRelationship;
    private ArrayList<RelationshipStorage>                    createSetRelationship;

    /**
     * Local timestamp of the current transaction.
     */
    private long localTimestamp = 0;

    /**
     * Create a threadsafe version of kryo.
     */
    private KryoFactory factory = () ->
    {
        Kryo kryo = new Kryo();
        kryo.register(NodeStorage.class, 100);
        kryo.register(RelationshipStorage.class, 200);
        return kryo;
    };

    public TestClient(final int processId)
    {
        super(processId);
        secureMode = false;
        initClient();
    }

    public TestClient(final int processId, final String configHome)
    {
        super(processId, configHome);
        initClient();
    }

    public TestClient(final int processId, final String configHome, final Comparator<byte[]> replyComparator, final Extractor replyExtractor)
    {
        super(processId, configHome, replyComparator, replyExtractor);
        initClient();
    }

    /**
     * Initiates the client maps and registers necessary operations.
     */
    private void initClient()
    {
        readsSetNode = new HashMap<>();
        updateSetNode = new HashMap<>();
        deleteSetNode = new ArrayList<>();
        createSetNode = new ArrayList<>();

        readsSetRelationship = new HashMap<>();
        updateSetRelationship = new HashMap<>();
        deleteSetRelationship = new ArrayList<>();
        createSetRelationship = new ArrayList<>();
    }

    /**
     * write requests. (Only reach database on commit)
     */
    public void write(Object identifier, Object value)
    {
        if(identifier == null && value == null)
        {
            Log.getLogger().warn("Unsupported write operation");
            return;
        }

        //Must be a create request.
        if(identifier == null)
        {
            handleCreateRequest(value);
            return;
        }

        //Must be a delete request.
        if(value == null)
        {
            handleDeleteRequest(identifier);
            return;
        }

        handleUpdateRequest(identifier, value);
    }

    /**
     * Fills the updateSet in the case of an update request.
     * Since we will execute writes after creates and before deletes. We don't have to check the other sets.
     * @param identifier the value to write to.
     * @param value what should be written.
     */
    private void handleUpdateRequest(Object identifier, Object value)
    {
        if(identifier instanceof NodeStorage && value instanceof NodeStorage)
        {
            updateSetNode.put((NodeStorage) identifier, (NodeStorage) value);
        }
        else if(identifier instanceof RelationshipStorage && value instanceof RelationshipStorage)
        {
            updateSetRelationship.put((RelationshipStorage) identifier, (RelationshipStorage) value);
        }
        else
        {
            Log.getLogger().warn("Unsupported update operation can't update a node with a relationship or vice versa");
        }
    }

    /**
     * Fills the createSet in the case of a create request.
     * @param value object to fill in the createSet.
     */
    private void handleCreateRequest(Object value)
    {
        if(value instanceof NodeStorage)
        {
            createSetNode.add((NodeStorage) value);
        }
        else if(value instanceof RelationshipStorage)
        {
            createSetRelationship.add((RelationshipStorage) value);
        }
    }

    /**
     * Fills the deleteSet in the case of a delete requests and deletes the node also from the create set and updateSet.
     * @param identifier the object to delete.
     */
    private void handleDeleteRequest(Object identifier)
    {
        if(identifier instanceof NodeStorage)
        {
            updateSetNode.remove(identifier);
            createSetNode.remove(identifier);

            deleteSetNode.add((NodeStorage) identifier);
        }
        else if(identifier instanceof RelationshipStorage)
        {
            updateSetRelationship.remove(identifier);
            createSetRelationship.remove(identifier);

            deleteSetRelationship.add((RelationshipStorage) identifier);
        }
    }

    //todo may add a list of identifier here. Could be a nested read request over various nodes and relationships (traversal)
    /**
     * ReadRequests.(Directly read database)
     * @param identifier, object which should be read, may be NodeStorage or RelationshipStorage
     */
    public void read(Object identifier)
    {
        byte[] readReturn;
        //todo use the return from invoke unordered and work with that.
        if(identifier instanceof NodeStorage)
        {
            readReturn = invokeUnordered(this.serialize(Constants.NODE_READ_MESSAGE, localTimestamp, identifier));
        }
        else if(identifier instanceof RelationshipStorage)
        {
            readReturn = invokeUnordered(this.serialize(Constants.RELATIONSHIP_READ_MESSAGE, localTimestamp, identifier));
        }
        else
        {
            Log.getLogger().warn("Unsupported identifier: " + identifier.toString());
            return;
        }
        processReadReturn(readReturn);
    }

    private void processReadReturn(byte[] value)
    {
        KryoPool pool = new KryoPool.Builder(factory).softReferences().build();
        Kryo kryo = pool.borrow();

        Input input = new Input(value);
        
        //todo use return from read request. Add returnValue to readSet.


        input.close();
        pool.release(kryo);
    }

    /**
     * Commit reaches the server, if secure commit send to all, else only send to one
     */
    public void commit()
    {
        boolean readOnly = isReadOnly();

        //Sample data just for testing purposes.
        readsSetNode.put(new NodeStorage("a"), new NodeStorage("e"));
        readsSetNode.put(new NodeStorage("b"), new NodeStorage("f"));
        readsSetNode.put(new NodeStorage("c"), new NodeStorage("g"));
        readsSetNode.put(new NodeStorage("d"), new NodeStorage("h"));

        byte[] bytes = serializeAll();
        if(readOnly && !secureMode)
        {
            Log.getLogger().info(String.format("Transaction with local transaction id: %d successfully commited", localTimestamp));
        }
        else
        {
            invokeOrdered(bytes);
        }
    }

    /**
     * Serializes the data and returns it in byte format.
     * @return the data in byte format.
     */
    private byte[] serialize(String reason, Object...args)
    {
        KryoPool pool = new KryoPool.Builder(factory).softReferences().build();
        Kryo kryo = pool.borrow();

        //Todo probably will need a bigger buffer in the future.
        Output output = new Output(0, 1024);

        kryo.writeClassAndObject(output, reason);
        for(Object identifier: args)
        {
            if(identifier instanceof NodeStorage || identifier instanceof RelationshipStorage || identifier instanceof Long)
            {
                kryo.writeClassAndObject(output, identifier);
            }
        }

        byte[] bytes = output.toBytes();
        output.close();
        pool.release(kryo);
        return bytes;
    }

    /**
     * Serializes all sets and returns it in byte format.
     * @return the data in byte format.
     */
    private byte[] serializeAll()
    {
        KryoPool pool = new KryoPool.Builder(factory).softReferences().build();
        Kryo kryo = pool.borrow();

        //Todo probably will need a bigger buffer in the future.
        Output output = new Output(0, 1024);

        kryo.writeClassAndObject(output, Constants.COMMIT_MESSAGE);
        //Write the timeStamp to the server
        kryo.writeClassAndObject(output, localTimestamp);

        //Write the node-sets to the server
        kryo.writeClassAndObject(output, readsSetNode);
        kryo.writeClassAndObject(output, updateSetNode);
        kryo.writeClassAndObject(output, deleteSetNode);
        kryo.writeClassAndObject(output, createSetNode);

        //Write the relationship-sets to the server
        kryo.writeClassAndObject(output, readsSetRelationship);
        kryo.writeClassAndObject(output, updateSetRelationship);
        kryo.writeClassAndObject(output, deleteSetRelationship);
        kryo.writeClassAndObject(output, createSetRelationship);

        byte[] bytes = output.toBytes();
        output.close();
        pool.release(kryo);
        return bytes;
    }

    /**
     * Checks if the transaction has made any changes to the update sets.
     * @return true if not.
     */
    private boolean isReadOnly()
    {
        return hadNoNodeWrites() && hadNoRelationshipWrites();
    }

    /**
     * Checks if there were writes in the node-sets.
     * @return true if not.
     */
    private boolean hadNoNodeWrites()
    {
        return updateSetNode.isEmpty() && deleteSetNode.isEmpty() && createSetNode.isEmpty();
    }

    /**
     * Checks if there were writes in the relationship-sets.
     * @return true if not.
     */
    private boolean hadNoRelationshipWrites()
    {
        return updateSetRelationship.isEmpty() && deleteSetRelationship.isEmpty() && createSetRelationship.isEmpty();
    }

}
