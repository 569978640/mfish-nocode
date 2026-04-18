package cn.com.mfish.graph.sync.service;

import cn.com.mfish.graph.client.NebulaGraphClient;
import cn.com.mfish.graph.model.GraphEdge;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.model.GraphNode;
import cn.com.mfish.graph.pool.NebulaSessionPool;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class NebulaWriteServiceTest {

    @Mock
    private NebulaGraphClient nebulaGraphClient;

    @Mock
    private NebulaSessionPool writePool;

    @InjectMocks
    private NebulaWriteService nebulaWriteService;

    private GraphSyncEvent createEvent;
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;

    @Before
    public void setUp() {
        GraphNode node = new GraphNode();
        node.setId("test-node-001");
        node.setNodeType("Product");
        node.setCreateBy("admin");
        node.setCreateTime(new Date());
        nodes = Collections.singletonList(node);

        GraphEdge edge = new GraphEdge();
        edge.setId("edge-001");
        edge.setEdgeType("ContainsLink");
        edge.setFromId("node-001");
        edge.setToId("node-002");
        edge.setFromType("Product");
        edge.setToType("Part");
        edge.setCreateBy("admin");
        edge.setCreateTime(new Date());
        edges = Collections.singletonList(edge);

        createEvent = new GraphSyncEvent();
        createEvent.setEventId("evt-001");
        createEvent.setEventType("CREATE");
        createEvent.setNodes(nodes);
        createEvent.setEdges(edges);
    }

    @Test
    public void testUpsertNodesAndEdges_Success() {
        when(nebulaGraphClient.getWritePool()).thenReturn(writePool);
        when(writePool.executeWrite(anyString())).thenReturn(true);

        nebulaWriteService.upsertNodesAndEdges(createEvent);

        verify(writePool, atLeastOnce()).executeWrite(anyString());
    }

    @Test
    public void testUpsertNodesAndEdges_NullEvent() {
        nebulaWriteService.upsertNodesAndEdges(null);
        verify(writePool, never()).executeWrite(anyString());
    }

    @Test
    public void testUpsertNodesAndEdges_EmptyNodes() {
        createEvent.setNodes(Collections.emptyList());
        createEvent.setEdges(edges);

        when(nebulaGraphClient.getWritePool()).thenReturn(writePool);
        when(writePool.executeWrite(anyString())).thenReturn(true);

        nebulaWriteService.upsertNodesAndEdges(createEvent);
        verify(writePool, atLeastOnce()).executeWrite(anyString());
    }

    @Test
    public void testDeleteNodesAndEdges_Success() {
        when(nebulaGraphClient.getWritePool()).thenReturn(writePool);
        when(writePool.executeWrite(anyString())).thenReturn(true);

        nebulaWriteService.deleteNodesAndEdges(createEvent);

        verify(writePool, atLeastOnce()).executeWrite(anyString());
    }

    @Test
    public void testDeleteNodesAndEdges_NullEvent() {
        nebulaWriteService.deleteNodesAndEdges(null);
        verify(writePool, never()).executeWrite(anyString());
    }
}
