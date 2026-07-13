package com.example.shixun.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/creative")
@CrossOrigin(origins = "*")
public class CreativeMarketplaceController {
    private final JdbcTemplate jdbc;

    public CreativeMarketplaceController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("artworkCount", count("artwork"));
        data.put("skuCount", count("product_sku"));
        data.put("designerCount", count("designer_profile"));
        data.put("orderCount", count("`order`"));
        data.put("revenue", jdbc.queryForObject("SELECT COALESCE(SUM(pay_amount),0) FROM `order` WHERE order_status <> 'cancelled'", BigDecimal.class));
        data.put("hotArtworks", jdbc.queryForList(
                "SELECT a.id, a.title, a.subtitle, a.image_url imageUrl, a.view_count viewCount, a.favorite_count favoriteCount, " +
                "c.name categoryName, d.brand_name designerName " +
                "FROM artwork a LEFT JOIN category c ON a.category_id=c.id LEFT JOIN designer_profile d ON a.designer_id=d.id " +
                "WHERE a.audit_status='approved' ORDER BY a.favorite_count DESC, a.view_count DESC LIMIT 5"));
        data.put("latestOrders", jdbc.queryForList(
                "SELECT id, order_no orderNo, total_amount totalAmount, pay_amount payAmount, order_status orderStatus, created_at createdAt " +
                "FROM `order` ORDER BY id DESC LIMIT 5"));
        return data;
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("SELECT id, name, description, sort_order sortOrder FROM category WHERE enabled=1 ORDER BY sort_order, id");
    }

    @GetMapping("/tags")
    public List<Map<String, Object>> tags() {
        return jdbc.queryForList("SELECT id, name FROM tag ORDER BY id");
    }

    @GetMapping("/artworks")
    public List<Map<String, Object>> artworks(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Long categoryId) {
        StringBuilder sql = new StringBuilder(
                "SELECT a.id, a.title, a.subtitle, a.image_url imageUrl, a.thumbnail_url thumbnailUrl, a.story, " +
                "a.license_type licenseType, a.sale_status saleStatus, a.view_count viewCount, a.favorite_count favoriteCount, " +
                "c.id categoryId, c.name categoryName, d.id designerId, d.brand_name designerName, " +
                "(SELECT MIN(price) FROM product_sku s WHERE s.artwork_id=a.id AND s.status='on_sale') minPrice, " +
                "(SELECT COUNT(*) FROM product_sku s WHERE s.artwork_id=a.id) skuCount " +
                "FROM artwork a LEFT JOIN category c ON a.category_id=c.id LEFT JOIN designer_profile d ON a.designer_id=d.id " +
                "WHERE a.audit_status='approved'");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (a.title LIKE ? OR a.subtitle LIKE ? OR a.story LIKE ?)");
            String k = "%" + keyword.trim() + "%";
            args.add(k); args.add(k); args.add(k);
        }
        if (categoryId != null) {
            sql.append(" AND a.category_id=?");
            args.add(categoryId);
        }
        sql.append(" ORDER BY a.id DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @GetMapping("/artworks/{id}")
    public Map<String, Object> artworkDetail(@PathVariable Long id) {
        jdbc.update("UPDATE artwork SET view_count=view_count+1 WHERE id=?", id);
        Map<String, Object> artwork = jdbc.queryForMap(
                "SELECT a.id, a.title, a.subtitle, a.image_url imageUrl, a.thumbnail_url thumbnailUrl, a.story, " +
                "a.license_type licenseType, a.sale_status saleStatus, a.view_count viewCount, a.favorite_count favoriteCount, " +
                "c.id categoryId, c.name categoryName, d.id designerId, d.brand_name designerName, d.bio designerBio " +
                "FROM artwork a LEFT JOIN category c ON a.category_id=c.id LEFT JOIN designer_profile d ON a.designer_id=d.id WHERE a.id=?", id);
        artwork.put("tags", jdbc.queryForList(
                "SELECT t.id, t.name FROM tag t JOIN artwork_tag at ON t.id=at.tag_id WHERE at.artwork_id=? ORDER BY t.id", id));
        artwork.put("skus", skus(id));
        return artwork;
    }

    @GetMapping("/skus")
    public List<Map<String, Object>> skus(@RequestParam(required = false) Long artworkId) {
        if (artworkId != null) {
            return jdbc.queryForList(
                    "SELECT s.id, s.artwork_id artworkId, s.sku_code skuCode, s.product_name productName, s.product_type productType, " +
                    "s.cover_url coverUrl, s.price, s.original_price originalPrice, s.stock, s.material, s.size, s.status, " +
                    "a.title artworkTitle, d.brand_name designerName " +
                    "FROM product_sku s JOIN artwork a ON s.artwork_id=a.id LEFT JOIN designer_profile d ON a.designer_id=d.id " +
                    "WHERE s.artwork_id=? ORDER BY s.id", artworkId);
        }
        return jdbc.queryForList(
                "SELECT s.id, s.artwork_id artworkId, s.sku_code skuCode, s.product_name productName, s.product_type productType, " +
                "s.cover_url coverUrl, s.price, s.original_price originalPrice, s.stock, s.material, s.size, s.status, " +
                "a.title artworkTitle, d.brand_name designerName " +
                "FROM product_sku s JOIN artwork a ON s.artwork_id=a.id LEFT JOIN designer_profile d ON a.designer_id=d.id ORDER BY s.id DESC");
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> orders() {
        List<Map<String, Object>> orders = jdbc.queryForList(
                "SELECT o.id, o.order_no orderNo, o.user_id userId, u.display_name buyerName, o.total_amount totalAmount, " +
                "o.pay_amount payAmount, o.payment_method paymentMethod, o.order_status orderStatus, o.remark, o.created_at createdAt " +
                "FROM `order` o LEFT JOIN platform_user u ON o.user_id=u.id ORDER BY o.id DESC");
        for (Map<String, Object> order : orders) {
            order.put("items", jdbc.queryForList(
                    "SELECT id, sku_id skuId, artwork_id artworkId, product_name productName, artwork_title artworkTitle, " +
                    "cover_url coverUrl, unit_price unitPrice, quantity, subtotal FROM order_item WHERE order_id=?", order.get("id")));
        }
        return orders;
    }

    @PostMapping("/orders")
    public Map<String, Object> createOrder(@RequestBody CreateOrderRequest request) {
        if (request.items == null || request.items.isEmpty()) {
            throw new IllegalArgumentException("订单至少需要一个商品");
        }
        Long userId = request.userId == null ? 3L : request.userId;
        String orderNo = "AT" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + (int)(Math.random() * 900 + 100);

        List<OrderLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderItem item : request.items) {
            Map<String, Object> sku = jdbc.queryForMap(
                    "SELECT s.*, a.title artwork_title, a.id artwork_id, a.designer_id, d.revenue_share " +
                    "FROM product_sku s JOIN artwork a ON s.artwork_id=a.id LEFT JOIN designer_profile d ON a.designer_id=d.id WHERE s.id=?", item.skuId);
            int qty = item.quantity == null || item.quantity <= 0 ? 1 : item.quantity;
            BigDecimal price = (BigDecimal) sku.get("price");
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty));
            total = total.add(subtotal);
            lines.add(new OrderLine(sku, qty, subtotal));
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        BigDecimal finalTotal = total;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO `order` (order_no, user_id, total_amount, pay_amount, payment_method, order_status, remark) VALUES (?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, orderNo);
            ps.setLong(2, userId);
            ps.setBigDecimal(3, finalTotal);
            ps.setBigDecimal(4, finalTotal);
            ps.setString(5, request.paymentMethod == null ? "mock" : request.paymentMethod);
            ps.setString(6, "paid");
            ps.setString(7, request.remark);
            return ps;
        }, keyHolder);
        Long orderId = Objects.requireNonNull(keyHolder.getKey()).longValue();

        for (OrderLine line : lines) {
            Map<String, Object> sku = line.sku;
            BigDecimal share = sku.get("revenue_share") == null ? BigDecimal.ZERO : (BigDecimal) sku.get("revenue_share");
            BigDecimal designerRevenue = line.subtotal.multiply(share).divide(BigDecimal.valueOf(100));
            jdbc.update("INSERT INTO order_item (order_id, sku_id, artwork_id, product_name, artwork_title, cover_url, unit_price, quantity, subtotal, designer_id, designer_revenue) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    orderId, sku.get("id"), sku.get("artwork_id"), sku.get("product_name"), sku.get("artwork_title"), sku.get("cover_url"), sku.get("price"), line.quantity, line.subtotal, sku.get("designer_id"), designerRevenue);
            jdbc.update("UPDATE product_sku SET stock = GREATEST(stock - ?, 0) WHERE id=?", line.quantity, sku.get("id"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("orderNo", orderNo);
        result.put("payAmount", total);
        result.put("orderStatus", "paid");
        return result;
    }

    @GetMapping("/designers")
    public List<Map<String, Object>> designers() {
        return jdbc.queryForList(
                "SELECT d.id, d.brand_name brandName, d.bio, d.revenue_share revenueShare, d.audit_status auditStatus, " +
                "u.display_name displayName, u.avatar_url avatarUrl, " +
                "(SELECT COUNT(*) FROM artwork a WHERE a.designer_id=d.id) artworkCount " +
                "FROM designer_profile d JOIN platform_user u ON d.user_id=u.id ORDER BY d.id DESC");
    }

    private Long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    public static class CreateOrderRequest {
        public Long userId;
        public String paymentMethod;
        public String remark;
        public List<CreateOrderItem> items;
    }

    public static class CreateOrderItem {
        public Long skuId;
        public Integer quantity;
    }

    private static class OrderLine {
        final Map<String, Object> sku;
        final int quantity;
        final BigDecimal subtotal;
        OrderLine(Map<String, Object> sku, int quantity, BigDecimal subtotal) {
            this.sku = sku;
            this.quantity = quantity;
            this.subtotal = subtotal;
        }
    }
}
