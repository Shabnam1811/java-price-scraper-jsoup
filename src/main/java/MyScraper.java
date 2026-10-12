
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MyScraper extends Application {

    private static final int HTTP_TIMEOUT_MS = 15_000;
    private static final Pattern PRICE_PATTERN = Pattern.compile("(\\$|USD\\s?)\\s?\\d{1,6}(?:[,\\.]\\d{3})*(?:\\.\\d{2})?");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "for", "with", "and", "or", "to", "of", "in", "on", "at", "by"
    );

    @Override
    public void start(Stage stage) {
        TextField productField = new TextField();
        productField.setPromptText("Enter product name (example: iPhone 15 128GB)");
        productField.setPrefWidth(600);
        productField.setStyle(
                "-fx-background-color: #1e293b;" +
                        "-fx-text-fill: #e2e8f0;" +
                        "-fx-prompt-text-fill: #94a3b8;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-color: #334155;" +
                        "-fx-padding: 10 12 10 12;"
        );

        Button compareButton = new Button("Compare Prices");
        compareButton.setStyle(
                "-fx-background-color: linear-gradient(to right, #0ea5e9, #8b5cf6);" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 10;" +
                        "-fx-padding: 8 18 8 18;" +
                        "-fx-cursor: hand;"
        );

        Label statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12px;");
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(20, 20);
        progressIndicator.setVisible(false);

        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setWrapText(true);
        resultArea.setPromptText("Search results will appear here...");
        resultArea.setPrefHeight(480);
        resultArea.setStyle(
                "-fx-control-inner-background: #0f172a;" +
                        "-fx-text-fill: #e2e8f0;" +
                        "-fx-prompt-text-fill: #64748b;" +
                        "-fx-highlight-fill: #334155;" +
                        "-fx-highlight-text-fill: #f8fafc;" +
                        "-fx-background-radius: 12;" +
                        "-fx-border-radius: 12;" +
                        "-fx-border-color: #334155;" +
                        "-fx-font-family: 'Consolas';" +
                        "-fx-font-size: 12px;"
        );

        VBox linksBox = new VBox(6);
        linksBox.setStyle("-fx-padding: 4 0 0 0;");

        compareButton.setOnAction(event -> {
            String productQuery = productField.getText().trim();
            if (productQuery.isEmpty()) {
                statusLabel.setText("Please enter a product name.");
                return;
            }

            compareButton.setDisable(true);
            statusLabel.setText("Searching websites...");
            progressIndicator.setVisible(true);
            resultArea.clear();
            linksBox.getChildren().clear();

            Task<ComparisonOutput> comparisonTask = new Task<>() {
                @Override
                protected ComparisonOutput call() {
                    return runComparison(productQuery);
                }
            };

            comparisonTask.setOnSucceeded(e -> {
                ComparisonOutput output = comparisonTask.getValue();
                resultArea.setText(output.reportText());
                renderClickableLinks(output.results(), linksBox, statusLabel);
                statusLabel.setText("Done");
                compareButton.setDisable(false);
                progressIndicator.setVisible(false);
            });

            comparisonTask.setOnFailed(e -> {
                Throwable ex = comparisonTask.getException();
                String message = ex == null ? "Unknown error" : ex.getMessage();
                resultArea.setText("Search failed:\n" + message);
                statusLabel.setText("Error");
                compareButton.setDisable(false);
                progressIndicator.setVisible(false);
            });

            Thread worker = new Thread(comparisonTask);
            worker.setDaemon(true);
            worker.start();
        });

        Label titleLabel = new Label("PricePilot");
        titleLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 28px; -fx-font-weight: bold;");
        Label subtitleLabel = new Label("Compare product prices across eCommerce websites");
        subtitleLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");

        Label inputLabel = new Label("Product name");
        inputLabel.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 13px;");
        HBox actionBox = new HBox(10, compareButton, progressIndicator, statusLabel);
        actionBox.setAlignment(Pos.CENTER_LEFT);

        VBox controlsCard = new VBox(
                12,
                inputLabel,
                productField,
                actionBox
        );
        controlsCard.setPadding(new Insets(16));
        controlsCard.setStyle(
                "-fx-background-color: rgba(15,23,42,0.86);" +
                        "-fx-background-radius: 14;" +
                        "-fx-border-color: #334155;" +
                        "-fx-border-radius: 14;"
        );

        Label resultsLabel = new Label("Live results");
        resultsLabel.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 13px;");

        Label linksLabel = new Label("Clickable links");
        linksLabel.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 12px;");
        VBox resultsCard = new VBox(8, resultsLabel, resultArea, linksLabel, linksBox);
        resultsCard.setPadding(new Insets(16));
        resultsCard.setStyle(
                "-fx-background-color: rgba(15,23,42,0.86);" +
                        "-fx-background-radius: 14;" +
                        "-fx-border-color: #334155;" +
                        "-fx-border-radius: 14;"
        );
        VBox.setVgrow(resultArea, Priority.ALWAYS);
        VBox.setVgrow(resultsCard, Priority.ALWAYS);

        VBox root = new VBox(
                16,
                titleLabel,
                subtitleLabel,
                new Separator(),
                controlsCard,
                resultsCard
        );
        root.setPadding(new Insets(22));
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #020617, #0f172a);");

        stage.setTitle("Product Price Comparator");
        stage.setScene(new Scene(root, 900, 760));
        stage.show();
    }

    private ComparisonOutput runComparison(String productQuery) {
        List<EcommerceSite> sites = buildSites();
        String encodedQuery = URLEncoder.encode(productQuery, StandardCharsets.UTF_8);
        String queryLower = productQuery.toLowerCase(Locale.ROOT);
        List<String> queryTokens = extractQueryTokens(productQuery);
        List<String> output = new ArrayList<>();
        List<PriceResult> foundResults = new ArrayList<>();
        List<String> unavailableSites = new ArrayList<>();

        output.add("Product: " + productQuery);
        output.add("Sites checked: " + sites.size());
        output.add("");

        for (EcommerceSite site : sites) {
            String targetUrl = site.searchUrlTemplate.replace("{query}", encodedQuery);
            output.add("--------------------------------------------------");
            output.add("Website: " + site.name);
            output.add("Search URL: " + targetUrl);

            try {
                Document doc = Jsoup.connect(targetUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .referrer("https://www.google.com/")
                        .timeout(HTTP_TIMEOUT_MS)
                        .get();

                String pageText = doc.body() == null ? "" : doc.body().text();
                if (pageText.isBlank()) {
                    output.add("Result: No readable text returned by website.");
                    unavailableSites.add(site.name);
                    continue;
                }

                PriceMatch match = findSiteSpecificPrice(doc, site, queryTokens, queryLower);
                if (match == null) {
                    match = findPriceNearQuery(doc, queryLower, queryTokens);
                }

                if (match != null) {
                    double numericPrice = toPriceValue(match.priceText());
                    boolean exactProductLink = match.productUrl() != null
                            && !match.productUrl().isBlank()
                            && !match.productUrl().equals(targetUrl);
                    String finalUrl = exactProductLink ? match.productUrl() : targetUrl;
                    foundResults.add(new PriceResult(site.name, match.priceText(), numericPrice, finalUrl, exactProductLink));
                    output.add("Detected price: " + match.priceText());
                    output.add("Product link: " + finalUrl);
                    output.add("Link confidence: " + (exactProductLink ? "Exact product link" : "Search fallback link"));
                } else {
                    output.add("Price not detected.");
                    unavailableSites.add(site.name);
                }
            } catch (Exception ex) {
                output.add("Result: Could not fetch website (" + sanitizeMessage(ex.getMessage()) + ")");
                unavailableSites.add(site.name);
            }
        }

        foundResults.sort(Comparator.comparingDouble(PriceResult::numericPrice));

        output.add("--------------------------------------------------");
        output.add("Price list (lowest to highest):");
        if (foundResults.isEmpty()) {
            output.add("No prices found from the selected websites.");
        } else {
            for (int i = 0; i < foundResults.size(); i++) {
                PriceResult result = foundResults.get(i);
                output.add((i + 1) + ". " + result.siteName + " -> " + result.priceText
                        + " [" + (result.exactProductLink ? "Exact product link" : "Search fallback link") + "] "
                        + "(" + result.sourceUrl + ")");
            }
            PriceResult bestDeal = foundResults.get(0);
            output.add("");
            output.add("Recommended for buyer: " + bestDeal.siteName + " (lowest detected price: " + bestDeal.priceText + ")");
        }

        if (!unavailableSites.isEmpty()) {
            output.add("");
            output.add("No clear price from: " + String.join(", ", unavailableSites));
        }

        output.add("");
        output.add("Note: Some websites block scraping or load prices dynamically. Results are best-effort.");
        return new ComparisonOutput(String.join("\n", output), foundResults);
    }

    private void renderClickableLinks(List<PriceResult> results, VBox linksBox, Label statusLabel) {
        linksBox.getChildren().clear();
        if (results.isEmpty()) {
            Label noLinksLabel = new Label("No links found.");
            noLinksLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
            linksBox.getChildren().add(noLinksLabel);
            return;
        }

        for (PriceResult result : results) {
            String url = result.sourceUrl();
            String confidence = result.exactProductLink() ? "Exact product link" : "Search fallback link";
            String text = result.siteName() + " - " + result.priceText() + " (" + confidence + ")";
            Hyperlink link = new Hyperlink(text);
            link.setStyle("-fx-text-fill: #7dd3fc;");
            link.setOnAction(event -> openLink(url, statusLabel));
            Tooltip.install(link, new Tooltip(url));
            linksBox.getChildren().add(link);
        }
    }

    private void openLink(String url, Label statusLabel) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                statusLabel.setText("Opening links is not supported on this system.");
                return;
            }
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ex) {
            statusLabel.setText("Could not open link: " + sanitizeMessage(ex.getMessage()));
        }
    }

    private PriceMatch findPriceNearQuery(Document doc, String queryLower, List<String> queryTokens) {
        Elements elements = doc.select("body *");
        for (Element element : elements) {
            String text = element.text();
            if (text == null || text.isBlank()) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            if (!matchesQuery(lower, queryLower, queryTokens)) {
                continue;
            }
            Matcher matcher = PRICE_PATTERN.matcher(text);
            if (matcher.find()) {
                String productUrl = findProductUrlFromElement(element, doc.baseUri());
                return new PriceMatch(matcher.group(), productUrl);
            }
        }
        return null;
    }

    private PriceMatch findSiteSpecificPrice(Document doc, EcommerceSite site, List<String> queryTokens, String queryLower) {
        for (String selector : site.priceSelectors) {
            Elements candidates = doc.select(selector);
            for (Element candidate : candidates) {
                String rowText = collectPriceContext(candidate);
                String rowLower = rowText.toLowerCase(Locale.ROOT);
                String candidatePrice = extractFirstPrice(rowText);
                if (candidatePrice.equals("Not found")) {
                    continue;
                }
                if (isStrongProductMatch(rowLower, queryLower, queryTokens, site.name())) {
                    String productUrl = findProductUrlFromElement(candidate, doc.baseUri());
                    return new PriceMatch(candidatePrice, productUrl);
                }
            }
        }
        return null;
    }

    private String findProductUrlFromElement(Element element, String fallbackUrl) {
        if (element == null) {
            return fallbackUrl;
        }
        Element directAnchor = element.selectFirst("a[href]");
        if (directAnchor != null) {
            String href = directAnchor.attr("abs:href");
            if (!href.isBlank()) {
                return href;
            }
        }
        Element current = element;
        for (int i = 0; i < 4 && current != null; i++) {
            Element parentAnchor = current.parent() == null ? null : current.parent().selectFirst("a[href]");
            if (parentAnchor != null) {
                String href = parentAnchor.attr("abs:href");
                if (!href.isBlank()) {
                    return href;
                }
            }
            current = current.parent();
        }
        return fallbackUrl;
    }

    private String collectPriceContext(Element baseElement) {
        String own = baseElement.text();
        String parent = baseElement.parent() == null ? "" : baseElement.parent().text();
        String sibling = "";
        if (baseElement.previousElementSibling() != null) {
            sibling += baseElement.previousElementSibling().text() + " ";
        }
        if (baseElement.nextElementSibling() != null) {
            sibling += baseElement.nextElementSibling().text();
        }
        return (own + " " + parent + " " + sibling).trim();
    }

    private List<String> extractQueryTokens(String productQuery) {
        return List.of(productQuery.toLowerCase(Locale.ROOT).split("\\s+"))
                .stream()
                .map(token -> token.replaceAll("[^a-z0-9]", ""))
                .filter(token -> token.length() >= 3)
                .filter(token -> !STOP_WORDS.contains(token))
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean matchesQuery(String textLower, String queryLower, List<String> queryTokens) {
        if (textLower.contains(queryLower)) {
            return true;
        }
        int matches = 0;
        for (String token : queryTokens) {
            if (textLower.contains(token)) {
                matches++;
            }
        }
        return matches >= Math.min(2, queryTokens.size());
    }

    private boolean isStrongProductMatch(String textLower, String queryLower, List<String> queryTokens, String siteName) {
        if (textLower.contains(queryLower)) {
            return true;
        }
        int matches = 0;
        for (String token : queryTokens) {
            if (textLower.contains(token)) {
                matches++;
            }
        }
        int requiredMatches;
        if ("AliExpress".equals(siteName) || "eBay".equals(siteName)) {
            requiredMatches = queryTokens.isEmpty() ? 0 : 1;
        } else {
            requiredMatches = queryTokens.size() <= 2 ? queryTokens.size() : 2;
        }
        return matches >= requiredMatches;
    }

    private boolean containsAnyQueryToken(String textLower, List<String> queryTokens) {
        for (String token : queryTokens) {
            if (textLower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String extractFirstPrice(String text) {
        Matcher matcher = PRICE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return "Not found";
    }

    private double toPriceValue(String detectedPrice) {
        String cleaned = detectedPrice
                .replace("USD", "")
                .replace("$", "")
                .replace(",", "")
                .trim();
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ex) {
            return Double.MAX_VALUE;
        }
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }
        return message.replace('\n', ' ').trim();
    }

    private List<EcommerceSite> buildSites() {
        return List.of(
                new EcommerceSite(
                        "Amazon",
                        "https://www.amazon.com/s?k={query}",
                        List.of("[data-component-type='s-search-result']", ".s-result-item", ".a-price")
                ),
                new EcommerceSite(
                        "Newegg",
                        "https://www.newegg.com/p/pl?d={query}",
                        List.of(".item-cell", ".item-info", ".price-current", ".price-current-label")
                ),
                new EcommerceSite(
                        "Wayfair",
                        "https://www.wayfair.com/keyword.php?keyword={query}",
                        List.of(".ProductCard", ".BasePriceBlock", ".notranslate", ".ProductCardPrice")
                )
        );
    }

    private record PriceResult(String siteName, String priceText, double numericPrice, String sourceUrl,
                               boolean exactProductLink) {
    }

    private record PriceMatch(String priceText, String productUrl) {
    }

    private record ComparisonOutput(String reportText, List<PriceResult> results) {
    }

    private record EcommerceSite(String name, String searchUrlTemplate, List<String> priceSelectors) {
    }

    public static void main(String[] args) {
        launch(args);
    }
}
