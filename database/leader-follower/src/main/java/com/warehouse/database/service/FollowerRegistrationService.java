package com.warehouse.database.service;

import com.warehouse.database.config.NodeConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles automatic registration of Follower nodes with the Leader on startup.
 * Followers call the Leader's /api/leader/register endpoint with their IP and port,
 * so the Leader knows where to send replication updates.
 */
@Service
public class FollowerRegistrationService {

  private final NodeConfig nodeConfig;
  private final RestTemplate restTemplate;

  @Value("${LEADER_URL:http://w1r5-leader:9080}")
  private String leaderUrl;

  @Autowired
  public FollowerRegistrationService(NodeConfig nodeConfig) {
    this.nodeConfig = nodeConfig;
    this.restTemplate = new RestTemplate();
  }

  /**
   * Runs automatically when the Spring Boot application is fully started.
   * If this is a Follower node, registers itself with the Leader.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void registerWithLeader() {
    if (!nodeConfig.isFollower()) {
      System.out.println("Leader node — no registration needed.");
      return;
    }

    System.out.println("Follower Registration Starting...");

    try {
      String myIpAddress = getMyIpAddress();
      int myPort = getMyPort();
      int myNodeId = nodeConfig.getId();

      System.out.printf("Node %d at %s:%d registering with Leader at %s%n",
          myNodeId, myIpAddress, myPort, leaderUrl);

      Map<String, Object> registrationData = new HashMap<>();
      registrationData.put("nodeId", myNodeId);
      registrationData.put("ipAddress", myIpAddress);
      registrationData.put("port", myPort);

      boolean registered = false;
      int maxRetries = 10;

      for (int attempt = 1; attempt <= maxRetries && !registered; attempt++) {
        try {
          System.out.println("Registration attempt " + attempt + "/" + maxRetries);
          String response = restTemplate.postForObject(
              leaderUrl + "/api/leader/register", registrationData, String.class);
          System.out.println("Registration successful: " + response);
          registered = true;
        } catch (Exception e) {
          System.err.println("Attempt " + attempt + " failed: " + e.getMessage());
          if (attempt < maxRetries) Thread.sleep(5000);
        }
      }

      if (!registered) {
        System.err.println("WARNING: Failed to register with Leader after " + maxRetries +
            " attempts. This Follower will not receive replications.");
      }

    } catch (Exception e) {
      System.err.println("ERROR during follower registration: " + e.getMessage());
    }
  }

  private String getMyIpAddress() throws Exception {
    String envIp = System.getenv("MY_IP_ADDRESS");
    if (envIp != null && !envIp.isEmpty()) return envIp;
    return InetAddress.getLocalHost().getHostAddress();
  }

  private int getMyPort() {
    String portStr = System.getenv("PORT");
    if (portStr != null && !portStr.isEmpty()) return Integer.parseInt(portStr);
    return 9080 + (nodeConfig.getId() - 1);
  }
}
