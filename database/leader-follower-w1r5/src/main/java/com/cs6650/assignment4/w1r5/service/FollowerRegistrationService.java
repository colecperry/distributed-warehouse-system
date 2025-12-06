package com.cs6650.assignment4.w1r5.service;

import com.cs6650.assignment4.w1r5.config.NodeConfig;
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
 * FollowerRegistrationService handles automatic registration of Follower nodes
 * with the Leader when the application starts up.
 *
 * This implements the professor's recommended approach:
 * "Leader sends register-yourself message. Followers respond with their IP address."
 *
 * In our implementation, Followers actively call the Leader to register on startup.
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
   * This method runs automatically when the Spring Boot application is ready.
   * If this is a Follower node, it will register itself with the Leader.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void registerWithLeader() {
    // Only Followers need to register
    if (!nodeConfig.isFollower()) {
      System.out.println("This is the Leader node. No registration needed.");
      return;
    }

    System.out.println("========================================");
    System.out.println("Follower Registration Starting...");
    System.out.println("========================================");

    try {
      // Get this container's IP address
      String myIpAddress = getMyIpAddress();
      int myPort = getMyPort();
      int myNodeId = nodeConfig.getId();

      System.out.println("My Node ID: " + myNodeId);
      System.out.println("My IP Address: " + myIpAddress);
      System.out.println("My Port: " + myPort);
      System.out.println("Leader URL: " + leaderUrl);

      // Prepare registration data
      Map<String, Object> registrationData = new HashMap<>();
      registrationData.put("nodeId", myNodeId);
      registrationData.put("ipAddress", myIpAddress);
      registrationData.put("port", myPort);

      // Try to register with the Leader (with retries)
      boolean registered = false;
      int maxRetries = 10;

      for (int attempt = 1; attempt <= maxRetries && !registered; attempt++) {
        try {
          System.out.println("Registration attempt " + attempt + "/" + maxRetries + "...");

          String registrationUrl = leaderUrl + "/api/leader/register";
          String response = restTemplate.postForObject(
              registrationUrl,
              registrationData,
              String.class
          );

          System.out.println("✅ Registration successful! Response: " + response);
          registered = true;

        } catch (Exception e) {
          System.err.println("❌ Registration attempt " + attempt + " failed: " + e.getMessage());

          if (attempt < maxRetries) {
            System.out.println("Waiting 5 seconds before retry...");
            Thread.sleep(5000);  // Wait 5 seconds before retry
          }
        }
      }

      if (!registered) {
        System.err.println("========================================");
        System.err.println("⚠️ WARNING: Failed to register with Leader after " + maxRetries + " attempts");
        System.err.println("This Follower will not receive replications!");
        System.err.println("========================================");
      }

    } catch (Exception e) {
      System.err.println("========================================");
      System.err.println("⚠️ ERROR during follower registration: " + e.getMessage());
      e.printStackTrace();
      System.err.println("========================================");
    }
  }

  /**
   * Get this container's IP address.
   * In ECS, this will be the task's private IP in the VPC.
   */
  private String getMyIpAddress() throws Exception {
    // First try environment variable (we'll set this in Terraform)
    String envIp = System.getenv("MY_IP_ADDRESS");
    if (envIp != null && !envIp.isEmpty()) {
      return envIp;
    }

    // Fallback: Try to detect IP automatically
    InetAddress localHost = InetAddress.getLocalHost();
    return localHost.getHostAddress();
  }

  /**
   * Get the port this service is running on.
   * Reads from server.port property or environment variable.
   */
  private int getMyPort() {
    String portStr = System.getenv("PORT");
    if (portStr != null && !portStr.isEmpty()) {
      return Integer.parseInt(portStr);
    }

    // Default to 9080 for leader, 9081-9084 for followers
    return 9080 + (nodeConfig.getId() - 1);
  }
}