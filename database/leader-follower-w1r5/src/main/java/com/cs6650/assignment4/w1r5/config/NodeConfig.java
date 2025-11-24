package com.cs6650.assignment4.w1r5.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "node")
public class NodeConfig {

    // These match the YAML file structure
    private int id;
    private String type;
    private List<String> nodes;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getNodes() {
        return nodes;
    }

    public void setNodes(List<String> nodes) {
        this.nodes = nodes;
    }

    /**
     * Check if this node is the Leader
     */
    public boolean isLeader() {
        return "leader".equalsIgnoreCase(type);
    }

    /**
     * Check if this node is a Follower
     */
    public boolean isFollower() {
        return "follower".equalsIgnoreCase(type);
    }

    /**
     * Get the URL of the Leader (always first in list)
     */
    public String getLeaderUrl() {
        return nodes.get(0);  // Leader is always at index 0
    }

    /**
     * Get URLs of all Followers (nodes 2-5)
     */
    public List<String> getFollowerUrls() {
        // Return all nodes except the first one (which is the Leader)
        return nodes.subList(1, nodes.size());
    }

    /**
     * Get URL of this node
     */
    public String getMyUrl() {
        return nodes.get(id - 1);  // id=1 → index 0, id=2 → index 1, etc.
    }

    /**
     * Get all node URLs (useful for R=5 reads)
     */
    public List<String> getAllNodes() {
        return nodes;
    }
}