package main.java.com.bag.server;

import main.java.com.bag.util.Constants;
import main.java.com.bag.util.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Scanner;

/**
 * Server wrapper class which will contain the instance of the local cluster and global cluster.
 */
public class ServerWrapper
{
    /**
     * String to print in the case of invalid arguments.
     */
    private static final String INVALID_ARGUMENTS = "Invalid program arguments, terminating server";

    /**
     * The instance of the server which responds to the global cluster.
     * If null -> only slave of local cluster.
     */
    @Nullable private GlobalClusterSlave globalCluster;

    /**
     * The instance of the server which responds to the local cluster.
     */
    private LocalClusterSlave  localCluster;

    /**
     * The id of the server in the global cluster.
     */
    private final int globalServerId;

    /**
     * The String instance of the database.
     */
    private final String instance;

    /**
     * The id of the server in the local cluster.
     */
    private final int localClusterId;

    /**
     * Creates a serverWrapper which contains the instances of the global and local clusters.
     * Can be called by main or when a replica recovers by itself.
     * @param globalServerId the id of the server in the global cluster and local cluster.
     * @param instance  the instance of the server (Ex: Neo4j, OrientDB etc).
     * @param isPrimary checks if it is a primary.
     * @param localClusterId the id of it in the local cluster.
     * @param initialLeaderId the id of its leader in the global cluster.
     */
    public ServerWrapper(final int globalServerId, @NotNull final String instance, final boolean isPrimary, final int localClusterId, final int initialLeaderId)
    {
        this.globalServerId = globalServerId;
        this.instance = instance;
        this.localClusterId = localClusterId;
        localCluster = new LocalClusterSlave(localClusterId, instance, this);
        if(isPrimary)
        {
            globalCluster = new GlobalClusterSlave(globalServerId, instance, this);
            localCluster.setPrimary(true);
        }

        localCluster.setPrimaryGlobalClusterId(initialLeaderId);
    }

    /**
     * Get the global cluster in this wrapper.
     * @return the global cluster.
     */
    @Nullable
    public GlobalClusterSlave getGlobalCluster()
    {
        return this.globalCluster;
    }

    /**
     * Get the id of the server in the global cluster.
     * @return the id, an int.
     */
    public int getGlobalServerId()
    {
        return globalServerId;
    }

    /**
     * Kill global and local clusters
     */
    private void terminate()
    {
        if(globalCluster != null)
        {
            globalCluster.terminate();
        }
        if(localCluster != null)
        {
            localCluster.terminate();
        }
    }

    /**
     * Main method used to start each GlobalClusterSlave.
     * @param args the id for each testServer, set it in the program arguments.
     */
    public static void main(String [] args)
    {
        //todo all ids are globally unique.
        int serverId;
        final String instance;
        final boolean actsInGlobalCluster;
        final int localClusterId;
        final int idOfPrimary;

        if (args.length <= 3)
        {
            Log.getLogger().warn(INVALID_ARGUMENTS);
            return;
        }

        try
        {
            serverId = Integer.parseInt(args[0]);
        }
        catch (NumberFormatException ne)
        {
            Log.getLogger().warn(INVALID_ARGUMENTS);
            return;
        }

        final String tempInstance = args[1];

        if (tempInstance.toLowerCase().contains("titan"))
        {
            instance = Constants.TITAN;
        }
        else if (tempInstance.toLowerCase().contains("orientdb"))
        {
            instance = Constants.ORIENTDB;
        }
        else if (tempInstance.toLowerCase().contains("sparksee"))
        {
            instance = Constants.SPARKSEE;
        }
        else
        {
            instance = Constants.NEO4J;
        }

        try
        {
            localClusterId = Integer.parseInt(args[2]);
        }
        catch (NumberFormatException ne)
        {
            Log.getLogger().warn(INVALID_ARGUMENTS);
            return;
        }

        try
        {
            idOfPrimary = Integer.parseInt(args[3]);
        }
        catch (NumberFormatException ne)
        {
            Log.getLogger().warn(INVALID_ARGUMENTS);
            return;
        }

        if (args.length == 4)
        {
            actsInGlobalCluster = false;
        }
        else
        {
            actsInGlobalCluster = Boolean.valueOf(args[2]);
        }
        @NotNull final ServerWrapper wrapper = new ServerWrapper(serverId, instance, actsInGlobalCluster, localClusterId, idOfPrimary);

        final Scanner reader = new Scanner(System.in);  // Reading from System.in
        Log.getLogger().info("Write anything to the console to kill this process");
        final String command = reader.next();

        if (command != null)
        {
            wrapper.terminate();
        }
    }

    /**
     * Turn on a new instance of the global cluster.
     */
    public void initNewGlobalClusterInstance()
    {
        globalCluster = new GlobalClusterSlave(globalServerId, instance, this);
    }

    /**
     * Turn on a new instance of the local cluster.
     */
    public void initNewLocalClusterInstance()
    {
        localCluster = new LocalClusterSlave(localClusterId, instance, this);
    }

    /**
     * Get an instance of the local cluster.
     * @return the local cluster instance.
     */
    public LocalClusterSlave getLocalCLuster()
    {
        return localCluster;
    }

    /**
     * Close the global cluster instance.
     */
    public void terminateGlobalCluster()
    {
        if(globalCluster != null)
        {
            globalCluster.close();
            globalCluster = null;
        }
    }

    /**
     * Get the id of the local cluster.
     * @return the id of it.
     */
    public int getLocalClusterId()
    {
        return localClusterId;
    }
}