package com.advisora.Services.event;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple geocoding service for common Tunisia locations
 * Maps location names to latitude/longitude coordinates
 */
public class EventGeocodingService {
    
    private static final Map<String, double[]> LOCATION_CACHE = new HashMap<>();
    
    static {
        // Major Tunisia cities and regions
        LOCATION_CACHE.put("tunis", new double[]{36.8065, 10.1815});
        LOCATION_CACHE.put("sousse", new double[]{35.8256, 10.6369});
        LOCATION_CACHE.put("sfax", new double[]{34.7405, 10.7603});
        LOCATION_CACHE.put("djerba", new double[]{33.8073, 10.9518});
        LOCATION_CACHE.put("monastir", new double[]{35.7794, 10.8297});
        LOCATION_CACHE.put("rades", new double[]{36.8013, 10.2301});
        LOCATION_CACHE.put("ben arous", new double[]{36.7592, 10.2275});
        LOCATION_CACHE.put("ariana", new double[]{36.8667, 10.1667});
        LOCATION_CACHE.put("manouba", new double[]{36.8133, 10.0925});
        LOCATION_CACHE.put("bardo", new double[]{36.7967, 10.1492});
        LOCATION_CACHE.put("hammam lif", new double[]{36.6872, 10.2887});
        LOCATION_CACHE.put("bizerte", new double[]{37.2744, 9.8739});
        LOCATION_CACHE.put("nabeul", new double[]{36.4514, 10.7356});
        LOCATION_CACHE.put("hammamet", new double[]{36.3980, 10.6141});
        LOCATION_CACHE.put("kairouan", new double[]{35.6781, 10.0982});
        LOCATION_CACHE.put("gafsa", new double[]{34.4257, 8.7841});
        LOCATION_CACHE.put("tozeur", new double[]{33.9197, 8.1347});
        LOCATION_CACHE.put("kebili", new double[]{33.7070, 9.0017});
        LOCATION_CACHE.put("tataouine", new double[]{32.9297, 10.4518});
        LOCATION_CACHE.put("meknes", new double[]{33.8869, 5.5500});
        LOCATION_CACHE.put("fez", new double[]{34.0331, -4.9998});
        LOCATION_CACHE.put("marrakech", new double[]{31.6295, -8.0088});
        LOCATION_CACHE.put("casablanca", new double[]{33.5731, -7.5898});
    }
    
    /**
     * Get coordinates for a location name
     * @param location The location name (e.g., "Tunis", "Sousse")
     * @return Array [latitude, longitude] or null if not found
     */
    public static double[] getCoordinates(String location) {
        if (location == null || location.isBlank()) {
            return new double[]{36.8065, 10.1815}; // Default to Tunis center
        }
        
        String key = location.toLowerCase().trim();
        
        // Direct match
        if (LOCATION_CACHE.containsKey(key)) {
            double[] coords = LOCATION_CACHE.get(key);
            return new double[]{coords[0], coords[1]};
        }
        
        // Partial match (substring)
        for (Map.Entry<String, double[]> entry : LOCATION_CACHE.entrySet()) {
            if (key.contains(entry.getKey()) || entry.getKey().contains(key)) {
                double[] coords = entry.getValue();
                return new double[]{coords[0], coords[1]};
            }
        }
        
        // Default to Tunisia center if no match
        return new double[]{36.8065, 10.1815};
    }
    
    /**
     * Add a custom location to the cache
     */
    public static void addLocation(String name, double latitude, double longitude) {
        LOCATION_CACHE.put(name.toLowerCase().trim(), new double[]{latitude, longitude});
    }
}
