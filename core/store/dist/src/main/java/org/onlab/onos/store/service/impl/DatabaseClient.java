package org.onlab.onos.store.service.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.kuujo.copycat.cluster.Member;
import net.kuujo.copycat.cluster.TcpMember;
import net.kuujo.copycat.event.LeaderElectEvent;
import net.kuujo.copycat.protocol.SubmitRequest;
import net.kuujo.copycat.protocol.SubmitResponse;
import net.kuujo.copycat.spi.protocol.ProtocolClient;

import org.onlab.onos.store.cluster.messaging.ClusterMessage;
import org.onlab.onos.store.cluster.messaging.ClusterMessageHandler;
import org.onlab.onos.store.service.BatchReadRequest;
import org.onlab.onos.store.service.BatchWriteRequest;
import org.onlab.onos.store.service.DatabaseException;
import org.onlab.onos.store.service.ReadResult;
import org.onlab.onos.store.service.VersionedValue;
import org.onlab.onos.store.service.WriteResult;
import org.slf4j.Logger;

/**
 * Client for interacting with the Copycat Raft cluster.
 */
public class DatabaseClient implements ClusterMessageHandler {

    private static final int RETRIES = 5;

    private static final int TIMEOUT_MS = 2000;

    private final Logger log = getLogger(getClass());

    private final DatabaseProtocolService protocol;
    private volatile ProtocolClient client = null;
    private volatile Member currentLeader = null;
    private volatile long currentLeaderTerm = 0;

    public DatabaseClient(DatabaseProtocolService protocol) {
        this.protocol = checkNotNull(protocol);
    }

    @Override
    public void handle(ClusterMessage message) {
        LeaderElectEvent event =
                ClusterMessagingProtocol.SERIALIZER.decode(message.payload());
        TcpMember newLeader = event.leader();
        long newLeaderTerm = event.term();
        if (newLeader != null && !newLeader.equals(currentLeader) && newLeaderTerm > currentLeaderTerm) {
            log.info("New leader detected. Leader: {}, term: {}", newLeader, newLeaderTerm);
            ProtocolClient prevClient = client;
            ProtocolClient newClient = protocol.createClient(newLeader);
            newClient.connect();
            client = newClient;
            currentLeader = newLeader;
            currentLeaderTerm = newLeaderTerm;

            if (prevClient != null) {
                prevClient.close();
            }
        }
    }

    private String nextRequestId() {
        return UUID.randomUUID().toString();
    }

    public void waitForLeader() {
        if (currentLeader != null) {
            return;
        }

        log.info("No leader in cluster, waiting for election.");

        try {
            while (currentLeader == null) {
                Thread.sleep(200);
            }
            return;
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for Leader", e);
            Thread.currentThread().interrupt();
        }
    }

    private <T> T submit(String operationName, Object... args) {
        waitForLeader();
        if (currentLeader == null) {
            throw new DatabaseException("Raft cluster does not have a leader.");
        }

        SubmitRequest request =
                new SubmitRequest(nextRequestId(), operationName, Arrays.asList(args));

        CompletableFuture<SubmitResponse> submitResponse = client.submit(request);

        log.debug("Sent {} to {}", request, currentLeader);

        try {
            return (T) submitResponse.get(TIMEOUT_MS, TimeUnit.MILLISECONDS).result();
        } catch (ExecutionException | InterruptedException e) {
            throw new DatabaseException(e);
        } catch (TimeoutException e) {
            throw new DatabaseException.Timeout(e);
        }
    }

    public boolean createTable(String tableName) {
        return submit("createTable", tableName);
    }

    public boolean createTable(String tableName, int ttlMillis) {
        return submit("createTable", tableName, ttlMillis);
    }

    public void dropTable(String tableName) {
        submit("dropTable", tableName);
    }

    public void dropAllTables() {
        submit("dropAllTables");
    }

    public Set<String> listTables() {
        return submit("listTables");
    }

    public List<ReadResult> batchRead(BatchReadRequest batchRequest) {
        return submit("read", batchRequest);
    }

    public List<WriteResult> batchWrite(BatchWriteRequest batchRequest) {
        return submit("write", batchRequest);
    }

    public Map<String, VersionedValue> getAll(String tableName) {
        return submit("getAll", tableName);
    }
}
