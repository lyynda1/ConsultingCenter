package com.advisora.Services.ressource;

import com.advisora.enums.UserRole;
import com.advisora.utils.MyConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class ResourceStatisticsService {
    private static final Set<String> ORDER_STATUSES = Set.of("LISTED", "ACTIVE", "OPEN", "CONFIRMED", "SOLD_OUT", "CANCELLED");
    private static final Set<String> PAYMENT_STATUSES = Set.of("PENDING", "PAID", "SUCCEEDED", "FAILED", "CANCELLED");
    private static final Set<String> PROJECT_STATUSES = Set.of("PENDING", "ACCEPTED", "REFUSED", "ARCHIVED");
    private static final DateTimeFormatter YM_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    public DashboardData loadDashboard(ResourceStatsFilter in, UserRole role, int currentUserId) {
        ResourceStatsFilter f = in == null ? new ResourceStatsFilter() : in.normalized();
        DashboardData d = new DashboardData();
        d.scopeLabel = buildScopeLabel(role, currentUserId);
        d.statusBreakdown = new ArrayList<>();
        d.topResources = new ArrayList<>();
        d.monthlyRevenue = new ArrayList<>();
        d.monthlyReservations = new ArrayList<>();
        d.paymentProviders = new ArrayList<>();

        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            if (tableExists(cnx, "resource_market_order")) {
                querySales(cnx, f, role, currentUserId, d);
                d.statusBreakdown = queryStatusBreakdown(cnx, f, role, currentUserId);
                d.topResources = queryTopResources(cnx, f, role, currentUserId);
                d.monthlyRevenue = queryMonthlyRevenue(cnx, f, role, currentUserId);
            }
            if (tableExists(cnx, "project_resources") && tableExists(cnx, "projects")) {
                queryReservations(cnx, f, role, currentUserId, d);
                d.monthlyReservations = queryMonthlyReservations(cnx, f, role, currentUserId);
            }
            if (tableExists(cnx, "resource_wallet_topup")) {
                queryPayments(cnx, f, role, currentUserId, d);
                d.paymentProviders = queryPaymentProviders(cnx, f, role, currentUserId);
            }
            if (tableExists(cnx, "resource_market_review") && tableExists(cnx, "resource_market_order")) {
                queryReviews(cnx, f, role, currentUserId, d);
            }
            computeComparison(d);
            return d;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur statistiques ressource: " + e.getMessage(), e);
        }
    }

    public FilterOptions loadFilterOptions(UserRole role, int currentUserId) {
        FilterOptions o = new FilterOptions();
        o.users = new ArrayList<>();
        o.resources = new ArrayList<>();
        o.projects = new ArrayList<>();
        o.statuses = new ArrayList<>();
        o.statuses.add("TOUS");

        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            o.users.add(new LookupItem(null, "Tous utilisateurs"));
            if (tableExists(cnx, "user")) {
                if (role == UserRole.ADMIN) {
                    try (PreparedStatement ps = cnx.prepareStatement(
                            "SELECT idUser, TRIM(CONCAT(COALESCE(PrenomUser,''),' ',COALESCE(nomUser,''))) AS n FROM `user` ORDER BY idUser");
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int id = rs.getInt("idUser");
                            String n = rs.getString("n");
                            o.users.add(new LookupItem(id, (n == null || n.isBlank()) ? ("User #" + id) : n.trim()));
                        }
                    }
                } else {
                    o.users.add(loadCurrentUser(cnx, currentUserId));
                }
            } else {
                o.users.add(new LookupItem(currentUserId, "User #" + currentUserId));
            }

            o.resources.add(new LookupItem(null, "Toutes ressources"));
            if (tableExists(cnx, "resources")) {
                try (PreparedStatement ps = cnx.prepareStatement("SELECT idRs, nomRs FROM resources ORDER BY nomRs");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("idRs");
                        String name = rs.getString("nomRs");
                        o.resources.add(new LookupItem(id, (name == null || name.isBlank()) ? ("Ressource #" + id) : name.trim()));
                    }
                }
            }

            o.projects.add(new LookupItem(null, "Tous projets"));
            if (tableExists(cnx, "projects")) {
                String sql = role == UserRole.ADMIN
                        ? "SELECT idProj, titleProj FROM projects ORDER BY idProj DESC"
                        : "SELECT idProj, titleProj FROM projects WHERE idClient = ? ORDER BY idProj DESC";
                try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                    if (role != UserRole.ADMIN) {
                        ps.setInt(1, currentUserId);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int id = rs.getInt("idProj");
                            String t = rs.getString("titleProj");
                            o.projects.add(new LookupItem(id, "#" + id + " - " + (t == null ? "-" : t.trim())));
                        }
                    }
                }
            }

            TreeSet<String> statuses = new TreeSet<>();
            statuses.addAll(loadDistinctStatus(cnx, "resource_market_order", "status"));
            statuses.addAll(loadDistinctStatus(cnx, "resource_wallet_topup", "status"));
            statuses.addAll(loadDistinctStatus(cnx, "projects", "stateProj"));
            o.statuses.addAll(statuses);
            return o;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur options statistiques: " + e.getMessage(), e);
        }
    }

    private void querySales(Connection cnx, ResourceStatsFilter f, UserRole role, int uid, DashboardData d) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) c, COALESCE(SUM(o.totalPrice),0) s FROM resource_market_order o "
                        + "LEFT JOIN resources r ON r.idRs=o.idRs LEFT JOIN projects p ON p.idProj=o.buyerProjectId "
                        + "LEFT JOIN `user` ub ON ub.idUser=o.buyerUserId LEFT JOIN `user` us ON us.idUser=o.sellerUserId WHERE 1=1");
        List<Object> params = new ArrayList<>();
        addOrderScope(sql, params, role, uid);
        addOrderFilters(sql, params, f);
        try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    d.salesCount = rs.getInt("c");
                    d.salesCoins = round3(rs.getDouble("s"));
                }
            }
        }
    }

    private void queryReservations(Connection cnx, ResourceStatsFilter f, UserRole role, int uid, DashboardData d) throws SQLException {
        String qtyCol = detectQtyColumn(cnx);
        String qtyExpr = qtyCol == null ? "COUNT(*)" : "COALESCE(SUM(pr." + qtyCol + "),0)";
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) linesCount, " + qtyExpr + " qty FROM project_resources pr "
                        + "JOIN projects p ON p.idProj=pr.idProj LEFT JOIN resources r ON r.idRs=pr.idRs "
                        + "LEFT JOIN `user` u ON u.idUser=p.idClient WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (role != UserRole.ADMIN) {
            sql.append(" AND p.idClient=?");
            params.add(uid);
        }
        if (f.userId != null) {
            sql.append(" AND p.idClient=?");
            params.add(f.userId);
        }
        if (f.resourceId != null) {
            sql.append(" AND pr.idRs=?");
            params.add(f.resourceId);
        }
        if (f.projectId != null) {
            sql.append(" AND p.idProj=?");
            params.add(f.projectId);
        }
        String st = normalizeStatus(f.status);
        if (PROJECT_STATUSES.contains(st)) {
            sql.append(" AND UPPER(COALESCE(p.stateProj,''))=?");
            params.add(st);
        }
        addDate(sql, params, "p.createdAtProj", f.fromDate, f.toDate);
        addReservationSearch(sql, params, f.searchText);
        try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    d.reservationLines = rs.getInt("linesCount");
                    d.reservedQty = rs.getInt("qty");
                }
            }
        }
    }

    private void queryPayments(Connection cnx, ResourceStatsFilter f, UserRole role, int uid, DashboardData d) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) c, COALESCE(SUM(t.coinAmount),0) s FROM resource_wallet_topup t "
                        + "LEFT JOIN `user` u ON u.idUser=t.idUser WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (role != UserRole.ADMIN) {
            sql.append(" AND t.idUser=?");
            params.add(uid);
        }
        if (f.userId != null) {
            sql.append(" AND t.idUser=?");
            params.add(f.userId);
        }
        String st = normalizeStatus(f.status);
        if (PAYMENT_STATUSES.contains(st)) {
            sql.append(" AND UPPER(COALESCE(t.status,''))=?");
            params.add(st);
        }
        addDate(sql, params, "t.createdAt", f.fromDate, f.toDate);
        if (f.searchText != null && !f.searchText.isBlank()) {
            String like = "%" + f.searchText.toLowerCase(Locale.ROOT) + "%";
            sql.append(" AND (LOWER(COALESCE(t.provider,'')) LIKE ? OR LOWER(COALESCE(t.externalRef,'')) LIKE ? OR ")
                    .append("LOWER(CONCAT(COALESCE(u.PrenomUser,''),' ',COALESCE(u.nomUser,''))) LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
        }
        try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    d.paymentCount = rs.getInt("c");
                    d.paymentCoins = round3(rs.getDouble("s"));
                }
            }
        }
    }

    private void queryReviews(Connection cnx, ResourceStatsFilter f, UserRole role, int uid, DashboardData d) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) c, COALESCE(AVG(rv.stars),0) a FROM resource_market_review rv "
                        + "LEFT JOIN resource_market_order o ON o.idOrder=rv.idOrder "
                        + "LEFT JOIN resources r ON r.idRs=o.idRs LEFT JOIN projects p ON p.idProj=o.buyerProjectId "
                        + "LEFT JOIN `user` ur ON ur.idUser=rv.reviewerUserId WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (role != UserRole.ADMIN) {
            sql.append(" AND (rv.reviewerUserId=? OR o.buyerUserId=? OR o.sellerUserId=?)");
            params.add(uid);
            params.add(uid);
            params.add(uid);
        }
        if (f.userId != null) {
            sql.append(" AND (rv.reviewerUserId=? OR o.buyerUserId=? OR o.sellerUserId=?)");
            params.add(f.userId);
            params.add(f.userId);
            params.add(f.userId);
        }
        if (f.resourceId != null) {
            sql.append(" AND o.idRs=?");
            params.add(f.resourceId);
        }
        if (f.projectId != null) {
            sql.append(" AND o.buyerProjectId=?");
            params.add(f.projectId);
        }
        String st = normalizeStatus(f.status);
        if (ORDER_STATUSES.contains(st)) {
            sql.append(" AND UPPER(COALESCE(o.status,''))=?");
            params.add(st);
        }
        addDate(sql, params, "rv.createdAt", f.fromDate, f.toDate);
        if (f.searchText != null && !f.searchText.isBlank()) {
            String like = "%" + f.searchText.toLowerCase(Locale.ROOT) + "%";
            sql.append(" AND (LOWER(COALESCE(r.nomRs,'')) LIKE ? OR LOWER(COALESCE(rv.comment,'')) LIKE ? OR LOWER(COALESCE(p.titleProj,'')) LIKE ? OR ")
                    .append("LOWER(CONCAT(COALESCE(ur.PrenomUser,''),' ',COALESCE(ur.nomUser,''))) LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    d.reviewCount = rs.getInt("c");
                    d.reviewAverage = round3(rs.getDouble("a"));
                }
            }
        }
    }

    private List<StatusPoint> queryStatusBreakdown(Connection cnx, ResourceStatsFilter f, UserRole role, int uid) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT UPPER(COALESCE(o.status,'UNKNOWN')) st, COUNT(*) c FROM resource_market_order o "
                + "LEFT JOIN resources r ON r.idRs=o.idRs LEFT JOIN projects p ON p.idProj=o.buyerProjectId "
                + "LEFT JOIN `user` ub ON ub.idUser=o.buyerUserId LEFT JOIN `user` us ON us.idUser=o.sellerUserId WHERE 1=1");
        List<Object> params = new ArrayList<>();
        addOrderScope(sql, params, role, uid);
        addOrderFilters(sql, params, f);
        sql.append(" GROUP BY UPPER(COALESCE(o.status,'UNKNOWN')) ORDER BY c DESC");
        List<StatusPoint> out = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StatusPoint(rs.getString("st"), rs.getInt("c")));
                }
            }
        }
        return out;
    }

    private List<ResourcePoint> queryTopResources(Connection cnx, ResourceStatsFilter f, UserRole role, int uid) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT o.idRs, COALESCE(r.nomRs, CONCAT('Ressource #',o.idRs)) n, COALESCE(SUM(o.quantity),0) q, COALESCE(SUM(o.totalPrice),0) s "
                + "FROM resource_market_order o LEFT JOIN resources r ON r.idRs=o.idRs LEFT JOIN projects p ON p.idProj=o.buyerProjectId "
                + "LEFT JOIN `user` ub ON ub.idUser=o.buyerUserId LEFT JOIN `user` us ON us.idUser=o.sellerUserId WHERE 1=1");
        List<Object> params = new ArrayList<>();
        addOrderScope(sql, params, role, uid);
        addOrderFilters(sql, params, f);
        sql.append(" GROUP BY o.idRs, COALESCE(r.nomRs, CONCAT('Ressource #',o.idRs)) ORDER BY q DESC, s DESC LIMIT 8");
        List<ResourcePoint> out = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ResourcePoint(rs.getInt("idRs"), rs.getString("n"), rs.getInt("q"), round3(rs.getDouble("s"))));
                }
            }
        }
        return out;
    }

    private List<ProviderPoint> queryPaymentProviders(Connection cnx, ResourceStatsFilter f, UserRole role, int uid) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT UPPER(COALESCE(provider,'UNKNOWN')) p, COUNT(*) c, COALESCE(SUM(coinAmount),0) s FROM resource_wallet_topup t WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (role != UserRole.ADMIN) {
            sql.append(" AND t.idUser=?");
            params.add(uid);
        }
        if (f.userId != null) {
            sql.append(" AND t.idUser=?");
            params.add(f.userId);
        }
        String st = normalizeStatus(f.status);
        if (PAYMENT_STATUSES.contains(st)) {
            sql.append(" AND UPPER(COALESCE(t.status,''))=?");
            params.add(st);
        }
        addDate(sql, params, "t.createdAt", f.fromDate, f.toDate);
        sql.append(" GROUP BY UPPER(COALESCE(provider,'UNKNOWN')) ORDER BY c DESC");
        List<ProviderPoint> out = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ProviderPoint(rs.getString("p"), rs.getInt("c"), round3(rs.getDouble("s"))));
                }
            }
        }
        return out;
    }

    private List<MonthPoint> queryMonthlyRevenue(Connection cnx, ResourceStatsFilter f, UserRole role, int uid) throws SQLException {
        YearMonth start = f.fromDate == null ? YearMonth.now().minusMonths(5) : YearMonth.from(f.fromDate);
        YearMonth end = f.toDate == null ? YearMonth.now() : YearMonth.from(f.toDate);
        if (end.isBefore(start)) {
            YearMonth t = start;
            start = end;
            end = t;
        }
        List<MonthPoint> out = emptyMonths(start, end);
        StringBuilder sql = new StringBuilder("SELECT DATE_FORMAT(o.createdAt,'%Y-%m') ym, COALESCE(SUM(o.totalPrice),0) s FROM resource_market_order o "
                + "LEFT JOIN resources r ON r.idRs=o.idRs LEFT JOIN projects p ON p.idProj=o.buyerProjectId "
                + "LEFT JOIN `user` ub ON ub.idUser=o.buyerUserId LEFT JOIN `user` us ON us.idUser=o.sellerUserId WHERE o.createdAt>=? AND o.createdAt<?");
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(start.atDay(1).atStartOfDay()));
        params.add(Timestamp.valueOf(end.plusMonths(1).atDay(1).atStartOfDay()));
        addOrderScope(sql, params, role, uid);
        addOrderFiltersNoDate(sql, params, f);
        sql.append(" GROUP BY DATE_FORMAT(o.createdAt,'%Y-%m') ORDER BY ym");
        try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    putMonth(out, rs.getString("ym"), round3(rs.getDouble("s")));
                }
            }
        }
        return out;
    }

    private List<MonthPoint> queryMonthlyReservations(Connection cnx, ResourceStatsFilter f, UserRole role, int uid) throws SQLException {
        String qtyCol = detectQtyColumn(cnx);
        String qtyExpr = qtyCol == null ? "COUNT(*)" : "COALESCE(SUM(pr." + qtyCol + "),0)";
        YearMonth start = f.fromDate == null ? YearMonth.now().minusMonths(5) : YearMonth.from(f.fromDate);
        YearMonth end = f.toDate == null ? YearMonth.now() : YearMonth.from(f.toDate);
        if (end.isBefore(start)) {
            YearMonth t = start;
            start = end;
            end = t;
        }
        List<MonthPoint> out = emptyMonths(start, end);
        StringBuilder sql = new StringBuilder("SELECT DATE_FORMAT(p.createdAtProj,'%Y-%m') ym, " + qtyExpr + " q FROM project_resources pr "
                + "JOIN projects p ON p.idProj=pr.idProj LEFT JOIN resources r ON r.idRs=pr.idRs LEFT JOIN `user` u ON u.idUser=p.idClient "
                + "WHERE p.createdAtProj>=? AND p.createdAtProj<?");
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(start.atDay(1).atStartOfDay()));
        params.add(Timestamp.valueOf(end.plusMonths(1).atDay(1).atStartOfDay()));
        if (role != UserRole.ADMIN) {
            sql.append(" AND p.idClient=?");
            params.add(uid);
        }
        if (f.userId != null) {
            sql.append(" AND p.idClient=?");
            params.add(f.userId);
        }
        if (f.resourceId != null) {
            sql.append(" AND pr.idRs=?");
            params.add(f.resourceId);
        }
        if (f.projectId != null) {
            sql.append(" AND p.idProj=?");
            params.add(f.projectId);
        }
        String st = normalizeStatus(f.status);
        if (PROJECT_STATUSES.contains(st)) {
            sql.append(" AND UPPER(COALESCE(p.stateProj,''))=?");
            params.add(st);
        }
        addReservationSearch(sql, params, f.searchText);
        sql.append(" GROUP BY DATE_FORMAT(p.createdAtProj,'%Y-%m') ORDER BY ym");
        try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    putMonth(out, rs.getString("ym"), round3(rs.getDouble("q")));
                }
            }
        }
        return out;
    }

    private void addOrderScope(StringBuilder sql, List<Object> params, UserRole role, int uid) {
        if (role != UserRole.ADMIN) {
            sql.append(" AND (o.buyerUserId=? OR o.sellerUserId=? OR p.idClient=?)");
            params.add(uid);
            params.add(uid);
            params.add(uid);
        }
    }

    private void addOrderFilters(StringBuilder sql, List<Object> params, ResourceStatsFilter f) {
        addOrderFiltersNoDate(sql, params, f);
        addDate(sql, params, "o.createdAt", f.fromDate, f.toDate);
    }

    private void addOrderFiltersNoDate(StringBuilder sql, List<Object> params, ResourceStatsFilter f) {
        if (f.userId != null) {
            sql.append(" AND (o.buyerUserId=? OR o.sellerUserId=?)");
            params.add(f.userId);
            params.add(f.userId);
        }
        if (f.resourceId != null) {
            sql.append(" AND o.idRs=?");
            params.add(f.resourceId);
        }
        if (f.projectId != null) {
            sql.append(" AND o.buyerProjectId=?");
            params.add(f.projectId);
        }
        String st = normalizeStatus(f.status);
        if (ORDER_STATUSES.contains(st)) {
            sql.append(" AND UPPER(COALESCE(o.status,''))=?");
            params.add(st);
        }
        if (f.searchText != null && !f.searchText.isBlank()) {
            String like = "%" + f.searchText.toLowerCase(Locale.ROOT) + "%";
            sql.append(" AND (LOWER(COALESCE(r.nomRs,'')) LIKE ? OR LOWER(COALESCE(p.titleProj,'')) LIKE ? OR ")
                    .append("LOWER(CONCAT(COALESCE(ub.PrenomUser,''),' ',COALESCE(ub.nomUser,''))) LIKE ? OR ")
                    .append("LOWER(CONCAT(COALESCE(us.PrenomUser,''),' ',COALESCE(us.nomUser,''))) LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
    }

    private void addReservationSearch(StringBuilder sql, List<Object> params, String search) {
        if (search == null || search.isBlank()) {
            return;
        }
        String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
        sql.append(" AND (LOWER(COALESCE(r.nomRs,'')) LIKE ? OR LOWER(COALESCE(p.titleProj,'')) LIKE ? OR ")
                .append("LOWER(CONCAT(COALESCE(u.PrenomUser,''),' ',COALESCE(u.nomUser,''))) LIKE ?)");
        params.add(like);
        params.add(like);
        params.add(like);
    }

    private void addDate(StringBuilder sql, List<Object> params, String column, LocalDate from, LocalDate to) {
        if (from != null) {
            sql.append(" AND ").append(column).append(">=?");
            params.add(Timestamp.valueOf(from.atStartOfDay()));
        }
        if (to != null) {
            sql.append(" AND ").append(column).append("<?");
            params.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        }
    }

    private void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object v = params.get(i);
            int idx = i + 1;
            if (v instanceof Integer n) {
                ps.setInt(idx, n);
            } else if (v instanceof Timestamp t) {
                ps.setTimestamp(idx, t);
            } else {
                ps.setString(idx, v == null ? null : String.valueOf(v));
            }
        }
    }

    private List<String> loadDistinctStatus(Connection cnx, String table, String col) throws SQLException {
        if (!tableExists(cnx, table) || !hasColumn(cnx, table, col)) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement("SELECT DISTINCT UPPER(COALESCE(" + col + ",'')) st FROM " + table + " ORDER BY st");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String st = rs.getString("st");
                if (st != null && !st.isBlank() && !"TOUS".equals(st)) {
                    out.add(st.trim());
                }
            }
        }
        return out;
    }

    private LookupItem loadCurrentUser(Connection cnx, int uid) throws SQLException {
        if (!tableExists(cnx, "user")) {
            return new LookupItem(uid, "User #" + uid);
        }
        try (PreparedStatement ps = cnx.prepareStatement("SELECT TRIM(CONCAT(COALESCE(PrenomUser,''),' ',COALESCE(nomUser,''))) n FROM `user` WHERE idUser=?")) {
            ps.setInt(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String n = rs.getString("n");
                    return new LookupItem(uid, (n == null || n.isBlank()) ? ("User #" + uid) : n.trim());
                }
            }
        }
        return new LookupItem(uid, "User #" + uid);
    }

    private boolean tableExists(Connection cnx, String table) throws SQLException {
        try (PreparedStatement ps = cnx.prepareStatement("SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean hasColumn(Connection cnx, String table, String column) throws SQLException {
        try (PreparedStatement ps = cnx.prepareStatement("SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String detectQtyColumn(Connection cnx) throws SQLException {
        if (!tableExists(cnx, "project_resources")) {
            return null;
        }
        try (PreparedStatement ps = cnx.prepareStatement("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='project_resources'");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String c = rs.getString(1);
                if ("quantite".equalsIgnoreCase(c) || "quantity".equalsIgnoreCase(c) || "qty".equalsIgnoreCase(c) || "qtyAllocated".equalsIgnoreCase(c)) {
                    return c;
                }
            }
        }
        return null;
    }

    private List<MonthPoint> emptyMonths(YearMonth start, YearMonth end) {
        List<MonthPoint> out = new ArrayList<>();
        YearMonth c = start;
        while (!c.isAfter(end)) {
            out.add(new MonthPoint(YM_FMT.format(c), c.getMonth().name().substring(0, 3) + " " + c.getYear(), 0.0));
            c = c.plusMonths(1);
        }
        return out;
    }

    private void putMonth(List<MonthPoint> list, String ym, double value) {
        for (MonthPoint p : list) {
            if (p.yearMonth.equals(ym)) {
                p.value = value;
                return;
            }
        }
    }

    private void computeComparison(DashboardData d) {
        YearMonth now = YearMonth.now();
        YearMonth prev = now.minusMonths(1);
        d.currentMonthRevenue = valueForMonth(d.monthlyRevenue, now);
        d.previousMonthRevenue = valueForMonth(d.monthlyRevenue, prev);
        if (Math.abs(d.previousMonthRevenue) < 1e-9) {
            d.monthlyRevenueDeltaPercent = d.currentMonthRevenue > 0 ? 100.0 : 0.0;
        } else {
            d.monthlyRevenueDeltaPercent = round3(((d.currentMonthRevenue - d.previousMonthRevenue) / d.previousMonthRevenue) * 100.0);
        }
    }

    private double valueForMonth(List<MonthPoint> list, YearMonth ym) {
        String k = YM_FMT.format(ym);
        for (MonthPoint p : list) {
            if (k.equals(p.yearMonth)) {
                return p.value;
            }
        }
        return 0.0;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        return "TOUS".equals(s) ? "" : s;
    }

    private String buildScopeLabel(UserRole role, int uid) {
        if (role == UserRole.ADMIN) {
            return "Scope: ADMIN (global)";
        }
        if (role == UserRole.GERANT) {
            return "Scope: GERANT (linked data user #" + uid + ")";
        }
        return "Scope: CLIENT (personal history user #" + uid + ")";
    }
    private double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }

    public static final class LookupItem {
        private final Integer id;
        private final String label;
        public LookupItem(Integer id, String label) { this.id = id; this.label = label == null ? "-" : label.trim(); }
        public Integer getId() { return id; }
        public String getLabel() { return label; }
        @Override public String toString() { return label; }
    }

    public static final class FilterOptions {
        public List<LookupItem> users;
        public List<LookupItem> resources;
        public List<LookupItem> projects;
        public List<String> statuses;
    }

    public static final class ResourceStatsFilter {
        public LocalDate fromDate;
        public LocalDate toDate;
        public Integer userId;
        public Integer resourceId;
        public Integer projectId;
        public String status;
        public String searchText;
        public ResourceStatsFilter normalized() {
            ResourceStatsFilter o = new ResourceStatsFilter();
            o.fromDate = fromDate;
            o.toDate = toDate;
            o.userId = userId;
            o.resourceId = resourceId;
            o.projectId = projectId;
            o.status = status == null ? "" : status.trim();
            o.searchText = searchText == null ? "" : searchText.trim();
            return o;
        }
    }

    public static final class DashboardData {
        public int salesCount;
        public double salesCoins;
        public int reservationLines;
        public int reservedQty;
        public int paymentCount;
        public double paymentCoins;
        public int reviewCount;
        public double reviewAverage;
        public double currentMonthRevenue;
        public double previousMonthRevenue;
        public double monthlyRevenueDeltaPercent;
        public String scopeLabel;
        public List<StatusPoint> statusBreakdown;
        public List<ResourcePoint> topResources;
        public List<ProviderPoint> paymentProviders;
        public List<MonthPoint> monthlyRevenue;
        public List<MonthPoint> monthlyReservations;
    }

    public static final class StatusPoint {
        public final String status;
        public final int count;
        public StatusPoint(String status, int count) { this.status = status == null ? "UNKNOWN" : status.trim(); this.count = count; }
    }

    public static final class ResourcePoint {
        public final int id;
        public final String name;
        public final int quantity;
        public final double coins;
        public ResourcePoint(int id, String name, int quantity, double coins) {
            this.id = id;
            this.name = name == null ? ("Ressource #" + id) : name.trim();
            this.quantity = quantity;
            this.coins = coins;
        }
    }

    public static final class ProviderPoint {
        public final String provider;
        public final int count;
        public final double coins;
        public ProviderPoint(String provider, int count, double coins) {
            this.provider = provider == null ? "UNKNOWN" : provider.trim();
            this.count = count;
            this.coins = coins;
        }
    }

    public static final class MonthPoint {
        public final String yearMonth;
        public final String label;
        public double value;
        public MonthPoint(String yearMonth, String label, double value) {
            this.yearMonth = yearMonth;
            this.label = label;
            this.value = value;
        }
    }
}
