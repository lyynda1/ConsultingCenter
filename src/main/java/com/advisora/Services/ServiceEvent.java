package com.advisora.Services;

import com.advisora.Util.DB;
import com.advisora.entity.Event;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ServiceEvent implements IService<Event> {

    @Override
    public void add(Event event) {
        String query = "INSERT INTO event (title, description, startDate, endDate, capacite, localisation) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, event.getTitle());
            stmt.setString(2, event.getDescription());
            stmt.setTimestamp(3, Timestamp.valueOf(event.getStartDate()));
            stmt.setTimestamp(4, Timestamp.valueOf(event.getEndDate()));
            stmt.setInt(5, event.getCapacite());
            stmt.setString(6, event.getLocalisation());
            
            stmt.executeUpdate();
            System.out.println("Event added successfully!");
            
        } catch (SQLException e) {
            System.err.println("Error adding event: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public List<Event> afficher() {
        List<Event> events = new ArrayList<>();
        String query = "SELECT * FROM event";
        
        try (Connection conn = DB.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                Event event = new Event();
                event.setId(rs.getInt("id"));
                event.setTitle(rs.getString("title"));
                event.setDescription(rs.getString("description"));
                
                Timestamp startTs = rs.getTimestamp("startDate");
                if (startTs != null) {
                    event.setStartDate(startTs.toLocalDateTime());
                }
                
                Timestamp endTs = rs.getTimestamp("endDate");
                if (endTs != null) {
                    event.setEndDate(endTs.toLocalDateTime());
                }
                
                event.setCapacite(rs.getInt("capacite"));
                event.setLocalisation(rs.getString("localisation"));
                
                events.add(event);
            }
            
        } catch (SQLException e) {
            System.err.println("Error fetching events: " + e.getMessage());
            e.printStackTrace();
        }
        
        return events;
    }

    @Override
    public void modifier(Event event) {
        String query = "UPDATE event SET title = ?, description = ?, startDate = ?, endDate = ?, capacite = ?, localisation = ? WHERE id = ?";
        
        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, event.getTitle());
            stmt.setString(2, event.getDescription());
            stmt.setTimestamp(3, Timestamp.valueOf(event.getStartDate()));
            stmt.setTimestamp(4, Timestamp.valueOf(event.getEndDate()));
            stmt.setInt(5, event.getCapacite());
            stmt.setString(6, event.getLocalisation());
            stmt.setInt(7, event.getId());
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Event updated successfully!");
            } else {
                System.out.println("No event found with ID: " + event.getId());
            }
            
        } catch (SQLException e) {
            System.err.println("Error updating event: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void supprimer(Event event) {
        String query = "DELETE FROM event WHERE id = ?";
        
        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setInt(1, event.getId());
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Event deleted successfully!");
            } else {
                System.out.println("No event found with ID: " + event.getId());
            }
            
        } catch (SQLException e) {
            System.err.println("Error deleting event: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Additional useful methods
    
    public Event getById(int id) {
        String query = "SELECT * FROM event WHERE id = ?";
        
        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                Event event = new Event();
                event.setId(rs.getInt("id"));
                event.setTitle(rs.getString("title"));
                event.setDescription(rs.getString("description"));
                
                Timestamp startTs = rs.getTimestamp("startDate");
                if (startTs != null) {
                    event.setStartDate(startTs.toLocalDateTime());
                }
                
                Timestamp endTs = rs.getTimestamp("endDate");
                if (endTs != null) {
                    event.setEndDate(endTs.toLocalDateTime());
                }
                
                event.setCapacite(rs.getInt("capacite"));
                event.setLocalisation(rs.getString("localisation"));
                
                return event;
            }
            
        } catch (SQLException e) {
            System.err.println("Error fetching event by ID: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    public List<Event> getEventsByLocalisation(String localisation) {
        List<Event> events = new ArrayList<>();
        String query = "SELECT * FROM event WHERE localisation LIKE ?";
        
        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, "%" + localisation + "%");
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                Event event = new Event();
                event.setId(rs.getInt("id"));
                event.setTitle(rs.getString("title"));
                event.setDescription(rs.getString("description"));
                
                Timestamp startTs = rs.getTimestamp("startDate");
                if (startTs != null) {
                    event.setStartDate(startTs.toLocalDateTime());
                }
                
                Timestamp endTs = rs.getTimestamp("endDate");
                if (endTs != null) {
                    event.setEndDate(endTs.toLocalDateTime());
                }
                
                event.setCapacite(rs.getInt("capacite"));
                event.setLocalisation(rs.getString("localisation"));
                
                events.add(event);
            }
            
        } catch (SQLException e) {
            System.err.println("Error fetching events by localisation: " + e.getMessage());
            e.printStackTrace();
        }
        
        return events;
    }
    
    public List<Event> getUpcomingEvents() {
        List<Event> events = new ArrayList<>();
        String query = "SELECT * FROM event WHERE startDate > NOW() ORDER BY startDate ASC";
        
        try (Connection conn = DB.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                Event event = new Event();
                event.setId(rs.getInt("id"));
                event.setTitle(rs.getString("title"));
                event.setDescription(rs.getString("description"));
                
                Timestamp startTs = rs.getTimestamp("startDate");
                if (startTs != null) {
                    event.setStartDate(startTs.toLocalDateTime());
                }
                
                Timestamp endTs = rs.getTimestamp("endDate");
                if (endTs != null) {
                    event.setEndDate(endTs.toLocalDateTime());
                }
                
                event.setCapacite(rs.getInt("capacite"));
                event.setLocalisation(rs.getString("localisation"));
                
                events.add(event);
            }
            
        } catch (SQLException e) {
            System.err.println("Error fetching upcoming events: " + e.getMessage());
            e.printStackTrace();
        }
        
        return events;
    }
}
