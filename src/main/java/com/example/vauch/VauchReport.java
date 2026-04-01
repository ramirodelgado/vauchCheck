package com.example.vauch;

import java.util.Map;

// Data structure mirroring the Tier 1 API response
public record VauchReport(
    String user, 
    String status, 
    String summary, 
    Map<String, Integer> flags
) {}