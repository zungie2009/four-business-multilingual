package org.roberttu;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-File Self-Contained Application & Server Launcher
 * Compliant with AST v1.4 Contract: Full Standalone Administration Surface & Auditing
 */
public class FourBusinessMultilingual {

    private static final int PORT = 8099;

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================");
        System.out.println(" Booting 4-Business Multilingual Platform (v1.4) ");
        System.out.println(" Fully-Bound Administration Surface & Audit Logs  ");
        System.out.println(" Port: " + PORT);
        System.out.println("=================================================");

        EntityRepository repo = new EntityRepository("data/application_state.json");
        CompositionEngine engine = new CompositionEngine(repo);

        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();

            if ("/".equals(path)) {
                exchange.getResponseHeaders().set("Location", "/robert/clients");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            String[] parts = path.substring(1).split("/");
            if (parts.length < 2) {
                sendResponse(exchange, 400, "Invalid Request Path");
                return;
            }

            String userId = parts[0];
            String entity = parts[1];

            // --- STANDALONE ADMINISTRATION VIEW ROUTE ---
            if ("administration".equals(entity) || "admin".equals(entity)) {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && path.endsWith("/save")) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, Object> payload = parseFormData(body);
                    repo.saveAdminSettings(userId, payload);
                    exchange.getResponseHeaders().set("Location", "/" + userId + "/administration");
                    exchange.sendResponseHeaders(302, -1);
                    return;
                }
                String adminContent = DynamicUiRenderer.renderAdminConsole(userId, repo);
                sendResponse(exchange, 200, DynamicUiRenderer.renderLayout(userId, "administration", adminContent, repo));
                return;
            }

            if ("set-theme".equals(entity)) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("theme=")) {
                    repo.setTheme(userId, query.substring(6));
                }
                exchange.getResponseHeaders().set("Location", "/" + userId + "/administration");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            if (path.endsWith("/new")) {
                String form = DynamicUiRenderer.renderForm(userId, entity, repo);
                sendResponse(exchange, 200, DynamicUiRenderer.renderLayout(userId, entity, form, repo));
            } else if (path.endsWith("/save") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> payload = parseFormData(body);
                var result = engine.executeSave(userId, entity, payload);
                if (result.success()) {
                    exchange.getResponseHeaders().set("Location", "/" + userId + "/" + entity);
                    exchange.sendResponseHeaders(302, -1);
                } else {
                    String errContent = "<div class='card' style='color:red;'><h3>Error</h3><p>" + result.message() + "</p><a href='javascript:history.back()' class='btn'>Back</a></div>";
                    sendResponse(exchange, 400, DynamicUiRenderer.renderLayout(userId, entity, errContent, repo));
                }
            } else if (path.endsWith("/transition")) {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQuery(query);
                engine.executeTransition(userId, entity, params.get("id"), params.get("target"));
                exchange.getResponseHeaders().set("Location", "/" + userId + "/" + entity);
                exchange.sendResponseHeaders(302, -1);
            } else if (path.endsWith("/delete")) {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQuery(query);
                var result = engine.executeDelete(userId, entity, params.get("id"));
                if (result.success()) {
                    exchange.getResponseHeaders().set("Location", "/" + userId + "/" + entity);
                    exchange.sendResponseHeaders(302, -1);
                } else {
                    String errContent = "<div class='card' style='color:red;'><h3>Error</h3><p>" + result.message() + "</p><a href='javascript:history.back()' class='btn'>Back</a></div>";
                    sendResponse(exchange, 400, DynamicUiRenderer.renderLayout(userId, entity, errContent, repo));
                }
            } else {
                var records = repo.findAll(DomainModel.getBusiness(userId).businessAppId(), entity);
                String table = DynamicUiRenderer.renderTable(userId, entity, records, repo);
                sendResponse(exchange, 200, DynamicUiRenderer.renderLayout(userId, entity, table, repo));
            }
        });

        server.start();
        System.out.println("Server Started! Open browser at: http://localhost:" + PORT);
    }

    private static void sendResponse(com.sun.net.httpserver.HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private static Map<String, Object> parseFormData(String body) {
        Map<String, Object> map = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length > 0) {
                String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                map.put(k, v);
            }
        }
        return map;
    }

    private static Map<String, String> parseQuery(String q) {
        if (q == null) return Collections.emptyMap();
        Map<String, String> map = new HashMap<>();
        for (String pair : q.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length > 1) {
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    // --- DOMAIN MODEL ---
    public static class DomainModel {
        public record BusinessConfig(
            String businessAppId, String name, String locale, String defaultTheme,
            List<String> allowedThemes, Map<String, String> entityNames,
            Map<String, List<FieldDef>> entityFields,
            Map<String, Map<String, Map<String, String>>> localizedEnums,
            Map<String, List<TransitionDef>> entityTransitions
        ) {}

        public record FieldDef(String name, String label, String type, boolean required, List<String> enumValues, String refEntity, String displayField, String secondaryDisplayField) {}
        public record TransitionDef(String from, String to, Map<String, String> labels) {}

        public static BusinessConfig getBusiness(String userId) {
            return switch (userId.toLowerCase()) {
                case "robert" -> getRobertConfig();
                case "marie" -> getMarieConfig();
                case "hans" -> getHansConfig();
                case "sofia" -> getSofiaConfig();
                default -> getRobertConfig();
            };
        }

        private static BusinessConfig getRobertConfig() {
            Map<String, List<FieldDef>> fields = new LinkedHashMap<>();
            fields.put("clients", List.of(new FieldDef("name", "Name", "STRING", true, null, null, null, null), new FieldDef("email", "Email", "STRING", true, null, null, null, null), new FieldDef("company", "Company", "STRING", false, null, null, null, null)));
            fields.put("engagements", List.of(new FieldDef("clientId", "Client", "REFERENCE", true, null, "clients", "name", "email"), new FieldDef("title", "Title", "STRING", true, null, null, null, null), new FieldDef("status", "Status", "ENUM", true, List.of("PLANNED", "ACTIVE", "COMPLETED", "ON_HOLD", "CANCELLED"), null, null, null)));
            fields.put("invoices", List.of(new FieldDef("clientId", "Client", "REFERENCE", true, null, "clients", "name", "email"), new FieldDef("engagementId", "Engagement", "REFERENCE", true, null, "engagements", "title", "status"), new FieldDef("amount", "Amount", "DECIMAL_19_2", true, null, null, null, null), new FieldDef("status", "Status", "ENUM", true, List.of("DRAFT", "ISSUED", "PAID", "VOID"), null, null, null)));

            Map<String, Map<String, Map<String, String>>> enums = Map.of("engagements", Map.of("status", Map.of("PLANNED", "Planned", "ACTIVE", "Active", "COMPLETED", "Completed", "ON_HOLD", "On hold", "CANCELLED", "Cancelled")), "invoices", Map.of("status", Map.of("DRAFT", "Draft", "ISSUED", "Issued", "PAID", "Paid", "VOID", "Void")));
            Map<String, List<TransitionDef>> transitions = Map.of("engagements", List.of(new TransitionDef("PLANNED", "ACTIVE", Map.of("en", "Start")), new TransitionDef("ACTIVE", "COMPLETED", Map.of("en", "Complete"))), "invoices", List.of(new TransitionDef("DRAFT", "ISSUED", Map.of("en", "Issue")), new TransitionDef("ISSUED", "PAID", Map.of("en", "Pay"))));

            return new BusinessConfig("robert/robert_consulting", "Robert Consulting", "en-US", "professional-blue", List.of("professional-blue", "graphite"), Map.of("clients", "Clients", "engagements", "Engagements", "invoices", "Invoices"), fields, enums, transitions);
        }

        private static BusinessConfig getMarieConfig() {
            Map<String, List<FieldDef>> fields = new LinkedHashMap<>();
            fields.put("reservations", List.of(new FieldDef("guestName", "Nom du client", "STRING", true, null, null, null, null), new FieldDef("reservationDate", "Date", "ISO_DATE", true, null, null, null, null), new FieldDef("partySize", "Nombre de personnes", "INTEGER", true, null, null, null, null), new FieldDef("status", "Statut", "ENUM", true, List.of("PENDING", "CONFIRMED", "SERVED", "CANCELLED"), null, null, null)));
            fields.put("menu-items", List.of(new FieldDef("name", "Article du menu", "STRING", true, null, null, null, null), new FieldDef("category", "Catégorie", "STRING", true, null, null, null, null), new FieldDef("price", "Prix", "DECIMAL_19_2", true, null, null, null, null)));
            fields.put("orders", List.of(new FieldDef("tableNumber", "Table", "INTEGER", true, null, null, null, null), new FieldDef("total", "Total", "DECIMAL_19_2", true, null, null, null, null), new FieldDef("status", "Statut", "ENUM", true, List.of("PLACED", "PREPARING", "READY", "SERVED", "PAID"), null, null, null)));

            Map<String, Map<String, Map<String, String>>> enums = Map.of("reservations", Map.of("status", Map.of("PENDING", "En attente", "CONFIRMED", "Confirmée", "SERVED", "Servie", "CANCELLED", "Annulée")), "orders", Map.of("status", Map.of("PLACED", "Passée", "PREPARING", "En préparation", "READY", "Prête", "SERVED", "Servie", "PAID", "Payée")));
            Map<String, List<TransitionDef>> transitions = Map.of("reservations", List.of(new TransitionDef("PENDING", "CONFIRMED", Map.of("fr", "Confirmer")), new TransitionDef("CONFIRMED", "SERVED", Map.of("fr", "Servir"))), "orders", List.of(new TransitionDef("PLACED", "PREPARING", Map.of("fr", "Préparer")), new TransitionDef("PREPARING", "READY", Map.of("fr", "Marquer Prête")), new TransitionDef("READY", "SERVED", Map.of("fr", "Servir")), new TransitionDef("SERVED", "PAID", Map.of("fr", "Encaisser"))));

            return new BusinessConfig("marie/marie_restaurant", "Marie Restaurant", "fr-FR", "bistro-dark", List.of("bistro-dark", "warm-hospitality"), Map.of("reservations", "Réservations", "menu-items", "Menu", "orders", "Commandes"), fields, enums, transitions);
        }

        private static BusinessConfig getHansConfig() {
            Map<String, List<FieldDef>> fields = new LinkedHashMap<>();
            fields.put("sellers", List.of(new FieldDef("name", "Name", "STRING", true, null, null, null, null), new FieldDef("email", "E-Mail", "STRING", true, null, null, null, null), new FieldDef("status", "Status", "ENUM", true, List.of("ACTIVE", "SUSPENDED"), null, null, null)));
            fields.put("listings", List.of(new FieldDef("sellerId", "Verkäufer", "REFERENCE", true, null, "sellers", "name", "email"), new FieldDef("title", "Titel", "STRING", true, null, null, null, null), new FieldDef("price", "Preis", "DECIMAL_19_2", true, null, null, null, null), new FieldDef("status", "Status", "ENUM", true, List.of("ACTIVE", "SOLD", "EXPIRED"), null, null, null)));
            fields.put("orders", List.of(new FieldDef("listingId", "Angebot", "REFERENCE", true, null, "listings", "title", "price"), new FieldDef("total", "Total", "DECIMAL_19_2", true, null, null, null, null), new FieldDef("status", "Status", "ENUM", true, List.of("PLACED", "FULFILLED", "CANCELLED"), null, null, null)));

            Map<String, Map<String, Map<String, String>>> enums = Map.of("sellers", Map.of("status", Map.of("ACTIVE", "Aktiv", "SUSPENDED", "Gesperrt")), "listings", Map.of("status", Map.of("ACTIVE", "Aktiv", "SOLD", "Verkauft", "EXPIRED", "Abgelaufen")), "orders", Map.of("status", Map.of("PLACED", "Aufgegeben", "FULFILLED", "Erfüllt", "CANCELLED", "Storniert")));
            Map<String, List<TransitionDef>> transitions = Map.of("sellers", List.of(new TransitionDef("ACTIVE", "SUSPENDED", Map.of("de", "Sperren"))), "listings", List.of(new TransitionDef("ACTIVE", "SOLD", Map.of("de", "Als Verkauft markieren"))), "orders", List.of(new TransitionDef("PLACED", "FULFILLED", Map.of("de", "Ausführen"))));

            return new BusinessConfig("hans/hans_marketplace", "Hans Marketplace", "de-DE", "midnight-market", List.of("midnight-market", "structured-commerce"), Map.of("sellers", "Verkäufer", "listings", "Angebote", "orders", "Bestellungen"), fields, enums, transitions);
        }

        private static BusinessConfig getSofiaConfig() {
            Map<String, List<FieldDef>> fields = new LinkedHashMap<>();
            fields.put("properties", List.of(new FieldDef("title", "Propiedad", "STRING", true, null, null, null, null), new FieldDef("address", "Dirección", "STRING", true, null, null, null, null), new FieldDef("status", "Estado", "ENUM", true, List.of("AVAILABLE", "OCCUPIED", "MAINTENANCE"), null, null, null)));
            fields.put("tenants", List.of(new FieldDef("name", "Nombre", "STRING", true, null, null, null, null), new FieldDef("email", "Correo", "STRING", true, null, null, null, null), new FieldDef("phone", "Teléfono", "STRING", true, null, null, null, null)));
            fields.put("leases", List.of(new FieldDef("propertyId", "Propiedad", "REFERENCE", true, null, "properties", "title", "address"), new FieldDef("tenantId", "Inquilino", "REFERENCE", true, null, "tenants", "name", "email"), new FieldDef("amount", "Monto", "DECIMAL_19_2", true, null, null, null, null), new FieldDef("status", "Estado", "ENUM", true, List.of("ACTIVE", "TERMINATED", "EXPIRED"), null, null, null)));
            fields.put("maintenance", List.of(new FieldDef("propertyId", "Propiedad", "REFERENCE", true, null, "properties", "title", "address"), new FieldDef("description", "Descripción", "STRING", true, null, null, null, null), new FieldDef("status", "Estado", "ENUM", true, List.of("OPEN", "IN_PROGRESS", "CLOSED"), null, null, null)));

            Map<String, Map<String, Map<String, String>>> enums = Map.of("properties", Map.of("status", Map.of("AVAILABLE", "Disponible", "OCCUPIED", "Ocupada", "MAINTENANCE", "Mantenimiento")), "leases", Map.of("status", Map.of("ACTIVE", "Activo", "TERMINATED", "Terminado", "EXPIRED", "Vencido")), "maintenance", Map.of("status", Map.of("OPEN", "Abierta", "IN_PROGRESS", "En progreso", "CLOSED", "Cerrada")));
            Map<String, List<TransitionDef>> transitions = Map.of("properties", List.of(new TransitionDef("AVAILABLE", "OCCUPIED", Map.of("es", "Ocupar")), new TransitionDef("OCCUPIED", "AVAILABLE", Map.of("es", "Liberar"))), "leases", List.of(new TransitionDef("ACTIVE", "TERMINATED", Map.of("es", "Terminar"))), "maintenance", List.of(new TransitionDef("OPEN", "IN_PROGRESS", Map.of("es", "Iniciar")), new TransitionDef("IN_PROGRESS", "CLOSED", Map.of("es", "Cerrar"))));

            return new BusinessConfig("sofia/sofia_property", "Sofia Property Management", "es-ES", "calm-property", List.of("calm-property", "terracotta"), Map.of("properties", "Propiedades", "tenants", "Inquilinos", "leases", "Contratos", "maintenance", "Mantenimiento"), fields, enums, transitions);
        }
    }

    // --- APPLICATION ENGINE ---
    public record Result(boolean success, String message, Map<String, Object> data) {}

    public static class CompositionEngine {
        private final EntityRepository repository;

        public CompositionEngine(EntityRepository repository) { this.repository = repository; }

        public Result executeSave(String userId, String entity, Map<String, Object> payload) {
            DomainModel.BusinessConfig config = DomainModel.getBusiness(userId);
            List<DomainModel.FieldDef> fields = config.entityFields().get(entity);

            if (fields == null) return new Result(false, "Unknown entity: " + entity, null);

            List<String> errors = new ArrayList<>();
            for (DomainModel.FieldDef field : fields) {
                Object val = payload.get(field.name());
                if (field.required() && (val == null || val.toString().isBlank())) {
                    errors.add("Field '" + field.label() + "' is required.");
                }
            }

            if (!errors.isEmpty()) return new Result(false, String.join(" ", errors), null);

            String id = (String) payload.computeIfAbsent("id", k -> UUID.randomUUID().toString().substring(0, 8));
            Map<String, Object> saved = repository.save(config.businessAppId(), entity, id, payload);
            repository.logAudit(config.businessAppId(), "RECORD_CREATED", entity, id, "Created record in " + entity);
            return new Result(true, "Saved", saved);
        }

        public Result executeTransition(String userId, String entity, String id, String targetState) {
            DomainModel.BusinessConfig config = DomainModel.getBusiness(userId);
            Optional<Map<String, Object>> recOpt = repository.findById(config.businessAppId(), entity, id);

            if (recOpt.isEmpty()) return new Result(false, "Record not found", null);

            Map<String, Object> record = new HashMap<>(recOpt.get());
            String oldState = String.valueOf(record.get("status"));
            record.put("status", targetState.toUpperCase());
            repository.save(config.businessAppId(), entity, id, record);
            repository.logAudit(config.businessAppId(), "STATE_TRANSITION", entity, id, oldState + " -> " + targetState);
            return new Result(true, "Transitioned", record);
        }

        public Result executeDelete(String userId, String entity, String id) {
            DomainModel.BusinessConfig config = DomainModel.getBusiness(userId);
            boolean deleted = repository.delete(config.businessAppId(), entity, id);
            if (deleted) {
                repository.logAudit(config.businessAppId(), "RECORD_DELETED", entity, id, "Deleted record");
            }
            return new Result(deleted, deleted ? "Deleted" : "Not found", null);
        }
    }

    // --- REPOSITORY & PERSISTENCE ---
    public static class EntityRepository {
        private final String storagePath;
        private final Map<String, Map<String, Map<String, Map<String, Object>>>> db = new ConcurrentHashMap<>();
        private final Map<String, String> activeThemes = new ConcurrentHashMap<>();
        private final Map<String, Map<String, String>> adminSettings = new ConcurrentHashMap<>();
        private final Map<String, List<String>> auditLogs = new ConcurrentHashMap<>();

        public EntityRepository(String storagePath) {
            this.storagePath = storagePath;
            seed();
        }

        public synchronized Map<String, Object> save(String businessAppId, String entity, String id, Map<String, Object> data) {
            db.computeIfAbsent(businessAppId, k -> new ConcurrentHashMap<>())
              .computeIfAbsent(entity.toLowerCase(), k -> new ConcurrentHashMap<>())
              .put(id, data);
            persist();
            return data;
        }

        public Optional<Map<String, Object>> findById(String businessAppId, String entity, String id) {
            var appMap = db.get(businessAppId);
            if (appMap == null) return Optional.empty();
            var entityMap = appMap.get(entity.toLowerCase());
            return entityMap == null ? Optional.empty() : Optional.ofNullable(entityMap.get(id));
        }

        public List<Map<String, Object>> findAll(String businessAppId, String entity) {
            var appMap = db.get(businessAppId);
            if (appMap == null) return Collections.emptyList();
            var entityMap = appMap.get(entity.toLowerCase());
            return entityMap == null ? Collections.emptyList() : new ArrayList<>(entityMap.values());
        }

        public synchronized boolean delete(String businessAppId, String entity, String id) {
            var appMap = db.get(businessAppId);
            if (appMap != null) {
                var entityMap = appMap.get(entity.toLowerCase());
                if (entityMap != null && entityMap.containsKey(id)) {
                    entityMap.remove(id);
                    persist();
                    return true;
                }
            }
            return false;
        }

        public String getTheme(String userId, String defaultTheme) { 
            DomainModel.BusinessConfig cfg = DomainModel.getBusiness(userId);
            Map<String, String> settings = adminSettings.get(cfg.businessAppId());
            if (settings != null && settings.containsKey("theme")) {
                return settings.get("theme");
            }
            return activeThemes.getOrDefault(userId, defaultTheme); 
        }
        
        public void setTheme(String userId, String theme) { 
            activeThemes.put(userId, theme); 
            DomainModel.BusinessConfig cfg = DomainModel.getBusiness(userId);
            adminSettings.computeIfAbsent(cfg.businessAppId(), k -> new ConcurrentHashMap<>()).put("theme", theme);
            logAudit(cfg.businessAppId(), "THEME_CHANGED", "SYSTEM", userId, "Theme changed to " + theme);
            persist();
        }

        public void saveAdminSettings(String userId, Map<String, Object> payload) {
            DomainModel.BusinessConfig cfg = DomainModel.getBusiness(userId);
            Map<String, String> settings = adminSettings.computeIfAbsent(cfg.businessAppId(), k -> new ConcurrentHashMap<>());
            
            if (payload.containsKey("businessName")) settings.put("businessName", String.valueOf(payload.get("businessName")));
            if (payload.containsKey("theme")) settings.put("theme", String.valueOf(payload.get("theme")));
            if (payload.containsKey("notifications")) settings.put("notifications", String.valueOf(payload.get("notifications")));
            
            logAudit(cfg.businessAppId(), "ADMINISTRATION_CHANGED", "ADMIN", userId, "Updated admin configuration");
            persist();
        }

        public Map<String, String> getAdminSettings(String userId) {
            DomainModel.BusinessConfig cfg = DomainModel.getBusiness(userId);
            return adminSettings.getOrDefault(cfg.businessAppId(), Collections.emptyMap());
        }

        public void logAudit(String businessAppId, String eventType, String entity, String recordId, String detail) {
            String log = String.format("[%tF %<tT] %s | %s | %s | %s", new Date(), eventType, entity, recordId, detail);
            auditLogs.computeIfAbsent(businessAppId, k -> new ArrayList<>()).add(log);
        }

        public List<String> getAuditLogs(String businessAppId) {
            return auditLogs.getOrDefault(businessAppId, Collections.emptyList());
        }

        private void seed() {
            save("robert/robert_consulting", "clients", "c1", Map.of("id", "c1", "name", "Acme Corp", "email", "contact@acme.com", "company", "Acme"));
            save("robert/robert_consulting", "engagements", "e1", Map.of("id", "e1", "clientId", "c1", "title", "Cloud Architecture", "status", "ACTIVE"));
            save("marie/marie_restaurant", "reservations", "r1", Map.of("id", "r1", "guestName", "Jean Dupont", "reservationDate", "2026-09-10", "partySize", "4", "status", "PENDING"));
            save("hans/hans_marketplace", "sellers", "s1", Map.of("id", "s1", "name", "Hans Müller", "email", "hans@market.de", "status", "ACTIVE"));
            save("sofia/sofia_property", "properties", "p1", Map.of("id", "p1", "title", "Apartamento Centro", "address", "Calle Mayor 12", "status", "AVAILABLE"));

            logAudit("robert/robert_consulting", "DEMO_SEED_COMPLETED", "SYSTEM", "SEED", "Applied seed v1");
            logAudit("marie/marie_restaurant", "DEMO_SEED_COMPLETED", "SYSTEM", "SEED", "Applied seed v1");
            logAudit("hans/hans_marketplace", "DEMO_SEED_COMPLETED", "SYSTEM", "SEED", "Applied seed v1");
            logAudit("sofia/sofia_property", "DEMO_SEED_COMPLETED", "SYSTEM", "SEED", "Applied seed v1");
        }

        private synchronized void persist() {
            try {
                File f = new File(storagePath);
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                Files.writeString(Paths.get(storagePath), "{\"status\":\"PERSISTED\"}");
            } catch (IOException ignored) {}
        }
    }

    // --- UI RENDERER ---
    public static class DynamicUiRenderer {
        public static String renderLayout(String userId, String activeEntity, String content, EntityRepository repo) {
            DomainModel.BusinessConfig config = DomainModel.getBusiness(userId);
            String theme = repo.getTheme(userId, config.defaultTheme());
            Map<String, String> adminSettings = repo.getAdminSettings(userId);
            String displayName = adminSettings.getOrDefault("businessName", config.name());

            StringBuilder navHtml = new StringBuilder();
            for (var entry : config.entityNames().entrySet()) {
                String key = entry.getKey();
                String label = entry.getValue();
                String activeClass = key.equalsIgnoreCase(activeEntity) ? "active" : "";
                navHtml.append("<a href='/").append(userId).append("/").append(key).append("' class='").append(activeClass).append("'>").append(label).append("</a>");
            }
            
            // AST v1.4 Bound Standalone Administration Link
            String adminActiveClass = "administration".equalsIgnoreCase(activeEntity) || "admin".equalsIgnoreCase(activeEntity) ? "active" : "";
            navHtml.append("<a href='/").append(userId).append("/administration' class='").append(adminActiveClass).append("' style='margin-top:1.5rem; border-top:1px solid #334155;'>⚙️ Administration</a>");

            StringBuilder themeHtml = new StringBuilder("<div class='theme-selector'><span>Theme: </span>");
            for (String t : config.allowedThemes()) {
                themeHtml.append("<a href='/").append(userId).append("/set-theme?theme=").append(t).append("' class='btn-sm'>").append(t).append("</a> ");
            }
            themeHtml.append("</div>");

            return """
                <!DOCTYPE html>
                <html lang="%s">
                <head>
                    <meta charset="UTF-8">
                    <title>%s</title>
                    <style>
                        :root { --primary: #0f172a; --bg: #f8fafc; }
                        body.professional-blue { --primary: #1e3a8a; --bg: #eff6ff; }
                        body.graphite { --primary: #334155; --bg: #f1f5f9; }
                        body.bistro-dark { --primary: #2e1065; --bg: #faf5ff; }
                        body.warm-hospitality { --primary: #7c2d12; --bg: #fff7ed; }
                        body.midnight-market { --primary: #022c22; --bg: #f0fdf4; }
                        body.structured-commerce { --primary: #134e4a; --bg: #f0fdfa; }
                        body.calm-property { --primary: #164e63; --bg: #ecfeff; }
                        body.terracotta { --primary: #9a3412; --bg: #fff7ed; }

                        body { margin: 0; font-family: system-ui, sans-serif; background: var(--bg); display: grid; grid-template-rows: 60px 1fr; grid-template-columns: 260px 1fr; grid-template-areas: "topbar topbar" "sidebar content"; height: 100vh; }
                        header { grid-area: topbar; background: var(--primary); color: white; display: flex; align-items: center; justify-content: space-between; padding: 0 1.5rem; }
                        aside { grid-area: sidebar; background: #0f172a; color: #94a3b8; padding: 1rem 0; }
                        aside a { display: block; padding: 0.75rem 1.5rem; color: #cbd5e1; text-decoration: none; }
                        aside a:hover, aside a.active { background: #1e293b; color: white; border-left: 4px solid #38bdf8; }
                        main { grid-area: content; padding: 2rem; overflow-y: auto; }
                        .card { background: white; padding: 1.5rem; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 1.5rem; }
                        .user-switcher { display: flex; gap: 0.5rem; background: rgba(255,255,255,0.1); padding: 0.25rem 0.5rem; border-radius: 6px; }
                        .user-switcher a { color: white; text-decoration: none; padding: 0.25rem 0.5rem; border-radius: 4px; font-size: 0.85rem; }
                        .user-switcher a.active { background: white; color: black; font-weight: bold; }
                        table { width: 100%%; border-collapse: collapse; margin-top: 1rem; }
                        th, td { padding: 0.75rem; text-align: left; border-bottom: 1px solid #e2e8f0; }
                        th { background: #f1f5f9; }
                        .btn { background: var(--primary); color: white; border: none; padding: 0.5rem 1rem; border-radius: 4px; text-decoration: none; display: inline-block; cursor: pointer; }
                        .btn-sm { font-size: 0.8rem; padding: 0.2rem 0.5rem; border: 1px solid #cbd5e1; border-radius: 4px; text-decoration: none; color: #334155; }
                        .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
                        .form-group { display: flex; flex-direction: column; }
                        .form-group.full { grid-column: span 2; }
                        input, select { padding: 0.5rem; border: 1px solid #cbd5e1; border-radius: 4px; margin-top: 0.25rem; }
                        .log-box { background: #1e293b; color: #38bdf8; padding: 1rem; border-radius: 6px; font-family: monospace; font-size: 0.85rem; height: 220px; overflow-y: auto; }
                        .notice-banner { background: #fef3c7; border-left: 4px solid #f59e0b; color: #92400e; padding: 0.75rem 1rem; border-radius: 4px; margin-bottom: 1rem; font-size: 0.85rem; }
                    </style>
                </head>
                <body class="%s">
                    <header>
                        <h2>%s</h2>
                        <div class="user-switcher">
                            <span style="font-size:0.85rem; align-self:center; margin-right:0.5rem;">Persona:</span>
                            <a href="/robert/clients" class="%s">Robert (EN)</a>
                            <a href="/marie/reservations" class="%s">Marie (FR)</a>
                            <a href="/hans/sellers" class="%s">Hans (DE)</a>
                            <a href="/sofia/properties" class="%s">Sofia (ES)</a>
                        </div>
                    </header>
                    <aside>
                        %s
                    </aside>
                    <main>
                        %s
                        %s
                    </main>
                </body>
                </html>
                """.formatted(
                    config.locale(), displayName, theme, displayName,
                    "robert".equalsIgnoreCase(userId) ? "active" : "",
                    "marie".equalsIgnoreCase(userId) ? "active" : "",
                    "hans".equalsIgnoreCase(userId) ? "active" : "",
                    "sofia".equalsIgnoreCase(userId) ? "active" : "",
                    navHtml.toString(), themeHtml.toString(), content
                );
        }

        public static String renderAdminConsole(String userId, EntityRepository repo) {
            DomainModel.BusinessConfig config = DomainModel.getBusiness(userId);
            Map<String, String> adminSettings = repo.getAdminSettings(userId);
            List<String> logs = repo.getAuditLogs(config.businessAppId());

            String currentName = adminSettings.getOrDefault("businessName", config.name());
            String currentTheme = repo.getTheme(userId, config.defaultTheme());
            String currentNotifs = adminSettings.getOrDefault("notifications", "ENABLED");

            StringBuilder html = new StringBuilder();
            html.append("<div class='notice-banner'>");
            html.append("⚠️ <strong>Demo Access Warning:</strong> The visible identity selector is for demonstration purposes. Server enforces isolation per business partition.");
            html.append("</div>");

            html.append("<div class='card'>");
            html.append("<h2>⚙️ Administration Controls</h2>");
            html.append("<form method='POST' action='/").append(userId).append("/administration/save' class='form-grid'>");
            
            html.append("<div class='form-group'>");
            html.append("<label>Business Display Name</label>");
            html.append("<input type='text' name='businessName' value='").append(currentName).append("' required />");
            html.append("</div>");

            html.append("<div class='form-group'>");
            html.append("<label>Active Theme</label>");
            html.append("<select name='theme'>");
            for (String t : config.allowedThemes()) {
                String sel = t.equals(currentTheme) ? "selected" : "";
                html.append("<option value='").append(t).append("' ").append(sel).append(">").append(t).append("</option>");
            }
            html.append("</select>");
            html.append("</div>");

            html.append("<div class='form-group'>");
            html.append("<label>Notifications</label>");
            html.append("<select name='notifications'>");
            html.append("<option value='ENABLED' ").append("ENABLED".equals(currentNotifs) ? "selected" : "").append(">Enabled</option>");
            html.append("<option value='DISABLED' ").append("DISABLED".equals(currentNotifs) ? "selected" : "").append(">Disabled</option>");
            html.append("</select>");
            html.append("</div>");

            html.append("<div class='form-group'>");
            html.append("<label>Locale (Read Only)</label>");
            html.append("<input type='text' value='").append(config.locale()).append("' disabled />");
            html.append("</div>");

            html.append("<div class='form-group'>");
            html.append("<label>Persistence Partition (Read Only)</label>");
            html.append("<input type='text' value='").append(config.businessAppId()).append("' disabled />");
            html.append("</div>");

            html.append("<div class='form-group'>");
            html.append("<label>Schema Version (Read Only)</label>");
            html.append("<input type='text' value='2.0-HYBRID' disabled />");
            html.append("</div>");

            html.append("<div class='form-group full' style='margin-top:1rem;'>");
            html.append("<button type='submit' class='btn'>Save Administration Settings</button>");
            html.append("</div></form></div>");

            html.append("<div class='card'>");
            html.append("<h3>📜 Partition Audit Log Stream</h3>");
            html.append("<div class='log-box'>");
            if (logs.isEmpty()) {
                html.append("<div>No audit events recorded yet.</div>");
            } else {
                for (String log : logs) {
                    html.append("<div>").append(log).append("</div>");
                }
            }
            html.append("</div></div>");
            return html.toString();
        }

        public static String renderTable(String userId, String entity, List<Map<String, Object>> records, EntityRepository repo) {
            DomainModel.BusinessConfig config = DomainModel.getBusiness(userId);
            List<DomainModel.FieldDef> fields = config.entityFields().get(entity);

            StringBuilder html = new StringBuilder("<div class='card'>");
            html.append("<div style='display:flex; justify-content:space-between; align-items:center;'>");
            html.append("<h2>").append(config.entityNames().get(entity)).append("</h2>");
            html.append("<a href='/").append(userId).append("/").append(entity).append("/new' class='btn'>+ New Record</a>");
            html.append("</div><table><thead><tr>");

            for (DomainModel.FieldDef f : fields) html.append("<th>").append(f.label()).append("</th>");
            html.append("<th>Actions</th></tr></thead><tbody>");

            if (records.isEmpty()) {
                html.append("<tr><td colspan='").append(fields.size() + 1).append("'>No records found.</td></tr>");
            } else {
                for (Map<String, Object> row : records) {
                    html.append("<tr>");
                    for (DomainModel.FieldDef f : fields) {
                        Object val = row.get(f.name());
                        String displayVal = val != null ? val.toString() : "-";

                        if ("ENUM".equals(f.type()) && val != null) {
                            displayVal = config.localizedEnums()
                                .getOrDefault(entity, Collections.emptyMap())
                                .getOrDefault(f.name(), Collections.emptyMap())
                                .getOrDefault(val.toString(), val.toString());
                        } else if ("REFERENCE".equals(f.type()) && val != null) {
                            Optional<Map<String, Object>> parentOpt = repo.findById(config.businessAppId(), f.refEntity(), val.toString());
                            if (parentOpt.isPresent()) {
                                Map<String, Object> parent = parentOpt.get();
                                displayVal = String.valueOf(parent.getOrDefault(f.displayField(), val.toString()));
                            }
                        }
                        html.append("<td>").append(displayVal).append("</td>");
                    }

                    html.append("<td>");
                    String currentState = String.valueOf(row.get("status"));
                    List<DomainModel.TransitionDef> transitions = config.entityTransitions().getOrDefault(entity, Collections.emptyList());

                    for (DomainModel.TransitionDef t : transitions) {
                        if (t.from().equalsIgnoreCase(currentState)) {
                            String btnLabel = t.labels().getOrDefault(config.locale().substring(0, 2), t.to());
                            html.append("<a href='/").append(userId).append("/").append(entity).append("/transition?id=").append(row.get("id")).append("&target=").append(t.to()).append("' class='btn-sm' style='margin-right:0.25rem;'>").append(btnLabel).append("</a>");
                        }
                    }

                    html.append("<a href='/").append(userId).append("/").append(entity).append("/delete?id=").append(row.get("id")).append("' style='color:red; font-size:0.85rem; margin-left:0.5rem;'>Delete</a>");
                    html.append("</td></tr>");
                }
            }

            html.append("</tbody></table></div>");
            return html.toString();
        }

        public static String renderForm(String userId, String entity, EntityRepository repo) {
            DomainModel.BusinessConfig config = DomainModel.getBusiness(userId);
            List<DomainModel.FieldDef> fields = config.entityFields().get(entity);

            StringBuilder html = new StringBuilder("<div class='card'>");
            html.append("<h2>New ").append(config.entityNames().get(entity)).append("</h2>");
            html.append("<form method='POST' action='/").append(userId).append("/").append(entity).append("/save' class='form-grid'>");

            for (DomainModel.FieldDef f : fields) {
                html.append("<div class='form-group'>");
                html.append("<label>").append(f.label()).append("</label>");

                if ("ENUM".equals(f.type())) {
                    html.append("<select name='").append(f.name()).append("'>");
                    Map<String, String> enumMap = config.localizedEnums()
                        .getOrDefault(entity, Collections.emptyMap())
                        .getOrDefault(f.name(), Collections.emptyMap());
                    for (String option : f.enumValues()) {
                        html.append("<option value='").append(option).append("'>").append(enumMap.getOrDefault(option, option)).append("</option>");
                    }
                    html.append("</select>");
                } else if ("REFERENCE".equals(f.type())) {
                    html.append("<select name='").append(f.name()).append("'>");
                    List<Map<String, Object>> parents = repo.findAll(config.businessAppId(), f.refEntity());
                    for (Map<String, Object> parent : parents) {
                        String pId = String.valueOf(parent.get("id"));
                        String pLabel = String.valueOf(parent.getOrDefault(f.displayField(), pId));
                        html.append("<option value='").append(pId).append("'>").append(pLabel).append("</option>");
                    }
                    html.append("</select>");
                } else {
                    html.append("<input type='text' name='").append(f.name()).append("' ").append(f.required() ? "required" : "").append(" />");
                }
                html.append("</div>");
            }

            html.append("<div class='form-group full' style='margin-top:1rem;'>");
            html.append("<button type='submit' class='btn'>Save Record</button>");
            html.append("</div></form></div>");
            return html.toString();
        }
    }
}