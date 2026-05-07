package com.example.currencyexchange.service;

import com.example.currencyexchange.DatabaseConnection;
import com.example.currencyexchange.util.AlertUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExchangeRateAutoUpdateService {
    private static final String BNM_ENDPOINT = "https://www.bnm.md/en/official_exchange_rates";
    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Chisinau");
    private static final DateTimeFormatter API_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final BigDecimal SELL_RATE_MARKUP = new BigDecimal("0.20");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();
    private final ScheduledExecutorService scheduler;
    private volatile boolean started;

    public ExchangeRateAutoUpdateService() {
        this(Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "exchange-rate-auto-update");
            thread.setDaemon(true);
            return thread;
        }));
    }

    private ExchangeRateAutoUpdateService(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    public static LocalDate todayInAppZone() {
        return LocalDate.now(APP_ZONE);
    }

    public static int updateRatesForDate(LocalDate date) throws IOException, InterruptedException, SQLException {
        return new ExchangeRateAutoUpdateService(null).fetchAndStoreRates(date);
    }

    public synchronized void start() {
        if (started || scheduler == null) {
            return;
        }

        started = true;
        updateTodayInBackground();
        scheduleNextMidnight();
    }

    public synchronized void stop() {
        started = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void updateTodayInBackground() {
        scheduler.execute(() -> updateSafely(todayInAppZone(), true));
    }

    private void scheduleNextMidnight() {
        if (!started || scheduler == null || scheduler.isShutdown()) {
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(APP_ZONE);
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(APP_ZONE);
        long delayMillis = Math.max(1, Duration.between(now, nextMidnight).toMillis());

        scheduler.schedule(() -> {
            updateSafely(todayInAppZone(), true);
            scheduleNextMidnight();
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void updateSafely(LocalDate date, boolean notify) {
        try {
            int changed = fetchAndStoreRates(date);
            if (notify && changed > 0) {
                AlertUtil.info("Курсы BNM", "Курсы за " + date + " обновлены: " + changed);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            notifyFailure(notify, e);
        } catch (IOException | SQLException | RuntimeException e) {
            notifyFailure(notify, e);
        }
    }

    private void notifyFailure(boolean notify, Exception e) {
        if (notify && started) {
            AlertUtil.warning("Курсы BNM", "Не удалось обновить курсы автоматически: " + e.getMessage());
        }
    }

    private int fetchAndStoreRates(LocalDate date) throws IOException, InterruptedException, SQLException {
        Map<String, BigDecimal> officialRates = fetchOfficialRates(date);
        if (officialRates.isEmpty()) {
            return 0;
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            Set<String> currencyCodes = loadCurrencyCodes(connection);
            int changed = 0;
            for (String currencyCode : currencyCodes) {
                BigDecimal rate = officialRates.get(currencyCode);
                if (rate != null) {
                    changed += upsertRate(connection, currencyCode, date, rate);
                }
            }
            return changed;
        }
    }

    private Map<String, BigDecimal> fetchOfficialRates(LocalDate date) throws IOException, InterruptedException {
        String url = BNM_ENDPOINT + "?date=" + API_DATE_FORMAT.format(date) + "&get_xml=1";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("BNM вернул HTTP " + response.statusCode());
        }

        try {
            return parseOfficialRates(response.body());
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("BNM вернул некорректный XML", e);
        }
    }

    private Map<String, BigDecimal> parseOfficialRates(byte[] xmlBytes)
            throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
        document.getDocumentElement().normalize();

        Map<String, BigDecimal> rates = new HashMap<>();
        NodeList nodes = document.getElementsByTagName("Valute");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element valute = (Element) nodes.item(i);
            String code = textOf(valute, "CharCode").toUpperCase(Locale.ROOT);
            BigDecimal nominal = parseDecimal(textOf(valute, "Nominal"));
            BigDecimal value = parseDecimal(textOf(valute, "Value"));

            if (!code.isBlank() && nominal.signum() > 0 && value.signum() > 0) {
                rates.put(code, value.divide(nominal, 8, RoundingMode.HALF_UP));
            }
        }
        return rates;
    }

    private String textOf(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.replace(',', '.').trim());
    }

    private Set<String> loadCurrencyCodes(Connection connection) throws SQLException {
        String sql = "SELECT currency_code FROM currencies WHERE currency_code <> 'MDL' ORDER BY currency_code";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            Set<String> currencyCodes = new HashSet<>();
            while (resultSet.next()) {
                currencyCodes.add(resultSet.getString("currency_code").toUpperCase(Locale.ROOT));
            }
            return currencyCodes;
        }
    }

    private int upsertRate(Connection connection, String currencyCode, LocalDate date, BigDecimal rate)
            throws SQLException {
        BigDecimal sellRate = rate.add(SELL_RATE_MARKUP);
        String updateSql = "UPDATE exchange_rates SET buy_rate_mdl=?, sell_rate_mdl=? WHERE currency_code=? AND rate_date=?";
        try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
            statement.setBigDecimal(1, rate);
            statement.setBigDecimal(2, sellRate);
            statement.setString(3, currencyCode);
            statement.setDate(4, Date.valueOf(date));
            int updated = statement.executeUpdate();
            if (updated > 0) {
                return updated;
            }
        }

        String insertSql = "INSERT INTO exchange_rates(currency_code, rate_date, buy_rate_mdl, sell_rate_mdl) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            statement.setString(1, currencyCode);
            statement.setDate(2, Date.valueOf(date));
            statement.setBigDecimal(3, rate);
            statement.setBigDecimal(4, sellRate);
            return statement.executeUpdate();
        }
    }
}
