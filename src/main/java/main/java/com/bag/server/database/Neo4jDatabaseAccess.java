package main.java.com.bag.server.database;

import main.java.com.bag.server.database.interfaces.IDatabaseAccess;
import main.java.com.bag.util.*;
import org.jetbrains.annotations.NotNull;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.kernel.impl.core.NodeProxy;
import org.neo4j.kernel.impl.core.RelationshipProxy;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Class created to handle access to the neo4j database.
 */
public class Neo4jDatabaseAccess implements IDatabaseAccess
{
    private static final String BASE_PATH    = "/home/ray/IdeaProjects/BAG - Byzantine fault-tolerant Architecture for Graph database/Neo4jDB";
    /**
     * The graphDB object.
     */
    private GraphDatabaseService graphDb;

    /**
     * Id of the database. (If multiple running on the same machine.
     */
    private int id;

    /**
     * String used to match key value pairs.
     */
    private static final String KEY_VALUE_PAIR = "%s: '%s'";

    private static final String MATCH = "MATCH ";

    @Override
    public void start(int id)
    {
        File dbPath = new File(BASE_PATH + id);

        this.id = id;
        graphDb = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(dbPath).newGraphDatabase();
        registerShutdownHook( graphDb );
    }

    @Override
    public void terminate()
    {
        graphDb.shutdown();
    }

    /**
     * Starts the graph database in readOnly mode.
     */
    public void startReadOnly(int id)
    {
        File dbPath = new File(BASE_PATH + id);

        graphDb = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder( dbPath )
                .setConfig( GraphDatabaseSettings.read_only, "true" )
                .newGraphDatabase();
    }

    /**
     * Creates a transaction which will get a list of nodes.
     * @param identifier the nodes which should be retrieved.
     * @return the result nodes as a List of NodeStorages..
     */
    @NotNull
    public List<Object> readObject(@NotNull Object identifier, long snapShotId)
    {
        NodeStorage nodeStorage = null;
        RelationshipStorage relationshipStorage =  null;

        if(identifier instanceof NodeStorage)
        {
            nodeStorage = (NodeStorage) identifier;
        }
        else if(identifier instanceof RelationshipStorage)
        {
            relationshipStorage = (RelationshipStorage) identifier;
        }
        else
        {
            Log.getLogger().warn("Can't read data on object: " + identifier.getClass().toString());
            return Collections.emptyList();
        }

        if(graphDb == null)
        {
            start(id);
        }

        //We only support 1 label each node/vertex because of compatibility with our graph dbs.
        ArrayList<Object> returnStorage =  new ArrayList<>();
        try(Transaction tx = graphDb.beginTx())
        {
            StringBuilder builder = new StringBuilder(MATCH);

            if(nodeStorage == null)
            {
                Log.getLogger().info(Long.toString(snapShotId));
                builder.append(buildRelationshipString(relationshipStorage));
                builder.append(String.format(" WHERE TOFLOAT(r.%s) <= %s OR n.%s IS NULL", Constants.TAG_SNAPSHOT_ID, Long.toString(snapShotId), Constants.TAG_SNAPSHOT_ID));
                builder.append(" RETURN r");
            }
            else
            {
                Log.getLogger().info(Long.toString(snapShotId));
                builder.append(buildNodeString(nodeStorage, ""));
                builder.append(String.format(" WHERE TOFLOAT(n.%s) <= %s OR n.%s IS NULL",Constants.TAG_SNAPSHOT_ID, snapShotId, Constants.TAG_SNAPSHOT_ID));
                builder.append(" RETURN n");
            }

            Result result = graphDb.execute(builder.toString());
            while (result.hasNext())
            {
                Map<String, Object> value = result.next();

                for(Map.Entry<String, Object> entry: value.entrySet())
                {
                    if(entry.getValue() instanceof NodeProxy)
                    {
                        NodeProxy n = (NodeProxy) entry.getValue();
                        NodeStorage temp = new NodeStorage(n.getLabels().iterator().next().name(), n.getAllProperties());
                        returnStorage.add(temp);
                    }
                    else if(entry.getValue() instanceof RelationshipProxy)
                    {
                        RelationshipProxy r = (RelationshipProxy) entry.getValue();
                        NodeStorage start = new NodeStorage(r.getStartNode().getLabels().iterator().next().name(), r.getStartNode().getAllProperties());
                        NodeStorage end = new NodeStorage(r.getEndNode().getLabels().iterator().next().name(), r.getEndNode().getAllProperties());


                        RelationshipStorage temp = new RelationshipStorage(r.getType().name(), r.getAllProperties(), start, end);
                        returnStorage.add(temp);
                    }
                }
            }

            tx.success();
        }
        return returnStorage;
    }

    /**
     * Creates a complete Neo4j cypher String for a certain relationshipStorage
     * @param relationshipStorage the relationshipStorage to transform.
     * @return a string which may be sent with cypher to neo4j.
     */
    private String buildRelationshipString(final RelationshipStorage relationshipStorage)
    {
        return buildNodeString(relationshipStorage.getStartNode(), "1") + buildPureRelationshipString(relationshipStorage) +
                buildNodeString(relationshipStorage.getEndNode(), "2");
    }

