package main.java.com.bag.server.database;

import main.java.com.bag.server.database.interfaces.IDatabaseAccess;
import main.java.com.bag.util.NodeStorage;
import main.java.com.bag.util.RelationshipStorage;

import java.util.List;
import java.util.Map;

/**
 * Created by ray on 10/12/16.
 */
public class ArangoDBDatabaseAccess implements IDatabaseAccess
{
    public void start(int id)
    {

    }

    public void terminate()
    {

    }

    @Override
    public boolean equalHash(final List readSet)
    {
        return false;
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

    }
}
