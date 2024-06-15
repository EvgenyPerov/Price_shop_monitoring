package org.home.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.home.dto.ResponseDto;
import org.home.dao.Info;
import org.home.dao.Product;
import org.home.repository.InfoRepository;
import org.home.repository.ProductRepository;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.select.Selector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceService {

    @Value("${url_file.source.path}")
    private String path;

    @Value("${url_file.update.time}")
    private String time;

    private final ProductRepository productRepository;
    private final InfoRepository infoRepository;


    @Scheduled(fixedRateString = "${url_file.update.time}", timeUnit = TimeUnit.DAYS)
    public void update(){
        log.info("Обновление запущено");
        List<String> strings = checkDataErrors(path);
        updateDataInDb(strings);
    }

    private void updateDataInDb(List<String> strings) {
        List<Product> productsForUpdate = new ArrayList<>();
        for (int i = 2; i < strings.size() - 2; i+=3) {
            if (strings.get(i).isBlank()) {
                i-=2;
                continue;
            }

            String name = strings.get(i);
            String url = strings.get(i+1);
            String tag = strings.get(i+2);

            Optional<Product> productFromDB = productRepository.findByNameAndUrl(name, url);

            LocalDate now = LocalDate.now();
            if (productFromDB.isPresent()) {
                Product product = productFromDB.get();
                if (product.getLastUpdateDate().plusDays(Long.parseLong(time)).isBefore(now)) { //если прошло N дней
                    product.setLastUpdateDate(now);
                    productsForUpdate.add(product);
                    createInfo(now, product);
                }
            } else {
                Product product = createProduct(name, url, tag, now);
                log.info("Создан продукт: " + product);
                createInfo(now, product);
            }
        }
        productRepository.saveAll(productsForUpdate);
        log.info("Обновлено {} позиций ", productsForUpdate.size());
    }

    @NotNull
    private Product createProduct(String name, String url, String tag, LocalDate now) {
        Product product = productRepository.save(Product.builder()
            .name(name)
            .url(url)
            .tag(tag)
            .lastUpdateDate(now)
            .build());
        return product;
    }

    private void createInfo(LocalDate now, Product product) {
        float price = getActualPrice(product);
       infoRepository.save(Info.builder()
            .date(now)
            .price(price)
            .product(product)
            .build());
    }

    @NotNull
    private static List<String> checkDataErrors(String path) {
        List<String> strings = null;
        File file = new File(path);
        try {
            strings = Files.readAllLines(file.toPath());
        } catch (IOException e) {
            log.info("Ошибка с чтением файла с данными. Прочитайте инструкцию в файле");
        }

        if (strings == null || strings.size()<3) {
            throw new RuntimeException("Не найдены данные для обработки в файле с данными. Прочитайте инструкцию в файле");
        }
        return strings;
    }

    private float getActualPrice(Product product) {
        Response response = null;
        String body;
        float price = 0;

        OkHttpClient client = new OkHttpClient().newBuilder().build();

            Request request = new Request.Builder().url(product.getUrl()).method("GET", null).build();
        Elements elements = null;
            try {
                response = client.newCall(request).execute();
                body = response.body().string();
                Document doc = Jsoup.parse(body);
                elements = doc.select(product.getTag());
            } catch (IOException e) {
                throw new RuntimeException("Ошибка выполнения HTTP запроса и получения данных.Проверьте корректность адреса: " + product.getUrl());
            } catch (Selector.SelectorParseException e) {
                log.info("Для товара \"" + product.getName() + "\" цена не определена, проверьте корректность Selector либо выберите другой магазин");
            }

        if (elements != null && !elements.isEmpty()) {
            Element element = elements.first();
            element.select("sup").remove();
//            System.out.println("Извлеченная цена: " + element.text());

            price = extractPrice(element.text());
//            System.out.println("Цена товара чистая: " + product.getName() + " = " + price);

        }
        response.close();
        return price;
    }

    public static float extractPrice(String priceText) {
        String deletedCube = priceText.replaceAll("m3", "").replaceAll("M3","").replaceAll("м3", "").replaceAll("М3", "")
            .replaceAll("m2", "").replaceAll("M2","").replaceAll("м2", "").replaceAll("М2", "");

        String cleanedText = deletedCube.replaceAll("[^0-9,.]", "").trim();
        // Проверяем, содержит ли строка запятую
        if (cleanedText.contains(",")) {
            cleanedText = cleanedText.replace(",", ".");
        }

        try {
            return Float.parseFloat(cleanedText);
        } catch (NumberFormatException e) {
            // Если не удалось напрямую преобразовать, попробуем найти число в тексте
            Pattern pattern = Pattern.compile("\\d+(?:\\.\\d+)?");
            Matcher matcher = pattern.matcher(cleanedText);
            if (matcher.find()) {
                return Float.parseFloat(matcher.group());
            } else {
                return 0f; // Вернуть 0, если не удалось извлечь число
            }
        }
    }

    public Map<String, Map<LocalDate, Float>> getResults(){
        List<ResponseDto> listDB =  productRepository.getProductInfo();

        if (listDB == null || listDB.isEmpty()) {
            return null;
        }

        Set<Integer> uniqueIdSet = listDB.stream().map(ResponseDto::getId).collect(Collectors.toSet());
        Map<String, Map<LocalDate, Float>> graphData = new TreeMap<>();

        for (Integer index : uniqueIdSet){
            listDB.forEach(item -> {
                if (Objects.equals(index, item.getId())) {

                    String key = String.valueOf(item.getId()).concat("-").concat(item.getName());

                    if (graphData.containsKey(key)) {
                        Map<LocalDate, Float> dataMap = graphData.get(key);
                        dataMap.put(item.getDate(), item.getPrice());
                    } else {
                        Map<LocalDate, Float> dataMap = new TreeMap<>();
                        dataMap.put(item.getDate(), item.getPrice());
                        graphData.put(key, dataMap);
                    }

                }
            });
        }
        return graphData;
    }
}