    /**
     * Creates a Neo4j cypher String for a certain relationshipStorage
     * @param relationshipStorage the relationshipStorage to transform.
     * @return a string which may be sent with cypher to neo4j.
     */
    private String buildPureRelationshipString(final RelationshipStorage relationshipStorage)
    {
        StringBuilder builder = new StringBuilder(buildNodeString(relationshipStorage.getStartNode(), "1"));

        builder.append("-[r");

        if (!relationshipStorage.getId().isEmpty())
        {
            builder.append(String.format(":%s", relationshipStorage.getId()));
        }

        builder.append(" {");
        Iterator<Map.Entry<String, Object>> iterator = relationshipStorage.getProperties().entrySet().iterator();

        while (iterator.hasNext())
        {
            Map.Entry<String, Object> currentProperty = iterator.next();
            builder.append(String.format(KEY_VALUE_PAIR, currentProperty.getKey(), currentProperty.getValue().toString()));

            if(iterator.hasNext())
            {
                builder.append(" , ");
            }
        }

        builder.append("}]->");

        return builder.toString();
    }

    /**
     * Creates a Neo4j cypher String for a certain nodeStorage.
     * @param nodeStorage the nodeStorage to transform.
     * @param n optional identifier in the query.
     * @return a string which may be sent with cypher to neo4j.
     */
    private String buildNodeString(NodeStorage nodeStorage, String n)
    {
        StringBuilder builder = new StringBuilder("(n").append(n);

        if (!nodeStorage.getId().isEmpty())
        {
            builder.append(String.format(":%s", nodeStorage.getId()));
        }
        builder.append(" {");

        Iterator<Map.Entry<String, Object>> iterator = nodeStorage.getProperties().entrySet().iterator();

        while(iterator.hasNext())
        {
            Map.Entry<String, Object> currentProperty = iterator.next();
            builder.append(String.format(KEY_VALUE_PAIR, currentProperty.getKey(), currentProperty.getValue().toString()));

            if(iterator.hasNext())
            {
                builder.append(" , ");
            }
        }
        builder.append("})");
        return builder.toString();
    }

    @Override
    public void execute(
            final List<NodeStorage> createSetNode,
            final List<RelationshipStorage> createSetRelationship,
            final Map<NodeStorage, NodeStorage> updateSetNode,
            final Map<RelationshipStorage, RelationshipStorage> updateSetRelationship,
            final List<NodeStorage> deleteSetNode,
            final List<RelationshipStorage> deleteSetRelationship)
    {
        try(Transaction tx = graphDb.beginTx())
        {
            //Create node
            for(NodeStorage node: createSetNode)
            {
                final Label label = node::getId;
                final Node myNode = graphDb.createNode(label);

                for(Map.Entry<String, Object> entry : node.getProperties().entrySet())
                {
                    myNode.setProperty(entry.getKey(), entry.getValue());
                }
            }

            //Create relationships
            for(RelationshipStorage relationship: createSetRelationship)
            {
                final String builder = MATCH + buildNodeString(relationship.getStartNode(), "1") +
                        ", " +
                        buildNodeString(relationship.getEndNode(), "2") +
                        " CREATE (n1)" +
                        buildPureRelationshipString(relationship) +
                        "(n2)";
                graphDb.execute(builder);
            }

            //Update nodes
            for(Map.Entry<NodeStorage, NodeStorage> node: updateSetNode.entrySet())
            {
                final StringBuilder builder = new StringBuilder(MATCH + buildNodeString(node.getKey(), ""));

                if(!node.getKey().getId().equals(node.getValue().getId()))
                {
                    builder.append(String.format("REMOVE n:%s", node.getKey().getId()));
                    builder.append(String.format("SET n:%s", node.getValue().getId()));
                }

                Set<String> keys = node.getKey().getProperties().keySet();
                keys.addAll(node.getValue().getProperties().keySet());

                for(String key : keys)
                {
                    Object value1 = node.getKey().getProperties().get(key);
                    Object value2 = node.getValue().getProperties().get(key);

                    if(value1 == null)
                    {
                        builder.append(String.format("SET n.%s = '%s'", key, value2));
                    }
                    else if(value2 == null)
                    {
                        builder.append(String.format("REMOVE n.%s", key));
                    }
                    else
                    {
                        if(value1.equals(value2))
                        {
                            continue;
                        }

                        builder.append(String.format("SET n.%s = '%s'", key, value2));
                    }
                }

                graphDb.execute(builder.toString());
            }

            //Update relationships
            for(Map.Entry<RelationshipStorage, RelationshipStorage> relationship: updateSetRelationship.entrySet())
            {
                final StringBuilder builder = new StringBuilder(MATCH + buildRelationshipString(relationship.getKey()));

                if(!relationship.getKey().getId().equals(relationship.getValue().getId()))
                {
                    builder.append(String.format("REMOVE n:%s", relationship.getKey().getId()));
                    builder.append(String.format("SET n:%s", relationship.getValue().getId()));
                }

                Set<String> keys = relationship.getKey().getProperties().keySet();
                keys.addAll(relationship.getValue().getProperties().keySet());

                for(String key : keys)
                {
                    Object value1 = relationship.getKey().getProperties().get(key);
                    Object value2 = relationship.getValue().getProperties().get(key);

                    if(value1 == null)
                    {
                        builder.append(String.format("SET n.%s = '%s'", key, value2));
                    }
                    else if(value2 == null)
                    {
                        builder.append(String.format("REMOVE n.%s", key));
                    }
                    else
                    {
                        if(value1.equals(value2))
                        {
                            continue;
                        }

                        builder.append(String.format("SET n.%s = '%s'", key, value2));
                    }
                }

                graphDb.execute(builder.toString());
            }

            //Delete relationships
            for(RelationshipStorage relationship: deleteSetRelationship)
            {
                final String cypher = MATCH + buildRelationshipString(relationship) + " DELETE r";

                graphDb.execute(cypher);
            }

            for(NodeStorage node: deleteSetNode)
            {
                final String cypher = MATCH + buildNodeString(node, "") + " DETACH DELETE n";

                graphDb.execute(cypher);
            }
            tx.success();
        }
    }

