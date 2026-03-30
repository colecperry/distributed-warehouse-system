package com.warehouse.database.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Holds node configuration for this database instance.
 *
 * How it works:
 * 1. application.yml defines values under "node:" (id, type, nodes)
 * 2. Spring Boot loads application.yml at startup
 * 3. @ConfigurationProperties maps node.id → setId(), node.type → setType(), node.nodes → setNodes()
 * 4. Other classes inject NodeConfig and call isLeader(), getLeaderUrl(), getFollowerUrls(), etc.
 */
@Component
@ConfigurationProperties(prefix = "node")  // Maps application.yml "node:" section to this class
public class NodeConfig {

    // Populated by Spring from application.yml; env vars (NODE_ID, NODE_TYPE) override defaults
    private int id;           // 1=Leader, 2-5=Follower
    private String type;      // "leader" or "follower"
    private List<String> nodes;  // URLs of all nodes (Leader at index 0)

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<String> getNodes() { return nodes; }
    public void setNodes(List<String> nodes) { this.nodes = nodes; }

    public boolean isLeader() { return "leader".equalsIgnoreCase(type); }
    public boolean isFollower() { return "follower".equalsIgnoreCase(type); }

    /** Leader is always at index 0 */
    public String getLeaderUrl() { return nodes.get(0); }

    /** All nodes except the first (Leader) */
    public List<String> getFollowerUrls() { return nodes.subList(1, nodes.size()); }

    /** id=1 → index 0, id=2 → index 1, etc. */
    public String getMyUrl() { return nodes.get(id - 1); }

    public List<String> getAllNodes() { return nodes; }
}
