package com.warehouse.database.model;

/**
 * Read strategy for database read operations.
 *
 * R1: Read from a single node (fast, eventual consistency)
 *     - Best for read-heavy workloads where speed matters more than strict consistency
 *     - Used by Product Service for browsing products
 *
 * R5: Read from all 5 nodes and return newest version (slower, strong consistency)
 *     - Best for write-heavy workloads where consistency is critical
 *     - Used by Shopping Cart Service for cart operations
 */
public enum ReadStrategy {
    R1,  // Read from 1 node (fast)
    R5   // Read from all 5 nodes (strong consistency)
}