    @Override
    public boolean equalHash(final List readSet)
    {
        if(readSet.isEmpty())
        {
            return true;
        }

        if(readSet.get(0) instanceof NodeStorage)
        {
            equalHashNode(readSet);
        }
        else if(readSet.get(0) instanceof RelationshipStorage)
        {
            equalHashRelationship(readSet);
        }

        return true;
    }

    private boolean equalHashNode(final List readSet)
    {
        for(Object storage: readSet)
        {
            if(storage instanceof NodeStorage)
            {
                NodeStorage nodeStorage = (NodeStorage) storage;

                if(!compareNode(nodeStorage))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Compares a nodeStorage with the node inside the db to check if correct.
     * @param nodeStorage the node to compare
     * @return true if equal hash, else false.
     */
    private boolean compareNode(final NodeStorage nodeStorage)
    {
        if(graphDb == null)
        {
            start(id);
        }

        try (Transaction tx = graphDb.beginTx())
        {
            final String builder = MATCH + buildNodeString(nodeStorage, "") + " RETURN n";
            Result result = graphDb.execute(builder);

            //Assuming we only get one node in return.
            if(result.hasNext())
            {
                Map<String, Object> value = result.next();
                for (Map.Entry<String, Object> entry : value.entrySet())
                {
                    if (entry.getValue() instanceof NodeProxy)
                    {
                        NodeProxy n = (NodeProxy) entry.getValue();

                        try
                        {
                            if(!HashCreator.sha1FromNode(nodeStorage).equals(n.getProperty("hash")))
                            {
                                return false;
                            }
                        }
                        catch (NoSuchAlgorithmException e)
                        {
                            Log.getLogger().warn("Couldn't execute SHA1 for node", e);
                        }
                        break;
                    }
                }
            }

            tx.success();
        }
        return true;
    }

    private boolean equalHashRelationship(final List<RelationshipStorage> readSet)
    {
        for(Object storage: readSet)
        {
            if(storage instanceof RelationshipStorage)
            {
                RelationshipStorage relationshipStorage = (RelationshipStorage) storage;

                if(!compareRelationship(relationshipStorage))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Compares a nodeStorage with the node inside the db to check if correct.
     * @param relationshipStorage the node to compare
     * @return true if equal hash, else false.
     */
    private boolean compareRelationship(final RelationshipStorage relationshipStorage)
    {
        try (Transaction tx = graphDb.beginTx())
        {
            final String builder = MATCH + buildRelationshipString(relationshipStorage) + " RETURN r";
            Result result = graphDb.execute(builder);

            //Assuming we only get one node in return.
            if(result.hasNext())
            {
                Map<String, Object> value = result.next();
                for (Map.Entry<String, Object> entry : value.entrySet())
                {
                    if (entry.getValue() instanceof NodeProxy)
                    {
                        NodeProxy n = (NodeProxy) entry.getValue();

                        try
                        {
                            if(!HashCreator.sha1FromRelationship(relationshipStorage).equals(n.getProperty("hash")))
                            {
                                return false;
                            }
                        }
                        catch (NoSuchAlgorithmException e)
                        {
                            Log.getLogger().warn("Couldn't execute SHA1 for relationship", e);
                        }
                        break;
                    }
                }
            }

            tx.success();
        }
        return true;
    }

    /**
     * Registers a shutdown hook for the Neo4j instance so that it
     * shuts down nicely when the VM exits (even if you "Ctrl-C" the
     * running application).
     * @param graphDb the graphDB to register the shutDownHook to.
     */
    private static void registerShutdownHook( final GraphDatabaseService graphDb )
    {
        Runtime.getRuntime().addShutdownHook( new Thread()
        {
            @Override
            public void run()
            {
                graphDb.shutdown();
            }
        } );
    }
}
